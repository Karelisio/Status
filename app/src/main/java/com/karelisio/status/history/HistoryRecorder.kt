package com.karelisio.status.history

import com.karelisio.status.AppGraph
import com.karelisio.status.data.db.SnapshotEntity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Writes history rows, de-duplicating plug events delivered by several sources at once. */
object HistoryRecorder {
    private val mutex = Mutex()
    private val lastEventAt = HashMap<Int, Long>()

    val RETENTION_MS = TimeUnit.DAYS.toMillis(30)

    suspend fun record(event: Int): SnapshotEntity? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (event != SnapshotEntity.EVENT_PERIODIC) {
            val last = lastEventAt[event] ?: 0L
            if (now - last < 15_000) return null
            lastEventAt[event] = now
        }
        val snapshot = AppGraph.metrics.snapshot(event)
        AppGraph.database.snapshots().insert(snapshot)
        snapshot
    }

    suspend fun purge(): Int =
        AppGraph.database.snapshots().purgeBefore(System.currentTimeMillis() - RETENTION_MS)
}
