package com.karelisio.status.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.status.AppGraph
import kotlinx.coroutines.launch

/** Restores background work after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AppGraph.init(context)
        val pending = goAsync()
        AppGraph.scope.launch {
            try {
                AppGraph.onBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
