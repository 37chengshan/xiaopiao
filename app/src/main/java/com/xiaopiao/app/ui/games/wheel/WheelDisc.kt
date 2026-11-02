package com.xiaopiao.app.ui.games.wheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.Contrast
import com.xiaopiao.app.ui.theme.XpColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 一个**有厚度**的转盘，不是一张饼图。
 *
 * 四层，从下到上：
 * 1. 投影 + 外圈（XpColors.Card）+ 左上高光 + 灯珠 —— 静态；转的时候灯珠按顺序亮；
 * 2. 扇面 + 分隔线 + 径向文字 —— 唯一跟着 rotation 转的层，转值只在 layer/draw 阶段读；
 * 3. 顶部指针（泪滴 + 投影），每次擦过分隔线被拨一下再弹回；
 * 4. 中央「转」按钮 —— 真按钮，按下 0.94。
 *
 * 结果态：中奖扇面抬高 6% 并提亮，其余压到 45%，灯珠全亮。
 */
@Composable
internal fun WheelDisc(
    slices: List<String>,
    palette: List<Color>,
    rotationProvider: () -> Float,
    spinning: Boolean,
    highlight: Int?,
    dimmed: Boolean,
    bulbsLit: Boolean,
    pointerKick: () -> Float,
    hubEnabled: Boolean,
    hubLabel: String,
    hubSubLabel: String,
    onSpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val count = slices.size.coerceAtLeast(1)
    val labelStyle = TextStyle(
        color = XpColors.Ink,
        fontSize = labelSizeSp(count).sp,
        fontWeight = FontWeight.Medium,
    )
    val separators = XpColors.Canvas.copy(alpha = if (XpColors.isDark) 0.55f else 0.7f)

    Box(modifier, contentAlignment = Alignment.Center) {
        // Back plate: shadow, rim, rim sheen, studs. Static apart from the twinkle.
        Canvas(Modifier.fillMaxSize()) {
            val radius = wheelRadius()
            if (radius <= 0f) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)
            val rim = rimWidth(radius)
            drawWheelShadow(center, radius)
            drawCircle(XpColors.Card, radius = radius, center = center)
            // Top-left sheen + darker lower-right edge: the ring reads as a turned bezel, not a flat border.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (XpColors.isDark) 0.10f else 0.75f),
                        Color.Transparent,
                        XpColors.Ink.copy(alpha = if (XpColors.isDark) 0.16f else 0.05f),
                    ),
                    center = Offset(center.x - radius * 0.45f, center.y - radius * 0.55f),
                    radius = radius * 1.55f,
                ),
                radius = radius,
                center = center,
            )
            drawRimStuds(
                center = center,
                ringRadius = radius - rim / 2f,
                studs = studCount(count),
                spinning = spinning,
                bulbsLit = bulbsLit,
                rotation = rotationProvider(),
            )
            drawCircle(
                color = XpColors.Ink.copy(alpha = 0.10f),
                radius = radius - 0.5.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        // The only layer that moves. Reading the animation inside `graphicsLayer` keeps the spin out of
        // composition: nothing recomposes while the wheel turns.
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotationProvider() }) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = wheelRadius()
                if (radius <= 0f) return@Canvas
                val center = Offset(size.width / 2f, size.height / 2f)
                val sliceRadius = radius - rimWidth(radius)
                val sweep = sliceSweep(count)

                for (index in 0 until count) {
                    val raised = highlight == index
                    val base = sliceColor(palette, index)
                    val fill = when {
                        raised -> highlightSlice(base)
                        dimmed -> dimSlice(base)
                        else -> base
                    }
                    val reach = if (raised) sliceRadius * 1.06f else sliceRadius
                    drawArc(
                        color = fill,
                        startAngle = -90f + index * sweep,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - reach, center.y - reach),
                        size = Size(reach * 2f, reach * 2f),
                    )
                    if (raised) {
                        drawArc(
                            color = XpColors.Accent.copy(alpha = 0.55f),
                            startAngle = -90f + index * sweep,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = Offset(center.x - reach, center.y - reach),
                            size = Size(reach * 2f, reach * 2f),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                // 1dp separators: on a 12-slice wheel they are what keeps neighbouring wedges apart.
                for (index in 0 until count) {
                    val rad = (-90f + index * sweep) * PI.toFloat() / 180f
                    drawLine(
                        color = separators,
                        start = Offset(center.x + cos(rad) * sliceRadius * 0.155f, center.y + sin(rad) * sliceRadius * 0.155f),
                        end = Offset(center.x + cos(rad) * sliceRadius, center.y + sin(rad) * sliceRadius),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                drawCircle(
                    color = XpColors.Ink.copy(alpha = 0.08f),
                    radius = sliceRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )

                for (index in 0 until count) {
                    val label = slices.getOrNull(index).orEmpty().trim()
                    if (label.isEmpty()) continue
                    drawRadialLabel(
                        measurer = measurer,
                        text = shortLabel(label),
                        style = labelStyle,
                        center = center,
                        sliceRadius = sliceRadius,
                        midAngle = -90f + index * sweep + sweep / 2f,
                        alpha = if (dimmed && highlight != index) 0.55f else 1f,
                    )
                }
            }
        }

        // Pointer: flaps when a separator passes, springs back. Above the slices, below the hub.
        Canvas(Modifier.fillMaxSize()) {
            val radius = wheelRadius()
            if (radius <= 0f) return@Canvas
            drawPointer(Offset(size.width / 2f, size.height / 2f), radius, pointerKick())
        }

        HubButton(
            enabled = hubEnabled,
            label = hubLabel,
            subLabel = hubSubLabel,
            modifier = Modifier.size(hubSize(count)),
            onClick = onSpin,
        )
    }
}

/**
 * The disc keeps a margin inside its box: the shadow spreads 16% past the rim and the pointer hangs above it,
 * and a Canvas clips to its own bounds — without this the shadow would end in a hard edge.
 */
private fun DrawScope.wheelRadius(): Float = size.minDimension / 2f * 0.86f

private fun DrawScope.rimWidth(radius: Float): Float = 15.dp.toPx().coerceAtMost(radius * 0.16f)

/** Proportional to the disc, but never a small target: 76dp with 12 slices, 84dp otherwise. */
private fun hubSize(count: Int): Dp = if (count > 8) 76.dp else 84.dp

@Composable
private fun HubButton(
    enabled: Boolean,
    label: String,
    subLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (enabled) XpColors.Accent else XpColors.Fill
    val ink = if (enabled) Contrast.onAccent(XpColors.Accent) else XpColors.InkTertiary
    Box(
        modifier
            .clip(CircleShape)
            .background(accent)
            .pressableClick(enabled = enabled, scaleDown = 0.94f, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Inner highlight drawn, not elevated: the hub is part of the wheel.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (XpColors.isDark) 0.22f else 0.45f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.34f, size.height * 0.26f),
                    radius = size.minDimension * 0.9f,
                ),
            )
            drawCircle(
                color = XpColors.Canvas.copy(alpha = 0.28f),
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1)
            Text(subLabel, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.72f), maxLines = 1)
        }
    }
}

// ── drawing helpers ─────────────────────────────────────────────────────────────────────────────────

/** A soft radial drop shadow under the disc — no Material elevation anywhere. */
private fun DrawScope.drawWheelShadow(center: Offset, radius: Float) {
    val lift = radius * 0.08f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Black.copy(alpha = if (XpColors.isDark) 0.55f else 0.22f),
            0.55f to Color.Black.copy(alpha = if (XpColors.isDark) 0.28f else 0.10f),
            1f to Color.Black.copy(alpha = 0f),
            center = Offset(center.x, center.y + lift),
            radius = radius * 1.16f,
        ),
        radius = radius * 1.16f,
        center = Offset(center.x, center.y + lift),
    )
}

/**
 * Studs on the rim. While the wheel turns, three of them light in sequence — the phase comes straight from the
 * rotation, so the twinkle costs one draw-phase calculation. On a result, all of them are lit.
 */
private fun DrawScope.drawRimStuds(
    center: Offset,
    ringRadius: Float,
    studs: Int,
    spinning: Boolean,
    bulbsLit: Boolean,
    rotation: Float,
) {
    val step = 360f / studs
    val phase = ((rotation / step).toInt() % studs + studs) % studs
    val dot = 2.4.dp.toPx()
    for (index in 0 until studs) {
        val rad = (-90f + index * step) * PI.toFloat() / 180f
        val at = Offset(center.x + cos(rad) * ringRadius, center.y + sin(rad) * ringRadius)
        val trail = when {
            bulbsLit -> 1f
            !spinning -> 0f
            (index - phase + studs) % studs == 0 -> 1f
            (index - phase + studs) % studs == 1 -> 0.45f
            (index - phase + studs) % studs == 2 -> 0.2f
            else -> 0f
        }
        if (trail > 0f) {
            drawCircle(color = XpColors.Highlight.copy(alpha = 0.28f * trail), radius = dot * 2.1f, center = at)
            drawCircle(color = XpColors.Highlight.copy(alpha = trail), radius = dot, center = at)
        } else {
            drawCircle(color = XpColors.Ink.copy(alpha = 0.14f), radius = dot * 0.8f, center = at)
        }
    }
}

/**
 * A label along the radius: rotated with its slice, centred at 63% of the slice radius, flipped when the radial
 * direction points left so it never reads upside down.
 */
private fun DrawScope.drawRadialLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    center: Offset,
    sliceRadius: Float,
    midAngle: Float,
    alpha: Float,
) {
    val maxWidth = (sliceRadius * 0.52f).roundToInt().coerceAtLeast(24)
    val layout = measurer.measure(
        text = text,
        style = style.copy(color = style.color.copy(alpha = alpha)),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        constraints = Constraints(maxWidth = maxWidth),
    )
    val anchorRadius = sliceRadius * 0.63f
    val normalised = ((midAngle % 360f) + 360f) % 360f
    val flipped = normalised > 90f && normalised < 270f
    rotate(degrees = midAngle, pivot = center) {
        val left = center.x + anchorRadius - layout.size.width / 2f
        val top = center.y - layout.size.height / 2f
        if (flipped) {
            rotate(degrees = 180f, pivot = Offset(center.x + anchorRadius, center.y)) {
                drawText(layout, topLeft = Offset(left, top))
            }
        } else {
            drawText(layout, topLeft = Offset(left, top))
        }
    }
}

/** Teardrop hanging over the rim at 12 o'clock with a small shadow; [kick] tips it as separators pass. */
private fun DrawScope.drawPointer(center: Offset, radius: Float, kick: Float) {
    val width = radius * 0.11f
    val length = radius * 0.20f
    val tipY = center.y - radius * 0.93f
    val topY = tipY - length

    fun teardrop(dx: Float, dy: Float): Path = Path().apply {
        moveTo(center.x, tipY + dy)
        cubicTo(
            center.x - width * 0.55f + dx, topY + length * 0.55f + dy,
            center.x - width + dx, topY + dy,
            center.x + dx, topY + dy,
        )
        cubicTo(
            center.x + width + dx, topY + dy,
            center.x + width * 0.55f + dx, topY + length * 0.55f + dy,
            center.x, tipY + dy,
        )
        close()
    }

    rotate(degrees = kick, pivot = Offset(center.x, topY)) {
        drawPath(teardrop(0f, 5.dp.toPx()), Color.Black.copy(alpha = 0.18f))
        drawPath(teardrop(0f, 0f), XpColors.Accent)
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = width * 0.22f,
            center = Offset(center.x, topY + length * 0.28f),
        )
    }
}

/** The winner's wedge: mixed toward the theme accent/paper, never a different hue. */
internal fun highlightSlice(base: Color): Color =
    lerp(base, if (XpColors.isDark) XpColors.Accent else XpColors.Card, 0.24f)

internal fun dimSlice(base: Color): Color = base.copy(alpha = 0.45f)
