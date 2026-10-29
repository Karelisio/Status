package com.karelisio.status.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalStatusColors = staticCompositionLocalOf { statusColorsFor(TealLight, false) }

/** True when the user disabled animations system-wide ("Remove animations" / animator scale 0). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun StatusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> TealDark
        else -> TealLight
    }
    val statusColors = remember(scheme, darkTheme) { statusColorsFor(scheme, darkTheme) }
    val reduceMotion = !ValueAnimator.areAnimatorsEnabled()

    CompositionLocalProvider(
        LocalStatusColors provides statusColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = StatusTypography,
            shapes = StatusShapes,
            content = content,
        )
    }
}

val MaterialTheme.statusColors: StatusColors
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current
