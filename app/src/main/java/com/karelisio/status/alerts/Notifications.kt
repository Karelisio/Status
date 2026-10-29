package com.karelisio.status.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.karelisio.status.MainActivity
import com.karelisio.status.R

object Notifications {
    const val CH_ALERTS = "alerts"
    const val CH_MONITOR = "monitor"
    const val CH_UPDATES = "updates"
    const val CH_TWEAKS = "tweaks"

    const val ID_MONITOR = 1
    const val ID_CHARGE = 10
    const val ID_OVERHEAT = 11
    const val ID_STORAGE = 12
    const val ID_PLAN = 13
    const val ID_UPDATE = 20
    const val ID_CAFFEINE = 30

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_ALERTS, "Alertes", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Seuil de charge, surchauffe, stockage, forfait"
                },
                NotificationChannel(CH_MONITOR, "Surveillance en cours", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Charge en cours et statistiques live"
                    setShowBadge(false)
                },
                NotificationChannel(CH_UPDATES, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_TWEAKS, "Tweaks actifs", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun alert(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_status)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
