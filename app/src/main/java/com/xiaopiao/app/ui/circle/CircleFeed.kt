package com.xiaopiao.app.ui.circle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.XpSwitch
import com.xiaopiao.app.ui.components.liquidGlass
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar

// --- one post --------------------------------------------------------------------------------------

/**
 * One 朋友圈-style post (tt 2026-10-29): avatar on the left, everything else in one column to its right —
 * name and time, the words, the day's receipt card, ♥ / 💬, then up to three replies on a soft panel.
 * The receipt card shows only what the author's sharing profile allows (default: total + title).
 */
@Composable
fun CircleFeedPost(
    post: CirclePost,
    circle: Circle,
    viewerId: String,
    now: Long,
    liked: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onReceiptOpen: (ReceiptPost) -> Unit,
    onMemberClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (post is SystemPost) {
        SystemFeedNote(post, modifier)
        return
    }
    val authorId = when (post) {
        is ReceiptPost -> post.authorId
        is TextPost -> post.authorId
        else -> return
    }
    val author = circle.member(authorId)
    val comments = remember(circle.comments, post.id) { circle.comments.filter { it.postId == post.id }.sortedBy { it.at } }
    val likeCount = (baseReactions(post.id)["❤️"] ?: 0) + if (liked) 1 else 0
    val en = LocalConfiguration.current.locales[0].language == "en"

    Row(modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.m)) {
        if (author != null) CircleAvatar(author, size = 40.dp, onClick = { onMemberClick(author.id) })
        else Box(Modifier.size(40.dp).clip(CircleShape).background(XpColors.Fill))
        Spacer(Modifier.width(XpSpace.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    author?.displayName(viewerId, circle.ownerId) ?: (if (en) "Former member" else "已退出的成员"),
                    style = MaterialTheme.typography.titleSmall,
                    color = XpColors.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(relativeTime(post.at, now, en), style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
            }
            val words = when (post) {
                is ReceiptPost -> post.caption
                is TextPost -> post.text
                else -> ""
            }
            if (words.isNotBlank()) {
                Text(
                    words,
                    style = MaterialTheme.typography.bodyLarge,
                    color = XpColors.Ink,
                    modifier = Modifier.padding(top = XpSpace.xs),
                )
            }
            if (post is ReceiptPost) {
                FeedReceiptCard(
                    post = post,
                    shares = circle.sharesOf(post.authorId),
                    title = author?.let { weeklyTitle(circle, it, now) },
                    en = en,
                    modifier = Modifier.padding(top = XpSpace.s),
                    onClick = { onReceiptOpen(post) },
                )
            }
            Row(Modifier.padding(top = XpSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                LikeButton(liked, likeCount, onLike)
                Spacer(Modifier.width(XpSpace.m))
                FeedAction(Icons.Outlined.ChatBubbleOutline, if (comments.isEmpty()) (if (en) "Reply" else "评论") else "${comments.size}", onComment)
            }
            if (comments.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = XpSpace.xs)
                        .clip(RoundedCornerShape(XpRadius.s))
                        .background(XpColors.Fill)
                        .pressableClick(scaleDown = 0.99f, onClick = onComment)
                        .padding(horizontal = XpSpace.m, vertical = XpSpace.s),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    comments.takeLast(3).forEach { c -> CommentLine(c, circle, viewerId, en) }
                    if (comments.size > 3) {
                        Text(
                            if (en) "All ${comments.size} replies" else "查看全部 ${comments.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = XpColors.Accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentLine(c: CircleComment, circle: Circle, viewerId: String, en: Boolean) {
    val name = circle.member(c.authorId)?.displayName(viewerId, circle.ownerId) ?: (if (en) "Former member" else "已退出")
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = XpColors.Accent))
            append(name)
            pop()
            append(if (en) ": " else "：")
            append(c.text)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = XpColors.Ink,
    )
}

/** ♥ pops (spring 0.6 → 1) when it turns on; the count sits beside it. */
@Composable
private fun LikeButton(liked: Boolean, count: Int, onClick: () -> Unit) {
    val haptics = rememberXpHaptics()
    val scale by animateFloatAsState(if (liked) 1f else 0.92f, spring(0.45f, 600f), label = "likePop")
    val tint by animateColorAsState(if (liked) XpColors.Negative else XpColors.InkSecondary, tween(140), label = "likeTint")
    Row(
        Modifier.clip(RoundedCornerShape(XpRadius.pill))
            .pressableClick { haptics.select(); onClick() }
            .padding(horizontal = XpSpace.xs, vertical = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text("$count", style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

@Composable
private fun FeedAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(XpRadius.pill)).pressableClick(onClick = onClick)
            .padding(horizontal = XpSpace.xs, vertical = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = XpColors.InkSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = XpColors.InkSecondary)
    }
}

/**
 * The day's receipt inside a post: torn paper, the total large, the week's title as a stamp. Detail lines
 * appear only when the author shares 单笔明细; otherwise 「N 笔 · 明细未共享」 says why they are missing.
 */
@Composable
private fun FeedReceiptCard(
    post: ReceiptPost,
    shares: Map<CircleShare, Boolean>,
    title: CircleBadge?,
    en: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val view = post.viewFor(shares)
    TornReceiptCard(modifier = modifier.widthInFeed(), onClick = onClick) {
        Text(
            (if (en) "Daily Slip · " else "今日小票 · ") + monthDay(post.dayStart, en),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                view.totalCents?.let { "¥ ${it.yuan()}" } ?: (if (en) "Total not shared" else "总额未共享"),
                style = if (view.totalCents != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                color = if (view.totalCents != null) XpColors.Ink else XpColors.InkTertiary,
                modifier = Modifier.weight(1f),
            )
            if (title != null && shares[CircleShare.WeeklyTitles] == true) {
                Text(
                    "${title.emoji} ${title.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.Stamp,
                    maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(XpRadius.pill))
                        .background(XpColors.Stamp.copy(alpha = 0.10f))
                        .padding(horizontal = XpSpace.s, vertical = 3.dp),
                )
            }
        }
        val detail = view.detail
        if (detail != null && detail.isNotEmpty()) {
            Column(Modifier.padding(top = XpSpace.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                detail.take(4).forEach { item ->
                    Row {
                        Text(item.merchant, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(item.cents.yuan(), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                    }
                }
            }
        } else {
            Text(
                when {
                    post.items.isEmpty() -> if (en) "No spending today" else "今天一笔没花"
                    en -> "${post.items.size} entries · details not shared"
                    else -> "${post.items.size} 笔 · 明细未共享"
                },
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Receipt cards stay receipt-shaped instead of stretching across a tablet. */
private fun Modifier.widthInFeed(): Modifier = this.then(Modifier.fillMaxWidth(0.86f))

@Composable
private fun SystemFeedNote(post: SystemPost, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.xl, vertical = XpSpace.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            post.title,
            style = MaterialTheme.typography.labelMedium,
            color = XpColors.InkSecondary,
            modifier = Modifier.clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
                .padding(horizontal = XpSpace.m, vertical = XpSpace.xs),
        )
    }
}

// --- entries under the header -------------------------------------------------------------------

/**
 * 排行榜 / 颁奖 / 游戏 as one row of capsules (tt: 收进顶部胶囊作为入口). The row stays under the header while the
 * feed scrolls beneath it, so each capsule is liquid glass sampling [backdrop] (the feed, a sibling).
 */
@Composable
fun CircleEntryRow(
    onLeaderboard: () -> Unit,
    onAward: () -> Unit,
    onGames: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val en = LocalConfiguration.current.locales[0].language == "en"
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        EntryCapsule("🏆", if (en) "Leaderboard" else "排行榜", backdrop, onLeaderboard)
        EntryCapsule("🎖", if (en) "This week's titles" else "本周颁奖", backdrop, onAward)
        EntryCapsule("🎮", if (en) "Games" else "小游戏", backdrop, onGames)
    }
}

@Composable
private fun EntryCapsule(emoji: String, label: String, backdrop: Backdrop?, onClick: () -> Unit) {
    Row(
        Modifier.liquidGlass(backdrop, RoundedCornerShape(XpRadius.pill), shadow = 10.dp)
            .clip(RoundedCornerShape(XpRadius.pill))
            .pressableClick(onClick = onClick)
            .padding(horizontal = XpSpace.m, vertical = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = XpColors.Ink)
    }
}

/** 发帖: a glass disc that refracts the feed scrolling under it. */
@Composable
fun CirclePostButton(backdrop: Backdrop?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val en = LocalConfiguration.current.locales[0].language == "en"
    Box(
        modifier.size(56.dp)
            .liquidGlass(
                backdrop,
                CircleShape,
                film = XpColors.Accent.copy(alpha = 0.78f),
                rim = XpColors.Accent.copy(alpha = 0.30f),
                lensHeight = 10.dp,
                lensAmount = 18.dp,
                fallback = XpColors.Accent,
            )
            .pressableClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Edit, contentDescription = if (en) "New post" else "发帖", tint = XpColors.OnPrimary, modifier = Modifier.size(24.dp))
    }
}

// --- compose -------------------------------------------------------------------------------------

/**
 * 发帖 / 发到圈子. Words, and optionally today's receipt (on by default when there is one). The card preview is
 * exactly what members will see under the author's sharing profile, so nothing is revealed by surprise.
 * Posting writes to [store] first and only then closes; [onPosted] gets the circle name for the toast.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CircleComposeSheet(
    store: CircleStore,
    viewerId: String,
    todayReceipt: ReceiptPost?,
    attachByDefault: Boolean,
    onPosted: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val book by store.book.collectAsStateWithLifecycle()
    val en = LocalConfiguration.current.locales[0].language == "en"
    val haptics = rememberXpHaptics()
    var circleId by rememberSaveable { mutableStateOf(book.currentId) }
    var text by rememberSaveable { mutableStateOf("") }
    var attach by rememberSaveable { mutableStateOf(attachByDefault && todayReceipt != null) }
    val circle = book.circles.firstOrNull { it.id == circleId } ?: book.current()
    val canPost = text.isNotBlank() || (attach && todayReceipt != null)

    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l).imePadding()) {
            Text(if (en) "Post to a circle" else "发到圈子", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            if (book.circles.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = XpSpace.m),
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                ) {
                    book.circles.forEach { c ->
                        com.xiaopiao.app.ui.components.SelectCapsule(c.name, c.id == circle.id, onClick = { circleId = c.id })
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = XpSpace.m)
                    .clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Fill)
                    .padding(XpSpace.m),
            ) {
                if (text.isEmpty()) {
                    Text(
                        if (en) "Say something…" else "说点什么…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = XpColors.InkTertiary,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink),
                    cursorBrush = SolidColor(XpColors.Accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = XpSpace.m), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (en) "Attach today's slip" else "附上今天的小票", style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink)
                    Text(
                        if (todayReceipt == null) (if (en) "Nothing recorded today yet" else "今天还没有账")
                        else if (en) "Members see what your sharing settings allow" else "成员只看到你共享设置里允许的部分",
                        style = MaterialTheme.typography.bodySmall,
                        color = XpColors.InkSecondary,
                    )
                }
                XpSwitch(checked = attach && todayReceipt != null, onCheckedChange = { if (todayReceipt != null) attach = it })
            }
            if (attach && todayReceipt != null) {
                val me = circle.member(viewerId)
                FeedReceiptCard(
                    post = todayReceipt,
                    shares = circle.sharesOf(viewerId),
                    title = me?.let { weeklyTitle(circle, it, System.currentTimeMillis()) },
                    en = en,
                    modifier = Modifier.padding(top = XpSpace.s),
                    onClick = {},
                )
            }
            XpButton(
                text = if (en) "Post" else "发布",
                onClick = {
                    val now = System.currentTimeMillis()
                    val words = text.trim()
                    val post: CirclePost = if (attach && todayReceipt != null) {
                        todayReceipt.copy(id = "local-$now", at = now, caption = words)
                    } else {
                        TextPost("local-$now", now, viewerId, words, localOnly = true)
                    }
                    store.post(circle.id, post)
                    haptics.confirm()
                    onPosted(circle.name)
                    onDismiss()
                },
                enabled = canPost,
                modifier = Modifier.fillMaxWidth().padding(top = XpSpace.l),
            )
        }
    }
}

/** Every reply under one post, and a field to add one. A sent reply appears in the list at once. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CircleCommentsSheet(store: CircleStore, circleId: String, postId: String, viewerId: String, onDismiss: () -> Unit) {
    val book by store.book.collectAsStateWithLifecycle()
    val en = LocalConfiguration.current.locales[0].language == "en"
    val circle = book.circles.firstOrNull { it.id == circleId } ?: return
    val comments = circle.comments.filter { it.postId == postId }.sortedBy { it.at }
    var draft by rememberSaveable { mutableStateOf("") }
    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l).imePadding()) {
            Text(
                if (en) "Replies" else "评论" + if (comments.isEmpty()) "" else " ${comments.size}",
                style = MaterialTheme.typography.titleLarge,
                color = XpColors.Ink,
            )
            if (comments.isEmpty()) {
                Text(
                    if (en) "No replies yet. Say the first word." else "还没有评论，说第一句吧。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(vertical = XpSpace.l),
                )
            } else {
                Column(Modifier.padding(vertical = XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                    comments.forEach { c ->
                        Row(verticalAlignment = Alignment.Top) {
                            circle.member(c.authorId)?.let { CircleAvatar(it, size = 28.dp) }
                            Spacer(Modifier.width(XpSpace.s))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    circle.member(c.authorId)?.displayName(viewerId, circle.ownerId) ?: "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = XpColors.Accent,
                                )
                                Text(c.text, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                            }
                            Text(relativeTime(c.at, System.currentTimeMillis(), en), style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
                    .padding(start = XpSpace.l, end = XpSpace.xs, top = XpSpace.xs, bottom = XpSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(if (en) "Reply…" else "回复…", style = MaterialTheme.typography.bodyLarge, color = XpColors.InkTertiary)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(200) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink),
                        cursorBrush = SolidColor(XpColors.Accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val canSend = draft.isNotBlank()
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (canSend) XpColors.Accent else XpColors.Fill)
                        .pressableClick(enabled = canSend) {
                            val now = System.currentTimeMillis()
                            store.comment(circleId, CircleComment("local-c-$now", postId, viewerId, draft.trim(), now))
                            draft = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = if (en) "Send" else "发送",
                        tint = if (canSend) XpColors.OnPrimary else XpColors.InkTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// --- helpers -------------------------------------------------------------------------------------

/** Today's spending as a postable receipt, or null when nothing was recorded today. */
fun todayReceiptPost(txns: List<com.xiaopiao.app.domain.Txn>, viewerId: String, now: Long): ReceiptPost? {
    val today = com.xiaopiao.app.domain.startOfDay(now)
    val items = txns.filter { it.occurredAt >= today && it.direction == com.xiaopiao.app.domain.Direction.OUT }
        .sortedBy { it.occurredAt }
        .map { CircleItem(it.merchant, it.amountCents, it.category) }
    if (items.isEmpty()) return null
    return ReceiptPost("today-$today", now, viewerId, today, items)
}

/** 刚刚 / 5 分钟前 / 2 小时前 / 昨天 21:10 / 10月27日. */
fun relativeTime(at: Long, now: Long, en: Boolean = false): String {
    val ago = (now - at).coerceAtLeast(0)
    val cal = Calendar.getInstance().apply { timeInMillis = at }
    val today = com.xiaopiao.app.domain.startOfDay(now)
    val hm = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    return when {
        ago < 60_000 -> if (en) "just now" else "刚刚"
        ago < 3_600_000 -> if (en) "${ago / 60_000}m" else "${ago / 60_000} 分钟前"
        at >= today -> if (en) "${ago / 3_600_000}h" else "${ago / 3_600_000} 小时前"
        at >= today - 86_400_000 -> if (en) "Yesterday $hm" else "昨天 $hm"
        else -> monthDay(at, en)
    }
}

private fun monthDay(at: Long, en: Boolean): String {
    val cal = Calendar.getInstance().apply { timeInMillis = at }
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return if (en) "$m/$d" else "${m}月${d}日"
}
