package com.xiaopiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/** Selectable capsule: fill when idle, ink when selected (clay stays reserved for the one highlighted datum). */
@Composable
fun SelectCapsule(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) XpColors.Ink else XpColors.Fill, tween(180, easing = XpEase.out), label = "capBg")
    val fg by animateColorAsState(if (selected) XpColors.Canvas else XpColors.Ink, tween(180, easing = XpEase.out), label = "capFg")
    Box(
        modifier.then(if (selected) Modifier.xpRaised(RoundedCornerShape(XpRadius.pill), bg, false, 3.dp) else Modifier.background(bg, RoundedCornerShape(XpRadius.pill)))
            .clip(RoundedCornerShape(XpRadius.pill))
            .pressableClick(onClick = onClick)
            .padding(horizontal = XpSpace.m + 2.dp, vertical = XpSpace.s),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1) }
}
