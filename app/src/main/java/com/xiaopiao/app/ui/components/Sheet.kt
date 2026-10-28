package com.xiaopiao.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
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
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius

/** 36x5 grabber, ink-3 at 40%. */
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
 * Surface-coloured modal sheet with a grabber, 20dp top corners and the theme scrim. Pass your own
 * [sheetState] when you need to `hide()` it before dismissing (so close plays the same path as open).
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = XpRadius.xl, topEnd = XpRadius.xl),
        containerColor = containerColor,
        scrimColor = XpColors.Scrim,
        dragHandle = { Grabber() },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), content = content)
    }
}
