package com.karelisio.status.tweaks

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.status.AppGraph
import com.karelisio.status.R
import com.karelisio.status.alerts.Notifications
import com.karelisio.status.data.prefs.SettingsStore
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/** Timed auto-restore and the ongoing "Caféine active" notification. */
object CaffeineScheduler {
    private const val WORK = "caffeine-restore"
    const val ACTION_STOP = "com.karelisio.status.action.CAFFEINE_STOP"

    fun schedule(context: Context, durationMin: Int?) {
        val wm = WorkManager.getInstance(context)
        if (durationMin == null) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = OneTimeWorkRequestBuilder<CaffeineRestoreWorker>().setInitialDelay(durationMin.toLong(), TimeUnit.MINUTES).build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        NotificationManagerCompat.from(context).cancel(Notifications.ID_CAFFEINE)
    }

    @Suppress("MissingPermission")
    fun showNotification(context: Context, until: Long?) {
        if (!Notifications.canPost(context)) return
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, CaffeineStopReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = until?.let { "Jusqu'à ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}, puis délai d'origine restauré" }
            ?: "L'écran reste allumé. Le délai d'origine sera restauré à l'arrêt."
        val n = NotificationCompat.Builder(context, Notifications.CH_TWEAKS)
            .setSmallIcon(R.drawable.ic_stat_status)
            .setContentTitle("Caféine active")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(Notifications.openAppIntent(context))
            .addAction(0, "Arrêter", stop)
            .build()
        NotificationManagerCompat.from(context).notify(Notifications.ID_CAFFEINE, n)
    }

    /** After a reboot: expire or re-arm caffeine. */
    suspend fun onBoot(context: Context) {
        val store = AppGraph.settings
        if (store.get(SettingsStore.Keys.caffeineActive) != true) return
        val until = store.get(SettingsStore.Keys.caffeineUntil)
        val now = System.currentTimeMillis()
        when {
            until != null && until <= now -> AppGraph.tweaks.setCaffeine(false)
            until != null -> {
                schedule(context, ((until - now) / 60_000).toInt().coerceAtLeast(1))
                showNotification(context, until)
            }
            else -> showNotification(context, null)
        }
    }
}

class CaffeineRestoreWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        AppGraph.tweaks.setCaffeine(false)
        AppGraph.refreshTiles()
        return Result.success()
    }
}

class CaffeineStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CaffeineScheduler.ACTION_STOP) return
        AppGraph.init(context)
        val pending = goAsync()
        AppGraph.scope.launch {
            try {
                AppGraph.tweaks.setCaffeine(false)
                AppGraph.refreshTiles()
            } finally {
                pending.finish()
            }
        }
    }
}
