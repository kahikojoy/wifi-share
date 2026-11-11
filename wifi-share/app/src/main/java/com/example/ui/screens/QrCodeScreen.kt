package com.example.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HotspotStatus
import com.example.model.SharingEngineMode
import com.example.ui.MainUiState
import com.example.ui.components.GlassNavyCard
import com.example.ui.components.MetricRow
import com.example.ui.components.SubScreenTopBar
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.Formatters
import com.example.util.QrCodeGenerator

@Composable
fun QrCodeScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onStartSharing: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val hotspot = uiState.hotspot
    val settings = uiState.settings
    var showPassword by remember { mutableStateOf(false) }

    val isLive = hotspot.status == HotspotStatus.ACTIVE &&
        hotspot.engineMode != SharingEngineMode.SYSTEM_HOTSPOT_BRIDGE &&
        hotspot.ssid.isNotBlank()

    val effectiveSsid = when {
        isLive -> hotspot.ssid
        settings.preferredEngineMode == SharingEngineMode.WIFI_DIRECT_RELAY ->
            Formatters.buildDirectSsid(settings.p2pSsidSuffix)
        else -> ""
    }
    val effectivePassword = when {
        isLive -> hotspot.passphrase
        settings.preferredEngineMode == SharingEngineMode.WIFI_DIRECT_RELAY ->
            settings.p2pPassphrase
        else -> ""
    }
    val effectiveHostIp = if (isLive) hotspot.proxyHostIp else "192.168.49.1"
    val effectivePort = if (isLive) hotspot.httpProxyPort else settings.proxyPort

    val qrMatrix = remember(effectiveSsid, effectivePassword) {
        if (effectiveSsid.isNotBlank()) {
            val payload = QrCodeGenerator.buildWifiQrPayload(effectiveSsid, effectivePassword)
            runCatching { QrCodeGenerator.encodeMatrix(payload, matrixSize = 51) }.getOrNull()
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            SubScreenTopBar(
                title = stringResource(id = R.string.title_qr_code),
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
                    .widthIn(max = 700.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    GlassNavyCard(highlightBorder = isLive) {
                        if (qrMatrix != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isLive) "Live Hotspot QR Code" else "Preview: Wi-Fi Direct Profile",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Scan with another phone's Camera or Wi-Fi QR scanner to join.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = Color.White,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .size(236.dp)
                                        .testTag("wifi_qr_canvas")
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp)
                                            .aspectRatio(1f)
                                    ) {
                                        val cellW = size.width / qrMatrix.width
                                        val cellH = size.height / qrMatrix.height
                                        val navyModule = Color(0xFF070D19)
                                        for (y in 0 until qrMatrix.height) {
                                            for (x in 0 until qrMatrix.width) {
                                                if (qrMatrix.isDark(x, y)) {
                                                    drawRect(
                                                        color = navyModule,
                                                        topLeft = Offset(x * cellW, y * cellH),
                                                        size = Size(cellW + 0.5f, cellH + 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Start Local Hotspot to Generate Credentials",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Android assigns LocalOnlyHotspot SSID & password dynamically when started. Or switch to Wi-Fi Direct mode for persistent custom credentials.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onStartSharing,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("qr_start_sharing_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(stringResource(id = R.string.action_start_sharing))
                                }
                            }
                        }
                    }
                }

                if (effectiveSsid.isNotBlank()) {
                    item {
                        GlassNavyCard {
                            Text(
                                text = "Credentials & Sensitive Copy",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            MetricRow("Network Name (SSID)", effectiveSsid, monoValue = true)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Password",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (showPassword) effectivePassword else "•".repeat(effectivePassword.length.coerceAtLeast(8)),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { showPassword = !showPassword },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (showPassword) "Hide password" else "Reveal password"
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, "WiFi SSID", effectiveSsid, sensitive = false)
                                        onShowMessage("SSID copied to clipboard.")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("copy_ssid_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy SSID")
                                }
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, "WiFi Password", effectivePassword, sensitive = true)
                                        onShowMessage("Password copied (masked in Android 13+ clipboard).")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("copy_password_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Pass")
                                }
                            }
                        }
                    }

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
                                    text = "Client Proxy Setup (Required for Relay Internet)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            MetricRow("Proxy Hostname", effectiveHostIp, monoValue = true, valueColor = MaterialTheme.colorScheme.secondary)
                            MetricRow("Proxy Port", effectivePort.toString(), monoValue = true, valueColor = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "1. Join '$effectiveSsid' on your laptop or second phone.\n" +
                                    "2. Open that device's Wi-Fi network settings -> Proxy -> Manual.\n" +
                                    "3. Enter Host: $effectiveHostIp and Port: $effectivePort.\n" +
                                    "4. Traffic now tunnels through this phone's Wi-Fi without using Mobile Data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "Proxy Address", "$effectiveHostIp:$effectivePort", sensitive = false)
                                    onShowMessage("Copied $effectiveHostIp:$effectivePort")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Proxy $effectiveHostIp:$effectivePort")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
    sensitive: Boolean
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}
