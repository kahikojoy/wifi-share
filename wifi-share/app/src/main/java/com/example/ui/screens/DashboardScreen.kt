package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.HotspotStatus
import com.example.model.SharingEngineMode
import com.example.ui.MainUiState
import com.example.ui.components.GlassNavyCard
import com.example.ui.components.MetricRow
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGuard
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.Formatters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onToggleSharing: () -> Unit,
    onSelectEngineMode: (SharingEngineMode) -> Unit,
    onOpenQrCode: () -> Unit,
    onOpenHotspotConfig: () -> Unit,
    onOpenWifiInfo: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenCompatibility: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
    onOpenSystemHotspotSettings: () -> Unit
) {
    val hotspot = uiState.hotspot
    val wifi = uiState.upstreamWifi
    val stats = uiState.trafficStats
    val isActive = hotspot.status == HotspotStatus.ACTIVE
    val isStarting = hotspot.status == HotspotStatus.STARTING

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Wi-Fi-Only Safety Guard Banner
            item {
                val guardEnabled = uiState.settings.wifiOnlySafetyMode
                val cellularDanger = wifi.isCellularDefaultNetwork && !wifi.isWifiConnected
                val pillColor = when {
                    cellularDanger -> CoralContainer
                    guardEnabled -> EmeraldContainer
                    else -> AmberContainer
                }
                val textColor = when {
                    cellularDanger -> CoralAlert
                    guardEnabled -> EmeraldSignal
                    else -> AmberGuard
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = pillColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (cellularDanger || !guardEnabled) {
                                Icons.Rounded.WarningAmber
                            } else {
                                Icons.Rounded.Shield
                            },
                            contentDescription = "Wi-Fi-only safety status",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    cellularDanger -> "Mobile Data Active — Relay Blocked"
                                    guardEnabled -> "Wi-Fi-Only Safety Mode Active"
                                    else -> "Wi-Fi-Only Safety Guard Disabled"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = textColor
                            )
                            Text(
                                text = when {
                                    cellularDanger -> "Upstream Wi-Fi is offline. Outbound relay refuses cellular fallback."
                                    guardEnabled -> "Outbound sockets are locked to TRANSPORT_WIFI. Blocked cellular attempts: ${stats.blockedCellularAttempts}."
                                    else -> "Enable Wi-Fi-Only Safety Mode in Settings to block mobile data routing."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Hero Relay Control Card
            item {
                GlassNavyCard(
                    highlightBorder = isActive,
                    accentColor = if (isActive) EmeraldSignal else MaterialTheme.colorScheme.secondary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            StatusPill(
                                label = when (hotspot.status) {
                                    HotspotStatus.ACTIVE -> "SHARING ACTIVE"
                                    HotspotStatus.STARTING -> "INITIALIZING"
                                    HotspotStatus.ERROR -> "ATTENTION NEEDED"
                                    HotspotStatus.IDLE -> "STANDBY"
                                },
                                containerColor = when (hotspot.status) {
                                    HotspotStatus.ACTIVE -> EmeraldContainer
                                    HotspotStatus.STARTING -> MaterialTheme.colorScheme.primaryContainer
                                    HotspotStatus.ERROR -> CoralContainer
                                    HotspotStatus.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = when (hotspot.status) {
                                    HotspotStatus.ACTIVE -> EmeraldSignal
                                    HotspotStatus.STARTING -> MaterialTheme.colorScheme.secondary
                                    HotspotStatus.ERROR -> CoralAlert
                                    HotspotStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                icon = Icons.Rounded.WifiTethering
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isActive) {
                                    Formatters.formatDuration(uiState.sharingElapsedMs)
                                } else {
                                    "Ready to Relay"
                                },
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = if (isActive) JetBrainsMonoFamily else MaterialTheme.typography.displaySmall.fontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = hotspot.statusNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        PulseBeaconIndicator(isActive = isActive)
                    }

                    AnimatedVisibility(visible = hotspot.errorMessage != null || hotspot.safetyGuardPausedReason != null) {
                        val msg = hotspot.errorMessage ?: hotspot.safetyGuardPausedReason.orEmpty()
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CoralContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoralAlert,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onToggleSharing,
                            enabled = !isStarting,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("toggle_share_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) CoralAlert else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isStarting -> "Starting..."
                                    isActive -> "Stop Sharing"
                                    else -> "Start Sharing"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (isActive && hotspot.engineMode != SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .height(54.dp)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onOpenQrCode)
                                    .testTag("dashboard_qr_shortcut")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCode2,
                                        contentDescription = "Show Wi-Fi QR Code",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "QR",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Sharing Engine",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SharingEngineMode.entries.forEach { mode ->
                            val selected = uiState.settings.preferredEngineMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectEngineMode(mode) },
                                label = { Text(mode.shortLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.testTag("engine_mode_${mode.name}")
                            )
                        }
                    }

                    if (uiState.settings.preferredEngineMode == SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenSystemHotspotSettings)
                                .testTag("open_system_hotspot_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Open Android Hotspot & Tethering Settings",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Active Hotspot Credentials & Proxy Banner
            if (isActive && hotspot.engineMode != SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE) {
                item {
                    GlassNavyCard(
                        highlightBorder = true,
                        onClick = onOpenQrCode
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hotspot & Relay Endpoint",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap for QR",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        MetricRow(label = "Broadcast SSID", value = hotspot.ssid.ifBlank { "--" }, monoValue = true)
                        MetricRow(
                            label = "HTTP Proxy Server",
                            value = "${hotspot.proxyHostIp}:${hotspot.httpProxyPort}",
                            monoValue = true,
                            valueColor = MaterialTheme.colorScheme.secondary
                        )
                        if (hotspot.socksProxyRunning) {
                            MetricRow(
                                label = "SOCKS5 Proxy",
                                value = "${hotspot.proxyHostIp}:${hotspot.socksProxyPort}",
                                monoValue = true
                            )
                        }
                    }
                }
            }

            // Telemetry Two-Card Row: Upstream Wi-Fi + Hotspot/Clients
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassNavyCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_wifi_card"),
                        onClick = onOpenWifiInfo
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (wifi.isWifiConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = if (wifi.isWifiConnected) EmeraldSignal else CoralAlert,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upstream Wi-Fi",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (wifi.isWifiConnected) wifi.ssid else "Offline",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (wifi.isWifiConnected) {
                                "${wifi.signalPercent}% • ${wifi.bandLabel} • ${wifi.linkSpeedMbps} Mbps"
                            } else {
                                "Connect to Wi-Fi"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    GlassNavyCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_devices_card"),
                        onClick = onOpenDevices
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Clients",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.connectedClients.size}",
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${stats.activeTunnelCount} active tunnels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Data Traffic Statistics Card
            item {
                GlassNavyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Data Traffic Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TrafficStatTile(
                            label = "Relay Upload (Tx)",
                            total = Formatters.formatBytes(stats.relayTxBytes),
                            rate = Formatters.formatRate(stats.txBytesPerSec),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TrafficStatTile(
                            label = "Relay Download (Rx)",
                            total = Formatters.formatBytes(stats.relayRxBytes),
                            rate = Formatters.formatRate(stats.rxBytesPerSec),
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow(
                        label = "Handled Proxy Requests",
                        value = stats.totalRequestsHandled.toString(),
                        monoValue = true
                    )
                }
            }

            // Quick Actions Grid
            item {
                Text(
                    text = "Tools & Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Rounded.Tune,
                        label = "Hotspot\nConfig",
                        onClick = onOpenHotspotConfig,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_hotspot_config")
                    )
                    QuickActionCard(
                        icon = Icons.Rounded.QrCode2,
                        label = "Join\nQR Code",
                        onClick = onOpenQrCode,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_qr_code")
                    )
                    QuickActionCard(
                        icon = Icons.Rounded.NetworkCheck,
                        label = "Device\nCompat",
                        onClick = onOpenCompatibility,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_compat")
                    )
                    QuickActionCard(
                        icon = Icons.Rounded.Build,
                        label = "Trouble-\nshoot",
                        onClick = onOpenTroubleshooting,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_troubleshoot")
                    )
                }
            }
        }
    }
}

@Composable
private fun PulseBeaconIndicator(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "beacon")
    val pulseScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isActive) 1.25f else 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(EmeraldSignal.copy(alpha = 0.2f))
            )
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (isActive) {
                            listOf(EmeraldSignal, MaterialTheme.colorScheme.secondary)
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = null,
                tint = if (isActive) Color(0xFF002117) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TrafficStatTile(
    label: String,
    total: String,
    rate: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = total,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
            Text(
                text = rate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label.replace('\n', ' '),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
        }
    }
}
