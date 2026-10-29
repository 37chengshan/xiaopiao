package com.xiaopiao.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.parse.ParsedEntry
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.SelectCapsule
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

private data class DraftItem(val id: Int, val entry: ParsedEntry)

/**
 * Confirm cards for a parsed sentence. Nothing is written from here: the parent receives the
 * (possibly edited) entries only when 「确认记账」 is tapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConfirmEntries(
    parsed: List<ParsedEntry>,
    onConfirm: (List<ParsedEntry>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberXpHaptics()
    val drafts = remember(parsed) {
        parsed.mapIndexed { index, entry -> DraftItem(index, entry) }.toMutableStateList()
    }
    var expandedId by remember { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        drafts.toList().forEach { draft ->
            XpCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(XpRadius.s)).background(XpColors.Fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(draft.entry.category.emoji, fontSize = 17.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            draft.entry.merchant,
                            style = MaterialTheme.typography.titleSmall,
                            color = XpColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(Modifier.padding(top = 4.dp)) {
                            SelectCapsule(
                                "${draft.entry.category.emoji} ${draft.entry.category.label}",
                                selected = false,
                                onClick = {
                                    expandedId = if (expandedId == draft.id) null else draft.id
                                },
                            )
                        }
                    }
                    MoneyText(
                        draft.entry.amountCents,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (draft.entry.direction == Direction.IN) XpColors.Positive else XpColors.Ink,
                        prefix = if (draft.entry.direction == Direction.IN) "+" else "",
                    )
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(XpRadius.pill))
                            .pressableClick(scaleDown = 0.92f) {
                                drafts.removeAll { it.id == draft.id }
                                if (expandedId == draft.id) expandedId = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "删除", tint = XpColors.InkTertiary, modifier = Modifier.size(18.dp))
                    }
                }
                if (expandedId == draft.id) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = XpSpace.m),
                        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
                    ) {
                        Category.entries.forEach { category ->
                            SelectCapsule(
                                "${category.emoji} ${category.label}",
                                selected = category == draft.entry.category,
                                onClick = {
                                    val index = drafts.indexOfFirst { it.id == draft.id }
                                    if (index >= 0) {
                                        drafts[index] = draft.copy(entry = draft.entry.copy(category = category))
                                    }
                                    expandedId = null
                                },
                            )
                        }
                    }
                }
            }
        }

        if (drafts.isEmpty()) {
            Text(
                "没有要记的账了",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(vertical = XpSpace.s),
            )
        } else {
            val spent = drafts.filter { it.entry.direction == Direction.OUT }.sumOf { it.entry.amountCents }
            val earned = drafts.filter { it.entry.direction == Direction.IN }.sumOf { it.entry.amountCents }
            Row(
                Modifier.fillMaxWidth().padding(top = XpSpace.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("共 ${drafts.size} 笔 · 支出", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                MoneyText(spent, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            }
            if (earned > 0L) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("收入", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                    MoneyText(earned, style = MaterialTheme.typography.titleLarge, color = XpColors.Positive, prefix = "+")
                }
            }
        }

        XpButton(
            text = "确认记账",
            onClick = {
                haptics.confirm()
                onConfirm(drafts.map { it.entry })
            },
            modifier = Modifier.fillMaxWidth().padding(top = XpSpace.xs),
            enabled = drafts.isNotEmpty(),
        )
    }
}
