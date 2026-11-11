package com.example.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.WifiShareContainer
import com.example.compatibility.CompatibilityEngine
import com.example.data.SettingsRepository
import com.example.model.CompatibilityReport
import com.example.model.ConnectedClient
import com.example.model.DiagnosticAction
import com.example.model.DiagnosticStatus
import com.example.model.DiagnosticStepResult
import com.example.model.HotspotState
import com.example.model.HotspotStatus
import com.example.model.PermissionStatusState
import com.example.model.SharingEngineMode
import com.example.model.TrafficStatsSnapshot
import com.example.model.UpstreamWifiState
import com.example.model.WifiShareSettings
import com.example.network.HotspotController
import com.example.network.UpstreamProbeResult
import com.example.network.UpstreamWifiMonitor
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: WifiShareSettings = WifiShareSettings(),
    val settingsLoaded: Boolean = false,
    val upstreamWifi: UpstreamWifiState = UpstreamWifiState(),
    val hotspot: HotspotState = HotspotState(),
    val connectedClients: List<ConnectedClient> = emptyList(),
    val trafficStats: TrafficStatsSnapshot = TrafficStatsSnapshot(),
    val compatibilityReport: CompatibilityReport? = null,
    val permissions: PermissionStatusState = PermissionStatusState(),
    val diagnosticSteps: List<DiagnosticStepResult> = emptyList(),
    val isRunningDiagnostics: Boolean = false,
    val upstreamProbeResult: UpstreamProbeResult? = null,
    val isProbingUpstream: Boolean = false,
    val sharingElapsedMs: Long = 0L,
    val snackbarMessage: String? = null
)

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val compatibilityEngine: CompatibilityEngine,
    private val wifiMonitor: UpstreamWifiMonitor,
    private val hotspotController: HotspotController
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        wifiMonitor.startMonitoring()
        refreshCompatibility()

        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                hotspotController.updateSettingsSnapshot(settings)
                _uiState.update { it.copy(settings = settings, settingsLoaded = true) }
            }
        }

        viewModelScope.launch {
            wifiMonitor.state.collect { wifi ->
                _uiState.update { it.copy(upstreamWifi = wifi) }
            }
        }

        viewModelScope.launch {
            wifiMonitor.permissionStatus.collect { perms ->
                _uiState.update { it.copy(permissions = perms) }
            }
        }

        viewModelScope.launch {
            hotspotController.hotspotState.collect { hs ->
                _uiState.update { it.copy(hotspot = hs) }
            }
        }

        viewModelScope.launch {
            hotspotController.connectedClients.collect { clients ->
                _uiState.update { it.copy(connectedClients = clients) }
            }
        }

        viewModelScope.launch {
            hotspotController.trafficStats.collect { stats ->
                _uiState.update { it.copy(trafficStats = stats) }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                val hs = _uiState.value.hotspot
                val elapsed = if (hs.status == HotspotStatus.ACTIVE && hs.startedAtEpochMs > 0L) {
                    (System.currentTimeMillis() - hs.startedAtEpochMs).coerceAtLeast(0L)
                } else {
                    0L
                }
                if (_uiState.value.sharingElapsedMs != elapsed) {
                    _uiState.update { it.copy(sharingElapsedMs = elapsed) }
                }
                delay(1_000L)
            }
        }
    }

    fun refreshEnvironment() {
        wifiMonitor.refreshState()
        hotspotController.refreshLocalHostIp()
    }

    fun onPermissionsUpdated() {
        wifiMonitor.refreshState()
    }

    fun refreshCompatibility() {
        val report = compatibilityEngine.inspectDevice()
        _uiState.update { it.copy(compatibilityReport = report) }
    }

    fun completeWelcomeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    fun toggleSharing() {
        val current = _uiState.value.hotspot.status
        if (current == HotspotStatus.ACTIVE || current == HotspotStatus.STARTING) {
            hotspotController.stopSharing()
        } else {
            hotspotController.startSharing(_uiState.value.settings)
        }
    }

    fun stopSharing() {
        hotspotController.stopSharing()
    }

    fun selectEngineMode(mode: SharingEngineMode) {
        viewModelScope.launch {
            settingsRepository.setPreferredEngineMode(mode)
            if (_uiState.value.hotspot.status == HotspotStatus.ACTIVE) {
                _uiState.update {
                    it.copy(
                        snackbarMessage = "Mode saved (${mode.shortLabel}). Restart sharing to switch active engine."
                    )
                }
            }
        }
    }

    fun updateHotspotConfig(
        proxyPort: Int,
        socksPort: Int,
        p2pSsidSuffix: String,
        p2pPassphrase: String
    ) {
        if (proxyPort !in 1024..65535) {
            _uiState.update { it.copy(snackbarMessage = "Proxy port must be between 1024 and 65535.") }
            return
        }
        if (p2pPassphrase.length < 8) {
            _uiState.update { it.copy(snackbarMessage = "WPA2 passphrase must be at least 8 characters.") }
            return
        }
        viewModelScope.launch {
            settingsRepository.updateHotspotConfig(
                proxyPort = proxyPort,
                socksPort = socksPort,
                p2pSsidSuffix = p2pSsidSuffix,
                p2pPassphrase = p2pPassphrase
            )
            _uiState.update { it.copy(snackbarMessage = "Hotspot & relay configuration saved.") }
        }
    }

    fun setWifiOnlySafetyMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnlySafetyMode(enabled)
        }
    }

    fun setAutoStopOnWifiLoss(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStopOnWifiLoss(enabled)
        }
    }

    fun setEnableSocks5Relay(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableSocks5Relay(enabled)
        }
    }

    fun setUseForegroundService(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseForegroundService(enabled)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun toggleClientBlocked(client: ConnectedClient) {
        hotspotController.toggleClientBlocked(client)
    }

    fun probeUpstreamConnectivity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProbingUpstream = true) }
            val result = wifiMonitor.verifyUpstreamConnectivity()
            _uiState.update {
                it.copy(
                    isProbingUpstream = false,
                    upstreamProbeResult = result
                )
            }
        }
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningDiagnostics = true) }
            wifiMonitor.refreshState()
            val perms = _uiState.value.permissions
            val wifi = _uiState.value.upstreamWifi
            val hotspot = _uiState.value.hotspot
            val settings = _uiState.value.settings
            val probe = wifiMonitor.verifyUpstreamConnectivity()

            val steps = buildList {
                // 1. Android 13+ Wi-Fi Permissions
                val permOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.nearbyWifiGranted
                } else {
                    perms.fineLocationGranted
                }
                add(
                    DiagnosticStepResult(
                        id = "permissions",
                        title = "Wi-Fi & Hotspot Permissions",
                        status = if (permOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
                        detail = if (permOk) {
                            "Required Wi-Fi control permission granted."
                        } else {
                            "Missing Nearby Wi-Fi Devices (Android 13+) or Location permission required to start hotspots."
                        },
                        action = if (permOk) DiagnosticAction.NONE else DiagnosticAction.REQUEST_PERMISSIONS
                    )
                )

                // 2. Upstream Wi-Fi Association & Validation
                add(
                    DiagnosticStepResult(
                        id = "upstream_wifi",
                        title = "Upstream Wi-Fi Association",
                        status = when {
                            wifi.isWifiConnected && wifi.isValidatedInternet -> DiagnosticStatus.PASS
                            wifi.isWifiConnected -> DiagnosticStatus.WARN
                            else -> DiagnosticStatus.FAIL
                        },
                        detail = when {
                            wifi.isWifiConnected && wifi.isValidatedInternet ->
                                "Connected to ${wifi.ssid} (${wifi.ipv4Address}) with validated internet."
                            wifi.isWifiConnected ->
                                "Connected to ${wifi.ssid}, but Android reports internet is unvalidated (captive portal or offline router)."
                            else ->
                                "No upstream Wi-Fi network connected."
                        },
                        action = if (wifi.isWifiConnected) DiagnosticAction.NONE else DiagnosticAction.OPEN_WIFI_SETTINGS
                    )
                )

                // 3. Wi-Fi-Only Cellular Guard
                add(
                    DiagnosticStepResult(
                        id = "cellular_guard",
                        title = "Mobile Data Protection Guard",
                        status = when {
                            !settings.wifiOnlySafetyMode -> DiagnosticStatus.WARN
                            wifi.isCellularDefaultNetwork -> DiagnosticStatus.WARN
                            else -> DiagnosticStatus.PASS
                        },
                        detail = when {
                            !settings.wifiOnlySafetyMode ->
                                "Wi-Fi-Only Safety Mode is turned OFF. Enable it in Settings to guarantee zero cellular usage."
                            wifi.isCellularDefaultNetwork ->
                                "System default route currently points to Cellular! Relay blocks cellular sockets, and System Hotspot would consume mobile data."
                            else ->
                                "Wi-Fi-Only Safety Mode active: outbound relay sockets are pinned exclusively to TRANSPORT_WIFI."
                        }
                    )
                )

                // 4. Real Upstream TCP/443 Probe
                add(
                    DiagnosticStepResult(
                        id = "upstream_probe",
                        title = "Wi-Fi-Bound Internet Reachability",
                        status = if (probe.reachable) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
                        detail = probe.detail
                    )
                )

                // 5. Downstream Hotspot / Relay Engine
                add(
                    DiagnosticStepResult(
                        id = "hotspot_relay",
                        title = "Local Hotspot & Relay Socket",
                        status = when (hotspot.status) {
                            HotspotStatus.ACTIVE -> DiagnosticStatus.PASS
                            HotspotStatus.STARTING -> DiagnosticStatus.WARN
                            HotspotStatus.ERROR -> DiagnosticStatus.FAIL
                            HotspotStatus.IDLE -> DiagnosticStatus.WARN
                        },
                        detail = when (hotspot.status) {
                            HotspotStatus.ACTIVE ->
                                if (hotspot.engineMode == SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE) {
                                    "System Hotspot Bridge Guard active. Check Android Hotspot settings for client credentials."
                                } else {
                                    "Broadcasting '${hotspot.ssid}' with relay listening at ${hotspot.proxyHostIp}:${hotspot.httpProxyPort}."
                                }
                            HotspotStatus.ERROR -> hotspot.errorMessage ?: "Hotspot encountered an error."
                            HotspotStatus.STARTING -> "Hotspot is initializing..."
                            HotspotStatus.IDLE -> "Hotspot is currently idle. Tap Start Sharing on the Dashboard."
                        },
                        action = if (hotspot.status == HotspotStatus.IDLE) DiagnosticAction.START_RELAY else DiagnosticAction.NONE
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isRunningDiagnostics = false,
                    upstreamProbeResult = probe,
                    diagnosticSteps = steps
                )
            }
        }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    class Factory(private val container: WifiShareContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                settingsRepository = container.settingsRepository,
                compatibilityEngine = container.compatibilityEngine,
                wifiMonitor = container.upstreamWifiMonitor,
                hotspotController = container.hotspotController
            ) as T
        }
    }
}
