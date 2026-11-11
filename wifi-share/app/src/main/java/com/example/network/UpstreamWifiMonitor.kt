package com.example.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.NetworkInterfaceInfo
import com.example.model.PermissionStatusState
import com.example.model.SsidVisibilityStatus
import com.example.model.UpstreamWifiState
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import kotlin.math.roundToInt

data class UpstreamProbeResult(
    val reachable: Boolean,
    val latencyMs: Long,
    val resolvedIp: String?,
    val detail: String
)

class UpstreamWifiMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _state = MutableStateFlow(UpstreamWifiState())
    val state: StateFlow<UpstreamWifiState> = _state.asStateFlow()

    private val _permissionStatus = MutableStateFlow(PermissionStatusState())
    val permissionStatus: StateFlow<PermissionStatusState> = _permissionStatus.asStateFlow()

    @Volatile
    var activeWifiNetwork: Network? = null
        private set

    @Volatile
    private var latestWifiCaps: NetworkCapabilities? = null

    @Volatile
    private var latestLinkProps: LinkProperties? = null

    @Volatile
    private var defaultIsCellular: Boolean = false

    @Volatile
    private var cellularNetworkPresent: Boolean = false

    private var registered = false

    private val wifiCallback: ConnectivityManager.NetworkCallback = createWifiNetworkCallback()
    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network)
            defaultIsCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            refreshState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            defaultIsCellular =
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            refreshState()
        }

        override fun onLost(network: Network) {
            defaultIsCellular = false
            refreshState()
        }
    }

    private val cellularCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            cellularNetworkPresent = true
            refreshState()
        }

        override fun onLost(network: Network) {
            cellularNetworkPresent = false
            refreshState()
        }
    }

    private fun createWifiNetworkCallback(): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) {
                    activeWifiNetwork = network
                    latestWifiCaps = connectivityManager.getNetworkCapabilities(network)
                    latestLinkProps = connectivityManager.getLinkProperties(network)
                    refreshState()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    activeWifiNetwork = network
                    latestWifiCaps = networkCapabilities
                    refreshState()
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    activeWifiNetwork = network
                    latestLinkProps = linkProperties
                    refreshState()
                }

                override fun onLost(network: Network) {
                    if (activeWifiNetwork == network) {
                        activeWifiNetwork = null
                        latestWifiCaps = null
                        latestLinkProps = null
                    }
                    refreshState()
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    activeWifiNetwork = network
                    latestWifiCaps = connectivityManager.getNetworkCapabilities(network)
                    latestLinkProps = connectivityManager.getLinkProperties(network)
                    refreshState()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    activeWifiNetwork = network
                    latestWifiCaps = networkCapabilities
                    refreshState()
                }

                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties
                ) {
                    activeWifiNetwork = network
                    latestLinkProps = linkProperties
                    refreshState()
                }

                override fun onLost(network: Network) {
                    if (activeWifiNetwork == network) {
                        activeWifiNetwork = null
                        latestWifiCaps = null
                        latestLinkProps = null
                    }
                    refreshState()
                }
            }
        }
    }

    fun startMonitoring() {
        refreshPermissions()
        if (registered) {
            refreshState()
            return
        }
        registered = true

        runCatching {
            val wifiRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback)

            val cellRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            connectivityManager.registerNetworkCallback(cellRequest, cellularCallback)

            connectivityManager.registerDefaultNetworkCallback(defaultCallback)
        }

        // Prime initial snapshot synchronously
        seedInitialNetworkSnapshot()
        refreshState()
    }

    fun stopMonitoring() {
        if (!registered) return
        registered = false
        runCatching { connectivityManager.unregisterNetworkCallback(wifiCallback) }
        runCatching { connectivityManager.unregisterNetworkCallback(cellularCallback) }
        runCatching { connectivityManager.unregisterNetworkCallback(defaultCallback) }
    }

    fun refreshPermissions(): PermissionStatusState {
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { locationManager.isLocationEnabled }.getOrDefault(false)
        } else {
            true
        }

        val status = PermissionStatusState(
            nearbyWifiGranted = nearbyGranted,
            fineLocationGranted = fineLocationGranted,
            notificationsGranted = notificationsGranted,
            isLocationServiceEnabled = locationEnabled
        )
        _permissionStatus.value = status
        return status
    }

    @Suppress("DEPRECATION")
    private fun seedInitialNetworkSnapshot() {
        runCatching {
            val active = connectivityManager.activeNetwork
            val activeCaps = active?.let { connectivityManager.getNetworkCapabilities(it) }
            if (activeCaps != null && activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                activeWifiNetwork = active
                latestWifiCaps = activeCaps
                latestLinkProps = connectivityManager.getLinkProperties(active)
            }
            defaultIsCellular =
                activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun refreshState() {
        val perms = refreshPermissions()
        val wifiNet = activeWifiNetwork
        val caps = latestWifiCaps ?: wifiNet?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProps = latestLinkProps ?: wifiNet?.let { connectivityManager.getLinkProperties(it) }

        val wifiConnected = wifiNet != null &&
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false

        val wifiInfo: WifiInfo? = if (wifiConnected) {
            val transportWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                caps?.transportInfo as? WifiInfo
            } else {
                null
            }
            transportWifi ?: runCatching { wifiManager.connectionInfo }.getOrNull()
        } else {
            null
        }

        val rawSsid = wifiInfo?.ssid?.trim('"').orEmpty()
        val isSsidRedacted = rawSsid.isBlank() || rawSsid.equals("<unknown ssid>", ignoreCase = true)

        val visibilityStatus = when {
            !wifiConnected -> SsidVisibilityStatus.DISCONNECTED
            !isSsidRedacted -> SsidVisibilityStatus.VISIBLE
            !perms.fineLocationGranted -> SsidVisibilityStatus.HIDDEN_PERMISSION_NEEDED
            !perms.isLocationServiceEnabled -> SsidVisibilityStatus.HIDDEN_LOCATION_DISABLED
            else -> SsidVisibilityStatus.HIDDEN_PERMISSION_NEEDED
        }

        val displaySsid = when (visibilityStatus) {
            SsidVisibilityStatus.VISIBLE -> rawSsid
            SsidVisibilityStatus.HIDDEN_PERMISSION_NEEDED -> "Wi-Fi Connected (SSID hidden)"
            SsidVisibilityStatus.HIDDEN_LOCATION_DISABLED -> "Wi-Fi Connected (Location off)"
            SsidVisibilityStatus.DISCONNECTED -> "Not connected"
        }

        val bssid = wifiInfo?.bssid?.takeIf {
            it.isNotBlank() && it != "02:00:00:00:00:00"
        } ?: "--"

        val rssi = wifiInfo?.rssi ?: -127
        val signalPct = if (wifiConnected && rssi > -127) Formatters.rssiToPercent(rssi) else 0
        val linkSpeed = wifiInfo?.linkSpeed ?: 0
        val txSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo?.txLinkSpeedMbps ?: linkSpeed
        } else {
            linkSpeed
        }
        val rxSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo?.rxLinkSpeedMbps ?: linkSpeed
        } else {
            linkSpeed
        }

        val freq = wifiInfo?.frequency ?: 0
        val channel = Formatters.frequencyToChannel(freq)
        val band = Formatters.frequencyToBandLabel(freq)
        val wifiStandard = resolveWifiStandard(wifiInfo)

        val ipv4Link = linkProps?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val ipv4Addr = ipv4Link?.address?.hostAddress ?: "--"
        val prefixLen = ipv4Link?.prefixLength ?: 0

        val gateway = linkProps?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
            ?: linkProps?.routes?.firstOrNull { it.gateway is Inet4Address }?.gateway?.hostAddress
            ?: "--"

        val dns = linkProps?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        val ifaceName = linkProps?.interfaceName ?: "--"

        val allInterfaces = enumerateNetworkInterfaces()

        _state.update {
            UpstreamWifiState(
                isWifiConnected = wifiConnected,
                hasInternetCapability = hasInternet,
                isValidatedInternet = isValidated,
                isMetered = isMetered,
                isCellularAvailable = cellularNetworkPresent,
                isCellularDefaultNetwork = defaultIsCellular,
                ssid = displaySsid,
                bssid = bssid,
                ssidVisibilityStatus = visibilityStatus,
                rssiDbm = rssi,
                signalPercent = signalPct,
                linkSpeedMbps = linkSpeed,
                txLinkSpeedMbps = txSpeed,
                rxLinkSpeedMbps = rxSpeed,
                frequencyMhz = freq,
                channelNumber = channel,
                bandLabel = band,
                wifiStandardLabel = wifiStandard,
                ipv4Address = ipv4Addr,
                subnetPrefixLength = prefixLen,
                gatewayAddress = gateway,
                dnsServers = dns,
                interfaceName = ifaceName,
                activeInterfaces = allInterfaces
            )
        }
    }

    private fun resolveWifiStandard(wifiInfo: WifiInfo?): String {
        if (wifiInfo == null) return "Unknown"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Wi-Fi (Legacy API)"
        return when (wifiInfo.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi 1/2/3 (802.11a/b/g)"
            ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
            ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
            ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 / 6E (802.11ax)"
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    wifiInfo.wifiStandard == ScanResult.WIFI_STANDARD_11BE
                ) {
                    "Wi-Fi 7 (802.11be)"
                } else {
                    "Unknown"
                }
            }
        }
    }

    fun enumerateNetworkInterfaces(): List<NetworkInterfaceInfo> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { !it.isLoopback }
                .mapNotNull { iface ->
                    val addrs = iface.inetAddresses?.toList().orEmpty()
                    val v4 = addrs.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
                    val v6 = addrs.filterIsInstance<Inet6Address>().mapNotNull {
                        it.hostAddress?.substringBefore('%')
                    }
                    if (v4.isEmpty() && v6.isEmpty()) {
                        null
                    } else {
                        NetworkInterfaceInfo(
                            name = iface.name,
                            ipv4Addresses = v4,
                            ipv6Addresses = v6,
                            isUp = runCatching { iface.isUp }.getOrDefault(false)
                        )
                    }
                }
                .sortedBy { it.name }
        }.getOrDefault(emptyList())
    }

    suspend fun verifyUpstreamConnectivity(): UpstreamProbeResult = withContext(Dispatchers.IO) {
        val network = activeWifiNetwork
            ?: return@withContext UpstreamProbeResult(
                reachable = false,
                latencyMs = 0L,
                resolvedIp = null,
                detail = "No active upstream Wi-Fi network handle."
            )

        val startNs = System.nanoTime()
        return@withContext runCatching {
            val addresses = network.getAllByName("connectivitycheck.gstatic.com")
            val targetIp = addresses.firstOrNull()
                ?: return@runCatching UpstreamProbeResult(
                    reachable = false,
                    latencyMs = 0L,
                    resolvedIp = null,
                    detail = "DNS resolution failed on upstream Wi-Fi."
                )

            network.socketFactory.createSocket().use { socket ->
                socket.soTimeout = 4_000
                socket.connect(InetSocketAddress(targetIp, 443), 4_000)
            }
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000.0).roundToInt().toLong()
            UpstreamProbeResult(
                reachable = true,
                latencyMs = elapsedMs,
                resolvedIp = targetIp.hostAddress,
                detail = "Verified TCP/443 upstream Wi-Fi reachability in ${elapsedMs}ms."
            )
        }.getOrElse { err ->
            UpstreamProbeResult(
                reachable = false,
                latencyMs = 0L,
                resolvedIp = null,
                detail = "Wi-Fi probe failed: ${err.localizedMessage ?: err.javaClass.simpleName}"
            )
        }
    }
}
