package com.karelisio.status.util

import java.util.Locale
import kotlin.math.abs

object Format {
    private val locale: Locale get() = Locale.getDefault()

    fun bytes(value: Long, decimals: Int = 1): String {
        val v = abs(value).toDouble()
        return when {
            v >= 1e12 -> "%.${decimals}f To".format(locale, value / 1e12)
            v >= 1e9 -> "%.${decimals}f Go".format(locale, value / 1e9)
            v >= 1e6 -> "%.${decimals}f Mo".format(locale, value / 1e6)
            v >= 1e3 -> "%.0f ko".format(locale, value / 1e3)
            else -> "$value o"
        }
    }

    /** Bits per second, as operators advertise it. */
    fun rate(bytesPerSec: Long): String {
        val bits = bytesPerSec * 8.0
        return when {
            bits >= 1e9 -> "%.2f Gb/s".format(locale, bits / 1e9)
            bits >= 1e6 -> "%.1f Mb/s".format(locale, bits / 1e6)
            bits >= 1e3 -> "%.0f kb/s".format(locale, bits / 1e3)
            else -> "%.0f b/s".format(locale, bits)
        }
    }

    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val d = totalMin / (60 * 24)
        val h = (totalMin / 60) % 24
        val m = totalMin % 60
        return when {
            d > 0 -> "${d} j ${h} h"
            h > 0 -> "${h} h ${m.toString().padStart(2, '0')}"
            else -> "${m} min"
        }
    }

    fun watts(w: Float): String = "%.1f W".format(locale, w)
    fun celsius(c: Float): String = "%.1f °C".format(locale, c)
    fun volts(mv: Int): String = "%.2f V".format(locale, mv / 1000f)
    fun pct(ratio: Float): String = "${(ratio * 100).toInt()} %"
    fun ghz(khz: Long): String = if (khz >= 1_000_000) "%.2f GHz".format(locale, khz / 1e6) else "${khz / 1000} MHz"
    fun decimal(v: Float, decimals: Int = 1): String = "%.${decimals}f".format(locale, v)
}
