package com.xiaopiao.app.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Locale

/** DESIGN §4.3: a first-level page's list runs under the glass bar and ends at least this far below its last row. */
val MerchantBottomInset: Dp = 128.dp

/**
 * New copy ships in both languages from the start (DESIGN §12). The app-wide language switch is someone else's
 * job; until it lands the device language decides, which is what `values-en` will do as well.
 */
@Composable
fun bi(zh: String, en: String): String =
    if (LocalConfiguration.current.locales[0].language == Locale.ENGLISH.language) en else zh

/**
 * DESIGN §4.1, 小店版 row: left empty, right = [AppTopActions] with the shop's 经营小票 in the receipt slot.
 * Identical on every first-level page; the gear comes from `LocalTopBarHost`, provided once by [MerchantShell].
 */
@Composable
fun RowScope.MerchantTopActions(onOpenReceipt: () -> Unit) {
    AppTopActions(receipt = {
        NavIconButton(Icons.AutoMirrored.Outlined.ReceiptLong, bi("经营日报小票", "Daily report"), onClick = onOpenReceipt)
    })
}

/** One-line text that shrinks (never wraps, never clips) before it ellipsizes — for numbers in narrow columns. */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = XpColors.Ink,
    minSize: TextUnit = 11.sp,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: TextAlign.Unspecified, fontFeatureSettings = "tnum"),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

/** A label above a value; both stay on one line and the value shrinks to fit its column. */
@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = XpColors.Ink) {
    Column(modifier) {
        Text(
            label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        FitText(value, Modifier.padding(top = 2.dp), MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp), valueColor, minSize = 10.sp)
    }
}

/**
 * The one large colour field of a 小店 page — the shop twin of the user edition's 账单总览卡: warm
 * `HighlightSoft` surface, one soft decorative arc, the big number, a row of columns, and an optional
 * lighter strip along the bottom edge (where the chart lives). Tokens only.
 */
@Composable
fun MerchantHeroCard(
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    corner: (@Composable () -> Unit)? = null,
    number: @Composable () -> Unit,
    columns: (@Composable RowScope.() -> Unit)? = null,
    strip: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.contentCard)).background(XpColors.HighlightSoft),
    ) {
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.size(210.dp).offset(x = 170.dp, y = (-100).dp)
                    .clip(CircleShape).background(XpColors.Highlight.copy(alpha = 0.22f)),
            )
        }
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = XpSpace.page, end = XpSpace.page, top = 18.dp, bottom = XpSpace.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (caption != null) {
                            Text(
                                caption, style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (corner != null) {
                        Box(Modifier.padding(start = XpSpace.s)) { corner() }
                    }
                }
                Box(Modifier.padding(top = 2.dp)) { number() }
                if (columns != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = XpSpace.m),
                        horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
                        content = columns,
                    )
                }
            }
            if (strip != null) {
                Column(
                    Modifier.fillMaxWidth().background(XpColors.Card.copy(alpha = 0.55f))
                        .padding(horizontal = XpSpace.page, vertical = XpSpace.m),
                    content = strip,
                )
            }
        }
    }
}

/** Small pill on a hero card's top-right corner (the 小店 twin of 按天查看). */
@Composable
fun HeroPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Card.copy(alpha = 0.6f))
            .pressableClick(haptic = false, scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = XpColors.InkSecondary, modifier = Modifier.size(15.dp))
        Text(
            text, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** 30sp tabular hero figure, the same size as the 账单总览卡's. */
val MerchantHeroNumber = TextStyle(
    fontFamily = com.xiaopiao.app.ui.theme.XpFonts.sans,
    fontSize = 32.sp, lineHeight = 38.sp,
    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    letterSpacing = (-0.6).sp, fontFeatureSettings = "tnum",
)

/** A change vs. a baseline, as a soft capsule (semantic colour on its own soft wash). */
@Composable
fun ChangePill(change: com.xiaopiao.app.merchant.Change, modifier: Modifier = Modifier, upIsGood: Boolean = true) {
    val label = changeText(change)
    val tone = when {
        !change.hasBase || change.diffCents == 0L -> XpColors.InkSecondary
        (change.diffCents > 0) == upIsGood -> XpColors.Positive
        else -> XpColors.Negative
    }
    Text(
        label, modifier.clip(RoundedCornerShape(XpRadius.pill)).background(tone.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** "多 12%" / "少 9%" / "持平" / "没有可比数据" — pure, so the wording is unit-tested. */
fun changeText(change: com.xiaopiao.app.merchant.Change): String = when {
    !change.hasBase -> "没有可比数据"
    change.diffCents == 0L -> "持平"
    else -> {
        val pct = com.xiaopiao.app.merchant.RevenueBook.percentText(change.pct)?.removePrefix("+")?.removePrefix("-")
        val word = if (change.diffCents > 0) "多" else "少"
        "$word ${pct ?: ""}".trim()
    }
}

/**
 * Chrome the experiment steps share: the glass bar's reserved [top], and a header (the step progress) that scrolls
 * with the page. Steps wrap their body in [FlowPage] so it runs under the bar like every other first-level list.
 */
@Immutable
class FlowChrome(val top: Dp, val header: @Composable () -> Unit)

val LocalFlowChrome = compositionLocalOf { FlowChrome(0.dp) {} }

@Composable
fun FlowChromeHost(chrome: FlowChrome, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFlowChrome provides chrome) { content() }
}

@Composable
fun FlowPage(
    modifier: Modifier = Modifier,
    horizontal: Dp = 0.dp,
    spacing: Dp = XpSpace.m,
    alignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chrome = LocalFlowChrome.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = chrome.top, bottom = MerchantBottomInset).padding(horizontal = horizontal),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = alignment,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = if (horizontal == 0.dp) XpSpace.l else 0.dp)) { chrome.header() }
        content()
    }
}

/** Empty state inside a flow step: clears the glass bar, centred, one primary action (DESIGN §9.5). */
@Composable
fun FlowEmpty(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    val chrome = LocalFlowChrome.current
    Column(Modifier.fillMaxSize().padding(top = chrome.top), verticalArrangement = Arrangement.Center) {
        com.xiaopiao.app.ui.components.EmptyState(icon, title, body, actionLabel = action, onAction = onAction)
    }
}

/**
 * Small confirmation: a centred window over the blurred page (DESIGN §6). The shell blurs the page while it is open.
 *
 * TODO(design-v3): replace with the shared `XpBlurDialog` once the coordinator adds it to components.
 */
@Composable
fun MerchantConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.xl)).background(XpColors.Card).padding(XpSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink, textAlign = TextAlign.Center)
            Text(
                message, Modifier.padding(top = XpSpace.xs), style = MaterialTheme.typography.bodyMedium,
                color = XpColors.InkSecondary, textAlign = TextAlign.Center,
            )
            com.xiaopiao.app.ui.components.XpButton(
                confirmLabel, onConfirm, Modifier.fillMaxWidth().padding(top = XpSpace.l),
                style = com.xiaopiao.app.ui.components.XpButtonStyle.Destructive,
            )
            com.xiaopiao.app.ui.components.XpButton(
                bi("再想想", "Keep it"), onDismiss, Modifier.fillMaxWidth().padding(top = XpSpace.s),
                style = com.xiaopiao.app.ui.components.XpButtonStyle.Plain,
            )
        }
    }
}
