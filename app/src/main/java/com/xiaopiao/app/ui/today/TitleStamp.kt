package com.xiaopiao.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Random

/** Animation values the stamp reads lazily (in draw/graphicsLayer), so changes never recompose. */
class StampVisuals(val scale: () -> Float, val alpha: () -> Float, val ring: () -> Float)

private val StampW = 160.dp
private val StampH = 84.dp

/** Pure: stamp font size shrinks as the title grows so it always fits the frame. */
fun stampFontSp(text: String): Float = when {
    text.length <= 4 -> 28f
    text.length <= 6 -> 23f
    else -> 17f
}

/**
 * The red title stamp. Slams in from 1.6x with a spring (scale driven by [StampVisuals]), tilted -8 degrees,
 * with an expanding ink ring and speckled ink so it looks pressed rather than pasted.
 */
@Composable
fun TitleStamp(text: String, visuals: StampVisuals, modifier: Modifier = Modifier) {
    val ink = LocalPaperColors.current.stamp
    // Never quite the same angle twice for different titles: -8 degrees give or take a couple.
    val tilt = -8f + ((text.hashCode() and 0x7fffffff) % 51 - 25) / 10f
    Box(modifier.size(StampW + 80.dp, StampH + 60.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(StampW + 80.dp, StampH + 60.dp)) {
            val p = visuals.ring()
            if (p > 0f && p < 1f) {
                val c = center
                val r = (StampW.toPx() * 0.42f) * (0.8f + p * 0.9f)
                drawCircle(ink.copy(alpha = (1f - p) * 0.28f), r, c, style = Stroke(width = (6f * (1f - p) + 1f).dp.toPx()))
                drawCircle(ink.copy(alpha = (1f - p) * 0.10f), r * 0.85f, c)
            }
        }
        Box(
            Modifier
                .size(StampW, StampH)
                .graphicsLayer {
                    val s = visuals.scale()
                    scaleX = s; scaleY = s
                    rotationZ = tilt
                    alpha = visuals.alpha()
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawFrame(ink)
                    drawSpeckle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("· 今日头衔 ·", style = TextStyle(fontSize = 9.sp, color = ink, letterSpacing = 1.sp, fontWeight = FontWeight.Bold))
                Text(
                    text,
                    style = TextStyle(fontSize = stampFontSp(text).sp, color = ink, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(ink: androidx.compose.ui.graphics.Color) {
    val r = CornerRadius(14.dp.toPx())
    drawRoundRect(ink, Offset.Zero, size, r, style = Stroke(3.2.dp.toPx()))
    val inset = 5.dp.toPx()
    drawRoundRect(ink, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(10.dp.toPx()), style = Stroke(1.2.dp.toPx()))
}

/** Knocks tiny holes out of the ink (BlendMode.DstOut) so the stamp looks pressed, not printed. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpeckle() {
    val rnd = Random(5)
    // Pressed unevenly: one corner takes less ink than the other, like a stamp rocked a hair off level.
    drawRect(
        androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.38f)),
            start = Offset(0f, 0f), end = Offset(size.width, size.height),
        ),
        blendMode = BlendMode.DstOut,
    )
    drawIntoCanvas {
        repeat(90) {
            val x = rnd.nextFloat() * size.width
            val y = rnd.nextFloat() * size.height
            val r = (0.5f + rnd.nextFloat() * 1.6f).dp.toPx()
            drawCircle(androidx.compose.ui.graphics.Color.Black, r, Offset(x, y), alpha = 0.55f, blendMode = BlendMode.DstOut)
        }
    }
}
