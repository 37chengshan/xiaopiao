package com.xiaopiao.app.ui.games.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.motion.XpMotion
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val EditorRowHeight = 56.dp

/**
 * 编辑模式（tt: 编辑扇面难用）。
 *
 * 一行一个选项：颜色点（和扇面同色）＋ 就地输入 ＋ 拖动把手 ＋ ✕；左滑也能删（删完有撤销条）。
 * 结束处是「添加选项」，2–12 个自动夹住，越界时给一行看得见的提示而不是静默拒绝。
 * 输入立刻回到上面的盘面——不用保存、不用确认。
 */
@Composable
internal fun WheelEditor(
    wheel: Wheel,
    palette: List<Color>,
    onSliceChange: (Int, String) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onPreset: (WheelPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberXpHaptics()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { EditorRowHeight.toPx() }
    val full = wheel.slices.size >= MaxWheelSlices

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.xs, vertical = XpSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr("选项", "Slices"),
                style = MaterialTheme.typography.titleSmall,
                color = XpColors.Ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${wheel.slices.size} / $MaxWheelSlices",
                style = MaterialTheme.typography.labelMedium,
                color = if (full) XpColors.Warning else XpColors.InkSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
            wheel.slices.forEachIndexed { index, value ->
                val dragging = draggingIndex == index
                WheelSliceRow(
                    index = index,
                    value = value,
                    dot = sliceColor(palette, index),
                    enabled = draggingIndex == null,
                    dragging = dragging,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                    onChange = { onSliceChange(index, it) },
                    onDelete = { onDelete(index) },
                    dragModifier = Modifier.pointerInput(wheel.slices.size, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.thud()
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                dragOffset += drag.y
                                val steps = (dragOffset / rowPx).roundToInt()
                                if (steps != 0) {
                                    val to = (index + steps).coerceIn(0, wheel.slices.lastIndex)
                                    if (to != index) {
                                        onReorder(index, to)
                                        draggingIndex = to
                                        dragOffset -= (to - index) * rowPx
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                        )
                    },
                )
            }

            AddSliceRow(enabled = !full, onClick = onAdd)
        }

        Text(
            when {
                wheel.slices.size < MinWheelSlices -> tr("至少要 2 个选项才能转", "Two slices minimum to spin")
                full -> tr("已经到 12 个上限，想加就得先删一个", "12 is the limit — remove one to add another")
                else -> tr("拖动把手可以换顺序，左滑删一格", "Drag the handle to reorder, swipe left to remove")
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (wheel.slices.size < MinWheelSlices) XpColors.Warning else XpColors.InkTertiary,
            modifier = Modifier.padding(start = XpSpace.xs, top = XpSpace.s),
        )

        Spacer(Modifier.height(XpSpace.l))
        Text(
            tr("从模板开始", "Start from a preset"),
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(start = XpSpace.xs, bottom = XpSpace.s),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.xs),
        ) {
            WheelPresets.all.forEach { preset ->
                Text(
                    preset.title(),
                    style = MaterialTheme.typography.labelLarge,
                    color = XpColors.Ink,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(XpRadius.pill))
                        .background(XpColors.Fill)
                        .pressableClick(haptic = false) { onPreset(preset) }
                        .padding(horizontal = XpSpace.m, vertical = 7.dp),
                )
            }
        }
        Text(
            tr("模板会新建一个盘，不动你现在这个。", "A preset adds a new wheel; this one stays as it is."),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
            modifier = Modifier.padding(start = XpSpace.xs, top = XpSpace.s),
        )
    }
}

@Composable
private fun WheelSliceRow(
    index: Int,
    value: String,
    dot: Color,
    enabled: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    val swipe = remember { Animatable(0f) }
    val swipeWidth = with(LocalDensity.current) { 88.dp.toPx() }
    val shape = RoundedCornerShape(XpRadius.contentCard)

    Box(modifier.fillMaxWidth()) {
        // The red bed under the row: only visible once the row has been pulled left.
        Box(
            Modifier.matchParentSize().clip(shape).background(XpColors.Negative),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(end = XpSpace.l).size(18.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .height(EditorRowHeight)
                .graphicsLayer { translationX = swipe.value }
                .clip(shape)
                .background(if (dragging) XpColors.Surface2 else XpColors.Card)
                .pointerInput(index) {
                    var x = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { x = swipe.value },
                        onDragEnd = {
                            if (-x > swipeWidth * 0.6f) {
                                haptics.reject()
                                onDelete()
                            }
                            scope.launch { swipe.snapTo(0f) }
                        },
                        onDragCancel = { scope.launch { swipe.snapTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        x = (x + dx).coerceIn(-swipeWidth, 0f)
                        val next = x
                        scope.launch { swipe.snapTo(next) }
                    }
                }
                .padding(start = XpSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.InkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = XpSpace.s).size(width = 18.dp, height = 18.dp),
            )
            Box(Modifier.weight(1f).padding(start = XpSpace.xs)) {
                if (value.isEmpty()) {
                    Text(
                        tr("填这一格", "Fill this slice"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = XpColors.InkTertiary,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink),
                    cursorBrush = SolidColor(XpColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .pressableClick(enabled = enabled, haptic = false, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = tr("删掉这一格", "Remove this slice"),
                    tint = XpColors.InkTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(dragModifier.size(width = 40.dp, height = EditorRowHeight), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.DragHandle,
                    contentDescription = tr("拖动排序", "Drag to reorder"),
                    tint = XpColors.InkTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddSliceRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(XpRadius.contentCard))
            .background(XpColors.Fill.copy(alpha = if (enabled) 0.75f else 0.3f))
            .pressableClick(enabled = enabled, haptic = false, onClick = onClick)
            .padding(horizontal = XpSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = null,
            tint = if (enabled) XpColors.Accent else XpColors.InkTertiary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            tr("添加选项", "Add a slice"),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) XpColors.Ink else XpColors.InkTertiary,
            modifier = Modifier.padding(start = XpSpace.s),
        )
    }
}
