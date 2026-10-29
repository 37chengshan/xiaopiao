package com.xiaopiao.app.ui.ledger

import androidx.compose.material.icons.outlined.CalendarMonth
import com.xiaopiao.app.domain.BudgetSummary
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.yuanGrouped
import com.xiaopiao.app.ui.components.NumberTicker
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * The warm card palette (docs/WEB-SPEC §0.2 「highlight」, reference `041428` 汇总卡 `#FFEDBD`).
 *
 * TODO(nav-v7 token): the navigation lane is adding `XpColors.Highlight` / `HighlightSoft`; when it
 * lands, delete this object and read the tokens. Values are the reference's cream, not new invention.
 */
internal object LedgerHighlight {
    val lightCard = Color(0xFFFFEDBD)
    val darkCard = Color(0xFF3A3110)
    val lightStrip = Color(0xFFFFFBF0)
    val darkStrip = Color(0xFF4A3F16)
    val lightDecor = Color(0xFFFFF3D3)
    val darkDecor = Color(0xFF463B12)

    val card: Color get() = if (XpColors.isDark) darkCard else lightCard
    val strip: Color get() = if (XpColors.isDark) darkStrip else lightStrip
    val decor: Color get() = if (XpColors.isDark) darkDecor else lightDecor
}

/** 30sp/600 tabular — the one big number of the ledger (reference `041428`, 30sp). */
private val HeroNumber = TextStyle(
    fontFamily = XpFonts.sans,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.6).sp,
    fontFeatureSettings = "tnum",
)

/** 22sp/600 tabular — the month's two columns. */
private val ColumnNumber = TextStyle(
    fontFamily = XpFonts.sans,
    fontSize = 22.sp,
    lineHeight = 27.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/**
 * 总览卡 (reference `041428`): 「今日支出（元）」+ 眼睛 + 30sp number, then 本月支出 / 本月收入 as two
 * 22sp columns, then a full-bleed bottom strip — budget entry when no budget is set, remaining quota
 * when one is. The card is the page's only large colour field; the strip is a lighter tint of it.
 */
@Composable
fun LedgerSummaryCard(
    summary: LedgerSummary,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    budget: BudgetSummary?,
    onOpenBudget: () -> Unit,
    modifier: Modifier = Modifier,
    /** 按天查看 sits in the card's top-right corner (tt: inside the card, not a row of its own). Null hides it. */
    onOpenCalendar: (() -> Unit)? = null,
) {
    Box(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.contentCard))
            .background(LedgerHighlight.card),
    ) {
        // The reference's soft decorative circle: one arc, clipped by the card, no gradient.
        // matchParentSize keeps it out of the card's measured height.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.size(210.dp).offset(x = 150.dp, y = (-96).dp)
                    .clip(CircleShape).background(LedgerHighlight.decor),
            )
        }
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = XpSpace.page, end = XpSpace.page, top = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日支出（元）",
                        style = MaterialTheme.typography.bodySmall,
                        color = XpColors.InkSecondary,
                    )
                    Spacer(Modifier.width(XpSpace.xs))
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(XpRadius.pill))
                            .pressableClick(haptic = false, onClick = onToggleHidden),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (hidden) "显示金额" else "隐藏金额",
                            tint = XpColors.InkSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    if (onOpenCalendar != null) {
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.height(28.dp).clip(RoundedCornerShape(XpRadius.pill)).background(Color.White.copy(alpha = 0.55f))
                                .pressableClick(haptic = false, scaleDown = 0.96f, onClick = onOpenCalendar)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = XpColors.InkSecondary, modifier = Modifier.size(15.dp))
                            Text(
                                "按天查看", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
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
                        Text(AMOUNT_MASK, style = HeroNumber, color = XpColors.Ink)
                    } else {
                        NumberTicker(
                            text = summary.todayOutCents.yuanGrouped(),
                            style = HeroNumber,
                            color = XpColors.Ink,
                            rollFromZero = false, // the hero must never flash 0 on first paint
                            staggerMs = 26L,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    SummaryColumn("本月支出（元）", summary.monthOutCents, hidden, Modifier.weight(1f))
                    SummaryColumn("本月收入（元）", summary.monthInCents, hidden, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }
            BudgetStrip(budget = budget, hidden = hidden, onClick = onOpenBudget)
        }
    }
}

@Composable
private fun SummaryColumn(label: String, cents: Cents, hidden: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        Text(
            if (hidden) AMOUNT_MASK else cents.yuanGrouped(),
            style = ColumnNumber,
            color = XpColors.Ink,
            maxLines = 1,
        )
    }
}

/**
 * The card's bottom strip (41dp in the reference): budget entry, or the remaining quota once the user
 * set one themselves. Never advice — the budget is the user's own number (product principles).
 */
@Composable
private fun BudgetStrip(budget: BudgetSummary?, hidden: Boolean, onClick: () -> Unit) {
    val text = budgetLineText(budget, hidden)
    Row(
        Modifier.fillMaxWidth().height(41.dp).background(LedgerHighlight.strip)
            .pressableClick(haptic = false, onClick = onClick)
            .padding(start = XpSpace.page, end = XpSpace.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = XpColors.Ink,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = XpColors.InkTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
