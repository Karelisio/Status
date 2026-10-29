package com.karelisio.status.ui.components

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.status.ui.theme.statusColors

enum class StatusLevel {
    NORMAL, WARNING, CRITICAL;

    /** Expressive shape that represents this state: calm cookie → sunny → burst. */
    val polygon: RoundedPolygon
        get() = when (this) {
            NORMAL -> MaterialShapes.Cookie9Sided
            WARNING -> MaterialShapes.Sunny
            CRITICAL -> MaterialShapes.SoftBurst
        }

    companion object {
        fun of(value: Float, warnAt: Float, critAt: Float): StatusLevel = when {
            value >= critAt -> CRITICAL
            value >= warnAt -> WARNING
            else -> NORMAL
        }

        /** For values where lower is worse (free storage, battery level…). */
        fun ofLow(value: Float, warnBelow: Float, critBelow: Float): StatusLevel = when {
            value <= critBelow -> CRITICAL
            value <= warnBelow -> WARNING
            else -> NORMAL
        }
    }
}

data class LevelColors(val container: Color, val content: Color, val accent: Color, val onAccent: Color)

@Composable
@ReadOnlyComposable
fun StatusLevel.colors(): LevelColors {
    val s = MaterialTheme.statusColors
    val cs = MaterialTheme.colorScheme
    return when (this) {
        StatusLevel.NORMAL -> LevelColors(cs.surfaceContainerHigh, cs.onSurface, cs.primaryContainer, cs.onPrimaryContainer)
        StatusLevel.WARNING -> LevelColors(s.warning, s.onWarning, s.onWarning, s.warning)
        StatusLevel.CRITICAL -> LevelColors(s.critical, s.onCritical, cs.error, cs.onError)
    }
}
