package com.example.compatibility

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import com.example.model.CompatibilityReport
import com.example.model.CompatibilityVerdict
import java.util.Locale

class CompatibilityEngine(private val context: Context) {

    fun inspectDevice(): CompatibilityReport {
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        val pm = context.packageManager

        val staApConcurrency: Boolean? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { wifiManager?.isStaApConcurrencySupported }.getOrNull()
        } else {
            null
        }

        val staLocalOnlyConcurrency: Boolean? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { wifiManager?.isStaConcurrencyForLocalOnlyConnectionsSupported }
                    .getOrNull()
            } else {
                null
            }

        val wifiDirectSupported =
            pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) ||
                runCatching { wifiManager?.isP2pSupported == true }.getOrDefault(false)

        val fiveGhzSupported =
            runCatching { wifiManager?.is5GHzBandSupported == true }.getOrDefault(false)

        val sixGhzSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { wifiManager?.is6GHzBandSupported == true }.getOrDefault(false)
        } else {
            false
        }

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }

        return buildReport(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE ?: "13+",
            sdkInt = Build.VERSION.SDK_INT,
            hardware = Build.HARDWARE,
            socModel = socModel,
            staApConcurrency = staApConcurrency,
            staLocalOnlyConcurrency = staLocalOnlyConcurrency,
            wifiDirectSupported = wifiDirectSupported,
            fiveGhzSupported = fiveGhzSupported,
            sixGhzSupported = sixGhzSupported,
            nowMs = System.currentTimeMillis()
        )
    }

    companion object {
        fun buildReport(
            manufacturer: String,
            brand: String,
            model: String,
            androidRelease: String,
            sdkInt: Int,
            hardware: String,
            socModel: String,
            staApConcurrency: Boolean?,
            staLocalOnlyConcurrency: Boolean?,
            wifiDirectSupported: Boolean,
            fiveGhzSupported: Boolean,
            sixGhzSupported: Boolean,
            nowMs: Long = System.currentTimeMillis()
        ): CompatibilityReport {
            val id = "${manufacturer.lowercase(Locale.US)} ${brand.lowercase(Locale.US)}"
            val isPixel = id.contains("google")
            val isSamsung = id.contains("samsung")
            val isXiaomiGroup =
                id.contains("xiaomi") || id.contains("redmi") || id.contains("poco")
            val isTranssion =
                id.contains("tecno") || id.contains("infinix") || id.contains("itel")

            val verdict = when {
                staApConcurrency == true -> CompatibilityVerdict.NATIVE_STA_AP_READY
                wifiDirectSupported || staLocalOnlyConcurrency == true ->
                    CompatibilityVerdict.RELAY_SUPPORTED
                else -> CompatibilityVerdict.LIMITED_HARDWARE
            }

            val profile = when {
                isPixel -> ManufacturerGuidance(
                    title = "Google Pixel (Stock Android / Tensor & Snapdragon)",
                    summary = if (staApConcurrency == true) {
                        "Hardware reports STA+AP concurrency. Pixel 4 and newer natively bridge upstream Wi-Fi through the system Hotspot when mobile data is disabled or Wi-Fi is active."
                    } else {
                        "Pixel firmware supports LocalOnlyHotspot and Wi-Fi Direct relay modes without root."
                    },
                    steps = listOf(
                        "For transparent all-app sharing without client proxy setup: keep Wi-Fi connected, turn off Mobile Data temporarily to guarantee zero cellular billing, and enable System Hotspot.",
                        "For proxy-level Wi-Fi-locked sharing (keeps Mobile Data safe): use Local Hotspot + Wi-Fi Relay or Wi-Fi Direct + Wi-Fi Relay.",
                        "Set Battery usage for WiFi Share to Unrestricted so Android Adaptive Battery does not throttle active relay sockets."
                    ),
                    quirks = listOf(
                        "Android Private DNS on client devices may bypass local HTTP proxy DNS; switch client Private DNS to Automatic if pages stall.",
                        "LocalOnlyHotspot assigns randomized system credentials on Android 13+."
                    )
                )

                isSamsung -> ManufacturerGuidance(
                    title = "Samsung Galaxy (One UI)",
                    summary = if (staApConcurrency == true) {
                        "Device supports STA+AP concurrency. One UI provides a built-in 'Wi-Fi sharing' toggle inside Mobile Hotspot Advanced settings on supported models."
                    } else {
                        "System Wi-Fi sharing appears restricted on this One UI build; use WiFi Share's Local Hotspot or Wi-Fi Direct Relay bound strictly to Wi-Fi."
                    },
                    steps = listOf(
                        "Check Settings -> Connections -> Mobile Hotspot and Tethering -> Mobile Hotspot -> Configure -> Advanced -> Enable 'Wi-Fi sharing'.",
                        "Disable One UI 'Switch to mobile data' under Wi-Fi Intelligent Wi-Fi settings while sharing.",
                        "Set App Battery to Unrestricted in App Info -> Battery so One UI Sleeping Apps does not pause the relay."
                    ),
                    quirks = listOf(
                        "Samsung Galaxy A-series / budget Exynos models may lack STA+AP queues and disconnect Wi-Fi when system Hotspot starts.",
                        "One UI 6+ may restrict /proc/net/arp reads; connected proxy clients still appear under Relay Sessions."
                    )
                )

                isXiaomiGroup -> ManufacturerGuidance(
                    title = "Xiaomi / Redmi / POCO (HyperOS & MIUI)",
                    summary = if (staApConcurrency == true) {
                        "Dual Wi-Fi STA+AP hardware concurrency detected. HyperOS/MIUI usually shares Wi-Fi automatically when Hotspot is started while connected to Wi-Fi."
                    } else {
                        "STA+AP system tethering is unavailable on this chipset; use WiFi Share Relay Mode so upstream Wi-Fi stays connected."
                    },
                    steps = listOf(
                        "If using System Hotspot Bridge: enable Portable Hotspot while already connected to Wi-Fi and verify the Wi-Fi icon stays active.",
                        "In Security -> Battery -> App battery saver, set WiFi Share to 'No restrictions' and lock it in Recents.",
                        "If LocalOnlyHotspot drops when screen turns off, switch to Wi-Fi Direct + Wi-Fi Relay or keep Foreground Service enabled."
                    ),
                    quirks = listOf(
                        "MIUI/HyperOS aggressive memory killer suspends background sockets unless battery saver is set to No restrictions.",
                        "Some MIUI builds hide LocalOnlyHotspot interface IPs until a client associates."
                    )
                )

                isTranssion -> ManufacturerGuidance(
                    title = "Tecno / Infinix / itel (HiOS & XOS)",
                    summary = if (staApConcurrency == true) {
                        "Hardware reports STA+AP support, though Transsion HiOS/XOS builds sometimes restrict simultaneous Wi-Fi sharing in System Hotspot."
                    } else {
                        "STA+AP simultaneous system hotspot is NOT supported on this hardware/ROM. Starting the phone's system Hotspot will disconnect Wi-Fi and risk using Mobile Data!"
                    },
                    steps = listOf(
                        "Prefer WiFi Share's 'Wi-Fi Direct + Wi-Fi Relay' or 'Local Hotspot + Wi-Fi Relay' mode instead of System Hotspot.",
                        "Keep 'Wi-Fi-Only Safety Mode' enabled so cellular data is never touched if Wi-Fi drops.",
                        "Open Phone Master / Battery Lab and whitelist WiFi Share from Auto-Clean and background freeze."
                    ),
                    quirks = listOf(
                        "Many Helio-based Tecno/Infinix phones turn off Wi-Fi client radio as soon as System Portable Hotspot activates.",
                        "HiOS/XOS restricts background services quickly after screen lock without Battery Lab exclusion."
                    )
                )

                else -> ManufacturerGuidance(
                    title = "$manufacturer ($brand) Android Device",
                    summary = if (staApConcurrency == true) {
                        "Your device reports STA+AP concurrency support. Both System Wi-Fi Bridge (if permitted by carrier/OEM) and App-Level Wi-Fi Relay are viable."
                    } else {
                        "System STA+AP concurrency is disabled or unsupported. Use WiFi Share's Local Hotspot or Wi-Fi Direct Relay to keep upstream Wi-Fi active."
                    },
                    steps = listOf(
                        "Keep Wi-Fi-Only Safety Mode enabled to prevent unintended mobile data usage.",
                        "Use Local Hotspot + Relay or Wi-Fi Direct + Relay, then set manual HTTP proxy on client devices.",
                        "Disable battery optimization for WiFi Share during long sharing sessions."
                    ),
                    quirks = listOf(
                        "Unrooted Android 13+ apps cannot force system NAT tethering; client devices need the displayed proxy host & port for Relay mode."
                    )
                )
            }

            return CompatibilityReport(
                manufacturer = manufacturer,
                brand = brand,
                model = model,
                androidVersionLabel = "Android $androidRelease (API $sdkInt)",
                sdkInt = sdkInt,
                hardware = hardware,
                socModel = socModel,
                staApConcurrencySupported = staApConcurrency,
                staLocalOnlyConcurrencySupported = staLocalOnlyConcurrency,
                wifiDirectSupported = wifiDirectSupported,
                fiveGhzSupported = fiveGhzSupported,
                sixGhzSupported = sixGhzSupported,
                verdict = verdict,
                manufacturerTitle = profile.title,
                manufacturerSummary = profile.summary,
                manufacturerSteps = profile.steps,
                knownQuirks = profile.quirks,
                checkedAtEpochMs = nowMs
            )
        }

        private data class ManufacturerGuidance(
            val title: String,
            val summary: String,
            val steps: List<String>,
            val quirks: List<String>
        )
    }
}
