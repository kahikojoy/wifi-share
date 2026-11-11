package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.MainUiState
import com.example.ui.components.GlassNavyCard
import com.example.ui.components.MetricRow
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.EmeraldSignal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onToggleWifiOnlySafety: (Boolean) -> Unit,
    onToggleAutoStopWifiLoss: (Boolean) -> Unit,
    onToggleSocks5Relay: (Boolean) -> Unit,
    onToggleForegroundService: (Boolean) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onOpenHotspotConfig: () -> Unit,
    onOpenTroubleshooting: () -> Unit,
    onOpenWelcomeTour: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val settings = uiState.settings

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeader(
                    title = "Settings & Security",
                    subtitle = "Wi-Fi-only guard, relay options & theme",
                    icon = Icons.Rounded.Settings
                )
            }

            item {
                GlassNavyCard(highlightBorder = settings.wifiOnlySafetyMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = EmeraldSignal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wi-Fi-Only Safety Guard",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Wi-Fi-Only Safety Mode",
                        subtitle = "Pins outbound relay DNS and sockets exclusively to TRANSPORT_WIFI and blocks cellular fallback.",
                        checked = settings.wifiOnlySafetyMode,
                        onCheckedChange = onToggleWifiOnlySafety,
                        testTag = "switch_wifi_only_safety"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingToggleRow(
                        title = "Auto-Stop if Wi-Fi Drops",
                        subtitle = "Immediately shuts down hotspot & relay if upstream Wi-Fi disconnects.",
                        checked = settings.autoStopOnWifiLoss,
                        onCheckedChange = onToggleAutoStopWifiLoss,
                        testTag = "switch_auto_stop_wifi"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingToggleRow(
                        title = "Enable SOCKS5 Relay",
                        subtitle = "Opens an auxiliary SOCKS5 TCP relay on port ${settings.socksPort} alongside HTTP/HTTPS.",
                        checked = settings.enableSocks5Relay,
                        onCheckedChange = onToggleSocks5Relay,
                        testTag = "switch_socks5_relay"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingToggleRow(
                        title = "Foreground Relay Notification",
                        subtitle = "Runs a connectedDevice foreground service with quick Stop control while sharing.",
                        checked = settings.useForegroundService,
                        onCheckedChange = onToggleForegroundService,
                        testTag = "switch_foreground_service"
                    )
                }
            }

            item {
                GlassNavyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeChip("Dark Navy", AppThemeMode.DARK_NAVY, settings.themeMode, onSelectThemeMode)
                        ThemeModeChip("Crisp Light", AppThemeMode.CRISP_LIGHT, settings.themeMode, onSelectThemeMode)
                        ThemeModeChip("System", AppThemeMode.SYSTEM, settings.themeMode, onSelectThemeMode)
                    }
                }
            }

            item {
                GlassNavyCard {
                    Text(
                        text = "Quick Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenHotspotConfig,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_hotspot_config_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hotspot Config")
                        }
                        OutlinedButton(
                            onClick = onOpenTroubleshooting,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_troubleshoot_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Troubleshoot")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenWelcomeTour,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Welcome Tour")
                        }
                        OutlinedButton(
                            onClick = onOpenAppSettings,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Android App Info")
                        }
                    }
                }
            }

            item {
                GlassNavyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PrivacyTip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security & Build Information",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Zero external servers or cloud telemetry.\n" +
                            "• Upstream Wi-Fi credentials are never read or uploaded.\n" +
                            "• Hotspot passwords copied on Android 13+ are flagged EXTRA_IS_SENSITIVE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow("Application ID", BuildConfig.APPLICATION_ID, monoValue = true)
                    MetricRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", monoValue = true)
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    mode: AppThemeMode,
    currentMode: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit
) {
    FilterChip(
        selected = currentMode == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
        modifier = Modifier.testTag("theme_${mode.name}")
    )
}
