package com.karelisio.status.pilote

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karelisio.status.AppGraph
import com.karelisio.status.tweaks.WriteResult
import kotlinx.coroutines.launch

/**
 * Applies a preset on request from Pilote. Protected by a signature permission declared in the
 * manifest, so only apps signed with the same key can send it. For ordered broadcasts the result
 * code tells the caller what happened (see [PiloteContract]).
 */
class ApplyPresetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PiloteContract.ACTION_APPLY_PRESET) return
        val requested = intent.getStringExtra(PiloteContract.EXTRA_PRESET)?.trim().orEmpty()
        AppGraph.init(context)
        val pending = goAsync()
        AppGraph.scope.launch {
            try {
                val tweaks = AppGraph.tweaks
                val result: Pair<Int, String> = if (requested.equals(PiloteContract.PRESET_RESTORE, ignoreCase = true)) {
                    tweaks.restorePreset().toResult("Restauré")
                } else {
                    val preset = tweaks.loadPresets().firstOrNull {
                        it.id.equals(requested, ignoreCase = true) || it.name.equals(requested, ignoreCase = true)
                    }
                    preset?.let { tweaks.applyPreset(it.id).toResult(it.name) }
                        ?: (PiloteContract.RESULT_UNKNOWN_PRESET to "Préréglage inconnu : $requested")
                }
                if (isOrderedBroadcast) {
                    resultCode = if (result.first == PiloteContract.RESULT_OK) Activity.RESULT_OK else result.first
                    resultData = result.second
                }
                AppGraph.refreshTiles()
            } finally {
                pending.finish()
            }
        }
    }

    private fun WriteResult.toResult(ok: String) = when (this) {
        WriteResult.Ok -> PiloteContract.RESULT_OK to ok
        is WriteResult.Failed -> PiloteContract.RESULT_FAILED to reason
    }
}
