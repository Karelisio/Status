package com.karelisio.status.history

import com.karelisio.status.data.db.SnapshotDao
import com.karelisio.status.data.db.SnapshotEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class HistoryRange(val label: String, val durationMs: Long, val bucketMs: Long) {
    DAY("24 h", TimeUnit.HOURS.toMillis(24), TimeUnit.HOURS.toMillis(1)),
    WEEK("7 j", TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(1)),
    MONTH("30 j", TimeUnit.DAYS.toMillis(30), TimeUnit.DAYS.toMillis(1)),
}

data class DataBucket(val startMs: Long, val mobileBytes: Long, val wifiBytes: Long)

data class ChargeSession(val startMs: Long, val endMs: Long?, val fromLevel: Int, val toLevel: Int?)

data class HistoryData(
    val range: HistoryRange,
    val from: Long,
    val points: List<SnapshotEntity>,
    val buckets: List<DataBucket>,
    val sessions: List<ChargeSession>,
) {
    val avgTemp: Float? = points.takeIf { it.isNotEmpty() }?.map { it.batteryTempC }?.average()?.toFloat()
    val maxTemp: Float? = points.maxOfOrNull { it.batteryTempC }
    val totalMobile: Long = buckets.sumOf { it.mobileBytes }
    val totalWifi: Long = buckets.sumOf { it.wifiBytes }
}

class HistoryRepository(private val dao: SnapshotDao) {

    fun observe(range: HistoryRange): Flow<HistoryData> {
        val from = alignedStart(range)
        return dao.observeSince(from).map { compute(range, from, it) }
    }

    suspend fun load(range: HistoryRange): HistoryData {
        val from = alignedStart(range)
        return compute(range, from, dao.since(from))
    }

    private fun alignedStart(range: HistoryRange): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (range != HistoryRange.DAY) cal.set(Calendar.HOUR_OF_DAY, 0)
        val end = cal.timeInMillis + range.bucketMs
        return end - range.durationMs
    }

    private fun compute(range: HistoryRange, from: Long, points: List<SnapshotEntity>): HistoryData {
        val count = (range.durationMs / range.bucketMs).toInt()
        val mobile = LongArray(count)
        val total = LongArray(count)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val rebooted = kotlin.math.abs(b.bootTime - a.bootTime) > 120_000 || b.totalRxBytes < a.totalRxBytes
            val dTotal = if (rebooted) b.totalRxBytes + b.totalTxBytes
            else (b.totalRxBytes - a.totalRxBytes) + (b.totalTxBytes - a.totalTxBytes)
            val dMobile = if (rebooted || b.mobileRxBytes < a.mobileRxBytes) b.mobileRxBytes + b.mobileTxBytes
            else (b.mobileRxBytes - a.mobileRxBytes) + (b.mobileTxBytes - a.mobileTxBytes)
            val idx = ((b.timestamp - from) / range.bucketMs).toInt()
            if (idx in 0 until count) {
                total[idx] += dTotal.coerceAtLeast(0)
                mobile[idx] += dMobile.coerceAtLeast(0)
            }
        }
        val buckets = (0 until count).map { i ->
            DataBucket(from + i * range.bucketMs, mobile[i], (total[i] - mobile[i]).coerceAtLeast(0))
        }
        return HistoryData(range, from, points, buckets, sessions(points))
    }

    private fun sessions(points: List<SnapshotEntity>): List<ChargeSession> {
        val result = mutableListOf<ChargeSession>()
        var open: SnapshotEntity? = null
        for (p in points) {
            when (p.event) {
                SnapshotEntity.EVENT_PLUGGED -> open = p
                SnapshotEntity.EVENT_UNPLUGGED -> {
                    open?.let { result += ChargeSession(it.timestamp, p.timestamp, it.batteryLevel, p.batteryLevel) }
                    open = null
                }
            }
        }
        open?.let { result += ChargeSession(it.timestamp, null, it.batteryLevel, null) }
        return result.asReversed()
    }
}
