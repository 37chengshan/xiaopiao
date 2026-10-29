@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaopiao.app.ui.circle

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 分享到圈子（docs/APP-DESIGN.md §5.1）：multi-select circles, with the exact list of items that would
 * become visible in each one. The sheet never decides masking itself — the post is stored once and every
 * reader re-masks it through the author's profile, so the summary here is a preview, not the rule.
 *
 * Pass [post] to have the sheet append a masked card to the chosen circles via [shareReceiptTo]; pass
 * [onSelected] when the caller wants the ids and will post itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleSendSheet(
    book: CircleBook,
    viewerId: String,
    onBookChange: (CircleBook) -> Unit,
    onClose: () -> Unit,
    post: ReceiptPost? = null,
    onSelected: ((Set<String>) -> Unit)? = null,
) {
    val targets = remember(book, viewerId) {
        shareTargets(book, viewerId).filter { target -> book.byId(target.circleId)?.member(viewerId) != null }
    }
    var selected by remember(book.currentId) {
        mutableStateOf(
            setOfNotNull(book.currentId.takeIf { id -> targets.any { it.circleId == id } }),
        )
    }
    val haptics = rememberXpHaptics()

    XpSheet(onDismiss = onClose) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.page)
                .padding(bottom = XpSpace.l),
        ) {
            CircleSheetHeader(
                title = stringResource(R.string.circle_send_title),
                subtitle = stringResource(R.string.circle_send_subtitle),
            )

            if (targets.isEmpty()) {
                Text(
                    stringResource(R.string.circle_send_no_circles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = XpColors.InkSecondary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    targets.forEach { target ->
                        val shares = book.byId(target.circleId)?.sharesOf(viewerId) ?: CircleShare.defaults()
                        CircleShareTargetRow(
                            target = target,
                            shares = shares,
                            isSelected = target.circleId in selected,
                            onToggle = {
                                selected = if (target.circleId in selected) {
                                    selected - target.circleId
                                } else {
                                    selected + target.circleId
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(XpSpace.l))
            XpButton(
                text = if (selected.isEmpty()) {
                    stringResource(R.string.circle_send_confirm_empty)
                } else {
                    stringResource(R.string.circle_send_confirm, selected.size)
                },
                onClick = {
                    val ids = selected
                    if (ids.isEmpty()) return@XpButton
                    haptics.confirm()
                    val now = System.currentTimeMillis()
                    if (post != null) {
                        // A fresh id per share keeps feed keys unique when the same receipt is posted twice.
                        onBookChange(shareReceiptTo(book, ids, post.copy(id = "${post.id}-$now"), now))
                    }
                    onSelected?.invoke(ids)
                    onClose()
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            CircleNote(stringResource(R.string.circle_send_footer))
            Spacer(Modifier.height(XpSpace.s))
        }
    }
}

@Composable
private fun CircleShareTargetRow(
    target: ShareTarget,
    shares: Map<CircleShare, Boolean>,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val semanticsModifier = Modifier.semantics {
        selected = isSelected
        role = Role.Checkbox
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.m))
            .background(if (isSelected) XpColors.AccentSoft else XpColors.Surface2)
            .then(semanticsModifier)
            .pressableClick(scaleDown = 0.98f, onClick = onToggle)
            .padding(XpSpace.l),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 1.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) XpColors.Accent else XpColors.Fill),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = XpColors.OnAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    target.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = XpColors.Ink,
                    maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(XpSpace.s))
                Text(
                    stringResource(R.string.circle_send_members, target.memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    maxLines = 1,
                )
            }
            Text(
                shareSummary(shares),
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** "会公开：今日总额、分类构成；单笔明细不公开" — every visible item named, every hidden one named. */
@Composable
private fun shareSummary(shares: Map<CircleShare, Boolean>): String {
    val separator = stringResource(R.string.circle_send_separator)
    val visible = CircleShare.entries.filter { shares[it] == true }
    if (visible.isEmpty()) return stringResource(R.string.circle_send_nothing)
    val shown = visible.map { shareLabel(it) }.joinToString(separator)
    val hidden = CircleShare.entries.filter { shares[it] != true }
    val base = stringResource(R.string.circle_send_public, shown)
    if (hidden.isEmpty()) return base
    val hiddenLabels = hidden.map { shareLabel(it) }.joinToString(separator)
    return base + stringResource(R.string.circle_send_hidden, hiddenLabels)
}
