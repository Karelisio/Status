package com.karelisio.status.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.karelisio.status.ui.theme.LocalReduceMotion
import kotlin.math.max

/** One heartbeat (P, QRS, T) on a unit segment; y in -0.3..1. */
private val BEAT = floatArrayOf(
    0f, 0f, 0.28f, 0f, 0.34f, 0.10f, 0.40f, 0f, 0.47f, 0f, 0.51f, -0.14f,
    0.555f, 1f, 0.60f, -0.30f, 0.64f, 0f, 0.74f, 0.18f, 0.84f, 0f, 1f, 0f,
)

private fun beatAt(u: Float): Float {
    var i = 0
    while (i < BEAT.size - 2) {
        val x0 = BEAT[i]; val y0 = BEAT[i + 1]; val x1 = BEAT[i + 2]; val y1 = BEAT[i + 3]
        if (u <= x1) return y0 + (y1 - y0) * ((u - x0) / (x1 - x0).coerceAtLeast(1e-4f))
        i += 2
    }
    return 0f
}

/** Square-root scale relative to the window peak: idle stays flat, bursts stand out. */
private fun amplitude(rate: Long, peak: Long): Float {
    if (rate <= 0) return 0.04f
    return (0.06f + 0.94f * kotlin.math.sqrt(rate.toDouble() / peak).toFloat()).coerceIn(0.04f, 1f)
}

/**
 * Real-time throughput drawn as an electrocardiogram: one beat per second, amplitude on a log
 * scale. Download beats upward, upload is mirrored below the baseline. The trace scrolls
 * continuously and a glowing "pen" marks the present.
 */
@Composable
fun EcgTrace(
    rx: List<Long>,
    tx: List<Long>,
    windowSize: Int,
    rxColor: Color,
    txColor: Color,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    var sampleAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(rx.size, rx.lastOrNull(), tx.lastOrNull()) {
        sampleAt = withFrameMillis { it }
    }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        while (true) now = withFrameMillis { it }
    }
    val rxShown = rx.takeLast(windowSize + 1)
    val txShown = tx.takeLast(windowSize + 1)
    val peak = max(256_000L, max(rxShown.maxOrNull() ?: 0, txShown.maxOrNull() ?: 0))

    Canvas(
        modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val frac = if (reduce) 1f else ((now - sampleAt) / 1000f).coerceIn(0f, 1f)
        val seg = size.width / (windowSize - 1).coerceAtLeast(1)
        val base = size.height * 0.62f
        val up = size.height * 0.55f
        val down = size.height * 0.30f

        // Baseline grid
        drawLine(rxColor.copy(alpha = 0.12f), Offset(0f, base), Offset(size.width, base), strokeWidth = 1.5f)

        drawBeats(txShown, frac, seg, base, -down, peak, txColor, 0.6f)
        drawBeats(rxShown, frac, seg, base, up, peak, rxColor, 1f)

        // Fade out toward the past (left)
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Transparent, 0.35f to Color.Black, 1f to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }
}

private fun DrawScope.drawBeats(
    samples: List<Long>,
    frac: Float,
    seg: Float,
    base: Float,
    height: Float,
    peak: Long,
    color: Color,
    weight: Float,
) {
    if (samples.isEmpty()) return
    val path = Path()
    val n = samples.size
    var started = false
    var headY = base
    for (i in 0 until n) {
        val start = size.width - (n - 1 - i + frac) * seg
        if (start + seg < 0f) continue
        val a = amplitude(samples[i], peak)
        var k = 0
        while (k < BEAT.size) {
            val x = start + BEAT[k] * seg
            val y = base - BEAT[k + 1] * a * height
            if (x <= size.width) {
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            k += 2
        }
        if (i == n - 1) {
            val u = ((size.width - start) / seg).coerceIn(0f, 1f)
            headY = base - beatAt(u) * a * height
        }
    }
    val w = 2.5f.coerceAtLeast(size.height * 0.022f) * weight
    drawPath(path, color.copy(alpha = 0.14f), style = Stroke(w * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val head = Offset(size.width - w * 2, headY)
    drawCircle(color.copy(alpha = 0.25f), radius = w * 4f, center = head)
    drawCircle(color, radius = w * 1.6f, center = head)
}
