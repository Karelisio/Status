package com.karelisio.status.tiles

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.karelisio.status.AppGraph
import com.karelisio.status.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class TileUi(val active: Boolean, val subtitle: String?, val available: Boolean = true)

/** Coroutine-friendly TileService: subclasses compute their UI and handle clicks off the main thread. */
abstract class BaseTile : TileService() {
    private var scope: CoroutineScope? = null

    abstract val label: String
    abstract val iconRes: Int
    abstract suspend fun read(): TileUi
    abstract suspend fun click()

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch { render(read()) }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val s = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        s.launch {
            if (!read().available) return@launch
            click()
            render(read())
            AppGraph.refreshTiles()
        }
    }

    private fun render(ui: TileUi) {
        val tile = qsTile ?: return
        tile.label = label
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.state = when {
            !ui.available -> Tile.STATE_UNAVAILABLE
            ui.active -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (ui.available) ui.subtitle else "Accès requis"
        tile.updateTile()
    }

    companion object {
        val all = listOf(
            CaffeineTile::class.java, DnsTile::class.java, AnimationTile::class.java,
            RefreshTile::class.java, PresetTile::class.java,
        )

        fun requestRefreshAll(context: Context) {
            all.forEach { runCatching { requestListeningState(context, ComponentName(context, it)) } }
        }
    }
}
