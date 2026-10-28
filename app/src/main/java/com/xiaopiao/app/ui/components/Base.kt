package com.xiaopiao.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/** Shared building blocks. Signatures are a contract: restyle freely, don't rename parameters. */

/** White rounded card with a hairline edge instead of a drop shadow. */
@Composable
fun XpCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(XpRadius.m)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(XpColors.Card)
            .border(BorderStroke(0.5.dp, XpColors.Hairline), shape)
            .padding(XpSpace.l),
        content = content,
    )
}

/** Apple-style large page title with an optional small caption above it. */
@Composable
fun LargeTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.m, bottom = XpSpace.xs)) {
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = XpColors.Ink)
    }
}

/** Section title row with an optional trailing slot (e.g. a "查看全部" link). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.l, bottom = XpSpace.xs),
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
            .clip(RoundedCornerShape(XpRadius.pill))
            .background(XpColors.AccentSoft)
            .padding(horizontal = XpSpace.s + 2.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = XpColors.Accent,
        )
    }
}
