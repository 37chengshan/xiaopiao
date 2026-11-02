package com.xiaopiao.app.ui.games.wheel

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.motion.XpMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr

/**
 * 多个盘的切换器（tt 2026-11-02: 上面的转盘 1、2、3 有点丑）。一条 Fill 底槽里横排各个盘，
 * 白色滑块跟着选中项滑过去（位置只在 draw 阶段读）；每个名字前是这只盘的迷你色盘。
 * 重命名与删除收进「⋯」，展开后**就地**操作（不用系统对话框）：删除先说后果，再按一次才真删；
 * 转的时候整组不可点。
 */
@Composable
internal fun WheelSwitcher(
    wheels: List<Wheel>,
    current: Int,
    enabled: Boolean,
    palette: List<Color>,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current

    // Each tab's (left, width) inside the scrolled row; the thumb animates between them.
    val tabs = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val thumbLeft = remember { Animatable(0f) }
    val thumbWidth = remember { Animatable(0f) }
    var thumbReady by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val target = tabs[current]
    LaunchedEffect(current, target) {
        val (left, width) = target ?: return@LaunchedEffect
        if (!thumbReady || reduced) {
            thumbLeft.snapTo(left)
            thumbWidth.snapTo(width)
            thumbReady = true
        } else {
            launch { thumbLeft.animateTo(left, XpMotion.snappy()) }
            launch { thumbWidth.animateTo(width, XpMotion.snappy()) }
        }
        // Keep the chosen wheel in view when there are more than fit.
        val pad = with(density) { XpSpace.l.toPx() }
        val viewport = scroll.viewportSize.toFloat()
        val to = when {
            left - pad < scroll.value -> (left - pad).coerceAtLeast(0f)
            left + width + pad > scroll.value + viewport -> left + width + pad - viewport
            else -> null
        }
        if (to != null) {
            if (reduced) scroll.scrollTo(to.roundToInt()) else scroll.animateScrollTo(to.roundToInt())
        }
    }
    LaunchedEffect(wheels.size) { tabs.keys.retainAll { it < wheels.size } }

    val thumbColor = XpColors.Raised
    val thumbShade = Color.Black.copy(alpha = if (XpColors.isDark) 0.24f else 0.07f)

    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .background(XpColors.Fill),
            ) {
                Row(
                    Modifier
                        .horizontalScroll(scroll)
                        .drawBehind {
                            if (!thumbReady) return@drawBehind
                            val inset = SwitcherInset.toPx()
                            val h = size.height - inset * 2
                            val r = CornerRadius(h / 2f)
                            val topLeft = Offset(thumbLeft.value, inset)
                            val box = Size(thumbWidth.value, h)
                            drawRoundRect(thumbShade, topLeft + Offset(0f, 1.dp.toPx()), box, r)
                            drawRoundRect(thumbColor, topLeft, box, r)
                        }
                        .padding(SwitcherInset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    wheels.forEachIndexed { index, wheel ->
                        val on = index == current
                        Row(
                            Modifier
                                .onPlaced { tabs[index] = it.positionInParent().x to it.size.width.toFloat() }
                                .height(SwitcherTabHeight)
                                .clip(RoundedCornerShape(XpRadius.pill))
                                .pressableClick(enabled = enabled && !on, haptic = true) {
                                    managing = false
                                    renaming = false
                                    confirming = false
                                    onSelect(index)
                                }
                                .padding(start = 10.dp, end = XpSpace.m),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MiniWheel(
                                count = wheel.slices.size.coerceAtLeast(MinWheelSlices),
                                palette = palette,
                                faded = !on,
                            )
                            Text(
                                wheel.name,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = if (on) XpColors.Ink else XpColors.InkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(XpSpace.s))
            SwitcherIcon(
                icon = Icons.Outlined.Add,
                label = tr("新建转盘", "New wheel"),
                enabled = enabled,
                onClick = {
                    managing = false
                    onAdd()
                },
            )
            Spacer(Modifier.width(XpSpace.xs))
            SwitcherIcon(
                icon = Icons.Outlined.MoreHoriz,
                label = tr("管理这个盘", "Manage this wheel"),
                enabled = enabled,
                active = managing || renaming || confirming,
                onClick = {
                    val open = !(managing || renaming || confirming)
                    managing = open
                    renaming = false
                    confirming = false
                },
            )
        }

        AnimatedVisibility(
            visible = managing && !renaming && !confirming,
            enter = if (reduced) XpMotion.enterFade() else XpMotion.enterRise(0.3f),
            exit = XpMotion.exitFade(),
        ) {
            Row(
                Modifier.padding(top = XpSpace.s),
                horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            ) {
                XpButton(
                    text = tr("重命名", "Rename"),
                    onClick = {
                        draft = wheels[current].name
                        managing = false
                        renaming = true
                    },
                    style = XpButtonStyle.Secondary,
                    size = XpButtonSize.Small,
                    enabled = enabled,
                )
                XpButton(
                    text = tr("删除这个盘", "Delete this wheel"),
                    onClick = {
                        managing = false
                        confirming = true
                    },
                    style = XpButtonStyle.Secondary,
                    size = XpButtonSize.Small,
                    enabled = enabled,
                )
            }
        }

        if (renaming) {
            Spacer(Modifier.height(XpSpace.s))
            InlinePanel {
                Text(
                    tr("给它起个名字", "Name this wheel"),
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.InkSecondary,
                )
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(20) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink),
                    cursorBrush = SolidColor(XpColors.Accent),
                    modifier = Modifier.fillMaxWidth().padding(top = XpSpace.s)
                        .clip(RoundedCornerShape(XpRadius.s))
                        .background(XpColors.Fill)
                        .padding(horizontal = XpSpace.m, vertical = 10.dp),
                )
                Row(Modifier.padding(top = XpSpace.m), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    XpButton(
                        text = tr("保存", "Save"),
                        onClick = {
                            if (draft.isNotBlank()) onRename(draft.trim())
                            renaming = false
                        },
                        size = XpButtonSize.Small,
                    )
                    XpButton(
                        text = tr("取消", "Cancel"),
                        onClick = { renaming = false },
                        style = XpButtonStyle.Secondary,
                        size = XpButtonSize.Small,
                    )
                }
            }
        }

        if (confirming) {
            Spacer(Modifier.height(XpSpace.s))
            InlinePanel {
                Text(
                    tr("删除「${wheels[current].name}」？", "Delete \u201C${wheels[current].name}\u201D?"),
                    style = MaterialTheme.typography.titleSmall,
                    color = XpColors.Ink,
                )
                Text(
                    tr("盘面上的字会一起删掉，其它盘不受影响。", "Its slices go with it. Your other wheels stay."),
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(Modifier.padding(top = XpSpace.m), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    XpButton(
                        text = tr("删除", "Delete"),
                        onClick = {
                            confirming = false
                            onDelete()
                        },
                        style = XpButtonStyle.Destructive,
                        size = XpButtonSize.Small,
                    )
                    XpButton(
                        text = tr("留着", "Keep it"),
                        onClick = { confirming = false },
                        style = XpButtonStyle.Secondary,
                        size = XpButtonSize.Small,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlinePanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.contentCard))
            .background(XpColors.Surface2)
            .padding(XpSpace.m),
        content = content,
    )
}

private val SwitcherInset = 3.dp
private val SwitcherTabHeight = 34.dp

/** 这只盘的缩影：按它的格数切开，用盘面同一套颜色。没选中时淡一些。 */
@Composable
private fun MiniWheel(count: Int, palette: List<Color>, faded: Boolean) {
    val ring = XpColors.Card
    Canvas(Modifier.size(16.dp).alpha(if (faded) 0.6f else 1f)) {
        val sweep = 360f / count
        for (i in 0 until count) {
            drawArc(sliceColor(palette, i), startAngle = -90f + i * sweep, sweepAngle = sweep, useCenter = true)
        }
        drawCircle(ring, radius = size.minDimension * 0.16f)
    }
}

@Composable
private fun SwitcherIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(SwitcherTabHeight + SwitcherInset * 2).clip(CircleShape)
            .background(if (active) XpColors.AccentSoft else XpColors.Fill)
            .pressableClick(enabled = enabled, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> XpColors.InkTertiary
                active -> XpColors.Accent
                else -> XpColors.InkSecondary
            },
            modifier = Modifier.size(20.dp),
        )
    }
}
