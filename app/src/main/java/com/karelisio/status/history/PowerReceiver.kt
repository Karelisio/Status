package com.karelisio.status.history

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.status.AppGraph
import com.karelisio.status.data.db.SnapshotEntity
import kotlinx.coroutines.launch

/**
 * Plug / unplug events. Declared in the manifest (honored by some OEM builds and older Android)
 * and also registered at runtime by [com.karelisio.status.StatusApp], since Android 8+ no longer
 * delivers these implicit broadcasts to manifest receivers.
 */
class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.init(context)
        val pending = goAsync()
        AppGraph.scope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> AppGraph.onPowerConnected()
                    Intent.ACTION_POWER_DISCONNECTED -> AppGraph.onPowerDisconnected()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
