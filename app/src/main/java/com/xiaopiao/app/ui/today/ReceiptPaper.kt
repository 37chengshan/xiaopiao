package com.xiaopiao.app.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

val PaperWidth = 296.dp
private val ToothDp = 12.dp
private val DepthDp = 6.dp

/**
 * The receipt sheet. Its height follows the print feed (zigzag edge always at the leading end);
 * everything is recorded into [layer] so the very same pixels can be shared as an image.
 */
@Composable
fun ReceiptPaper(
    lines: List<ReceiptLine>,
    clock: PrintClock,
    stampText: String,
    stamp: StampVisuals,
    layer: GraphicsLayer,
    tornTop: Boolean,
    onToggleMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeline = clock.timeline
    val stampTop = ReceiptLayout.stampTopDp(lines)
    Box(
        modifier
            .width(PaperWidth)
            .drawWithContent {
                val w = size.width
                val h = size.height
                val path = paperPath(w, h, ToothDp.toPx(), DepthDp.toPx(), tornTop)
                layer.record {
                    clipPath(path) {
                        drawPaperBackground(density)
                        this@drawWithContent.drawContent()
                    }
                }
                drawLayer(layer)
            }
            .layout { measurable, c ->
                val p = measurable.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val revealed = (timeline.revealedHeightDp(clock.now()).dp.toPx() + DepthDp.toPx()).toInt()
                val h = if (clock.instant) p.height else revealed.coerceIn(0, p.height)
                layout(p.width, h) { p.place(0, 0) }
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            lines.forEachIndexed { i, line ->
                key(i, line.kind) { ReceiptLineView(line, timeline.steps[i], clock, onToggleMore) }
            }
        }
        if (stampTop >= 0f) {
            Box(Modifier.offset(y = stampTop.dp).fillMaxWidth().height(LineKind.STAMP_SPACE.heightDp.dp), contentAlignment = Alignment.Center) {
                TitleStamp(stampText, stamp, Modifier.offset(x = 14.dp))
            }
        }
    }
}
