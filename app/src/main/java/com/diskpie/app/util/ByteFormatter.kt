package com.diskpie.app.util

import java.util.Locale
import kotlin.math.abs

object ByteFormatter {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun format(bytes: Long): String {
        if (bytes == 0L) return "0 B"

        var value = abs(bytes.toDouble())
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }

        val signedValue = if (bytes < 0L) -value else value
        val pattern = when {
            unitIndex == 0 -> "%.0f %s"
            signedValue >= 100 -> "%.0f %s"
            signedValue >= 10 -> "%.1f %s"
            else -> "%.2f %s"
        }
        return String.format(Locale.KOREA, pattern, signedValue, units[unitIndex])
    }
}
