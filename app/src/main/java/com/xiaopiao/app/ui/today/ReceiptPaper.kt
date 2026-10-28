package com.xiaopiao.app.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors

val PaperWidth = 296.dp
private val ToothDp = 12.dp
internal val PaperDepth = 6.dp

/**
 * The receipt sheet. Its height follows the print feed (zigzag edge always at the leading end);
 * while [clock] is `instant` it is simply the whole receipt (share sheet, reduced motion, after printing).
 */
@Composable
fun ReceiptPaper(
    lines: List<ReceiptLine>,
    clock: PrintClock,
    stampText: String,
    stamp: StampVisuals,
    tornTop: Boolean,
    modifier: Modifier = Modifier,
) {
    val timeline = clock.timeline
    val stampTop = ReceiptLayout.stampTopDp(lines)
    Box(
        modifier
            .width(PaperWidth)
            .drawWithContent {
                val path = paperPath(size.width, size.height, ToothDp.toPx(), PaperDepth.toPx(), tornTop)
                // Soft contact shadow: a few translucent copies of the outline, nudged down.
                for (i in 1..5) translate(top = i * 1.6f * density) { drawPath(path, Color.Black.copy(alpha = 0.035f)) }
                clipPath(path) {
                    drawPaperBackground(density)
                    this@drawWithContent.drawContent()
                    if (!clock.instant) drawThermalHead(density)
                }
            }
            .layout { measurable, c ->
                val p = measurable.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val revealed = (timeline.revealedHeightDp(clock.now()).dp.toPx() + PaperDepth.toPx()).toInt()
                val h = if (clock.instant) p.height else revealed.coerceIn(0, p.height)
                layout(p.width, h) { p.place(0, 0) }
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            lines.forEachIndexed { i, line ->
                key(i, line.kind) { ReceiptLineView(line, timeline.steps[i], clock) }
            }
            Spacer(Modifier.height(PaperDepth))
        }
        if (stampTop >= 0f) {
            Box(Modifier.offset(y = stampTop.dp).fillMaxWidth().height(LineKind.STAMP_SPACE.heightDp.dp), contentAlignment = Alignment.Center) {
                TitleStamp(stampText, stamp, Modifier.offset(x = 16.dp))
            }
        }
    }
}

/**
 * Thermal development at the leading edge while the paper feeds: freshly printed ink is still pale and darkens
 * as the paper moves on, plus the print head's scan line at the very bottom.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThermalHead(density: Float) {
    val fade = 64f * density
    val top = (size.height - fade).coerceAtLeast(0f)
    drawRect(
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, XpColors.Paper.copy(alpha = 0.78f)), startY = top, endY = size.height),
        topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
    )
    val y = size.height - 8f * density
    drawRect(Color.Black.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1.5f * density))
}
