package com.xiaopiao.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** Calm empty/error placeholder: a 48dp line icon, a serif title, one line of body, an optional secondary action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.xl, vertical = XpSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        Icon(icon, contentDescription = null, tint = XpColors.InkTertiary, modifier = Modifier.size(48.dp))
        Text(
            title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = XpColors.Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = XpSpace.xs),
        )
        Text(body, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            XpButton(actionLabel, onAction, style = XpButtonStyle.Secondary, size = XpButtonSize.Small, modifier = Modifier.padding(top = XpSpace.s))
        }
    }
}

/** Source-compatible overload for older call sites: the emoji is ignored in favour of a generic line icon. */
@Composable
fun EmptyState(
    @Suppress("UNUSED_PARAMETER") emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = EmptyState(Icons.Outlined.Receipt, title, body, modifier, actionLabel, onAction)
