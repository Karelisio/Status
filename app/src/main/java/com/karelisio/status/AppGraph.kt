package com.karelisio.status

import android.content.Context
import com.karelisio.status.data.db.StatusDatabase
import com.karelisio.status.data.prefs.SettingsStore
import com.karelisio.status.metrics.MetricsRepository
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

    fun init(context: Context) {
        app = context.applicationContext
    }
}
