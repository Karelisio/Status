package com.karelisio.status.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One point of history: recorded every 15 min and on every plug / unplug event. */
@Entity(tableName = "snapshots", indices = [Index("timestamp")])
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val event: Int = EVENT_PERIODIC,
    val batteryLevel: Int,
    val batteryTempC: Float,
    val voltageMv: Int,
    val currentMa: Int,
    val powerW: Float,
    val charging: Boolean,
    val plugType: Int,
    /** Cumulative counters since boot (TrafficStats); deltas are computed when reading. */
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    /** Boot timestamp (wall clock), used to detect counter resets after a reboot. */
    val bootTime: Long,
    val storageFreeBytes: Long,
    val ramAvailBytes: Long,
) {
    companion object {
        const val EVENT_PERIODIC = 0
        const val EVENT_PLUGGED = 1
        const val EVENT_UNPLUGGED = 2
    }
}
