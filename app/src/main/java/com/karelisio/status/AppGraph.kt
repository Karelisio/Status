package com.karelisio.status

import android.content.Context
import com.karelisio.status.data.db.SnapshotEntity
import com.karelisio.status.data.db.StatusDatabase
import com.karelisio.status.data.prefs.SettingsStore
import com.karelisio.status.history.HistoryRecorder
import com.karelisio.status.history.HistoryRepository
import com.karelisio.status.metrics.MetricsRepository
import com.karelisio.status.usage.AppInfoCache
import com.karelisio.status.usage.DataUsageRepository
import com.karelisio.status.usage.ScreenTimeRepository
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

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    suspend fun onPowerConnected() {
        HistoryRecorder.record(SnapshotEntity.EVENT_PLUGGED)
    }

    suspend fun onPowerDisconnected() {
        HistoryRecorder.record(SnapshotEntity.EVENT_UNPLUGGED)
    }

    /** Called by the 15-minute worker after the snapshot is stored. */
    suspend fun onPeriodicTick() {
    }
}
