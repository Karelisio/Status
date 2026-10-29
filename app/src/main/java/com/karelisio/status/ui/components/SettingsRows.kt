package com.karelisio.status.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Rounded group container: items inside get small gaps and the group gets large outer corners. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSecondaryContainer, background: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(Modifier.size(40.dp).background(background, MaterialShapes.Cookie7Sided.toShape()), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val toggle = { v: Boolean ->
        haptics.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onChange(v)
    }
    Row(
        modifier.fillMaxWidth().clickable(enabled = enabled) { toggle(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIcon(icon)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = toggle,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else null,
        )
    }
}

@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIcon(icon)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Integer slider that only commits on release, with a tick haptic on each step. */
@Composable
fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    enabled: Boolean = true,
    format: (Int) -> String = { it.toString() },
) {
    val haptics = LocalHapticFeedback.current
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(format(current.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = current,
            onValueChange = {
                val snapped = ((it / step).roundToInt() * step).toFloat()
                if (snapped != current) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                current = snapped
            },
            onValueChangeFinished = { onChange(current.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}
