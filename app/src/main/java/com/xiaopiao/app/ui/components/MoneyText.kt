package com.xiaopiao.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.theme.XpColors

/**
 * Tabular amount with a small, secondary-ink ¥ (40% smaller than the digits) — docs/DESIGN.md §3.
 * [prefix] is drawn before the ¥ in the digit colour (e.g. "+" for income).
 */
@Composable
fun MoneyText(
    cents: Cents,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = XpColors.Ink,
    prefix: String = "",
    textAlign: TextAlign? = null,
) = MoneyText(cents.yuan(), modifier, style, color, prefix, textAlign)

/** Same, for an already formatted amount such as "1,280.5" or "0". */
@Composable
fun MoneyText(
    amount: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = XpColors.Ink,
    prefix: String = "",
    textAlign: TextAlign? = null,
) {
    val base = style.fontSize
    Text(
        buildAnnotatedString {
            if (prefix.isNotEmpty()) append(prefix)
            withStyle(SpanStyle(fontSize = base * 0.6f, color = XpColors.InkSecondary, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)) { append("¥") }
            append(amount)
        },
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        textAlign = textAlign,
        maxLines = 1,
    )
}
