package com.xiaopiao.app.ui.games.wheel

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
 * 多个盘的切换器。重命名与删除都**就地展开**（不用系统对话框）：删除先展开一句后果说明，
 * 再按一次才真删；转的时候整组不可点。
 */
@Composable
internal fun WheelSwitcher(
    wheels: List<Wheel>,
    current: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            wheels.forEachIndexed { index, wheel ->
                val on = index == current
                Text(
                    wheel.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) XpColors.OnAccent else XpColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(XpRadius.pill))
                        .background(if (on) XpColors.Accent else XpColors.Fill)
                        .pressableClick(enabled = enabled && !on, haptic = false) { onSelect(index) }
                        .padding(horizontal = XpSpace.m, vertical = 7.dp),
                )
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(XpColors.Fill)
                    .pressableClick(enabled = enabled, haptic = false, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = tr("新建转盘", "New wheel"),
                    tint = if (enabled) XpColors.InkSecondary else XpColors.InkTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (!renaming && !confirming) {
            Spacer(Modifier.height(XpSpace.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                Text(
                    tr("重命名", "Rename"),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) XpColors.InkSecondary else XpColors.InkTertiary,
                    modifier = Modifier
                        .pressableClick(enabled = enabled, haptic = false) {
                            draft = wheels[current].name
                            renaming = true
                        }
                        .padding(vertical = 4.dp),
                )
                Text(
                    tr("删除这个盘", "Delete this wheel"),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) XpColors.Negative else XpColors.InkTertiary,
                    modifier = Modifier
                        .pressableClick(enabled = enabled, haptic = false) { confirming = true }
                        .padding(vertical = 4.dp),
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
