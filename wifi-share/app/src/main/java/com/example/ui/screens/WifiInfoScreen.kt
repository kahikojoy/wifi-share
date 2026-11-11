package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.SsidVisibilityStatus
import com.example.ui.MainUiState
import com.example.ui.components.GlassNavyCard
import com.example.ui.components.MetricRow
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGuard
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun WifiInfoScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onProbeConnectivity: () -> Unit,
    onRequestLocationForSsid: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenWifiSettings: () -> Unit
) {
    val wifi = uiState.upstreamWifi

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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = "Upstream Wi-Fi Telemetry",
                        subtitle = "Live Android ConnectivityManager & WifiInfo inspection",
                        icon = if (wifi.isWifiConnected) Icons.Rounded.SignalWifi4Bar else Icons.Rounded.SignalWifiOff,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("wifi_refresh_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Wi-Fi details")
                    }
                }
            }

            if (wifi.ssidVisibilityStatus == SsidVisibilityStatus.HIDDEN_PERMISSION_NEEDED ||
                wifi.ssidVisibilityStatus == SsidVisibilityStatus.HIDDEN_LOCATION_DISABLED
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = AmberContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = AmberGuard
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (wifi.ssidVisibilityStatus == SsidVisibilityStatus.HIDDEN_PERMISSION_NEEDED) {
                                        "SSID Hidden by Android Privacy Rules"
                                    } else {
                                        "Device Location Toggle is Off"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AmberGuard
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Android 13+ redacts connected Wi-Fi network names unless Fine Location permission is granted and device Location is turned on. Relay sharing still works without it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberGuard
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    if (wifi.ssidVisibilityStatus == SsidVisibilityStatus.HIDDEN_PERMISSION_NEEDED) {
                                        onRequestLocationForSsid()
                                    } else {
                                        onOpenLocationSettings()
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = if (wifi.ssidVisibilityStatus == SsidVisibilityStatus.HIDDEN_PERMISSION_NEEDED) {
                                        "Grant Location for SSID"
                                    } else {
                                        "Open Location Settings"
                                    },
                                    color = AmberGuard
                                )
                            }
                        }
                    }
                }
            }

            item {
                GlassNavyCard(highlightBorder = wifi.isWifiConnected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wifi.ssid,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${wifi.wifiStandardLabel} • ${wifi.bandLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            label = when {
                                !wifi.isWifiConnected -> "Disconnected"
                                wifi.isValidatedInternet -> "Internet OK"
                                else -> "Unvalidated"
                            },
                            containerColor = when {
                                !wifi.isWifiConnected -> CoralContainer
                                wifi.isValidatedInternet -> EmeraldContainer
                                else -> AmberContainer
                            },
                            contentColor = when {
                                !wifi.isWifiConnected -> CoralAlert
                                wifi.isValidatedInternet -> EmeraldSignal
                                else -> AmberGuard
                            }
                        )
                    }

                    if (wifi.isWifiConnected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Signal Strength (${wifi.rssiDbm} dBm)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${wifi.signalPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { wifi.signalPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        MetricRow("BSSID", wifi.bssid, monoValue = true)
                        MetricRow(
                            "Frequency / Channel",
                            if (wifi.frequencyMhz > 0) "${wifi.frequencyMhz} MHz (Ch ${wifi.channelNumber})" else "--",
                            monoValue = true
                        )
                        MetricRow("Tx / Rx Link Speed", "${wifi.txLinkSpeedMbps} / ${wifi.rxLinkSpeedMbps} Mbps", monoValue = true)
                        MetricRow("Metered Network", if (wifi.isMetered) "Yes (Metered)" else "No (Unmetered)")
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenWifiSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("Open Android Wi-Fi Settings")
                        }
                    }
                }
            }

            item {
                GlassNavyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IP Routing & Upstream Probe",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow("Interface", wifi.interfaceName, monoValue = true)
                    MetricRow(
                        "Local IPv4 / Prefix",
                        if (wifi.ipv4Address != "--") "${wifi.ipv4Address}/${wifi.subnetPrefixLength}" else "--",
                        monoValue = true
                    )
                    MetricRow("Gateway", wifi.gatewayAddress, monoValue = true)
                    MetricRow(
                        "DNS Servers",
                        wifi.dnsServers.joinToString(", ").ifBlank { "--" },
                        monoValue = true
                    )
                    MetricRow(
                        "Default OS Route",
                        if (wifi.isCellularDefaultNetwork) "Cellular (Mobile Data!)" else if (wifi.isWifiConnected) "Wi-Fi" else "None"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onProbeConnectivity,
                        enabled = !uiState.isProbingUpstream,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("probe_upstream_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (uiState.isProbingUpstream) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Wi-Fi-Bound TCP Reachability...")
                        } else {
                            Text("Verify Upstream Wi-Fi Connectivity")
                        }
                    }

                    uiState.upstreamProbeResult?.let { probe ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (probe.reachable) EmeraldContainer else CoralContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (probe.reachable) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = if (probe.reachable) EmeraldSignal else CoralAlert
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = probe.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (probe.reachable) EmeraldSignal else CoralAlert
                                    )
                                    probe.resolvedIp?.let {
                                        Text(
                                            text = "Resolved via Wi-Fi DNS: $it",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (probe.reachable) EmeraldSignal else CoralAlert
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (wifi.activeInterfaces.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Active Device Interfaces",
                        subtitle = "Detected network interfaces on this phone",
                        icon = Icons.Rounded.Lan
                    )
                }
                items(wifi.activeInterfaces, key = { it.name }) { iface ->
                    GlassNavyCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = iface.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = if (iface.isUp) "UP" else "DOWN",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (iface.isUp) EmeraldSignal else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (iface.ipv4Addresses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "IPv4: ${iface.ipv4Addresses.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (iface.ipv6Addresses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "IPv6: ${iface.ipv6Addresses.take(2).joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
