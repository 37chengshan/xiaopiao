package com.xiaopiao.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.R
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/** Compact top of Today (no large title): caption row, one hero number, one assistant line. */
@Composable
fun TodayHeader(caption: String, demo: Boolean, spendLabel: String, spendCents: Cents, line: String, reduced: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = XpSpace.l).padding(top = XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            Text(caption, style = MaterialTheme.typography.labelLarge, color = XpColors.InkSecondary)
            if (demo) Pill(stringResource(R.string.today_demo_badge))
        }
        Text(spendLabel, style = MaterialTheme.typography.bodySmall, color = XpColors.InkTertiary)
        Text(heroMoney(spendCents), color = XpColors.Ink)
        TypedLine(line, reduced, Modifier.padding(top = XpSpace.xs))
    }
}

/** Integer part big and bold, decimals at 60% (docs: one hero number per screen). */
private fun heroMoney(cents: Cents) = buildAnnotatedString {
    val whole = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium)) { append("¥") }
    withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) { append(whole) }
    withStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)) { append(".%02d".format(cents % 100)) }
}

/** The assistant's one line; types in (instantly when motion is reduced) and reserves its final height. */
@Composable
private fun TypedLine(text: String, reduced: Boolean, modifier: Modifier = Modifier) {
    var shown by remember(text) { mutableIntStateOf(if (reduced) text.length else 0) }
    LaunchedEffect(text) { for (i in shown..text.length) { shown = i; delay(26) } }
    Box(modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink.copy(alpha = 0f))
        Text(text.take(shown), style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
    }
}

/** Pinned above the bottom bar: the one-tap primary action never scrolls away (docs/APP-DESIGN §7.2). */
@Composable
fun PrintDock(isToday: Boolean, demo: Boolean, canOfferDemo: Boolean, onPrint: () -> Unit, onDemo: () -> Unit, onExitDemo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(XpColors.Canvas).padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalAlignment = Alignment.CenterVertically,
    ) {
        XpButton(stringResource(if (isToday) R.string.today_print else R.string.today_print_day), onPrint, Modifier.weight(1f))
        when {
            demo -> XpButton(stringResource(R.string.today_demo_exit), onExitDemo, style = XpButtonStyle.Secondary)
            canOfferDemo -> XpButton(stringResource(R.string.today_demo_try), onDemo, style = XpButtonStyle.Secondary)
        }
    }
}
