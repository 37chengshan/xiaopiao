package com.xiaopiao.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch

/**
 * Bottom-sheet confirmation (never a system dialog). Prefer [UndoToastHost] for reversible actions;
 * use this for truly irreversible ones. [emoji] is accepted for source compatibility and not shown.
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
    @Suppress("UNUSED_PARAMETER") emoji: String? = null,
    cancelLabel: String = "再想想",
    destructive: Boolean = false,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    fun close(then: () -> Unit) = scope.launch { sheetState.hide() }.invokeOnCompletion { then() }

    XpSheet(onDismiss = onDismiss, modifier = modifier, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.l).padding(bottom = XpSpace.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(XpSpace.xs))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(XpSpace.xl))
            XpButton(
                confirmLabel, { haptics.confirm(); close(onConfirm) }, Modifier.fillMaxWidth(),
                style = if (destructive) XpButtonStyle.Destructive else XpButtonStyle.Primary,
            )
            Spacer(Modifier.height(XpSpace.s))
            XpButton(cancelLabel, { close(onDismiss) }, Modifier.fillMaxWidth(), style = XpButtonStyle.Plain)
        }
    }
}
