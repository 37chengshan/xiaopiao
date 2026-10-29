package com.xiaopiao.app.ui.category

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.CategoryHue
import com.xiaopiao.app.domain.CategoryRepository
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.ui.components.ConfirmSheet
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch

/**
 * 新建 / 编辑分类 (tt#71): name, one of ~30 icons, one of the 12 colours, and who it is offered to
 * (支出 / 收入 / 都记录). Custom rows can be deleted; their rows move to 其他, which the confirm says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryEditorSheet(
    categories: CategoryRepository,
    initial: CategoryDef?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: CategoryIconCatalog.defaultKey) }
    var hue by remember { mutableStateOf(CategoryHue.of(initial?.hue ?: CategoryHue.ORANGE.key)) }
    var direction by remember { mutableStateOf(initial?.direction) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var deleteCount by remember { mutableStateOf(0) }

    val preview = CategoryDef(
        id = initial?.id ?: "preview",
        label = label.ifBlank { "新分类" },
        iconKey = iconKey,
        hue = hue.key,
        direction = direction,
        builtIn = initial?.builtIn ?: false,
        sortOrder = initial?.sortOrder ?: 0,
    )
    val canSave = label.isNotBlank()

    XpSheet(onDismiss = onDone) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.page)
                .padding(bottom = XpSpace.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(preview, size = 44.dp)
                Spacer(Modifier.width(XpSpace.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (initial == null) "新建分类" else "编辑分类",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        color = XpColors.Ink,
                        maxLines = 1,
                    )
                    Text(
                        "名称、图标和颜色随时可改",
                        style = MaterialTheme.typography.bodySmall,
                        color = XpColors.InkSecondary,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(XpSpace.l))
            BasicTextField(
                value = label,
                onValueChange = { label = it.take(CategoryDefaults.MAX_LABEL) },
                modifier = Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(XpRadius.m))
                    .background(XpColors.Fill).padding(horizontal = XpSpace.m),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(XpColors.Accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (label.isEmpty()) {
                            Text("分类名称", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = XpColors.InkTertiary)
                        }
                        inner()
                    }
                },
            )

            Spacer(Modifier.height(XpSpace.l))
            Label("图标")
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxWidth().height(44.dp * 4),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(CategoryIconCatalog.entries, key = { it.key }) { entry ->
                    val selected = entry.key == iconKey
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(XpRadius.s))
                            .background(if (selected) XpColors.AccentSoft else XpColors.Fill)
                            .pressableClick(haptic = false, scaleDown = 0.92f) { iconKey = entry.key },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = null,
                            tint = if (selected) XpColors.Accent else XpColors.InkSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(XpSpace.m))
            Label("颜色")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            ) {
                CategoryHue.pickable.forEach { option ->
                    val selected = option.key == hue.key
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(if (XpColors.isDark) androidx.compose.ui.graphics.Color(option.dark) else androidx.compose.ui.graphics.Color(option.light))
                            .pressableClick(haptic = false, scaleDown = 0.9f) { hue = option },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(XpSpace.l))
            Label("收支")
            Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                DirectionChip("支出", direction == Direction.OUT) { direction = Direction.OUT }
                DirectionChip("收入", direction == Direction.IN) { direction = Direction.IN }
                DirectionChip("都记录", direction == null) { direction = null }
            }

            Spacer(Modifier.height(XpSpace.xl))
            XpButton(
                text = if (initial == null) "新建分类" else "保存",
                onClick = {
                    haptics.confirm()
                    scope.launch {
                        if (initial == null) {
                            categories.create(label.trim(), iconKey, hue, direction)
                        } else {
                            categories.update(initial.copy(label = label.trim(), iconKey = iconKey, hue = hue.key, direction = direction))
                        }
                    }
                    onDone()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            )
            if (initial != null && initial.isCustom) {
                TextButton(
                    onClick = {
                        scope.launch { deleteCount = categories.countTxns(initial.id) }
                        confirmingDelete = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除分类", color = XpColors.Negative, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (confirmingDelete && initial != null) {
        ConfirmSheet(
            visible = true,
            title = "删除「${initial.label}」？",
            message = if (deleteCount > 0) {
                "这些账目会归到 其他，金额不会变。"
            } else {
                "这个分类下还没有账目。"
            },
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                scope.launch { categories.deleteCustom(initial.id) }
                confirmingDelete = false
                onDone()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = XpColors.InkSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = XpSpace.s),
    )
}

@Composable
private fun DirectionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(XpRadius.pill))
            .background(if (selected) XpColors.AccentSoft else XpColors.Fill)
            .pressableClick(haptic = false, scaleDown = 0.95f, onClick = onClick)
            .padding(horizontal = XpSpace.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) XpColors.Accent else XpColors.Ink,
            maxLines = 1,
        )
    }
}
