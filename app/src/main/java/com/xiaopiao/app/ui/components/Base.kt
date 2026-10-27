package com.xiaopiao.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/** Shared building blocks. Signatures are a contract: restyle freely, don't rename parameters. */

@Composable
fun XpCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.m))
            .background(XpColors.Card)
            .padding(XpSpace.l),
        content = content,
    )
}

@Composable
fun LargeTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s)) {
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = XpColors.Ink)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        trailing?.invoke()
    }
}

/** Small pill label, e.g. "演示数据". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(XpColors.AccentSoft)
            .padding(horizontal = XpSpace.s, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = XpColors.Accent)
    }
}
