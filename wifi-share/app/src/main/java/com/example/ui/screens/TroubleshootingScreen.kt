package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DiagnosticAction
import com.example.model.DiagnosticStatus
import com.example.ui.MainUiState
import com.example.ui.components.GlassNavyCard
import com.example.ui.components.SubScreenTopBar
import com.example.ui.theme.AmberGuard
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSignal

private data class FaqGuide(
    val title: String,
    val answer: String
)

private val TroubleshootingFaqs = listOf(
    FaqGuide(
        title = "Why do client devices need Manual Proxy settings in Relay Mode?",
        answer = "Android prohibits ordinary unrooted apps from creating kernel NAT routes (TetheringManager requires privileged OEM permissions). Using LocalOnlyHotspot or Wi-Fi Direct paired with an app-level proxy relay lets browsers and apps forward HTTP/HTTPS over your Wi-Fi interface legally with zero mobile data usage."
    ),
    FaqGuide(
        title = "Why does my connected Wi-Fi name show 'SSID hidden'?",
        answer = "Starting in Android 10–16, reading the upstream Wi-Fi SSID requires both Fine Location runtime permission AND the system Location toggle turned ON. You can still share Wi-Fi via Relay without granting Location."
    ),
    FaqGuide(
        title = "How do I prevent Tecno, Infinix, or Samsung from switching to Mobile Data?",
        answer = "Always keep Wi-Fi-Only Safety Mode enabled inside WiFi Share. If using your phone's System Hotspot instead of Relay mode, turn off Mobile Data or verify STA+AP support in the Compatibility Checker first."
    ),
    FaqGuide(
        title = "Clients connect to the hotspot but web pages fail to load",
        answer = "Confirm two things: (1) On the client device's Wi-Fi settings, Proxy is set to Manual with the exact Host IP and Port shown on the QR Code screen. (2) Upstream Wi-Fi passed the TCP/443 reachability check in Wi-Fi Info (captive-portal hotel Wi-Fi must be signed in on this phone first)."
    ),
    FaqGuide(
        title = "Hotspot stops shortly after locking the screen",
        answer = "OEM battery managers (MIUI/HyperOS, Samsung One UI, HiOS/XOS) aggressively sleep background sockets. Keep 'Foreground Relay Service' enabled and set WiFi Share battery usage to Unrestricted in Android App Settings."
    )
)

@Composable
fun TroubleshootingScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartSharing: () -> Unit
) {
    var expandedFaq by remember { mutableStateOf<String?>(TroubleshootingFaqs.first().title) }

    Scaffold(
        topBar = {
            SubScreenTopBar(
                title = stringResource(id = R.string.title_troubleshooting),
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                    GlassNavyCard(highlightBorder = true) {
                        Text(
                            text = "Automated Network Self-Check",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tests permissions, upstream Wi-Fi validation, mobile data safety guard, live TCP/443 Wi-Fi reachability, and local relay status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRunDiagnostics,
                            enabled = !uiState.isRunningDiagnostics,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("run_diagnostics_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (uiState.isRunningDiagnostics) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Running Diagnostics...")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(id = R.string.action_run_diagnostic))
                            }
                        }
                    }
                }

                items(uiState.diagnosticSteps, key = { it.id }) { step ->
                    val statusColor = when (step.status) {
                        DiagnosticStatus.PASS -> EmeraldSignal
                        DiagnosticStatus.WARN -> AmberGuard
                        DiagnosticStatus.FAIL -> CoralAlert
                    }
                    val statusIcon = when (step.status) {
                        DiagnosticStatus.PASS -> Icons.Rounded.CheckCircle
                        DiagnosticStatus.WARN -> Icons.Rounded.WarningAmber
                        DiagnosticStatus.FAIL -> Icons.Rounded.ErrorOutline
                    }

                    GlassNavyCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = step.status.name,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (step.action != DiagnosticAction.NONE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    when (step.action) {
                                        DiagnosticAction.REQUEST_PERMISSIONS -> onRequestPermissions()
                                        DiagnosticAction.OPEN_WIFI_SETTINGS -> onOpenWifiSettings()
                                        DiagnosticAction.OPEN_HOTSPOT_SETTINGS -> onOpenHotspotSettings()
                                        DiagnosticAction.OPEN_APP_SETTINGS -> onOpenAppSettings()
                                        DiagnosticAction.START_RELAY -> onStartSharing()
                                        DiagnosticAction.NONE -> Unit
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    when (step.action) {
                                        DiagnosticAction.REQUEST_PERMISSIONS -> "Grant Permissions"
                                        DiagnosticAction.OPEN_WIFI_SETTINGS -> "Open Wi-Fi Settings"
                                        DiagnosticAction.OPEN_HOTSPOT_SETTINGS -> "Open Hotspot Settings"
                                        DiagnosticAction.OPEN_APP_SETTINGS -> "Open App Settings"
                                        DiagnosticAction.START_RELAY -> "Start Sharing"
                                        DiagnosticAction.NONE -> ""
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Android 13–16 & OEM Guides",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(TroubleshootingFaqs, key = { it.title }) { faq ->
                    val expanded = expandedFaq == faq.title
                    GlassNavyCard(
                        onClick = { expandedFaq = if (expanded) null else faq.title }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = faq.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        AnimatedVisibility(visible = expanded) {
                            Text(
                                text = faq.answer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenAppSettings,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("App Battery / Info")
                        }
                        OutlinedButton(
                            onClick = onOpenHotspotSettings,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("System Hotspot")
                        }
                    }
                }
            }
        }
    }
}
