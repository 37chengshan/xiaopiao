package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.NumberTicker
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/** 26sp/600 tabular — the one big number of the ledger (docs/APP-DESIGN.md §3.7). */
private val HeroNumber = TextStyle(
    fontFamily = XpFonts.sans,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.4).sp,
    fontFeatureSettings = "tnum",
)

private val ColumnNumber = TextStyle(
    fontFamily = XpFonts.sans,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/**
 * Summary card: today's spend as the page's single large number, this month's two totals below it, and the
 * 「隐藏金额」 eye. No budget block, no advice (product principles / §3.7 否决).
 */
@Composable
fun LedgerSummaryCard(
    summary: LedgerSummary,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.contentCard))
            .background(XpColors.Card)
            .padding(horizontal = XpSpace.l, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日支出", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            Spacer(Modifier.width(XpSpace.xs))
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(XpRadius.pill)).pressableClick(haptic = false, onClick = onToggleHidden),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (hidden) "显示金额" else "隐藏金额",
                    tint = XpColors.InkSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        // The mask and the number are different widgets, so they cross-fade instead of rolling into garbage.
        AnimatedContent(
            targetState = hidden,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "mask",
        ) { masked ->
            if (masked) {
                Text("¥ $AMOUNT_MASK", style = HeroNumber, color = XpColors.Ink)
            } else {
                NumberTicker(
                    text = summary.todayOutCents.yuan(),
                    style = HeroNumber,
                    color = XpColors.Ink,
                    rollFromZero = false,   // the hero must never flash 0 on first paint
                    staggerMs = 28,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = XpSpace.m).height(0.5.dp).background(XpColors.Hairline))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.l)) {
            SummaryColumn("本月支出", summary.monthOutCents, hidden, Modifier.weight(1f))
            SummaryColumn("本月收入", summary.monthInCents, hidden, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryColumn(label: String, cents: Cents, hidden: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        Text(
            if (hidden) "¥ $AMOUNT_MASK" else "¥ ${cents.yuan()}",
            style = ColumnNumber,
            color = XpColors.Ink,
            maxLines = 1,
        )
    }
}
