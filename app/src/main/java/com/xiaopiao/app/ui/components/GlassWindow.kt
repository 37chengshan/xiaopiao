package com.xiaopiao.app.ui.components

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.xiaopiao.app.ui.theme.XpColors

/**
 * Sheets and dialogs live in their own window, so the in-window liquid glass (Kyant backdrop) cannot see the
 * page behind them. Instead the system blurs it: Android 12+ cross-window blur behind this window
 * (docs/DESIGN.md §3 / §6). Call from inside a Dialog / ModalBottomSheet. Returns whether the blur is actually
 * on right now (off below API 31, on devices without it, and while battery saver disables it), so the surface
 * can stay opaque when there is nothing to see through.
 */
@Composable
fun rememberWindowBlurBehind(radius: Dp = 28.dp): Boolean {
    val view = LocalView.current
    val px = with(LocalDensity.current) { radius.roundToPx() }
    var enabled by remember { mutableStateOf(false) }
    DisposableEffect(view, px) {
        val window = dialogWindowOf(view)
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@DisposableEffect onDispose {}
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply { blurBehindRadius = px }
        val wm = view.context.getSystemService(WindowManager::class.java)
        val listener = java.util.function.Consumer<Boolean> { enabled = it }
        wm.addCrossWindowBlurEnabledListener(listener)   // also delivers the current value
        onDispose { wm.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

private fun dialogWindowOf(view: View): android.view.Window? {
    var v: Any? = view
    while (v != null) {
        if (v is DialogWindowProvider) return v.window
        v = (v as? View)?.parent ?: (v as? android.view.ViewParent)?.parent
    }
    return null
}

/** Frosted surface for a sheet / dialog body: translucent fill over the blurred page plus a light top rim. */
fun Modifier.glassSurface(shape: Shape, blurred: Boolean): Modifier =
    if (!blurred) background(XpColors.Card, shape)
    else background(XpColors.Card.copy(alpha = GlassSurfaceAlpha), shape).glassRim(shape)

/** The light catching the top edge of a glass pane; fades out down the sides. */
fun Modifier.glassRim(shape: Shape): Modifier = border(
    0.75.dp,
    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (XpColors.isDark) 0.18f else 0.75f), Color.Transparent)),
    shape,
)

/** How much of the card colour stays over the blur: enough for text contrast, little enough to read as glass. */
const val GlassSurfaceAlpha = 0.66f

/** True inside a sheet / dialog whose page is blurred behind it: fills there should stay translucent too. */
val LocalSheetGlass = androidx.compose.runtime.compositionLocalOf { false }
