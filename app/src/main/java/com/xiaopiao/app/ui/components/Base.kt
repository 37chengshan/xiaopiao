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

/** Plain surface container: [XpRadius.m] corners, [XpColors.Card] fill, no edge or shadow (separation comes from background). */
@Composable
fun XpCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(XpRadius.m)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(XpColors.Card)
            .padding(XpSpace.l),
        content = content,
    )
}

/** Serif large title with an optional footnote line under it. Use below a [NavScaffold] bar. */
@Composable
fun LargeTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.xs, bottom = XpSpace.s)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = XpColors.Ink)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Footnote-style section label (13sp, ink-2) with an optional trailing slot. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.xl, bottom = XpSpace.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        trailing?.invoke()
    }
}

/** Quiet capsule label: fill background, secondary ink, 11sp. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(XpRadius.pill))
            .background(XpColors.Fill)
            .padding(horizontal = XpSpace.s + 2.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
    }
}
