package com.xiaopiao.app.ui.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.DemoChip
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/** One feed row: receipt card, text bubble or centred system message. */
@Composable
fun CirclePostRow(
    post: CirclePost,
    author: CircleMember?,
    now: Long,
    mineReactions: Set<String>,
    onToggleReaction: (postId: String, emoji: String) -> Unit,
    onMemberClick: (memberId: String) -> Unit,
    modifier: Modifier = Modifier,
    onReceiptOpen: (ReceiptPost) -> Unit = {},
) {
    when (post) {
        is ReceiptPost -> if (author != null) {
            ReceiptMessage(post, author, now, mineReactions, onToggleReaction, onMemberClick, modifier) { onReceiptOpen(post) }
        }
        is TextPost -> if (author != null) {
            TextMessage(post, author, onMemberClick, modifier)
        }
        is SystemPost -> SystemMessage(post, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptMessage(
    post: ReceiptPost,
    author: CircleMember,
    now: Long,
    mineReactions: Set<String>,
    onToggleReaction: (String, String) -> Unit,
    onMemberClick: (String) -> Unit,
    modifier: Modifier,
    onReceiptOpen: () -> Unit = {},
) {
    val view = remember(post, author.shares) { post.viewFor(author.shares) }
    val mine = author.isMe
    Row(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            CircleAvatar(author, size = 30.dp, onClick = { onMemberClick(author.id) })
            Spacer(Modifier.width(XpSpace.s))
        }
        Column(if (mine) Modifier.widthIn(max = 300.dp) else Modifier.weight(1f)) {
            Text(
                "${author.name} · ${timeLabel(post.at)}",
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(start = XpSpace.xs, bottom = XpSpace.xs),
            )
            TornReceiptCard(onClick = onReceiptOpen) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${dayLabel(post.dayStart, now)}的小票",
                            style = MaterialTheme.typography.labelSmall,
                            color = XpColors.InkSecondary,
                        )
                        Spacer(Modifier.height(XpSpace.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (view.totalCents != null) {
                                MoneyText(
                                    view.totalCents,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = XpColors.Ink,
                                )
                            } else {
                                MoneyText(
                                    amount = " ···",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = XpColors.InkSecondary,
                                )
                                Spacer(Modifier.width(6.dp))
                                CircleChip("未共享")
                            }
                        }
                        if (post.items.isEmpty() && view.totalCents != null) {
                            Text(
                                "今天钱包休息",
                                style = MaterialTheme.typography.bodySmall,
                                color = XpColors.InkSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    DemoChip()
                }

                Spacer(Modifier.height(XpSpace.s + 2.dp))
                val slices = view.slices
                when {
                    slices == null -> CircleChip("分类未共享")
                    slices.isEmpty() -> CircleChip("今天没有支出")
                    else -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        slices.take(3).forEach { CircleChip("${it.category.label} ${it.percent}%") }
                        if (slices.size > 3) CircleChip("+${slices.size - 3}")
                    }
                }

                val detail = view.detail
                if (detail != null && detail.isNotEmpty()) {
                    Spacer(Modifier.height(XpSpace.s + 2.dp))
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(XpRadius.s))
                            .background(XpColors.Surface2)
                            .padding(horizontal = XpSpace.s + 2.dp, vertical = XpSpace.s),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        detail.take(4).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.merchant,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = XpColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                MoneyText(
                                    item.cents,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = XpColors.InkSecondary,
                                )
                            }
                        }
                        if (detail.size > 4) {
                            Text(
                                "还有 ${detail.size - 4} 笔",
                                style = MaterialTheme.typography.labelSmall,
                                color = XpColors.InkTertiary,
                            )
                        }
                    }
                } else if (view.totalCents != null) {
                    Spacer(Modifier.height(XpSpace.s + 2.dp))
                    CircleChip("单笔明细 未共享")
                }

                val full = view.full
                if (full != null && full.isNotEmpty()) {
                    Spacer(Modifier.height(XpSpace.s + 2.dp))
                    ReceiptBody(post)
                } else if (author.shows(CircleShare.ReceiptFull)) {
                    Spacer(Modifier.height(XpSpace.s + 2.dp))
                    CircleChip("小票全文要先开单笔明细")
                }

                Spacer(Modifier.height(XpSpace.s + 2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    reactionsFor(post.id, mineReactions).forEach { reaction ->
                        val tone = if (reaction.mine) XpColors.AccentSoft else XpColors.Fill
                        Row(
                            Modifier.clip(RoundedCornerShape(XpRadius.pill)).background(tone)
                                .pressableClick(scaleDown = 0.92f) { onToggleReaction(post.id, reaction.emoji) }
                                .padding(horizontal = XpSpace.s, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(reaction.emoji, style = MaterialTheme.typography.labelSmall)
                            if (reaction.count > 0) {
                                Text(
                                    " ${reaction.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (reaction.mine) XpColors.Accent else XpColors.InkSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Monospace 小票全文 block, only rendered when the author shares it (and detail + total are on). */
@Composable
private fun ReceiptBody(post: ReceiptPost) {
    val mono = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
    val serial = (post.id.hashCode() and 0x7fffffff) % 9000 + 1000
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.s))
            .background(XpColors.Surface2)
            .padding(horizontal = XpSpace.s + 2.dp, vertical = XpSpace.s),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("NO.$serial · ${post.items.size} 笔", style = mono, color = XpColors.InkSecondary)
        Text("------------------------", style = mono, color = XpColors.InkTertiary, maxLines = 1)
        post.items.forEach { item ->
            Row(Modifier.fillMaxWidth()) {
                Text(item.merchant, style = mono, color = XpColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text(item.cents.yuan(), style = mono, color = XpColors.Ink)
            }
        }
        Text("------------------------", style = mono, color = XpColors.InkTertiary, maxLines = 1)
        Row(Modifier.fillMaxWidth()) {
            Text("合计", style = mono, color = XpColors.Ink)
            Spacer(Modifier.weight(1f))
            Text("¥${post.totalCents.yuan()}", style = mono, color = XpColors.Ink)
        }
        Text("谢谢惠顾 · 本机演示", style = mono, color = XpColors.InkTertiary, maxLines = 1)
    }
}

@Composable
private fun TextMessage(
    post: TextPost,
    author: CircleMember,
    onMemberClick: (String) -> Unit,
    modifier: Modifier,
) {
    val mine = author.isMe
    Row(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            CircleAvatar(author, size = 30.dp, onClick = { onMemberClick(author.id) })
            Spacer(Modifier.width(XpSpace.s))
        }
        Column(
            Modifier.widthIn(max = 268.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            if (!mine) {
                Text(
                    author.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkTertiary,
                    modifier = Modifier.padding(start = XpSpace.xs, bottom = 3.dp),
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(XpRadius.l))
                    .background(if (mine) XpColors.AccentSoft else XpColors.Fill)
                    .padding(horizontal = XpSpace.m, vertical = XpSpace.s + 1.dp),
            ) {
                Text(post.text, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
            }
            Row(
                Modifier.padding(top = 3.dp, start = if (mine) 0.dp else XpSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(timeLabel(post.at), style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
                if (post.localOnly) CircleChip("仅本机")
            }
        }
        if (mine) {
            Spacer(Modifier.width(XpSpace.s))
            CircleAvatar(author, size = 30.dp, onClick = { onMemberClick(author.id) })
        }
    }
}

@Composable
private fun SystemMessage(post: SystemPost, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.xl, vertical = XpSpace.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(XpRadius.m))
                .background(XpColors.Fill)
                .padding(horizontal = XpSpace.m, vertical = XpSpace.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(post.title, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
            if (post.body.isNotEmpty()) {
                Text(
                    post.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                timeLabel(post.at),
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
fun CircleDayDivider(dayStart: Long, now: Long, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(0.5.dp).background(XpColors.Hairline))
        Text(
            dayLabel(dayStart, now),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
            modifier = Modifier.padding(horizontal = XpSpace.m),
        )
        Box(Modifier.weight(1f).height(0.5.dp).background(XpColors.Hairline))
    }
}
