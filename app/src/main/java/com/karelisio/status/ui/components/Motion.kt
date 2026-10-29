package com.karelisio.status.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.karelisio.status.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared bounds between screens when a shared-transition scope is available. */
@Composable
fun Modifier.sharedBoundsIfAvailable(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedBoundsIfAvailable.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

@Composable
fun Modifier.sharedElementIfAvailable(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedElementIfAvailable.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = animated)
    }
}

/** Staggered "cascade" entrance for list items: rise + fade + slight scale, on a spring. */
@Composable
fun Modifier.cascadeIn(index: Int, stepMs: Long = 45): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    val offset = with(LocalDensity.current) { 40.dp.toPx() }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index.coerceAtMost(12) * stepMs)
            progress.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 260f))
        }
    }
    return graphicsLayer {
        val v = progress.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * offset
        val s = 0.94f + 0.06f * v
        scaleX = s
        scaleY = s
    }
}
