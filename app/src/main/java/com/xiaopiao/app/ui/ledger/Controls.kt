package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** Full-width coral button with a springy press. Shared by the ledger and settings screens. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "press",
    )
    val color by animateColorAsState(
        when {
            !enabled -> XpColors.InkTertiary
            destructive -> XpColors.Stamp
            else -> XpColors.Accent
        },
        label = "btn",
    )
    Box(
        modifier.scale(scale).clip(RoundedCornerShape(14.dp)).background(color)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = XpSpace.l),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = XpColors.Card, style = MaterialTheme.typography.labelLarge) }
}

/** Low-emphasis text button. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = XpColors.Accent, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = XpSpace.m),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, style = MaterialTheme.typography.labelLarge) }
}

/** iOS-style segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(XpColors.Canvas).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val bg by animateColorAsState(if (i == selected) XpColors.Card else XpColors.Canvas, label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    color = if (i == selected) XpColors.Ink else XpColors.InkSecondary,
                )
            }
        }
    }
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) XpColors.AccentSoft else XpColors.Canvas, label = "chipBg")
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = XpSpace.m, vertical = XpSpace.s),
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) XpColors.Accent else XpColors.InkSecondary,
        )
    }
}
