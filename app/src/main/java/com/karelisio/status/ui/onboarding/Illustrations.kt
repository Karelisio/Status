package com.karelisio.status.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.karelisio.status.ui.components.animatedPolygonShape
import com.karelisio.status.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

private val blobCycle = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny,
    MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Cookie12Sided,
)

/** Welcome hero: a blob that keeps morphing between expressive shapes, with the logo tracing itself. */
@Composable
fun WelcomeIllustration(modifier: Modifier = Modifier, size: Dp = 260.dp) {
    val reduce = LocalReduceMotion.current
    val cs = MaterialTheme.colorScheme
    var index by remember { mutableIntStateOf(0) }
    val morphProgress = remember { Animatable(0f) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        while (true) {
            delay(1_400)
            morphProgress.snapTo(0f)
            morphProgress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessVeryLow))
            index = (index + 1) % blobCycle.size
            morphProgress.snapTo(0f)
        }
    }
    val morph = remember(index) { Morph(blobCycle[index], blobCycle[(index + 1) % blobCycle.size]) }
    val infinite = rememberInfiniteTransition(label = "welcome")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "spin")
    val trace by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = LinearEasing)), label = "trace")

    Canvas(modifier.size(size)) {
        val path: Path = morph.toPath(morphProgress.value, Path())
        path.transform(Matrix().apply { scale(this@Canvas.size.width, this@Canvas.size.height) })
        rotate(if (reduce) 0f else spin) {
            drawPath(path, Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer), Offset.Zero, Offset(this.size.width, this.size.height)))
        }
        // Logo: open gauge ring + pulse (same geometry as the launcher icon, viewport 108).
        val s = this.size.minDimension / 108f
        translate(this.size.width / 2 - 54 * s, this.size.height / 2 - 54 * s) {
            val stroke = 6f * s
            val ringStart = 135f
            drawArc(cs.primary, ringStart, 270f, false, Offset(32 * s, 32 * s), Size(44 * s, 44 * s), style = Stroke(stroke, cap = StrokeCap.Round))
            val pulse = Path().apply {
                moveTo(26 * s, 56 * s); lineTo(40 * s, 56 * s); lineTo(44 * s, 49 * s); lineTo(49.5f * s, 65 * s)
                lineTo(56 * s, 39 * s); lineTo(61.5f * s, 59 * s); lineTo(65 * s, 56 * s); lineTo(82 * s, 56 * s)
            }
            val measure = PathMeasure().apply { setPath(pulse, false) }
            val seg = Path()
            val len = measure.length
            val p = if (reduce) 1f else trace
            measure.getSegment(0f, len * (p * 1.3f).coerceAtMost(1f), seg, true)
            drawPath(seg, cs.tertiary, style = Stroke(4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Floating feature bubbles that bob gently, each in its own expressive shape. */
@Composable
fun FeaturesIllustration(icons: List<ImageVector>, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val cs = MaterialTheme.colorScheme
    val infinite = rememberInfiniteTransition(label = "features")
    val shapes = listOf(MaterialShapes.Cookie6Sided, MaterialShapes.Pill, MaterialShapes.Clover4Leaf, MaterialShapes.Gem, MaterialShapes.Sunny, MaterialShapes.Arch)
    val colors = listOf(cs.primaryContainer, cs.secondaryContainer, cs.tertiaryContainer)
    val onColors = listOf(cs.onPrimaryContainer, cs.onSecondaryContainer, cs.onTertiaryContainer)
    val positions = listOf(Offset(-80f, -70f), Offset(70f, -90f), Offset(-30f, 40f), Offset(90f, 50f), Offset(-95f, 110f), Offset(30f, 140f))
    Box(modifier.size(280.dp), contentAlignment = Alignment.Center) {
        icons.take(positions.size).forEachIndexed { i, icon ->
            val bob by infinite.animateFloat(-8f, 8f, infiniteRepeatable(tween(1_600 + i * 230), RepeatMode.Reverse), label = "bob$i")
            val enter = remember { Animatable(if (reduce) 1f else 0f) }
            LaunchedEffect(Unit) { delay(i * 90L); enter.animateTo(1f, spring(0.55f, Spring.StiffnessLow)) }
            Box(
                Modifier
                    .offset(positions[i].x.dp, positions[i].y.dp)
                    .graphicsLayer {
                        translationY = if (reduce) 0f else bob * density
                        scaleX = enter.value; scaleY = enter.value
                    }
                    .size(if (i % 2 == 0) 84.dp else 68.dp)
                    .background(colors[i % 3], shapes[i].toShape()),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = onColors[i % 3], modifier = Modifier.size(32.dp)) }
        }
    }
}

/** Permission hero: rotating morphing shape that settles into a circle with a check once granted. */
@Composable
fun PermissionIllustration(icon: ImageVector, granted: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val cs = MaterialTheme.colorScheme
    val infinite = rememberInfiniteTransition(label = "perm")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "spin")
    val bounce by infinite.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "bounce")
    val container by animateColorAsState(if (granted) cs.primary else cs.secondaryContainer, tween(500), label = "c")
    val content by animateColorAsState(if (granted) cs.onPrimary else cs.onSecondaryContainer, tween(500), label = "o")
    val check = remember { Animatable(if (granted) 1f else 0f) }
    LaunchedEffect(granted) {
        if (granted) check.animateTo(1f, spring(0.45f, Spring.StiffnessMediumLow)) else check.snapTo(0f)
    }
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { if (!reduce && !granted) rotationZ = spin }
                .background(container, animatedPolygonShape(if (granted) MaterialShapes.Circle else MaterialShapes.Cookie9Sided)),
        )
        Icon(
            icon, null, tint = content,
            modifier = Modifier.size(84.dp).graphicsLayer {
                val s = if (reduce || granted) 1f else bounce
                val hide = 1f - check.value
                scaleX = s * hide; scaleY = s * hide; alpha = hide
            },
        )
        Icon(
            androidx.compose.material.icons.Icons.Rounded.Check, null, tint = content,
            modifier = Modifier.size(96.dp).graphicsLayer {
                scaleX = check.value; scaleY = check.value; alpha = check.value.coerceIn(0f, 1f)
                rotationZ = (1f - check.value) * -45f
            },
        )
    }
}
