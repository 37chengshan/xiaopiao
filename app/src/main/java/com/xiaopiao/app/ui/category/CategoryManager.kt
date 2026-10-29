package com.xiaopiao.app.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.CategoryRepository
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.NavTextAction
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val RowHeight = 56.dp

/**
 * 分类管理 (tt#71): every category with its colour, long-press drag to reorder, the eye hides a
 * built-in, the pencil edits or deletes a custom one, 「＋ 新建」 in the header. Reached from the record
 * sheet's 「自定义」 cell — a category is created while recording, not from a settings maze.
 */
@Composable
fun CategoryManagerScreen(categories: CategoryRepository, onDismiss: () -> Unit) {
    val defs = rememberCategoryDefs(categories)
    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    val local = remember { mutableStateListOf<CategoryDef>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // null = editor closed, otherwise the row being edited (def == null means 「新建」).
    var editor by remember { mutableStateOf<CategoryEditorTarget?>(null) }
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }

    // Mirror the flow unless a drag is in flight, so a reorder write does not fight the finger.
    LaunchedEffect(defs) {
        if (draggingId == null) {
            local.clear()
            local.addAll(defs)
        }
    }

    NavScaffold(
        title = "分类",
        scroll = NavScrollNone,
        onBack = onDismiss,
        trailing = { NavTextAction("＋ 新建", onClick = { editor = CategoryEditorTarget(null) }) },
    ) { top ->
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = XpSpace.page, end = XpSpace.page, top = top + XpSpace.xs, bottom = XpSpace.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            itemsIndexed(local, key = { _, def -> def.id }) { _, def ->
                val isDragging = draggingId == def.id
                CategoryRow(
                    def = def,
                    dragging = isDragging,
                    onHide = {
                        haptics.tick()
                        scope.launch { categories.setHidden(def.id, !def.hidden) }
                    },
                    onEdit = { editor = CategoryEditorTarget(def) },
                    dragModifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .pointerInput(def.id, local.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.thud()
                                    draggingId = def.id
                                    dragOffset = 0f
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    dragOffset += drag.y
                                    val steps = (dragOffset / rowPx).roundToInt()
                                    if (steps != 0) {
                                        val from = local.indexOfFirst { it.id == def.id }
                                        val to = (from + steps).coerceIn(0, local.lastIndex)
                                        if (from >= 0 && to != from) {
                                            local.removeAt(from)
                                            local.add(to, def)
                                            dragOffset -= steps * rowPx
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                    scope.launch { categories.reorder(local.map { it.id }) }
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                            )
                        },
                )
            }
            item {
                Text(
                    "长按拖动排序 · 眼睛隐藏内置分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkTertiary,
                    modifier = Modifier.padding(top = XpSpace.s, start = XpSpace.xs),
                )
            }
        }
    }

    editor?.let { target ->
        CategoryEditorSheet(
            categories = categories,
            initial = target.def,
            onDone = { editor = null },
        )
    }
}

/** Wrapper so the editor can be opened for 「新建」 (def == null) as well as for an existing row. */
private data class CategoryEditorTarget(val def: CategoryDef?)

@Composable
private fun CategoryRow(
    def: CategoryDef,
    dragging: Boolean,
    onHide: () -> Unit,
    onEdit: () -> Unit,
    dragModifier: Modifier,
) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight).clip(RoundedCornerShape(XpRadius.contentCard))
            .background(if (dragging) XpColors.Surface2 else XpColors.Card)
            .padding(horizontal = XpSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Long-press anywhere on the handle (28dp) starts the reorder; the glyph advertises it.
        Box(dragModifier.size(width = 28.dp, height = RowHeight), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = "拖动排序",
                tint = XpColors.InkTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
        CategoryTile(def, size = 34.dp)
        Spacer(Modifier.width(XpSpace.m))
        Column(Modifier.weight(1f)) {
            Text(
                def.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = if (def.hidden) XpColors.InkSecondary else XpColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (def.hidden) {
                Text("已隐藏", style = MaterialTheme.typography.bodySmall, color = XpColors.InkTertiary, maxLines = 1)
            }
        }
        if (def.builtIn) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(XpRadius.pill)).pressableClick(haptic = false, onClick = onHide),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (def.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (def.hidden) "显示「${def.label}」" else "隐藏「${def.label}」",
                    tint = XpColors.InkSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(XpRadius.pill)).pressableClick(haptic = false, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑「${def.label}」",
                    tint = XpColors.InkSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Full-screen host used from the record sheet (a Dialog, like 卡包, so it can cover the tab bar). */
@Composable
fun CategoryManagerDialog(categories: CategoryRepository, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CategoryManagerScreen(categories, onDismiss)
    }
}
