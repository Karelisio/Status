package com.karelisio.status

import android.content.Context
import com.karelisio.status.alerts.AlertEngine
import com.karelisio.status.alerts.ChargingTriggerWorker
import com.karelisio.status.alerts.MonitorService
import com.karelisio.status.data.db.SnapshotEntity
import com.karelisio.status.data.db.StatusDatabase
import com.karelisio.status.data.prefs.SettingsStore
import com.karelisio.status.history.HistoryRecorder
import com.karelisio.status.history.HistoryRepository
import com.karelisio.status.history.SnapshotWorker
import com.karelisio.status.metrics.MetricsRepository
import com.karelisio.status.usage.AppInfoCache
import com.karelisio.status.usage.DataUsageRepository
import com.karelisio.status.usage.ScreenTimeRepository
import com.karelisio.status.shizuku.ShizukuManager
import com.karelisio.status.tweaks.CaffeineScheduler
import com.karelisio.status.tweaks.SettingsBackend
import com.karelisio.status.tweaks.TweakBackup
import com.karelisio.status.tweaks.TweakManager
import com.karelisio.status.tiles.BaseTile
import com.karelisio.status.widget.StatusWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Minimal manual DI: process-wide singletons created lazily from the application context. */
object AppGraph {
    lateinit var app: Context
        private set

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { StatusDatabase.create(app) }
    val settings by lazy { SettingsStore(app) }
    val metrics by lazy { MetricsRepository(app, scope) }
    val history by lazy { HistoryRepository(database.snapshots()) }
    val apps by lazy { AppInfoCache(app) }
    val dataUsage by lazy { DataUsageRepository(app, apps) }
    val screenTime by lazy { ScreenTimeRepository(app, apps) }
    val alerts by lazy { AlertEngine(app, settings, metrics.system, dataUsage) }
    val tweaks by lazy { TweakManager(app, SettingsBackend(app), settings, TweakBackup(settings)) }

    fun init(context: Context) {
        if (!::app.isInitialized) {
            app = context.applicationContext
            ShizukuManager.init()
        }
    }

    suspend fun onPowerConnected(startService: Boolean = true) {
        HistoryRecorder.record(SnapshotEntity.EVENT_PLUGGED)
        if (startService && settings.current().chargingServiceEnabled) MonitorService.start(app)
        updateWidgets(force = true)
    }

    suspend fun onPowerDisconnected() {
        HistoryRecorder.record(SnapshotEntity.EVENT_UNPLUGGED)
        alerts.onUnplugged()
        ChargingTriggerWorker.arm(app, replace = true)
        updateWidgets(force = true)
    }

    /** Called by the 15-minute worker after the snapshot is stored. */
    suspend fun onPeriodicTick() {
        alerts.checkStorage()
        alerts.checkPlan()
        alerts.onBattery(metrics.battery.read())
        updateWidgets(force = true)
    }

    suspend fun onBoot() {
        SnapshotWorker.schedule(app)
        ChargingTriggerWorker.arm(app)
        ensureMonitor()
        CaffeineScheduler.onBoot(app)
    }

    /** Starts the monitor service if the live notification is on or the phone is charging. */
    suspend fun ensureMonitor() {
        val s = settings.current()
        val plugged = metrics.battery.read().plugged != 0
        if (s.liveNotification || (s.chargingServiceEnabled && plugged)) MonitorService.start(app)
        else MonitorService.stop(app)
    }

    suspend fun updateWidgets(force: Boolean = false) {
        StatusWidget.updateAll(app, force)
    }

    fun refreshTiles() {
        BaseTile.requestRefreshAll(app)
    }
}
