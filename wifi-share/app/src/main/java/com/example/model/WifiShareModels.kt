package com.example.model

import com.example.ui.theme.AppThemeMode

enum class SharingEngineMode(
    val title: String,
    val shortLabel: String,
    val summary: String
) {
    LOCAL_HOTSPOT_RELAY(
        title = "Local Hotspot + Wi-Fi Relay",
        shortLabel = "Local Relay",
        summary = "Starts an Android LocalOnlyHotspot and bridges HTTP/HTTPS/SOCKS5 traffic strictly over your upstream Wi-Fi interface."
    ),
    WIFI_DIRECT_RELAY(
        title = "Wi-Fi Direct + Wi-Fi Relay",
        shortLabel = "Wi-Fi Direct",
        summary = "Creates a custom-named Wi-Fi Direct group (DIRECT-WS-...) with custom password and relays traffic over upstream Wi-Fi."
    ),
    SYSTEM_HOTSPOT_BRIDGE(
        title = "System Hotspot Guide (STA+AP)",
        shortLabel = "System Bridge",
        summary = "Uses your device's built-in STA+AP Wi-Fi sharing when supported by your manufacturer, guarded by Wi-Fi-Only monitor."
    )
}

enum class SsidVisibilityStatus {
    VISIBLE,
    HIDDEN_PERMISSION_NEEDED,
    HIDDEN_LOCATION_DISABLED,
    DISCONNECTED
}

data class NetworkInterfaceInfo(
    val name: String,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val isUp: Boolean
)

data class UpstreamWifiState(
    val isWifiConnected: Boolean = false,
    val hasInternetCapability: Boolean = false,
    val isValidatedInternet: Boolean = false,
    val isMetered: Boolean = false,
    val isCellularAvailable: Boolean = false,
    val isCellularDefaultNetwork: Boolean = false,
    val ssid: String = "Not connected",
    val bssid: String = "--",
    val ssidVisibilityStatus: SsidVisibilityStatus = SsidVisibilityStatus.DISCONNECTED,
    val rssiDbm: Int = -127,
    val signalPercent: Int = 0,
    val linkSpeedMbps: Int = 0,
    val txLinkSpeedMbps: Int = 0,
    val rxLinkSpeedMbps: Int = 0,
    val frequencyMhz: Int = 0,
    val channelNumber: Int = 0,
    val bandLabel: String = "--",
    val wifiStandardLabel: String = "Unknown",
    val ipv4Address: String = "--",
    val subnetPrefixLength: Int = 0,
    val gatewayAddress: String = "--",
    val dnsServers: List<String> = emptyList(),
    val interfaceName: String = "--",
    val activeInterfaces: List<NetworkInterfaceInfo> = emptyList()
)

enum class HotspotStatus {
    IDLE,
    STARTING,
    ACTIVE,
    ERROR
}

enum class ClientSource(val label: String) {
    WIFI_DIRECT_P2P("Wi-Fi Direct"),
    PROXY_RELAY("Relay Session"),
    NEIGHBOR_TABLE("Local Neighbor")
}

data class ConnectedClient(
    val id: String,
    val displayName: String,
    val ipAddress: String,
    val macAddress: String,
    val source: ClientSource,
    val connectedAtEpochMs: Long,
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val activeConnections: Int = 0,
    val isBlocked: Boolean = false
)

data class HotspotState(
    val status: HotspotStatus = HotspotStatus.IDLE,
    val engineMode: SharingEngineMode = SharingEngineMode.LOCAL_HOTSPOT_RELAY,
    val ssid: String = "",
    val passphrase: String = "",
    val proxyHostIp: String = "",
    val httpProxyPort: Int = 8080,
    val socksProxyPort: Int = 1080,
    val socksProxyRunning: Boolean = false,
    val interfaceName: String = "",
    val startedAtEpochMs: Long = 0L,
    val errorMessage: String? = null,
    val safetyGuardPausedReason: String? = null,
    val statusNote: String = "Ready to share upstream Wi-Fi without consuming mobile data."
)

data class TrafficStatsSnapshot(
    val relayTxBytes: Long = 0L,
    val relayRxBytes: Long = 0L,
    val appTotalTxBytes: Long = 0L,
    val appTotalRxBytes: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val activeTunnelCount: Int = 0,
    val totalRequestsHandled: Long = 0L,
    val blockedCellularAttempts: Int = 0
)

enum class CompatibilityVerdict(val title: String, val badgeLabel: String) {
    NATIVE_STA_AP_READY("Native STA+AP & Relay Ready", "Full Concurrency"),
    RELAY_SUPPORTED("Relay Mode Supported (STA+AP Restricted)", "Relay Ready"),
    LIMITED_HARDWARE("Restricted Hardware / Missing Capabilities", "Limited")
}

data class CompatibilityReport(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidVersionLabel: String,
    val sdkInt: Int,
    val hardware: String,
    val socModel: String,
    val staApConcurrencySupported: Boolean?,
    val staLocalOnlyConcurrencySupported: Boolean?,
    val wifiDirectSupported: Boolean,
    val fiveGhzSupported: Boolean,
    val sixGhzSupported: Boolean,
    val verdict: CompatibilityVerdict,
    val manufacturerTitle: String,
    val manufacturerSummary: String,
    val manufacturerSteps: List<String>,
    val knownQuirks: List<String>,
    val checkedAtEpochMs: Long
)

enum class DiagnosticStatus {
    PASS,
    WARN,
    FAIL
}

enum class DiagnosticAction {
    NONE,
    REQUEST_PERMISSIONS,
    OPEN_WIFI_SETTINGS,
    OPEN_HOTSPOT_SETTINGS,
    OPEN_APP_SETTINGS,
    START_RELAY
}

data class DiagnosticStepResult(
    val id: String,
    val title: String,
    val status: DiagnosticStatus,
    val detail: String,
    val action: DiagnosticAction = DiagnosticAction.NONE
)

data class PermissionStatusState(
    val nearbyWifiGranted: Boolean = false,
    val fineLocationGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val isLocationServiceEnabled: Boolean = false
)

data class WifiShareSettings(
    val wifiOnlySafetyMode: Boolean = true,
    val autoStopOnWifiLoss: Boolean = true,
    val enableSocks5Relay: Boolean = false,
    val useForegroundService: Boolean = true,
    val preferredEngineMode: SharingEngineMode = SharingEngineMode.LOCAL_HOTSPOT_RELAY,
    val proxyPort: Int = 8080,
    val socksPort: Int = 1080,
    val p2pSsidSuffix: String = "WiFiShare",
    val p2pPassphrase: String = "ShareNet2026",
    val themeMode: AppThemeMode = AppThemeMode.DARK_NAVY,
    val onboardingCompleted: Boolean = false
)
