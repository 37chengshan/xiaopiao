package com.xiaopiao.app.ui.today

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons

/** The two versions of the printed card, both pre-built by the caller (the same receipt, more or fewer rows). */
class ShareContent(
    val full: List<ReceiptLine>,
    val stampText: String,
    val fileKey: String,
    /** A purpose-built 简版 (hero total + share bars + stamp); null derives one from [full]. */
    briefLines: List<ReceiptLine>? = null,
    /** The paper colours chosen in 小票页; null = the app's classic paper. */
    val colors: PaperColors? = null,
) {
    val brief: List<ReceiptLine> = briefLines ?: ReceiptLayout.brief(full)
    fun linesFor(briefMode: Boolean): List<ReceiptLine> = if (briefMode) brief else full
}

/** "分享小票" sheet: the receipt on a pick-your-own gradient card, 完整/简版, 分享 / 保存图片 / 复制文字. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptShareSheet(content: ShareContent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    val layer = rememberGraphicsLayer()
    var gradient by rememberSaveable { mutableIntStateOf(0) }
    var briefMode by rememberSaveable { mutableStateOf(false) }
    val lines = content.linesFor(briefMode)
    val plain = ReceiptShare.textFor(content, briefMode)

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    suspend fun render() = layer.toImageBitmap().asAndroidBitmap()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = XpRadius.l, topEnd = XpRadius.l),
        containerColor = XpColors.Card,
        scrimColor = XpColors.Scrim,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = XpSpace.l)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("分享小票", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
                Box(
                    Modifier.defaultMin().pressableClick { ReceiptShare.copyText(context, plain); toast("已复制文字") }
                        .padding(horizontal = XpSpace.s),
                    contentAlignment = Alignment.Center,
                ) { Text("复制文字", style = MaterialTheme.typography.labelLarge, color = XpColors.Accent) }
            }
            Box(
                Modifier.weight(1f, fill = false).fillMaxWidth().padding(vertical = XpSpace.s)
                    .clip(RoundedCornerShape(XpRadius.m)).animateContentSize(spring(0.85f, 380f)).verticalScroll(rememberScrollState()),
            ) {
                ShareCard(lines, content.stampText, GradientPresets.byIndex(gradient), content.colors, Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                })
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(GradientPresets.all) { i, g -> Swatch(g, i == gradient) { haptics.select(); gradient = i } }
            }
            Spacer(Modifier.height(XpSpace.m))
            // 简版/完整 on the left (narrow), the single save action on the right (tt#31).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ModeToggle(briefMode, Modifier.width(132.dp)) { haptics.select(); briefMode = it }
                Spacer(Modifier.weight(1f))
                XpButton("保存图片", {
                    scope.launch {
                        val ok = ReceiptShare.save(context, render(), content.fileKey)
                        toast(if (ok) "已保存到相册" else "保存失败，试试「分享」")
                    }
                }, style = XpButtonStyle.Secondary)
            }
            Spacer(Modifier.height(XpSpace.m))
            // One row of icon + small label actions — not two rows of pills.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ShareAction(Icons.Outlined.Share, "分享") {
                    haptics.confirm()
                    scope.launch { ReceiptShare.share(context, render(), plain, content.fileKey) }
                }
                ShareAction(Icons.Outlined.Groups, "发到圈子") {
                    haptics.select()
                    ReceiptShare.copyText(context, plain)
                    toast("小票文字已复制，去圈子粘贴")
                }
                ShareAction(Icons.Outlined.ContentCopy, "复制文字") {
                    haptics.select()
                    ReceiptShare.copyText(context, plain)
                    toast("已复制文字")
                }
            }
            Spacer(Modifier.height(XpSpace.l))
        }
    }
}

private fun Modifier.defaultMin() = this.then(Modifier.height(48.dp))

/** The exported image: the paper floating on the chosen gradient with a small signature. Captured as-is by the sheet. */
@Composable
private fun ShareCard(lines: List<ReceiptLine>, stampText: String, gradient: CardGradient, colors: PaperColors?, modifier: Modifier = Modifier) {
    val timeline = remember(lines) { PrintTimeline.build(lines, reduced = true) }
    val clock = remember(timeline) { PrintClock(timeline, instant = true) { 0L } }
    val stamp = remember { StampVisuals({ 1f }, { 1f }, { 0f }) }
    val ink = if (gradient.isDark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.55f)
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(gradient.top), Color(gradient.bottom))))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPaperColors provides (colors ?: LocalPaperColors.current)) {
            ReceiptPaper(lines, clock, stampText, stamp, tornTop = true)
        }
        Spacer(Modifier.height(18.dp))
        Text("今日小票 · 每天印一张", style = MaterialTheme.typography.labelMedium, color = ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Swatch(g: CardGradient, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).pressableClick(haptic = false, onClick = onClick)
            .semantics { contentDescription = "背景 ${g.name}"; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(g.top), Color(g.bottom))))
                .border(if (selected) 2.dp else 0.5.dp, if (selected) XpColors.Ink else XpColors.Hairline, CircleShape),
        )
    }
}

/** Segmented 完整 / 简版. */
@Composable
private fun ModeToggle(brief: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.height(34.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill).padding(2.dp)) {
        listOf(false to "完整", true to "简版").forEach { (value, label) ->
            val on = brief == value
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(XpRadius.pill))
                    .background(if (on) XpColors.Card else Color.Transparent)
                    .pressableClick(haptic = false) { onChange(value) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (on) XpColors.Ink else XpColors.InkSecondary)
            }
        }
    }
}

/** The black primary of the share sheet (Ink fill, Canvas text): the one place the sheet is allowed to shout. */
@Composable
private fun InkButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(14.dp)).background(XpColors.Ink).pressableClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = XpColors.Canvas) }
}

/** Icon over a small caption: the only shape used for the share actions (tt#31). */
@Composable
private fun ShareAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(XpRadius.m)).pressableClick(onClick = onClick)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = XpColors.Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
    }
}
