package com.example.network

import android.net.Network
import com.example.model.ClientSource
import com.example.model.ConnectedClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class RelayRuntimeSnapshot(
    val isRunning: Boolean = false,
    val httpPort: Int = 8080,
    val socksPort: Int = 1080,
    val socksRunning: Boolean = false,
    val txBytes: Long = 0L,
    val rxBytes: Long = 0L,
    val activeTunnels: Int = 0,
    val totalRequests: Long = 0L,
    val blockedCellularAttempts: Int = 0,
    val clients: List<ConnectedClient> = emptyList()
)

/**
 * Unprivileged user-space HTTP/HTTPS CONNECT & optional SOCKS5 relay server.
 *
 * Outbound connections are explicitly resolved and dialed through the provided
 * upstream Wi-Fi [Network] handle (`network.getAllByName` + `network.socketFactory`)
 * so traffic cannot silently leak onto mobile cellular data when Wi-Fi-Only Safety
 * Mode is active.
 */
class WifiBoundRelayServer(
    private val upstreamNetworkProvider: () -> Network?,
    private val wifiOnlySafetyProvider: () -> Boolean
) {
    private var scope: CoroutineScope? = null
    private var httpServerSocket: ServerSocket? = null
    private var socksServerSocket: ServerSocket? = null

    private val totalTxBytes = AtomicLong(0L)
    private val totalRxBytes = AtomicLong(0L)
    private val activeTunnels = AtomicInteger(0)
    private val totalRequests = AtomicLong(0L)
    private val blockedCellularAttempts = AtomicInteger(0)

    private val blockedIps = ConcurrentHashMap.newKeySet<String>()
    private val clientRecords = ConcurrentHashMap<String, MutableClientStats>()

    private val _snapshot = MutableStateFlow(RelayRuntimeSnapshot())
    val snapshot: StateFlow<RelayRuntimeSnapshot> = _snapshot.asStateFlow()

    private class MutableClientStats(
        val ipAddress: String,
        val connectedAtEpochMs: Long
    ) {
        val bytesSent = AtomicLong(0L)
        val bytesReceived = AtomicLong(0L)
        val activeConns = AtomicInteger(0)
    }

    fun start(
        httpPort: Int,
        enableSocks5: Boolean,
        socksPort: Int
    ): Result<Unit> {
        stop()

        return runCatching {
            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = serverScope

            val httpSock = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), httpPort), 64)
            }
            httpServerSocket = httpSock

            var socksSock: ServerSocket? = null
            if (enableSocks5 && socksPort != httpPort) {
                socksSock = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), socksPort), 64)
                }
                socksServerSocket = socksSock
            }

            serverScope.launch { acceptLoop(serverScope, httpSock, isSocks = false) }
            if (socksSock != null) {
                serverScope.launch { acceptLoop(serverScope, socksSock, isSocks = true) }
            }

            emitSnapshot(isRunning = true, httpPort = httpPort, socksPort = socksPort, socksRunning = socksSock != null)
        }.onFailure {
            stop()
        }
    }

    fun stop() {
        runCatching { httpServerSocket?.close() }
        runCatching { socksServerSocket?.close() }
        httpServerSocket = null
        socksServerSocket = null
        scope?.cancel()
        scope = null
        activeTunnels.set(0)
        emitSnapshot(isRunning = false, httpPort = 8080, socksPort = 1080, socksRunning = false)
    }

    fun setClientBlocked(ipAddress: String, blocked: Boolean) {
        if (blocked) {
            blockedIps.add(ipAddress)
        } else {
            blockedIps.remove(ipAddress)
        }
        publishSnapshotOnly()
    }

    fun refreshSnapshot() {
        publishSnapshotOnly()
    }

    private fun acceptLoop(
        serverScope: CoroutineScope,
        serverSocket: ServerSocket,
        isSocks: Boolean
    ) {
        while (serverScope.isActive && !serverSocket.isClosed) {
            try {
                val client = serverSocket.accept()
                val remoteIp = client.inetAddress.hostAddress ?: "unknown"
                if (blockedIps.contains(remoteIp)) {
                    runCatching { client.close() }
                    continue
                }
                serverScope.launch {
                    if (isSocks) {
                        handleSocks5Client(this, client, remoteIp)
                    } else {
                        handleHttpClient(this, client, remoteIp)
                    }
                }
            } catch (_: IOException) {
                break
            }
        }
    }

    private suspend fun handleHttpClient(
        tunnelScope: CoroutineScope,
        clientSocket: Socket,
        clientIp: String
    ) {
        val stats = clientRecords.getOrPut(clientIp) {
            MutableClientStats(clientIp, System.currentTimeMillis())
        }
        stats.activeConns.incrementAndGet()
        activeTunnels.incrementAndGet()
        publishSnapshotOnly()

        var upstreamSocket: Socket? = null
        try {
            clientSocket.soTimeout = 30_000
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val headerBytes = readHttpHeaderBlock(clientIn) ?: return
            val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
            val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) return

            val requestParts = lines[0].split(" ")
            if (requestParts.size < 3) {
                writeHttpError(clientOut, 400, "Bad Request")
                return
            }

            val method = requestParts[0].uppercase()
            val target = requestParts[1]
            val version = requestParts[2]

            totalRequests.incrementAndGet()

            if (blockedIps.contains(clientIp)) {
                writeHttpError(clientOut, 403, "Blocked by host")
                return
            }

            val wifiNetwork = upstreamNetworkProvider()
            if (wifiNetwork == null && wifiOnlySafetyProvider()) {
                blockedCellularAttempts.incrementAndGet()
                writeHttpError(
                    clientOut,
                    503,
                    "Wi-Fi Safety Guard: Upstream Wi-Fi unavailable; cellular data blocked"
                )
                return
            }

            if (method == "CONNECT") {
                val hostPort = parseHostPort(target, defaultPort = 443)
                if (hostPort == null) {
                    writeHttpError(clientOut, 400, "Invalid CONNECT authority")
                    return
                }
                upstreamSocket = openUpstreamSocket(hostPort.first, hostPort.second, wifiNetwork)
                clientOut.write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: WiFiShare/1.0\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                clientOut.flush()

                clientSocket.soTimeout = 0
                upstreamSocket.soTimeout = 0
                relayBidirectional(tunnelScope, clientSocket, upstreamSocket, stats)
            } else {
                val parsed = parseAbsoluteHttpUri(target, lines.drop(1))
                if (parsed == null) {
                    writeHttpError(clientOut, 400, "Unsupported proxy URL")
                    return
                }
                upstreamSocket = openUpstreamSocket(parsed.host, parsed.port, wifiNetwork)
                val outboundHeaders = buildForwardedHttpHeader(method, parsed.pathAndQuery, version, parsed.hostHeader, lines.drop(1))
                val upstreamOut = upstreamSocket.getOutputStream()
                upstreamOut.write(outboundHeaders)
                upstreamOut.flush()
                totalTxBytes.addAndGet(outboundHeaders.size.toLong())
                stats.bytesSent.addAndGet(outboundHeaders.size.toLong())

                clientSocket.soTimeout = 0
                upstreamSocket.soTimeout = 0
                relayBidirectional(tunnelScope, clientSocket, upstreamSocket, stats)
            }
        } catch (_: Exception) {
            runCatching {
                clientSocket.getOutputStream().let {
                    writeHttpError(it, 502, "Upstream Wi-Fi dial failed")
                }
            }
        } finally {
            runCatching { upstreamSocket?.close() }
            runCatching { clientSocket.close() }
            stats.activeConns.decrementAndGet()
            activeTunnels.decrementAndGet()
            publishSnapshotOnly()
        }
    }

    private suspend fun handleSocks5Client(
        tunnelScope: CoroutineScope,
        clientSocket: Socket,
        clientIp: String
    ) {
        val stats = clientRecords.getOrPut(clientIp) {
            MutableClientStats(clientIp, System.currentTimeMillis())
        }
        stats.activeConns.incrementAndGet()
        activeTunnels.incrementAndGet()
        publishSnapshotOnly()

        var upstreamSocket: Socket? = null
        try {
            clientSocket.soTimeout = 15_000
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            val ver = input.read()
            val nMethods = input.read()
            if (ver != 0x05 || nMethods <= 0) return
            val methods = ByteArray(nMethods)
            readFully(input, methods)
            if (!methods.contains(0x00.toByte())) {
                output.write(byteArrayOf(0x05, 0xFF.toByte()))
                return
            }
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            val reqHead = ByteArray(4)
            readFully(input, reqHead)
            if (reqHead[0].toInt() != 0x05 || reqHead[1].toInt() != 0x01) {
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                return
            }

            val host: String = when (reqHead[3].toInt() and 0xFF) {
                0x01 -> {
                    val addr = ByteArray(4)
                    readFully(input, addr)
                    InetAddress.getByAddress(addr).hostAddress ?: return
                }
                0x03 -> {
                    val len = input.read()
                    if (len <= 0) return
                    val dom = ByteArray(len)
                    readFully(input, dom)
                    String(dom, StandardCharsets.UTF_8)
                }
                0x04 -> {
                    val addr = ByteArray(16)
                    readFully(input, addr)
                    InetAddress.getByAddress(addr).hostAddress ?: return
                }
                else -> return
            }

            val portBytes = ByteArray(2)
            readFully(input, portBytes)
            val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            totalRequests.incrementAndGet()

            val wifiNetwork = upstreamNetworkProvider()
            if (wifiNetwork == null && wifiOnlySafetyProvider()) {
                blockedCellularAttempts.incrementAndGet()
                output.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                return
            }

            upstreamSocket = openUpstreamSocket(host, port, wifiNetwork)
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()

            clientSocket.soTimeout = 0
            upstreamSocket.soTimeout = 0
            relayBidirectional(tunnelScope, clientSocket, upstreamSocket, stats)
        } catch (_: Exception) {
            // Connection terminated cleanly
        } finally {
            runCatching { upstreamSocket?.close() }
            runCatching { clientSocket.close() }
            stats.activeConns.decrementAndGet()
            activeTunnels.decrementAndGet()
            publishSnapshotOnly()
        }
    }

    private fun openUpstreamSocket(host: String, port: Int, wifiNetwork: Network?): Socket {
        if (wifiNetwork != null) {
            val resolved = wifiNetwork.getAllByName(host)
            val targetAddr = resolved.firstOrNull { it is Inet4Address }
                ?: resolved.firstOrNull { it is Inet6Address }
                ?: resolved.first()
            return wifiNetwork.socketFactory.createSocket().apply {
                connect(InetSocketAddress(targetAddr, port), 10_000)
            }
        }
        // Only reached when Wi-Fi-Only Safety Mode is explicitly disabled by user
        return Socket().apply {
            connect(InetSocketAddress(host, port), 10_000)
        }
    }

    private suspend fun relayBidirectional(
        parentScope: CoroutineScope,
        clientSocket: Socket,
        upstreamSocket: Socket,
        stats: MutableClientStats
    ) {
        val upJob: Job = parentScope.launch(Dispatchers.IO) {
            pipeBytes(
                input = clientSocket.getInputStream(),
                output = upstreamSocket.getOutputStream(),
                onBytes = { count ->
                    totalTxBytes.addAndGet(count)
                    stats.bytesSent.addAndGet(count)
                }
            )
            runCatching { upstreamSocket.shutdownOutput() }
        }
        val downJob: Job = parentScope.launch(Dispatchers.IO) {
            pipeBytes(
                input = upstreamSocket.getInputStream(),
                output = clientSocket.getOutputStream(),
                onBytes = { count ->
                    totalRxBytes.addAndGet(count)
                    stats.bytesReceived.addAndGet(count)
                }
            )
            runCatching { clientSocket.shutdownOutput() }
        }
        upJob.join()
        downJob.join()
    }

    private fun pipeBytes(
        input: InputStream,
        output: OutputStream,
        onBytes: (Long) -> Unit
    ) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = try {
                input.read(buffer)
            } catch (_: IOException) {
                -1
            }
            if (read <= 0) break
            try {
                output.write(buffer, 0, read)
                output.flush()
                onBytes(read.toLong())
            } catch (_: IOException) {
                break
            }
        }
    }

    private fun readHttpHeaderBlock(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream(1024)
        var state = 0
        while (out.size() < 32 * 1024) {
            val b = input.read()
            if (b == -1) return null
            out.write(b)
            state = when (state) {
                0 -> if (b == '\r'.code) 1 else 0
                1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                2 -> if (b == '\r'.code) 3 else 0
                3 -> if (b == '\n'.code) return out.toByteArray() else 0
                else -> 0
            }
        }
        return null
    }

    private fun readFully(input: InputStream, dest: ByteArray) {
        var offset = 0
        while (offset < dest.size) {
            val read = input.read(dest, offset, dest.size - offset)
            if (read == -1) throw EOFException("Unexpected EOF")
            offset += read
        }
    }

    private fun writeHttpError(out: OutputStream, code: Int, message: String) {
        val body = "$code $message\n"
        val response = "HTTP/1.1 $code $message\r\n" +
            "Content-Type: text/plain\r\n" +
            "Content-Length: ${body.length}\r\n" +
            "Connection: close\r\n\r\n$body"
        out.write(response.toByteArray(StandardCharsets.ISO_8859_1))
        out.flush()
    }

    private fun parseHostPort(authority: String, defaultPort: Int): Pair<String, Int>? {
        val trimmed = authority.trim()
        if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx <= 1) return null
            val host = trimmed.substring(1, closeIdx)
            val port = trimmed.substring(closeIdx + 1)
                .removePrefix(":")
                .toIntOrNull() ?: defaultPort
            return host to port
        }
        val colonIdx = trimmed.lastIndexOf(':')
        return if (colonIdx > 0) {
            val host = trimmed.substring(0, colonIdx)
            val port = trimmed.substring(colonIdx + 1).toIntOrNull() ?: return null
            host to port
        } else {
            trimmed to defaultPort
        }
    }

    private data class ParsedHttpTarget(
        val host: String,
        val port: Int,
        val pathAndQuery: String,
        val hostHeader: String
    )

    private fun parseAbsoluteHttpUri(
        target: String,
        headers: List<String>
    ): ParsedHttpTarget? {
        if (target.startsWith("http://", ignoreCase = true)) {
            val noScheme = target.substring(7)
            val slashIdx = noScheme.indexOf('/')
            val authority = if (slashIdx >= 0) noScheme.substring(0, slashIdx) else noScheme
            val path = if (slashIdx >= 0) noScheme.substring(slashIdx) else "/"
            val (host, port) = parseHostPort(authority, 80) ?: return null
            return ParsedHttpTarget(host, port, path, authority)
        }
        val hostHeader = headers.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
            ?.substringAfter(':')?.trim() ?: return null
        val (host, port) = parseHostPort(hostHeader, 80) ?: return null
        return ParsedHttpTarget(host, port, target, hostHeader)
    }

    private fun buildForwardedHttpHeader(
        method: String,
        path: String,
        version: String,
        hostHeader: String,
        originalHeaders: List<String>
    ): ByteArray {
        val sb = StringBuilder()
        sb.append("$method $path $version\r\n")
        var sawHost = false
        for (h in originalHeaders) {
            val lower = h.lowercase()
            if (lower.startsWith("proxy-connection:") || lower.startsWith("proxy-authorization:")) {
                continue
            }
            if (lower.startsWith("host:")) sawHost = true
            sb.append(h).append("\r\n")
        }
        if (!sawHost) {
            sb.append("Host: ").append(hostHeader).append("\r\n")
        }
        sb.append("\r\n")
        return sb.toString().toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun emitSnapshot(
        isRunning: Boolean,
        httpPort: Int,
        socksPort: Int,
        socksRunning: Boolean
    ) {
        _snapshot.value = RelayRuntimeSnapshot(
            isRunning = isRunning,
            httpPort = httpPort,
            socksPort = socksPort,
            socksRunning = socksRunning,
            txBytes = totalTxBytes.get(),
            rxBytes = totalRxBytes.get(),
            activeTunnels = activeTunnels.get(),
            totalRequests = totalRequests.get(),
            blockedCellularAttempts = blockedCellularAttempts.get(),
            clients = buildClientsList()
        )
    }

    private fun publishSnapshotOnly() {
        val prev = _snapshot.value
        _snapshot.value = prev.copy(
            txBytes = totalTxBytes.get(),
            rxBytes = totalRxBytes.get(),
            activeTunnels = activeTunnels.get(),
            totalRequests = totalRequests.get(),
            blockedCellularAttempts = blockedCellularAttempts.get(),
            clients = buildClientsList()
        )
    }

    private fun buildClientsList(): List<ConnectedClient> {
        return clientRecords.values.map { rec ->
            ConnectedClient(
                id = "relay_${rec.ipAddress}",
                displayName = "Proxy Client (${rec.ipAddress})",
                ipAddress = rec.ipAddress,
                macAddress = "--",
                source = ClientSource.PROXY_RELAY,
                connectedAtEpochMs = rec.connectedAtEpochMs,
                bytesSent = rec.bytesSent.get(),
                bytesReceived = rec.bytesReceived.get(),
                activeConnections = rec.activeConns.get(),
                isBlocked = blockedIps.contains(rec.ipAddress)
            )
        }.sortedByDescending { it.activeConnections }
    }
}
