package com.example

import com.example.compatibility.CompatibilityEngine
import com.example.model.CompatibilityVerdict
import com.example.network.WifiBoundRelayServer
import com.example.util.Formatters
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class WifiShareUnitTests {

    @Test
    fun `frequency maps accurately to 2_4 5 and 6 GHz channels`() {
        assertEquals(1, Formatters.frequencyToChannel(2412))
        assertEquals(6, Formatters.frequencyToChannel(2437))
        assertEquals(14, Formatters.frequencyToChannel(2484))
        assertEquals(36, Formatters.frequencyToChannel(5180))
        assertEquals(149, Formatters.frequencyToChannel(5745))
        assertEquals(1, Formatters.frequencyToChannel(5955))
        assertEquals("2.4 GHz", Formatters.frequencyToBandLabel(2437))
        assertEquals("5 GHz", Formatters.frequencyToBandLabel(5180))
        assertEquals("6 GHz", Formatters.frequencyToBandLabel(5955))
    }

    @Test
    fun `formatters handle bytes duration and direct ssid cleanly`() {
        assertEquals("512 B", Formatters.formatBytes(512))
        assertEquals("1.5 KB", Formatters.formatBytes(1536))
        assertEquals("00:01:05", Formatters.formatDuration(65_000L))
        assertEquals("DIRECT-WS-PhoneRelay", Formatters.buildDirectSsid("Phone Relay!!"))
        val pass = Formatters.generateSecurePassphrase(12)
        assertEquals(12, pass.length)
    }

    @Test
    fun `qr payload escapes special characters and encodes valid matrix`() {
        val payload = QrCodeGenerator.buildWifiQrPayload(
            ssid = "My;Wifi:Home\\1",
            password = "pass,word;1"
        )
        assertEquals(
            "WIFI:T:WPA;S:My\\;Wifi\\:Home\\\\1;P:pass\\,word\\;1;H:false;;",
            payload
        )
        val matrix = QrCodeGenerator.encodeMatrix(payload, matrixSize = 41)
        assertTrue(matrix.width >= 41)
        assertTrue(matrix.height >= 41)
        assertTrue(matrix.modules.any { it })
    }

    @Test
    fun `compatibility engine distinguishes Pixel Samsung Xiaomi and Tecno profiles`() {
        val pixel = CompatibilityEngine.buildReport(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 8",
            androidRelease = "15",
            sdkInt = 35,
            hardware = "shiba",
            socModel = "Tensor G3",
            staApConcurrency = true,
            staLocalOnlyConcurrency = true,
            wifiDirectSupported = true,
            fiveGhzSupported = true,
            sixGhzSupported = true
        )
        assertEquals(CompatibilityVerdict.NATIVE_STA_AP_READY, pixel.verdict)
        assertTrue(pixel.manufacturerTitle.contains("Pixel"))

        val tecno = CompatibilityEngine.buildReport(
            manufacturer = "TECNO",
            brand = "TECNO",
            model = "TECNO Camon 20",
            androidRelease = "13",
            sdkInt = 33,
            hardware = "mt6768",
            socModel = "Helio G85",
            staApConcurrency = false,
            staLocalOnlyConcurrency = true,
            wifiDirectSupported = true,
            fiveGhzSupported = true,
            sixGhzSupported = false
        )
        assertEquals(CompatibilityVerdict.RELAY_SUPPORTED, tecno.verdict)
        assertTrue(tecno.manufacturerTitle.contains("Tecno"))

        val samsung = CompatibilityEngine.buildReport(
            manufacturer = "samsung",
            brand = "samsung",
            model = "SM-S921B",
            androidRelease = "14",
            sdkInt = 34,
            hardware = "s5e9945",
            socModel = "Exynos 2400",
            staApConcurrency = true,
            staLocalOnlyConcurrency = true,
            wifiDirectSupported = true,
            fiveGhzSupported = true,
            sixGhzSupported = true
        )
        assertEquals(CompatibilityVerdict.NATIVE_STA_AP_READY, samsung.verdict)
        assertTrue(samsung.manufacturerTitle.contains("Samsung"))

        val redmi = CompatibilityEngine.buildReport(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            model = "Redmi Note 13",
            androidRelease = "14",
            sdkInt = 34,
            hardware = "qcom",
            socModel = "SM6225",
            staApConcurrency = false,
            staLocalOnlyConcurrency = false,
            wifiDirectSupported = false,
            fiveGhzSupported = true,
            sixGhzSupported = false
        )
        assertEquals(CompatibilityVerdict.LIMITED_HARDWARE, redmi.verdict)
        assertTrue(redmi.manufacturerTitle.contains("Xiaomi"))
    }

    @Test
    fun `relay server blocks outbound requests with 503 when wifi-only guard has no wifi network`() {
        val freePort = ServerSocket(0).use { it.localPort }
        val relay = WifiBoundRelayServer(
            upstreamNetworkProvider = { null },
            wifiOnlySafetyProvider = { true }
        )

        val started = relay.start(httpPort = freePort, enableSocks5 = false, socksPort = 0)
        assertTrue(started.isSuccess)

        try {
            Socket("127.0.0.1", freePort).use { client ->
                client.soTimeout = 3_000
                val out = client.getOutputStream()
                out.write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                out.flush()

                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val statusLine = reader.readLine()
                assertTrue(statusLine != null && statusLine.contains("503"))
            }
            assertEquals(1, relay.snapshot.value.blockedCellularAttempts)
            assertFalse(relay.snapshot.value.clients.isEmpty())
        } finally {
            relay.stop()
        }
    }
}
