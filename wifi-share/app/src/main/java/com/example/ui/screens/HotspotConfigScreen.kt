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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.SharingEngineMode
import com.example.ui.MainUiState
import com.example.ui.components.GlassNavyCard
import com.example.ui.components.SubScreenTopBar
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.Formatters

@Composable
fun HotspotConfigScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onSelectEngineMode: (SharingEngineMode) -> Unit,
    onSaveConfig: (proxyPort: Int, socksPort: Int, p2pSsidSuffix: String, p2pPassphrase: String) -> Unit
) {
    val settings = uiState.settings
    var proxyPortText by remember(settings.proxyPort) { mutableStateOf(settings.proxyPort.toString()) }
    var socksPortText by remember(settings.socksPort) { mutableStateOf(settings.socksPort.toString()) }
    var ssidSuffix by remember(settings.p2pSsidSuffix) { mutableStateOf(settings.p2pSsidSuffix) }
    var passphrase by remember(settings.p2pPassphrase) { mutableStateOf(settings.p2pPassphrase) }
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SubScreenTopBar(
                title = stringResource(id = R.string.title_hotspot_config),
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
                    .fillMaxSize()
                    .testTag("hotspot_config_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    GlassNavyCard(highlightBorder = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LocalOnlyHotspot vs. Internet Tethering",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Android's public LocalOnlyHotspot API intentionally disables OS-level internet NAT and assigns randomized credentials. WiFi Share bridges this gap legitimately by running a local HTTP/HTTPS CONNECT & SOCKS5 relay bound exclusively to your upstream Wi-Fi network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    GlassNavyCard {
                        Text(
                            text = "Select Sharing Engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SharingEngineMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                RadioButton(
                                    selected = settings.preferredEngineMode == mode,
                                    onClick = { onSelectEngineMode(mode) }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    GlassNavyCard {
                        Text(
                            text = "Wi-Fi Direct Credentials (Custom SSID & WPA2)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Used when Engine Mode is set to Wi-Fi Direct + Wi-Fi Relay (Android 10+ requires 'DIRECT-WS-' prefix).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = ssidSuffix,
                            onValueChange = { ssidSuffix = it.take(20) },
                            label = { Text("Hotspot Name Suffix") },
                            prefix = {
                                Text(
                                    "DIRECT-WS-",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_p2p_ssid")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Broadcast name: ${Formatters.buildDirectSsid(ssidSuffix)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it.take(63) },
                            label = { Text("WPA2 Passphrase (min 8 chars)") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showPassword = !showPassword },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPassword) "Hide passphrase" else "Show passphrase"
                                    )
                                }
                            },
                            isError = passphrase.length < 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_p2p_password")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { passphrase = Formatters.generateSecurePassphrase(12) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Strong Random Password")
                        }
                    }
                }

                item {
                    GlassNavyCard {
                        Text(
                            text = "Relay Proxy Ports",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = proxyPortText,
                                onValueChange = { proxyPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                                label = { Text("HTTP/HTTPS Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_http_port")
                            )
                            OutlinedTextField(
                                value = socksPortText,
                                onValueChange = { socksPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                                label = { Text("SOCKS5 Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_socks_port")
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val http = proxyPortText.toIntOrNull() ?: 8080
                                val socks = socksPortText.toIntOrNull() ?: 1080
                                onSaveConfig(http, socks, ssidSuffix, passphrase)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_hotspot_config_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Save Hotspot Configuration")
                        }
                    }
                }
            }
        }
    }
}
