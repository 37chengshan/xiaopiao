package com.xiaopiao.app.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius

/** 36x5 grabber, ink-3 at 40%. Sheets are draggable: the grabber is what tells people that. */
@Composable
fun Grabber(modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(XpColors.InkTertiary.copy(alpha = 0.4f)),
        )
    }
}

/**
 * Surface-coloured modal sheet with a grabber, 32dp top corners (docs/APP-DESIGN.md §7.5) and the theme
 * scrim. Pass your own [sheetState] when you need to `hide()` it before dismissing, so close plays the
 * same path as open.
 *
 * Motion note: the rise/settle is `Material3`'s own spatial spring (`ModalBottomSheet`); in M3 1.4 the
 * show/hide specs live in an internal `MotionScheme` bound to `DefaultSpatial`, so they cannot be injected
 * per-sheet from app code (the 300ms/200ms pair in the spec is not addressable without forking the sheet).
 * What we do control is the content: it fades and rises 6dp on the way in, so the sheet reads as landing
 * instead of sliding out from under the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XpSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: androidx.compose.ui.graphics.Color = XpColors.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Frosted glass (tt: 抽屉弹窗都要液态玻璃): the page behind is blurred by the system, the sheet turns
    // translucent with a light rim. The blur is only known from inside the sheet's window, hence the state.
    var blurred by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(topStart = XpRadius.sheet, topEnd = XpRadius.sheet)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = if (blurred) modifier.glassRim(shape) else modifier,
        shape = shape,
        containerColor = if (blurred) containerColor.copy(alpha = GlassSurfaceAlpha) else containerColor,
        // The blur already separates the sheet from the page, so the scrim can be lighter.
        scrimColor = if (blurred) XpColors.Scrim.copy(alpha = XpColors.Scrim.alpha * 0.55f) else XpColors.Scrim,
        dragHandle = { Grabber() },
    ) {
        val on = rememberWindowBlurBehind()
        SideEffect { blurred = on }
        androidx.compose.runtime.CompositionLocalProvider(LocalSheetGlass provides on) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .xpRevealLayer(index = 0, riseDp = 6.dp, durationMs = 200),
            content = content,
        )
        }
    }
}
