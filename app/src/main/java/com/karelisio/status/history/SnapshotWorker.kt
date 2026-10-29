package com.karelisio.status.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.status.AppGraph
import com.karelisio.status.data.db.SnapshotEntity
import java.util.concurrent.TimeUnit

/** Periodic job (every 15 min): records a snapshot, purges old rows and runs background checks. */
class SnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        HistoryRecorder.record(SnapshotEntity.EVENT_PERIODIC)
        HistoryRecorder.purge()
        AppGraph.onPeriodicTick()
        return Result.success()
    }

    companion object {
        private const val NAME = "snapshot"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SnapshotWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
