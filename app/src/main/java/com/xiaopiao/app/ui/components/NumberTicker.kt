package com.xiaopiao.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.theme.XpColors
import kotlinx.coroutines.delay

/** Pure helper: same text with every digit replaced by "0" (the rolled-from state). */
fun zeroedDigits(text: String): String = text.map { if (it.isDigit()) '0' else it }.joinToString("")

/**
 * Odometer-style number: each digit rolls (spring) to its new value, staggered from the right.
 * Passing a new [text] rolls the changed digits. [rollFromZero] makes the first appearance roll up from zeros.
 */
@Composable
fun NumberTicker(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold),
    color: Color = XpColors.Ink,
    rollFromZero: Boolean = true,
    staggerMs: Long = 70,
) {
    Row(modifier) {
        text.forEachIndexed { index, ch ->
            val fromRight = text.length - 1 - index
            RollingChar(ch, if (rollFromZero) '0' else ch, fromRight * staggerMs, style, color)
        }
    }
}

@Composable
private fun RollingChar(target: Char, initial: Char, delayMs: Long, style: TextStyle, color: Color) {
    var shown by remember { mutableStateOf(if (target.isDigit()) initial else target) }
    LaunchedEffect(target) {
        if (target.isDigit() && shown != target) delay(delayMs)
        shown = target
    }
    AnimatedContent(
        targetState = shown,
        transitionSpec = {
            val up = targetState.isDigit() && initialState.isDigit() && targetState > initialState
            val dir = if (up) 1 else -1
            (slideInVertically(spring(0.75f, 420f)) { it * dir } + fadeIn(spring(stiffness = 600f))) togetherWith
                (slideOutVertically(spring(0.75f, 420f)) { -it * dir } + fadeOut(spring(stiffness = 600f)))
        },
        label = "digit",
    ) { c -> Text(c.toString(), style = style, color = color) }
}
