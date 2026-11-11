package com.example.util

import java.security.SecureRandom
import java.util.Locale

object Formatters {

    private val passphraseChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    private val secureRandom = SecureRandom()

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatRate(bytesPerSec: Long): String {
        return "${formatBytes(bytesPerSec.coerceAtLeast(0L))}/s"
    }

    fun formatDuration(elapsedMs: Long): String {
        if (elapsedMs <= 0L) return "00:00:00"
        val totalSeconds = elapsedMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun frequencyToChannel(frequencyMhz: Int): Int {
        return when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
            else -> 0
        }
    }

    fun frequencyToBandLabel(frequencyMhz: Int): String {
        return when {
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            frequencyMhz in 4900..5900 -> "5 GHz"
            frequencyMhz in 5925..7125 -> "6 GHz"
            else -> "--"
        }
    }

    fun rssiToPercent(rssiDbm: Int): Int {
        if (rssiDbm <= -100) return 0
        if (rssiDbm >= -50) return 100
        return ((rssiDbm + 100) * 2).coerceIn(0, 100)
    }

    fun generateSecurePassphrase(length: Int = 12): String {
        val targetLength = length.coerceIn(8, 32)
        return buildString(targetLength) {
            repeat(targetLength) {
                append(passphraseChars[secureRandom.nextInt(passphraseChars.length)])
            }
        }
    }

    fun sanitizeP2pSsidSuffix(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "")
        return cleaned.ifEmpty { "WiFiShare" }.take(20)
    }

    fun buildDirectSsid(suffix: String): String {
        val clean = sanitizeP2pSsidSuffix(suffix)
        return "DIRECT-WS-$clean".take(32)
    }
}
