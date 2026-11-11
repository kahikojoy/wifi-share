package com.example.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class QrMatrix(
    val width: Int,
    val height: Int,
    val modules: BooleanArray
) {
    fun isDark(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return modules[y * width + x]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrix) return false
        return width == other.width && height == other.height && modules.contentEquals(other.modules)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + modules.contentHashCode()
        return result
    }
}

object QrCodeGenerator {

    fun escapeWifiField(value: String): String {
        return buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '\\', ';', ',', ':' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
        }
    }

    fun buildWifiQrPayload(
        ssid: String,
        password: String,
        hidden: Boolean = false
    ): String {
        val escapedSsid = escapeWifiField(ssid)
        val escapedPass = escapeWifiField(password)
        val security = if (password.isEmpty()) "nopass" else "WPA"
        val hiddenFlag = if (hidden) "true" else "false"
        return if (security == "nopass") {
            "WIFI:T:nopass;S:$escapedSsid;H:$hiddenFlag;;"
        } else {
            "WIFI:T:WPA;S:$escapedSsid;P:$escapedPass;H:$hiddenFlag;;"
        }
    }

    fun encodeMatrix(content: String, matrixSize: Int = 64): QrMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            matrixSize,
            matrixSize,
            hints
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val modules = BooleanArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                modules[y * width + x] = bitMatrix.get(x, y)
            }
        }
        return QrMatrix(width = width, height = height, modules = modules)
    }
}
