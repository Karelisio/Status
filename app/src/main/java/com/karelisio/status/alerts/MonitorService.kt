package com.karelisio.status.alerts

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.karelisio.status.AppGraph
import com.karelisio.status.R
import com.karelisio.status.metrics.BatteryInfo
import com.karelisio.status.util.Format
import com.karelisio.status.util.Labels
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single foreground service covering two needs:
 *  - charge monitoring (threshold / overheat alerts), started on power connection;
 *  - the optional persistent notification with live stats.
 * It stops itself when neither is needed.
 */
class MonitorService : LifecycleService() {
    private var batteryReceiver: BroadcastReceiver? = null
    private var liveJob: Job? = null
    private var wasPlugged = false
    private var liveEnabled = false
    private var chargeEnabled = true
    private var lastBattery: BatteryInfo? = null

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val battery = AppGraph.metrics.battery.read()
        lastBattery = battery
        val started = runCatching {
            ServiceCompat.startForeground(
                this, Notifications.ID_MONITOR, buildNotification(battery, null, null),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        wasPlugged = battery.plugged != 0
        lifecycleScope.launch {
            val s = AppGraph.settings.current()
            liveEnabled = s.liveNotification
            chargeEnabled = s.chargingServiceEnabled
            if (!shouldRun(battery)) {
                stopSelf()
                return@launch
            }
            registerBattery()
            if (liveEnabled) startLiveLoop()
            AppGraph.alerts.onBattery(battery)
        }
        return START_STICKY
    }

    private fun shouldRun(b: BatteryInfo) = liveEnabled || (chargeEnabled && b.plugged != 0)

    private fun registerBattery() {
        if (batteryReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val b = AppGraph.metrics.battery.read(intent)
                lastBattery = b
                lifecycleScope.launch { onBattery(b) }
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        batteryReceiver = receiver
    }

    private suspend fun onBattery(b: BatteryInfo) {
        val plugged = b.plugged != 0
        if (wasPlugged && !plugged) AppGraph.onPowerDisconnected()
        if (!wasPlugged && plugged) AppGraph.onPowerConnected(startService = false)
        wasPlugged = plugged
        AppGraph.alerts.onBattery(b)
        if (!shouldRun(b)) {
            stopSelf()
            return
        }
        if (!liveEnabled) update(b, null, null)
    }

    private fun startLiveLoop() {
        liveJob?.cancel()
        liveJob = lifecycleScope.launch {
            while (isActive) {
                val m = AppGraph.metrics.sample()
                lastBattery = m.battery
                update(m.battery, m.network.rxRate, m.network.txRate)
                AppGraph.updateWidgets()
                delay(LIVE_INTERVAL)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun update(b: BatteryInfo, rx: Long?, tx: Long?) {
        if (!Notifications.canPost(this)) return
        NotificationManagerCompat.from(this).notify(Notifications.ID_MONITOR, buildNotification(b, rx, tx))
    }

    private fun buildNotification(b: BatteryInfo, rx: Long?, tx: Long?): Notification {
        val parts = buildList {
            add("${b.level} %")
            b.powerW?.let { add(Format.watts(abs(it))) }
            add(Format.celsius(b.tempC))
        }
        val title = if (b.charging) "En charge · ${parts.joinToString(" · ")}" else parts.joinToString(" · ")
        val text = buildString {
            if (b.charging) {
                val remaining = timeToFull()
                append(Labels.batteryStatus(b))
                if (remaining != null) append(" · plein dans ${Format.duration(remaining)}")
            } else {
                append(Labels.batteryStatus(b))
            }
            if (rx != null && tx != null) append("\n↓ ${Format.rate(rx)}   ↑ ${Format.rate(tx)}")
        }
        return NotificationCompat.Builder(this, Notifications.CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_status)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setProgress(100, b.level, false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Notifications.openAppIntent(this))
            .build()
    }

    private fun timeToFull(): Long? = runCatching {
        getSystemService(BatteryManager::class.java).computeChargeTimeRemaining().takeIf { it > 0 }
    }.getOrNull()

    override fun onDestroy() {
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver = null
        liveJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val LIVE_INTERVAL = 5_000L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
