package com.karelisio.status.metrics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import java.io.File

data class CoreInfo(val index: Int, val online: Boolean, val curKhz: Long?, val minKhz: Long?, val maxKhz: Long?) {
    val ratio: Float? get() = if (curKhz != null && maxKhz != null && maxKhz > 0) {
        val min = minKhz ?: 0
        ((curKhz - min).toFloat() / (maxKhz - min).coerceAtLeast(1)).coerceIn(0f, 1f)
    } else null
}

data class CpuInfo(
    val cores: List<CoreInfo>,
    /** 0..1. Exact when /proc/stat is readable, otherwise estimated from current frequencies. */
    val load: Float?,
    val loadIsEstimate: Boolean,
    val socTempC: Float?,
)

data class MemoryInfo(val totalBytes: Long, val availBytes: Long, val lowMemory: Boolean) {
    val usedRatio: Float get() = if (totalBytes > 0) 1f - availBytes.toFloat() / totalBytes else 0f
}

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedRatio: Float get() = if (totalBytes > 0) 1f - freeBytes.toFloat() / totalBytes else 0f
    val freePct: Float get() = if (totalBytes > 0) freeBytes * 100f / totalBytes else 100f
}

data class ThermalInfo(val status: Int, val headroom: Float?)

class SystemReader(private val context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)
    private val pm = context.getSystemService(PowerManager::class.java)
    private val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(readPossibleCores())
    private var lastStat: LongArray? = null
    private var procStatReadable = true
    private val socZone: File? by lazy { findSocThermalZone() }

    fun cpu(): CpuInfo {
        val cores = (0 until coreCount).map { i ->
            val base = "/sys/devices/system/cpu/cpu$i"
            val online = i == 0 || readLong("$base/online")?.let { it == 1L } ?: true
            CoreInfo(
                index = i,
                online = online,
                curKhz = if (online) readLong("$base/cpufreq/scaling_cur_freq") else null,
                minKhz = readLong("$base/cpufreq/cpuinfo_min_freq"),
                maxKhz = readLong("$base/cpufreq/cpuinfo_max_freq"),
            )
        }
        val exact = readProcStatLoad()
        val estimate = cores.mapNotNull { it.ratio }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return CpuInfo(
            cores = cores,
            load = exact ?: estimate,
            loadIsEstimate = exact == null,
            socTempC = socZone?.let { readLong(it.path)?.let { v -> if (v > 1000) v / 1000f else v.toFloat() } }
                ?.takeIf { it in 5f..130f },
        )
    }

    fun memory(): MemoryInfo {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return MemoryInfo(mi.totalMem, mi.availMem, mi.lowMemory)
    }

    fun storage(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(stat.totalBytes, stat.availableBytes)
    }

    fun thermal(): ThermalInfo = ThermalInfo(
        status = pm.currentThermalStatus,
        headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getThermalHeadroom(10).takeIf { !it.isNaN() }
        } else null,
    )

    fun uptimeMs(): Long = SystemClock.elapsedRealtime()

    private fun readProcStatLoad(): Float? {
        if (!procStatReadable) return null
        val line = runCatching { File("/proc/stat").useLines { it.firstOrNull() } }.getOrNull()
        if (line == null || !line.startsWith("cpu ")) {
            procStatReadable = false
            return null
        }
        val values = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }.toLongArray()
        val prev = lastStat
        lastStat = values
        if (prev == null || prev.size != values.size) return null
        val idle = (values[3] + values.getOrElse(4) { 0 }) - (prev[3] + prev.getOrElse(4) { 0 })
        val total = values.sum() - prev.sum()
        return if (total > 0) (1f - idle.toFloat() / total).coerceIn(0f, 1f) else null
    }

    private fun readPossibleCores(): Int = runCatching {
        // Format "0-7"
        File("/sys/devices/system/cpu/possible").readText().trim().substringAfter('-').toInt() + 1
    }.getOrDefault(1)

    private fun findSocThermalZone(): File? = runCatching {
        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.firstOrNull { zone ->
                val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrDefault("")
                (type.contains("cpu") || type.contains("soc") || type.contains("tsens")) &&
                    File(zone, "temp").canRead()
            }?.let { File(it, "temp") }
    }.getOrNull()

    private fun readLong(path: String): Long? =
        runCatching { File(path).readText().trim().toLong() }.getOrNull()
}
