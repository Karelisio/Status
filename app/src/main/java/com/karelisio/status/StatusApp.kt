package com.karelisio.status

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.karelisio.status.alerts.ChargingTriggerWorker
import com.karelisio.status.alerts.Notifications
import com.karelisio.status.history.PowerReceiver
import com.karelisio.status.history.SnapshotWorker

class StatusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifications.createChannels(this)
        SnapshotWorker.schedule(this)
        ChargingTriggerWorker.arm(this)
        // Runtime registration: works whenever the process is alive (see PowerReceiver).
        ContextCompat.registerReceiver(
            this,
            PowerReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        AppGraph.scope.launch { AppGraph.ensureMonitor() }
    }
}
