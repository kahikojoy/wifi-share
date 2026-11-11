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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
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
import com.example.model.CompatibilityVerdict
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

@Composable
fun CompatibilityScreen(
    uiState: MainUiState,
    onRefreshCheck: () -> Unit,
    onOpenTroubleshooting: () -> Unit
) {
    val report = uiState.compatibilityReport

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
                        title = "Hardware & OEM Compatibility",
                        subtitle = "STA+AP concurrency & manufacturer Wi-Fi rules",
                        icon = Icons.Rounded.NetworkCheck,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onRefreshCheck,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_compat_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Re-check hardware capabilities")
                    }
                }
            }

            if (report != null) {
                item {
                    val badgeColors = when (report.verdict) {
                        CompatibilityVerdict.NATIVE_STA_AP_READY -> EmeraldContainer to EmeraldSignal
                        CompatibilityVerdict.RELAY_SUPPORTED -> AmberContainer to AmberGuard
                        CompatibilityVerdict.LIMITED_HARDWARE -> CoralContainer to CoralAlert
                    }

                    GlassNavyCard(highlightBorder = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Rounded.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${report.manufacturer} ${report.model}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${report.androidVersionLabel} • ${report.socModel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StatusPill(
                                label = report.verdict.badgeLabel,
                                containerColor = badgeColors.first,
                                contentColor = badgeColors.second
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = report.verdict.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = badgeColors.second
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        CapabilityRow("STA + AP Simultaneous Hotspot", report.staApConcurrencySupported)
                        CapabilityRow("STA + LocalOnlyHotspot Concurrency", report.staLocalOnlyConcurrencySupported)
                        CapabilityRow("Wi-Fi Direct (P2P Group)", report.wifiDirectSupported)
                        CapabilityRow("5 GHz Radio Support", report.fiveGhzSupported)
                        CapabilityRow("6 GHz (Wi-Fi 6E/7) Support", report.sixGhzSupported)
                    }
                }

                item {
                    GlassNavyCard {
                        Text(
                            text = report.manufacturerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = report.manufacturerSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Recommended Device Steps",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        report.manufacturerSteps.forEachIndexed { idx, step ->
                            Text(
                                text = "${idx + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Known OEM / Android 13–16 Quirks",
                            style = MaterialTheme.typography.titleSmall,
                            color = AmberGuard
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        report.knownQuirks.forEach { quirk ->
                            Text(
                                text = "• $quirk",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                item {
                    GlassNavyCard {
                        Text(
                            text = "Manufacturer Reference Matrix",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        MetricRow("Google Pixel", "STA+AP System & Relay Ready")
                        MetricRow("Samsung One UI", "Wi-Fi Sharing toggle (flagship/mid)")
                        MetricRow("Xiaomi / Redmi / POCO", "STA+AP on modern SoCs; disable MIUI Saver")
                        MetricRow("Tecno / Infinix (HiOS/XOS)", "Often Relay-only; watch Mobile Data fallback")
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenTroubleshooting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("compat_open_troubleshoot"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Open Diagnostics & Troubleshooting")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, supported: Boolean?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (supported == true) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = when (supported) {
                    true -> EmeraldSignal
                    false -> CoralAlert
                    null -> AmberGuard
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (supported) {
                    true -> "Supported"
                    false -> "Unsupported"
                    null -> "API < 30/31"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (supported) {
                    true -> EmeraldSignal
                    false -> CoralAlert
                    null -> AmberGuard
                }
            )
        }
    }
}
