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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ConnectedClient
import com.example.model.HotspotStatus
import com.example.ui.MainUiState
import com.example.ui.components.GlassNavyCard
import com.example.ui.components.MetricRow
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.Formatters

@Composable
fun ConnectedDevicesScreen(
    uiState: MainUiState,
    onRefreshClients: () -> Unit,
    onToggleBlockClient: (ConnectedClient) -> Unit
) {
    val clients = uiState.connectedClients

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
                        title = "Connected Devices (${clients.size})",
                        subtitle = "Active relay IP sessions & Wi-Fi Direct peers",
                        icon = Icons.Rounded.Devices,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onRefreshClients,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_clients_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh client list")
                    }
                }
            }

            item {
                GlassNavyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android 13+ Client Visibility Scope",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Android 10–16 restricts raw ARP table MAC scanning for unprivileged apps. WiFi Share reliably tracks every client connecting through the Relay Server by IP & byte usage, plus hardware peer info in Wi-Fi Direct mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (clients.isEmpty()) {
                item {
                    GlassNavyCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DevicesOther,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (uiState.hotspot.status == HotspotStatus.ACTIVE) {
                                    "Waiting for client devices"
                                } else {
                                    "No active hotspot session"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (uiState.hotspot.status == HotspotStatus.ACTIVE) {
                                    "Connect a second device to '${uiState.hotspot.ssid}' and set its HTTP proxy to ${uiState.hotspot.proxyHostIp}:${uiState.hotspot.httpProxyPort}."
                                } else {
                                    "Start sharing from the Dashboard first, then share the QR code and proxy port with other devices."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(clients, key = { it.id }) { client ->
                    GlassNavyCard(highlightBorder = !client.isBlocked) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = client.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = client.source.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            StatusPill(
                                label = if (client.isBlocked) "Blocked" else "Allowed",
                                containerColor = if (client.isBlocked) CoralContainer else EmeraldContainer,
                                contentColor = if (client.isBlocked) CoralAlert else EmeraldSignal,
                                icon = if (client.isBlocked) Icons.Rounded.Block else Icons.Rounded.CheckCircle
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        MetricRow("IP Address", client.ipAddress, monoValue = true)
                        if (client.macAddress != "--") {
                            MetricRow("MAC Address", client.macAddress, monoValue = true)
                        }
                        MetricRow(
                            "Relay Traffic (Tx / Rx)",
                            "${Formatters.formatBytes(client.bytesSent)} / ${Formatters.formatBytes(client.bytesReceived)}",
                            monoValue = true
                        )
                        MetricRow("Active Tunnels", client.activeConnections.toString(), monoValue = true)

                        if (!client.ipAddress.startsWith("192.168.49.x") && client.ipAddress != "--") {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { onToggleBlockClient(client) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("block_client_${client.ipAddress}"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = if (client.isBlocked) "Unblock Relay Access" else "Block Relay Access",
                                    color = if (client.isBlocked) EmeraldSignal else CoralAlert
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
