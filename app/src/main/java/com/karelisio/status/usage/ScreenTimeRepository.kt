package com.karelisio.status.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppScreenTime(val packageName: String, val label: String, val foregroundMs: Long, val launches: Int)

data class DayScreenTime(val dayStart: Long, val totalMs: Long, val unlocks: Int)

data class ScreenTimeReport(
    val today: DayScreenTime,
    val apps: List<AppScreenTime>,
    val week: List<DayScreenTime>,
)

class ScreenTimeRepository(private val context: Context, private val apps: AppInfoCache) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    suspend fun report(): ScreenTimeReport? = withContext(Dispatchers.IO) {
        if (!UsageAccess.granted(context)) return@withContext null
        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)
        val week = (6 downTo 0).map { back ->
            val start = todayStart - back * DAY
            val end = if (back == 0) now else start + DAY
            val r = aggregate(start, end)
            DayScreenTime(start, r.perApp.values.sumOf { it[0] }, r.unlocks)
        }
        val today = aggregate(todayStart, now)
        val appList = today.perApp.map { (pkg, v) ->
            AppScreenTime(pkg, apps.label(pkg).label, v[0], v[1].toInt())
        }.filter { it.foregroundMs >= 1_000 }.sortedByDescending { it.foregroundMs }
        ScreenTimeReport(week.last(), appList, week)
    }

    /** Foreground time today per package, for ranking "greedy" apps. */
    suspend fun todayByPackage(): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!UsageAccess.granted(context)) return@withContext emptyMap()
        val now = System.currentTimeMillis()
        aggregate(startOfDay(now), now).perApp.mapValues { it.value[0] }
    }

    private class Aggregate(val perApp: Map<String, LongArray>, val unlocks: Int)

    private fun aggregate(start: Long, end: Long): Aggregate {
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val resumedAt = HashMap<String, Long>() // key = package/class
        val perApp = HashMap<String, LongArray>() // [foregroundMs, launches]
        var unlocks = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val key = pkg + "/" + event.className
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (resumedAt.keys.none { it.startsWith("$pkg/") }) {
                        perApp.getOrPut(pkg) { LongArray(2) }[1]++
                    }
                    resumedAt[key] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    resumedAt.remove(key)?.let { t ->
                        perApp.getOrPut(pkg) { LongArray(2) }[0] += (event.timeStamp - t).coerceAtLeast(0)
                    }
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // Screen off: close every open session.
                    resumedAt.forEach { (k, t) ->
                        perApp.getOrPut(k.substringBefore('/')) { LongArray(2) }[0] += (event.timeStamp - t).coerceAtLeast(0)
                    }
                    resumedAt.clear()
                }
            }
        }
        resumedAt.forEach { (k, t) ->
            perApp.getOrPut(k.substringBefore('/')) { LongArray(2) }[0] += (end - t).coerceAtLeast(0)
        }
        launchers.forEach { perApp.remove(it) }
        return Aggregate(perApp, unlocks)
    }

    private val launchers: Set<String> by lazy {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    private fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        private const val DAY = 86_400_000L
    }
}
