package com.example.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import com.example.model.ClientSource
import com.example.model.ConnectedClient
import com.example.model.HotspotState
import com.example.model.HotspotStatus
import com.example.model.SharingEngineMode
import com.example.model.TrafficStatsSnapshot
import com.example.model.WifiShareSettings
import com.example.service.HotspotForegroundService
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface

class HotspotController(
    private val context: Context,
    private val wifiMonitor: UpstreamWifiMonitor
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var currentSettings = WifiShareSettings()

    private val relayServer = WifiBoundRelayServer(
        upstreamNetworkProvider = { wifiMonitor.activeWifiNetwork },
        wifiOnlySafetyProvider = { currentSettings.wifiOnlySafetyMode }
    )

    private val _hotspotState = MutableStateFlow(HotspotState())
    val hotspotState: StateFlow<HotspotState> = _hotspotState.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    private val _trafficStats = MutableStateFlow(TrafficStatsSnapshot())
    val trafficStats: StateFlow<TrafficStatsSnapshot> = _trafficStats.asStateFlow()

    private var localOnlyReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var p2pReceiverRegistered = false
    private val p2pGroupClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    private val neighborClients = MutableStateFlow<List<ConnectedClient>>(emptyList())

    private var statsPollingJob: Job? = null
    private var previousAppTx = 0L
    private var previousAppRx = 0L

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    queryP2pGroupAndConnectionInfo()
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    queryP2pGroupAndConnectionInfo()
                }
            }
        }
    }

    init {
        controllerScope.launch {
            combine(
                relayServer.snapshot,
                p2pGroupClients,
                neighborClients
            ) { relaySnap, p2pList, neighborList ->
                mergeClientLists(relaySnap.clients, p2pList, neighborList)
            }.collect { merged ->
                _connectedClients.value = merged
            }
        }

        // Enforce Wi-Fi-Only Safety Guard dynamically if upstream Wi-Fi drops
        controllerScope.launch {
            wifiMonitor.state.collect { upstream ->
                val state = _hotspotState.value
                if (state.status == HotspotStatus.ACTIVE &&
                    currentSettings.wifiOnlySafetyMode &&
                    currentSettings.autoStopOnWifiLoss &&
                    !upstream.isWifiConnected
                ) {
                    stopSharing(
                        pausedReason = "Safety Guard stopped sharing because upstream Wi-Fi disconnected (cellular data protected)."
                    )
                }
            }
        }
    }

    fun updateSettingsSnapshot(settings: WifiShareSettings) {
        currentSettings = settings
    }

    fun startSharing(settings: WifiShareSettings) {
        currentSettings = settings
        val perms = wifiMonitor.refreshPermissions()
        val upstream = wifiMonitor.state.value

        if (settings.wifiOnlySafetyMode && !upstream.isWifiConnected) {
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    engineMode = settings.preferredEngineMode,
                    errorMessage = "Wi-Fi-Only Safety Mode blocked start: Connect to an upstream Wi-Fi network first so mobile data is never used."
                )
            }
            return
        }

        if (settings.preferredEngineMode == SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE) {
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ACTIVE,
                    engineMode = SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE,
                    ssid = "System Hotspot (Managed in Android Settings)",
                    passphrase = "",
                    proxyHostIp = upstream.ipv4Address,
                    httpProxyPort = settings.proxyPort,
                    socksProxyPort = settings.socksPort,
                    socksProxyRunning = false,
                    startedAtEpochMs = System.currentTimeMillis(),
                    errorMessage = null,
                    safetyGuardPausedReason = null,
                    statusNote = "System Hotspot Guard active: Android manages NAT routing if STA+AP is permitted; WiFi Share monitors for cellular fallback."
                )
            }
            startStatsPolling()
            if (settings.useForegroundService) {
                HotspotForegroundService.start(context)
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !perms.nearbyWifiGranted) {
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "Grant the 'Nearby Wi-Fi Devices' permission before starting a local hotspot or Wi-Fi Direct group."
                )
            }
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !perms.fineLocationGranted) {
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "Grant Location permission (required by Android 12 and below for hotspot APIs)."
                )
            }
            return
        }

        stopInternalEnginesOnly()

        _hotspotState.update {
            it.copy(
                status = HotspotStatus.STARTING,
                engineMode = settings.preferredEngineMode,
                errorMessage = null,
                safetyGuardPausedReason = null,
                statusNote = "Starting wireless access point and Wi-Fi-bound relay..."
            )
        }

        when (settings.preferredEngineMode) {
            SharingEngineMode.LOCAL_HOTSPOT_RELAY -> startLocalOnlyHotspotEngine(settings)
            SharingEngineMode.WIFI_DIRECT_RELAY -> startWifiDirectGroupEngine(settings)
            SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocalOnlyHotspotEngine(settings: WifiShareSettings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "LocalOnlyHotspot requires Android 8.0+ (API 26+)."
                )
            }
            return
        }

        val relayResult = relayServer.start(
            httpPort = settings.proxyPort,
            enableSocks5 = settings.enableSocks5Relay,
            socksPort = settings.socksPort
        )
        if (relayResult.isFailure) {
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "Could not bind proxy port ${settings.proxyPort}: ${relayResult.exceptionOrNull()?.localizedMessage}"
                )
            }
            return
        }

        runCatching {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    @Suppress("DEPRECATION")
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        localOnlyReservation = reservation
                        val ssid: String
                        val password: String
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val softAp = reservation.softApConfiguration
                            ssid = softAp?.ssid ?: "AndroidShareAP"
                            password = softAp?.passphrase.orEmpty()
                        } else {
                            val cfg = reservation.wifiConfiguration
                            ssid = cfg?.SSID ?: "AndroidShareAP"
                            password = cfg?.preSharedKey.orEmpty()
                        }

                        controllerScope.launch {
                            // Brief settle delay so OS assigns ap/wlan IPv4 before probing interfaces
                            delay(350)
                            val (iface, hostIp) = findHotspotInterfaceAndIp(
                                preferredPrefixes = listOf("swlan", "ap", "wlan"),
                                excludeIp = wifiMonitor.state.value.ipv4Address
                            )
                            _hotspotState.update {
                                it.copy(
                                    status = HotspotStatus.ACTIVE,
                                    engineMode = SharingEngineMode.LOCAL_HOTSPOT_RELAY,
                                    ssid = ssid,
                                    passphrase = password,
                                    proxyHostIp = hostIp,
                                    httpProxyPort = settings.proxyPort,
                                    socksProxyPort = settings.socksPort,
                                    socksProxyRunning = settings.enableSocks5Relay,
                                    interfaceName = iface,
                                    startedAtEpochMs = System.currentTimeMillis(),
                                    errorMessage = null,
                                    statusNote = "LocalOnlyHotspot active + Relay pinned to upstream Wi-Fi. Clients must set HTTP Proxy $hostIp:${settings.proxyPort}."
                                )
                            }
                            startStatsPolling()
                            if (settings.useForegroundService) {
                                HotspotForegroundService.start(context)
                            }
                        }
                    }

                    override fun onStopped() {
                        controllerScope.launch {
                            if (_hotspotState.value.status == HotspotStatus.ACTIVE) {
                                stopSharing(pausedReason = "System stopped the LocalOnlyHotspot.")
                            }
                        }
                    }

                    override fun onFailed(reason: Int) {
                        relayServer.stop()
                        val msg = when (reason) {
                            ERROR_NO_CHANNEL -> "No free Wi-Fi radio channel available for simultaneous LocalOnlyHotspot."
                            ERROR_INCOMPATIBLE_MODE -> "Incompatible Wi-Fi mode: disable System Portable Hotspot or Wi-Fi Direct first."
                            ERROR_TETHERING_DISALLOWED -> "Hotspot disallowed by device/enterprise policy."
                            else -> "LocalOnlyHotspot failed to start (code $reason)."
                        }
                        _hotspotState.update {
                            it.copy(
                                status = HotspotStatus.ERROR,
                                errorMessage = msg
                            )
                        }
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }.onFailure { err ->
            relayServer.stop()
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "Hotspot start error: ${err.localizedMessage ?: err.javaClass.simpleName}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWifiDirectGroupEngine(settings: WifiShareSettings) {
        val manager = wifiP2pManager
        if (manager == null) {
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "Wi-Fi Direct (WifiP2pManager) is not supported on this device."
                )
            }
            return
        }

        val relayResult = relayServer.start(
            httpPort = settings.proxyPort,
            enableSocks5 = settings.enableSocks5Relay,
            socksPort = settings.socksPort
        )
        if (relayResult.isFailure) {
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "Could not bind proxy port ${settings.proxyPort}: ${relayResult.exceptionOrNull()?.localizedMessage}"
                )
            }
            return
        }

        if (!p2pReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(
                context,
                p2pReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            p2pReceiverRegistered = true
        }

        val channel = p2pChannel ?: manager.initialize(context, Looper.getMainLooper(), null)
        p2pChannel = channel
        if (channel == null) {
            relayServer.stop()
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "Failed to initialize Wi-Fi Direct channel."
                )
            }
            return
        }

        val actionListener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                controllerScope.launch {
                    delay(450)
                    queryP2pGroupAndConnectionInfo()
                    startStatsPolling()
                    if (settings.useForegroundService) {
                        HotspotForegroundService.start(context)
                    }
                }
            }

            override fun onFailure(reason: Int) {
                relayServer.stop()
                val msg = when (reason) {
                    WifiP2pManager.BUSY -> "Wi-Fi Direct subsystem is busy. Turn Wi-Fi off and on or wait a few seconds."
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is unsupported on this hardware."
                    else -> "Failed to create Wi-Fi Direct group (code $reason)."
                }
                _hotspotState.update {
                    it.copy(
                        status = HotspotStatus.ERROR,
                        errorMessage = msg
                    )
                }
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val networkName = Formatters.buildDirectSsid(settings.p2pSsidSuffix)
                val passphrase = settings.p2pPassphrase.takeIf { it.length >= 8 } ?: "ShareNet2026"
                val config = WifiP2pConfig.Builder()
                    .setNetworkName(networkName)
                    .setPassphrase(passphrase)
                    .build()
                manager.createGroup(channel, config, actionListener)
            } else {
                manager.createGroup(channel, actionListener)
            }
        }.onFailure { err ->
            relayServer.stop()
            _hotspotState.update {
                it.copy(
                    status = HotspotStatus.ERROR,
                    errorMessage = "Wi-Fi Direct error: ${err.localizedMessage}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun queryP2pGroupAndConnectionInfo() {
        val manager = wifiP2pManager ?: return
        val channel = p2pChannel ?: return

        runCatching {
            manager.requestGroupInfo(channel) { group ->
                if (group == null) return@requestGroupInfo
                val clients = group.clientList.orEmpty().mapIndexed { idx, dev ->
                    mapP2pDeviceToClient(dev, idx)
                }
                p2pGroupClients.value = clients

                val current = _hotspotState.value
                if (current.engineMode == SharingEngineMode.WIFI_DIRECT_RELAY &&
                    (current.status == HotspotStatus.STARTING || current.status == HotspotStatus.ACTIVE)
                ) {
                    val (iface, hostIp) = findHotspotInterfaceAndIp(
                        preferredPrefixes = listOf("p2p"),
                        excludeIp = wifiMonitor.state.value.ipv4Address
                    )
                    val resolvedIp = if (hostIp == "--") "192.168.49.1" else hostIp
                    _hotspotState.update {
                        it.copy(
                            status = HotspotStatus.ACTIVE,
                            engineMode = SharingEngineMode.WIFI_DIRECT_RELAY,
                            ssid = group.networkName.orEmpty(),
                            passphrase = group.passphrase.orEmpty(),
                            proxyHostIp = resolvedIp,
                            httpProxyPort = currentSettings.proxyPort,
                            socksProxyPort = currentSettings.socksPort,
                            socksProxyRunning = currentSettings.enableSocks5Relay,
                            interfaceName = group.`interface` ?: iface,
                            startedAtEpochMs = if (it.startedAtEpochMs == 0L) System.currentTimeMillis() else it.startedAtEpochMs,
                            errorMessage = null,
                            statusNote = "Wi-Fi Direct group active + Relay pinned to upstream Wi-Fi. Set client HTTP Proxy $resolvedIp:${currentSettings.proxyPort}."
                        )
                    }
                }
            }

            manager.requestConnectionInfo(channel) { info ->
                val ownerIp = info?.groupOwnerAddress?.hostAddress
                if (!ownerIp.isNullOrBlank() &&
                    _hotspotState.value.engineMode == SharingEngineMode.WIFI_DIRECT_RELAY
                ) {
                    _hotspotState.update {
                        it.copy(
                            proxyHostIp = ownerIp,
                            statusNote = "Wi-Fi Direct group active + Relay pinned to upstream Wi-Fi. Set client HTTP Proxy $ownerIp:${currentSettings.proxyPort}."
                        )
                    }
                }
            }
        }
    }

    private fun mapP2pDeviceToClient(device: WifiP2pDevice, index: Int): ConnectedClient {
        val name = device.deviceName?.takeIf { it.isNotBlank() } ?: "Wi-Fi Direct Peer #${index + 1}"
        val mac = device.deviceAddress?.takeIf { it.isNotBlank() } ?: "--"
        return ConnectedClient(
            id = "p2p_$mac",
            displayName = name,
            ipAddress = "192.168.49.x",
            macAddress = mac,
            source = ClientSource.WIFI_DIRECT_P2P,
            connectedAtEpochMs = System.currentTimeMillis(),
            activeConnections = 1
        )
    }

    fun stopSharing(pausedReason: String? = null) {
        stopInternalEnginesOnly()
        HotspotForegroundService.stop(context)
        _hotspotState.update {
            HotspotState(
                status = HotspotStatus.IDLE,
                engineMode = currentSettings.preferredEngineMode,
                safetyGuardPausedReason = pausedReason,
                statusNote = pausedReason ?: "Sharing stopped. Mobile data remains protected."
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopInternalEnginesOnly() {
        statsPollingJob?.cancel()
        statsPollingJob = null
        relayServer.stop()

        runCatching { localOnlyReservation?.close() }
        localOnlyReservation = null

        val manager = wifiP2pManager
        val channel = p2pChannel
        if (manager != null && channel != null) {
            runCatching { manager.removeGroup(channel, null) }
        }
        p2pGroupClients.value = emptyList()
        neighborClients.value = emptyList()

        if (p2pReceiverRegistered) {
            runCatching { context.unregisterReceiver(p2pReceiver) }
            p2pReceiverRegistered = false
        }
    }

    fun toggleClientBlocked(client: ConnectedClient) {
        if (client.ipAddress.startsWith("192.168.49.x") || client.ipAddress == "--") return
        relayServer.setClientBlocked(client.ipAddress, !client.isBlocked)
    }

    fun refreshLocalHostIp() {
        val state = _hotspotState.value
        if (state.status != HotspotStatus.ACTIVE) return
        val prefixes = when (state.engineMode) {
            SharingEngineMode.WIFI_DIRECT_RELAY -> listOf("p2p")
            SharingEngineMode.LOCAL_HOTSPOT_RELAY -> listOf("swlan", "ap", "wlan")
            SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE -> listOf("swlan", "ap", "wlan")
        }
        val (iface, ip) = findHotspotInterfaceAndIp(
            preferredPrefixes = prefixes,
            excludeIp = wifiMonitor.state.value.ipv4Address
        )
        if (ip != "--") {
            _hotspotState.update { it.copy(proxyHostIp = ip, interfaceName = iface) }
        }
        if (state.engineMode == SharingEngineMode.WIFI_DIRECT_RELAY) {
            queryP2pGroupAndConnectionInfo()
        }
        refreshNeighborSnapshot()
    }

    private fun startStatsPolling() {
        statsPollingJob?.cancel()
        val uid = Process.myUid()
        previousAppTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        previousAppRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)

        statsPollingJob = controllerScope.launch {
            var tick = 0
            while (isActive) {
                delay(1_000L)
                tick++
                val currentTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
                val currentRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
                val txRate = (currentTx - previousAppTx).coerceAtLeast(0L)
                val rxRate = (currentRx - previousAppRx).coerceAtLeast(0L)
                previousAppTx = currentTx
                previousAppRx = currentRx

                relayServer.refreshSnapshot()
                val relaySnap = relayServer.snapshot.value

                _trafficStats.value = TrafficStatsSnapshot(
                    relayTxBytes = relaySnap.txBytes,
                    relayRxBytes = relaySnap.rxBytes,
                    appTotalTxBytes = currentTx,
                    appTotalRxBytes = currentRx,
                    txBytesPerSec = txRate,
                    rxBytesPerSec = rxRate,
                    activeTunnelCount = relaySnap.activeTunnels,
                    totalRequestsHandled = relaySnap.totalRequests,
                    blockedCellularAttempts = relaySnap.blockedCellularAttempts
                )

                if (tick % 4 == 0) {
                    refreshNeighborSnapshot()
                    if (_hotspotState.value.engineMode == SharingEngineMode.WIFI_DIRECT_RELAY) {
                        queryP2pGroupAndConnectionInfo()
                    } else if (_hotspotState.value.proxyHostIp == "--") {
                        refreshLocalHostIp()
                    }
                }
            }
        }
    }

    private fun refreshNeighborSnapshot() {
        controllerScope.launch(Dispatchers.IO) {
            val hotspotSubnetPrefix = _hotspotState.value.proxyHostIp
                .takeIf { it.count { ch -> ch == '.' } == 3 }
                ?.substringBeforeLast('.')

            val found = mutableListOf<ConnectedClient>()
            // Attempt /proc/net/arp safely (Android 10+ SELinux typically blocks this for non-system apps)
            runCatching {
                val arpFile = File("/proc/net/arp")
                if (arpFile.canRead()) {
                    arpFile.readLines().drop(1).forEach { line ->
                        val cols = line.trim().split(Regex("\\s+"))
                        if (cols.size >= 6) {
                            val ip = cols[0]
                            val flags = cols[2]
                            val mac = cols[3]
                            if (flags != "0x0" && mac != "00:00:00:00:00:00" &&
                                (hotspotSubnetPrefix == null || ip.startsWith(hotspotSubnetPrefix))
                            ) {
                                found.add(
                                    ConnectedClient(
                                        id = "arp_$ip",
                                        displayName = "Hotspot Client ($ip)",
                                        ipAddress = ip,
                                        macAddress = mac,
                                        source = ClientSource.NEIGHBOR_TABLE,
                                        connectedAtEpochMs = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (found.isEmpty()) {
                runCatching {
                    val proc = ProcessBuilder("ip", "neigh", "show").start()
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            val cols = line.trim().split(Regex("\\s+"))
                            val ip = cols.firstOrNull().orEmpty()
                            val lladdrIdx = cols.indexOf("lladdr")
                            val mac = if (lladdrIdx >= 0 && lladdrIdx + 1 < cols.size) cols[lladdrIdx + 1] else "--"
                            val state = cols.lastOrNull().orEmpty()
                            if (ip.contains('.') &&
                                (state == "REACHABLE" || state == "STALE" || state == "DELAY") &&
                                (hotspotSubnetPrefix == null || ip.startsWith(hotspotSubnetPrefix))
                            ) {
                                found.add(
                                    ConnectedClient(
                                        id = "neigh_$ip",
                                        displayName = "Hotspot Client ($ip)",
                                        ipAddress = ip,
                                        macAddress = mac,
                                        source = ClientSource.NEIGHBOR_TABLE,
                                        connectedAtEpochMs = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
            }

            neighborClients.value = found
        }
    }

    private fun findHotspotInterfaceAndIp(
        preferredPrefixes: List<String>,
        excludeIp: String
    ): Pair<String, String> {
        return runCatching {
            val candidates = mutableListOf<Pair<String, String>>()
            val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in ifaces) {
                if (iface.isLoopback || !iface.isUp) continue
                val name = iface.name
                val ipv4s = iface.inetAddresses?.toList().orEmpty()
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                for (ip in ipv4s) {
                    if (ip != excludeIp && isPrivateIpv4(ip)) {
                        candidates.add(name to ip)
                    }
                }
            }

            for (prefix in preferredPrefixes) {
                val match = candidates.firstOrNull { (name, _) ->
                    name.startsWith(prefix, ignoreCase = true)
                }
                if (match != null) return@runCatching match
            }

            candidates.firstOrNull { (_, ip) ->
                ip.startsWith("192.168.43.") || ip.startsWith("192.168.49.")
            } ?: candidates.firstOrNull() ?: ("--" to "--")
        }.getOrDefault("--" to "--")
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        return ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")
    }

    private fun mergeClientLists(
        relayClients: List<ConnectedClient>,
        p2pClients: List<ConnectedClient>,
        neighborList: List<ConnectedClient>
    ): List<ConnectedClient> {
        val byKey = LinkedHashMap<String, ConnectedClient>()
        for (client in p2pClients) {
            byKey[client.id] = client
        }
        for (client in neighborList) {
            byKey[client.ipAddress] = client
        }
        for (client in relayClients) {
            val existing = byKey[client.ipAddress]
            if (existing != null) {
                byKey[client.ipAddress] = client.copy(
                    macAddress = existing.macAddress,
                    displayName = if (existing.macAddress != "--") {
                        "${client.ipAddress} (${existing.macAddress})"
                    } else {
                        client.displayName
                    }
                )
            } else {
                byKey[client.ipAddress] = client
            }
        }
        return byKey.values.toList()
    }
}
