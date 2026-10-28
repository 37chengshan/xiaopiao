package com.xiaopiao.app.ui.record

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * The single voice surface (docs/APP-DESIGN.md §4.2). Any entry point — the centre key, the record
 * ball — asks this host to listen; the bar lives above the tab bar so it survives tab switches.
 */
class VoiceHostState(val controller: VoiceController) {
    var open by mutableStateOf(false)
        private set
    var requested by mutableStateOf(false)
        private set
    var cancelledBySwipe by mutableStateOf(false)
        private set

    fun request() { requested = true }

    fun permissionResult(granted: Boolean) {
        requested = false
        if (granted) {
            open = true
            controller.start()
        }
    }

    fun stop() { controller.stop() }

    fun cancel() {
        controller.cancel()
        open = false
        cancelledBySwipe = false
    }

    fun dismiss() { open = false }
}

@Composable
fun rememberVoiceHost(): VoiceHostState {
    val context = LocalContext.current
    val controller = rememberVoiceController()
    val host = remember(controller) { VoiceHostState(controller) }
    DisposableEffect(host) { onDispose { controller.release() } }
    return host
}

/**
 * The listening bar. Expands with a small spring, shows the live transcript and the mic level,
 * and cancels when dragged up (thumb reaches for it by reflex).
 */
@Composable
fun VoiceOverlay(host: VoiceHostState, onTranscript: (String) -> Unit, modifier: Modifier = Modifier) {
    val controller = host.controller
    val haptics = rememberXpHaptics()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        host.permissionResult(granted)
    }

    // Ask for the mic the first time it is actually needed, then start listening.
    LaunchedEffect(host.requested) {
        if (!host.requested) return@LaunchedEffect
        if (controller.hasPermission) host.permissionResult(true) else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(controller.transcript) {
        controller.consume()?.let { text ->
            host.dismiss()
            onTranscript(text)
        }
    }
    LaunchedEffect(controller.error) {
        val err = controller.error ?: return@LaunchedEffect
        kotlinx.coroutines.delay(1900)
        if (!controller.listening && controller.error == err) host.cancel()
    }

    AnimatedVisibility(
        visible = host.open,
        modifier = modifier,
        enter = expandVertically(spring(0.85f, 340f), expandFrom = Alignment.Bottom) + fadeIn(tween(120)),
        exit = shrinkVertically(tween(140)) + fadeOut(tween(110)),
    ) {
        var dragUp by remember { mutableStateOf(0f) }
        val lift = (dragUp / 160f).coerceIn(0f, 1f)
        Row(
            Modifier.fillMaxWidth()
                .graphicsLayer { alpha = 1f - lift * 0.7f; translationY = -lift * 12.dp.toPx() }
                .padding(horizontal = XpSpace.l)
                .height(56.dp)
                .clip(RoundedCornerShape(XpRadius.pill))
                .background(
                    when {
                        controller.error != null -> XpColors.Negative
                        lift > 0.5f -> XpColors.Ink
                        else -> XpColors.Primary
                    },
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragUp = 0f },
                        onDrag = { change, delta ->
                            change.consume()
                            dragUp = (dragUp - delta.y).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (dragUp > 110f) { haptics.reject(); host.cancel() } else dragUp = 0f
                        },
                        onDragCancel = { dragUp = 0f },
                    )
                }
                .padding(horizontal = XpSpace.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = XpColors.OnPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(XpSpace.s))
            VoiceLevel(active = controller.listening || controller.partial.isNotEmpty(), level = controller.rms)
            Spacer(Modifier.width(XpSpace.m))
            Text(
                controller.error ?: controller.partial.ifBlank { if (controller.listening) "听你说…" else "松手记账" },
                style = MaterialTheme.typography.bodyLarge,
                color = XpColors.OnPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (lift > 0.5f) "松手取消" else "上滑取消",
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.OnPrimary.copy(alpha = 0.7f),
            )
        }
    }
}

/** Bars driven by the live mic level: the user must be able to see that the phone hears them. */
@Composable
private fun VoiceLevel(active: Boolean, level: Float) {
    val factors = remember { floatArrayOf(0.55f, 1f, 0.75f, 0.9f, 0.6f) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        factors.forEach { f ->
            val h = if (active) (8f + 20f * f * level.coerceIn(0.08f, 1f)) else 8f
            Box(
                Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp))
                    .background(XpColors.OnPrimary.copy(alpha = if (active) 0.95f else 0.45f)),
            )
        }
    }
}
