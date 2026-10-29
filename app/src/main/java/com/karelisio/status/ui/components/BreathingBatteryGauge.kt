package com.karelisio.status.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.karelisio.status.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private const val SWEEP = 300f
private const val START = 120f
private const val PARTICLES = 36

private class Particle(val x: Float, val y: Float, val speed: Float, val size: Float, val wobble: Float)

/**
 * Circular battery gauge. While charging it "breathes" (slow scale + glow) and a stream of
 * particles rises inside the ring at a speed proportional to the real charging power.
 */
@Composable
fun BreathingBatteryGauge(
    level: Int,
    charging: Boolean,
    powerW: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val animatedLevel by animateFloatAsState(
        level / 100f,
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow),
        label = "level",
    )
    val intro = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1_100)) }

    val infinite = rememberInfiniteTransition(label = "breath")
    val breath by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_200), RepeatMode.Reverse), label = "breath",
    )
    val breathing = charging && !reduce
    val breathAmount by animateFloatAsState(if (breathing) 1f else 0f, tween(800), label = "breathAmount")

    // Particle phase is integrated frame by frame so speed changes never cause jumps.
    val particles = remember { List(PARTICLES) { Random(it * 7919).let { r -> Particle(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.8f, 0.5f + r.nextFloat(), r.nextFloat() * 2f * PI.toFloat()) } } }
    var phase by remember { mutableFloatStateOf(0f) }
    val currentPower by rememberUpdatedState(abs(powerW))
    LaunchedEffect(breathing) {
        if (!breathing) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1e9f
                    // 5 W → slow drift, 65 W+ → fast stream.
                    val speed = 0.08f + (currentPower.coerceIn(0f, 80f) / 80f) * 0.9f
                    phase += dt * speed
                }
                last = now
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val scale = 1f + 0.025f * breath * breathAmount
            val radius = (size.minDimension - stroke) / 2f * scale * intro.value.coerceAtLeast(0.6f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)

            // Glow halo
            if (breathAmount > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = 0.28f * breathAmount * (0.5f + breath * 0.5f)), Color.Transparent),
                        center = center,
                        radius = radius * 1.25f,
                    ),
                    radius = radius * 1.25f,
                    center = center,
                )
            }

            drawArc(trackColor, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

            val sweep = SWEEP * animatedLevel * intro.value
            rotate(START, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to secondaryColor, (SWEEP / 360f) to color, 1f to secondaryColor,
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = sweep.coerceAtLeast(0.1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            if (breathAmount > 0f) {
                val inner = radius - stroke * 1.2f
                val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, inner)) }
                clipPath(clip) {
                    particles.forEach { p ->
                        val y = 1f - ((p.y + phase * p.speed) % 1f) // 1 = bottom, 0 = top
                        val px = center.x + (p.x - 0.5f) * inner * 1.8f + sin(phase * 6f + p.wobble) * inner * 0.04f
                        val py = center.y - inner + y * inner * 2f
                        val fade = sin(y * PI.toFloat()).coerceAtLeast(0f)
                        drawCircle(
                            color = color.copy(alpha = 0.55f * fade * breathAmount),
                            radius = stroke * 0.16f * p.size,
                            center = Offset(px, py),
                        )
                    }
                }
            }
        }
        content()
    }
}
