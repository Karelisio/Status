package com.karelisio.status.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.status.ui.theme.LocalReduceMotion
import com.karelisio.status.ui.theme.NumberStyle

/** Corner radii per state: calm and round → asymmetric "leaning" → tight and tense. */
private fun cornersFor(level: StatusLevel) = when (level) {
    StatusLevel.NORMAL -> floatArrayOf(28f, 28f, 28f, 28f)
    StatusLevel.WARNING -> floatArrayOf(32f, 10f, 32f, 10f)
    StatusLevel.CRITICAL -> floatArrayOf(12f, 12f, 12f, 12f)
}

/**
 * A metric card whose container shape, color and badge polygon morph with [level].
 * The badge continuously "breathes" when critical to draw the eye, unless motion is reduced.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    level: StatusLevel,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    onClick: (() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val reduce = LocalReduceMotion.current
    val colors = level.colors()
    val springSpec = spring<androidx.compose.ui.unit.Dp>(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
    val c = cornersFor(level)
    val tl by animateDpAsState(c[0].dp, springSpec, label = "tl")
    val tr by animateDpAsState(c[1].dp, springSpec, label = "tr")
    val br by animateDpAsState(c[2].dp, springSpec, label = "br")
    val bl by animateDpAsState(c[3].dp, springSpec, label = "bl")
    val container by animateColorAsState(colors.container, tween(450), label = "container")
    val content by animateColorAsState(colors.content, tween(450), label = "content")
    val accent by animateColorAsState(colors.accent, tween(450), label = "accent")
    val onAccent by animateColorAsState(colors.onAccent, tween(450), label = "onAccent")

    val infinite = rememberInfiniteTransition(label = "badge")
    val spin by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(if (level == StatusLevel.CRITICAL) 6_000 else 24_000, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by infinite.animateFloat(
        1f, if (level == StatusLevel.CRITICAL) 1.12f else 1f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
    )

    val shape = RoundedCornerShape(tl, tr, br, bl)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                if (!reduce) {
                                    rotationZ = spin
                                    scaleX = pulse
                                    scaleY = pulse
                                }
                            }
                            .background(accent, animatedPolygonShape(level.polygon)),
                    )
                    Icon(icon, null, tint = onAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            AnimatedCounter(value, style = MaterialTheme.typography.headlineSmall.merge(NumberStyle))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                LinearWavyProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = content.copy(alpha = 0.12f),
                )
            }
            if (extra != null) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = extra)
            }
        }
    }
}
