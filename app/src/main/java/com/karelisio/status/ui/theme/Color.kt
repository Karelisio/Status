package com.karelisio.status.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Teal fallback palette (devices without dynamic color). Tones generated from seed #006A60. */
val TealLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF006A60), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF74F8E5), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2), onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF456179), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCE5FF), onTertiaryContainer = Color(0xFF001E31),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FBF8), onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4FBF8), onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    inverseSurface = Color(0xFF2B3230), inverseOnSurface = Color(0xFFECF2EF),
    inversePrimary = Color(0xFF53DBC9),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEEF5F2),
    surfaceContainer = Color(0xFFE9EFEC), surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
)

val TealDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF53DBC9), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048), onPrimaryContainer = Color(0xFF74F8E5),
    secondary = Color(0xFFB1CCC6), onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFADCAE5), onTertiary = Color(0xFF153349),
    tertiaryContainer = Color(0xFF2D4961), onTertiaryContainer = Color(0xFFCCE5FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1513), onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513), onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4947),
    inverseSurface = Color(0xFFDDE4E1), inverseOnSurface = Color(0xFF2B3230),
    inversePrimary = Color(0xFF006A60),
    surfaceContainerLowest = Color(0xFF090F0E), surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120), surfaceContainerHigh = Color(0xFF242B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
)

/** Status colors that are not part of the M3 scheme, harmonized toward the current primary. */
data class StatusColors(
    val good: Color,
    val onGood: Color,
    val warning: Color,
    val onWarning: Color,
    val critical: Color,
    val onCritical: Color,
)

fun statusColorsFor(scheme: ColorScheme, dark: Boolean): StatusColors {
    val amber = if (dark) Color(0xFFFFC56B) else Color(0xFF8B5000)
    val amberContainer = if (dark) Color(0xFF6A3C00) else Color(0xFFFFDDB8)
    return StatusColors(
        good = scheme.primaryContainer,
        onGood = scheme.onPrimaryContainer,
        warning = lerp(amberContainer, scheme.primaryContainer, 0.15f),
        onWarning = lerp(if (dark) Color(0xFFFFDDB8) else Color(0xFF2C1600), scheme.onPrimaryContainer, 0.1f),
        critical = scheme.errorContainer,
        onCritical = scheme.onErrorContainer,
    ).let { if (dark) it else it.copy(onWarning = lerp(amber, Color.Black, 0.55f)) }
}
