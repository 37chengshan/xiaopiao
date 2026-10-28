package com.xiaopiao.app.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.merchant.Change
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueSource
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar

/**
 * Money for the shop screens: [MoneyText] with grouped thousands ("¥2,315"), because a day's take is
 * four digits more often than not. Tabular digits and the small ¥ still come from [MoneyText].
 */
@Composable
fun RevenueMoney(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = XpColors.Ink,
    prefix: String = "",
    textAlign: TextAlign? = null,
) = MoneyText(
    amount = RevenueBook.money(cents).replace("¥", ""),
    modifier = modifier, style = style, color = color, prefix = prefix, textAlign = textAlign,
)

/** 40dp date block: the day number is the icon, the weekday the caption. */
@Composable
fun DateTile(dayStart: Long, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val c = Calendar.getInstance().apply { timeInMillis = dayStart }
    Column(
        modifier.size(40.dp).clip(RoundedCornerShape(XpRadius.m))
            .background(if (dimmed) XpColors.Surface2 else XpColors.Fill),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${c.get(Calendar.DAY_OF_MONTH)}",
            fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
            color = if (dimmed) XpColors.InkSecondary else XpColors.Ink,
        )
        Text(
            RevenueBook.weekdayLabel(dayStart),
            fontSize = 9.sp, lineHeight = 11.sp, color = XpColors.InkSecondary,
        )
    }
}

/** Small source marker: demo rows are always labelled, imported rows lead back to the file. */
@Composable
fun SourcePill(source: RevenueSource, modifier: Modifier = Modifier) {
    when (source) {
        RevenueSource.SEED -> Pill("演示", modifier)
        RevenueSource.CSV -> Pill("导入", modifier)
        RevenueSource.MANUAL -> Unit
    }
}

/** "多 12%" / "少 9%" / "持平" in the semantic colour; no baseline reads as "没有可比数据". */
@Composable
fun ChangeLabel(change: Change, modifier: Modifier = Modifier, upIsGood: Boolean = true) {
    val (text, color) = when {
        !change.hasBase -> "没有可比数据" to XpColors.InkSecondary
        change.diffCents == 0L -> "持平" to XpColors.InkSecondary
        else -> {
            val pct = RevenueBook.percentText(change.pct)?.removePrefix("+")?.removePrefix("-")
            val word = if (change.diffCents > 0) "多" else "少"
            val good = if (change.diffCents > 0) upIsGood else !upIsGood
            "$word ${pct ?: ""}".trim() to if (good) XpColors.Positive else XpColors.Negative
        }
    }
    Text(text, modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

/** One label/value cell of a two- or three-up strip. */
@Composable
fun MetricCell(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
        Box(Modifier.padding(top = 2.dp)) { value() }
    }
}

/** Quiet inline "查看全部 ›" escape. */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = XpColors.Accent) {
    Text(
        text, modifier.clickable(onClick = onClick).padding(vertical = XpSpace.xs),
        style = MaterialTheme.typography.labelMedium, color = color,
    )
}

/** Capture header row: section title on the left, a quiet escape on the right. */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.xl, bottom = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Full-width hairline-free spacer used under floating content. */
@Composable
fun NavBottomSpace() = Spacer(Modifier.height(XpSpace.xl))

/** Label above a large number, used by the AI cards. */
@Composable
fun BigValue(text: String, modifier: Modifier = Modifier, color: Color = XpColors.Ink) {
    Text(
        text, modifier,
        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
        color = color, maxLines = 1, textAlign = TextAlign.Start,
    )
}

/** A 2-line date + weekday caption ("10月28日 · 周三"). */
@Composable
fun DayCaption(dayStart: Long, modifier: Modifier = Modifier) {
    Text(
        "${RevenueBook.shortDate(dayStart)} · ${RevenueBook.weekdayLabel(dayStart)}",
        modifier, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
    )
}

/** Vertical rail used to align a number row with a date tile. */
@Composable
fun RailGap() = Spacer(Modifier.width(XpSpace.m))
