package com.xiaopiao.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/**
 * Level-1 feedback (docs/APP-DESIGN.md §7.7): one ink capsule at the bottom, 13sp/500 white text, 2.4s,
 * the newest replaces the old one. Never stack two, never put an error in here.
 */
@Stable
class XpToastState internal constructor() {
    internal var message by mutableStateOf<ToastMessage?>(null)
        private set

    /** Shows [text] and restarts the 2.4s timer (a repeated message still counts as new feedback). */
    fun show(text: String) {
        message = ToastMessage(text, System.nanoTime())
    }

    internal fun clear() {
        message = null
    }
}

internal data class ToastMessage(val text: String, val token: Long)

@Composable
fun rememberXpToast(): XpToastState = remember { XpToastState() }

/**
 * Draws the current toast. Place it once per screen (or sheet) at the bottom of the content that owns the
 * action; it positions itself bottom-centre with 16dp insets.
 */
@Composable
fun XpToastHost(state: XpToastState, modifier: Modifier = Modifier) {
    val current = state.message
    LaunchedEffect(current?.token) {
        val token = current?.token ?: return@LaunchedEffect
        delay(ToastDurationMs)
        if (state.message?.token == token) state.clear()
    }
    // Keep the final string around so the exit animation never flashes an empty capsule.
    var lastText by remember { mutableStateOf("") }
    SideEffect { current?.let { lastText = it.text } }

    Box(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.xl, vertical = XpSpace.l),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(tween(200, easing = XpEase.out)) { it / 2 } + fadeIn(tween(200, easing = XpEase.out)),
            exit = slideOutVertically(tween(160, easing = XpEase.out)) { it / 2 } + fadeOut(tween(160)),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(XpRadius.pill))
                    .background(XpColors.ToastBackground)
                    .padding(horizontal = XpSpace.l, vertical = 10.dp),
            ) {
                Text(
                    if (current != null) current.text else lastText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = XpColors.ToastText,
                )
            }
        }
    }
}

private const val ToastDurationMs = 2400L
