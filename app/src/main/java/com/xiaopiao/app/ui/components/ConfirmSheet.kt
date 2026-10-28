package com.xiaopiao.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch

/**
 * Apple-like bottom-sheet confirmation (never a system dialog). Slides up with a spring, drag to dismiss.
 * Prefer [UndoToastHost] for reversible actions; use this for truly irreversible ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    cancelLabel: String = "再想想",
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    fun close(then: () -> Unit) = scope.launch { sheetState.hide() }.invokeOnCompletion { then() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = XpRadius.l, topEnd = XpRadius.l),
        containerColor = XpColors.Card,
        scrimColor = XpColors.Scrim,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = XpSpace.l).padding(bottom = XpSpace.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (emoji != null) Text(emoji, fontSize = 44.sp)
            Spacer(Modifier.height(XpSpace.s))
            Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(XpSpace.xs))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(XpSpace.xl))
            XpButton(confirmLabel, { haptics.confirm(); close(onConfirm) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(XpSpace.s))
            XpButton(cancelLabel, { close(onDismiss) }, Modifier.fillMaxWidth(), style = XpButtonStyle.Plain)
        }
    }
}
