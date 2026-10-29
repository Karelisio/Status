package com.karelisio.status.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.status.AppGraph

/**
 * Reliable "charger connected" trigger on Android 8+: JobScheduler runs this job as soon as the
 * device starts charging, even if the process was killed. Re-armed by [AppGraph.onPowerDisconnected].
 */
class ChargingTriggerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        if (AppGraph.metrics.battery.read().plugged != 0) AppGraph.onPowerConnected()
        return Result.success()
    }

    companion object {
        private const val NAME = "charging-trigger"

        fun arm(context: Context, replace: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<ChargingTriggerWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
