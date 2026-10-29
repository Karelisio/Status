package com.karelisio.status.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.karelisio.status.ui.theme.LocalReduceMotion

/**
 * Text whose characters roll like an odometer when they change. Digits that go up slide up,
 * digits that go down slide down; unchanged characters stay still.
 */
@Composable
fun AnimatedCounter(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
) {
    if (LocalReduceMotion.current) {
        Text(text, modifier, style = style, color = color)
        return
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { index, char ->
            // Key from the right so units stay aligned when the number of digits changes.
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val up = targetState > initialState
                        val spec = spring<androidx.compose.ui.unit.IntOffset>(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                        (slideInVertically(spec) { if (up) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically(spec) { if (up) -it else it } + fadeOut()) using SizeTransform(clip = false)
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}
