package com.karelisio.status.usage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.karelisio.status.data.prefs.AppSettings
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlanCycle(val start: Long, val end: Long) {
    val lengthMs: Long get() = end - start
}

data class PlanUsage(
    val cycle: PlanCycle,
    val usedBytes: Long,
    val planBytes: Long,
    val projectedBytes: Long,
    val now: Long,
) {
    val ratio: Float get() = if (planBytes > 0) usedBytes.toFloat() / planBytes else 0f
    val projectedRatio: Float get() = if (planBytes > 0) projectedBytes.toFloat() / planBytes else 0f
    val remainingBytes: Long get() = (planBytes - usedBytes).coerceAtLeast(0)
    val daysLeft: Int get() = ((cycle.end - now) / 86_400_000.0).let { kotlin.math.ceil(it).toInt() }
}

data class AppDataUsage(val key: String, val label: String, val packageName: String?, val rxBytes: Long, val txBytes: Long) {
    val total: Long get() = rxBytes + txBytes
}

enum class NetType(val label: String, val type: Int) {
    MOBILE("Mobile", ConnectivityManager.TYPE_MOBILE),
    WIFI("Wi-Fi", ConnectivityManager.TYPE_WIFI),
}

class DataUsageRepository(private val context: Context, private val apps: AppInfoCache) {
    private val nsm = context.getSystemService(NetworkStatsManager::class.java)

    fun cycle(renewDay: Int, now: Long = System.currentTimeMillis()): PlanCycle {
        val day = renewDay.coerceIn(1, 28)
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (get(Calendar.DAY_OF_MONTH) < day) add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return PlanCycle(start.timeInMillis, end.timeInMillis)
    }

    suspend fun plan(settings: AppSettings): PlanUsage? = withContext(Dispatchers.IO) {
        if (!UsageAccess.granted(context)) return@withContext null
        val now = System.currentTimeMillis()
        val cycle = cycle(settings.planRenewDay, now)
        val used = runCatching {
            nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, cycle.start, now)
                .let { it.rxBytes + it.txBytes }
        }.getOrDefault(0L)
        val elapsed = (now - cycle.start).coerceAtLeast(3_600_000)
        val projected = (used.toDouble() * cycle.lengthMs / elapsed).toLong()
        PlanUsage(cycle, used, (settings.planGb * 1e9).toLong(), projected, now)
    }

    suspend fun perApp(type: NetType, start: Long, end: Long = System.currentTimeMillis()): List<AppDataUsage> =
        withContext(Dispatchers.IO) {
            if (!UsageAccess.granted(context)) return@withContext emptyList()
            val byUid = HashMap<Int, LongArray>()
            runCatching {
                val stats = nsm.querySummary(type.type, null, start, end)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val acc = byUid.getOrPut(bucket.uid) { LongArray(2) }
                    acc[0] += bucket.rxBytes
                    acc[1] += bucket.txBytes
                }
                stats.close()
            }
            byUid.map { (uid, v) ->
                val (label, pkg) = labelForUid(uid)
                AppDataUsage("$uid", label, pkg, v[0], v[1])
            }.filter { it.total > 0 }.sortedByDescending { it.total }
        }

    private fun labelForUid(uid: Int): Pair<String, String?> = when (uid) {
        NetworkStats.Bucket.UID_REMOVED -> "Applis désinstallées" to null
        NetworkStats.Bucket.UID_TETHERING -> "Partage de connexion" to null
        android.os.Process.SYSTEM_UID -> "Système Android" to "android"
        0 -> "Noyau / root" to null
        else -> {
            val pkgs = apps.packagesForUid(uid)
            when {
                pkgs.isEmpty() -> "UID $uid" to null
                pkgs.size == 1 -> apps.label(pkgs[0]).label to pkgs[0]
                else -> {
                    val main = pkgs.map { apps.label(it) }.minByOrNull { it.label.length }!!
                    "${main.label} (+${pkgs.size - 1})" to main.packageName
                }
            }
        }
    }
}
