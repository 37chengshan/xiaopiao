package com.xiaopiao.app.ui.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.games.GameContent
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.motion.XpPushHost
import com.xiaopiao.app.ui.games.GamesHub
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

private val ComposerHeight = 64.dp

/**
 * 圈子 tab: one family = one group chat (docs/APP-DESIGN.md §5). Every member, message and amount is
 * local demo data — no account, no network, no ledger reads — so the whole tab is marked 演示数据 and
 * each receipt card carries its own marker.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun CircleHome(container: AppContainer, onOpenSettings: () -> Unit = {}, openGames: Int = 0) {
    val now = remember { System.currentTimeMillis() }
    var members by remember { mutableStateOf(CircleDemo.members()) }
    var posts by remember { mutableStateOf(CircleDemo.feed(now)) }
    var mineReactions by remember { mutableStateOf(emptySet<String>()) }
    var shareSheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var gamesOpen by rememberSaveable { mutableStateOf(false) }
    var openedPost by remember { mutableStateOf<ReceiptPost?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var localSeq by rememberSaveable { mutableStateOf(0) }
    var sendTick by remember { mutableStateOf(0) }

    val rows = remember(posts) { circleFeedRows(posts) }
    val byId = remember(members) { members.associateBy { it.id } }
    val listState = rememberLazyListState()

    fun toggleShare(item: CircleShare, visible: Boolean) {
        members = members.map { if (it.isMe) it.withShare(item, visible) else it }
    }

    fun toggleReaction(postId: String, emoji: String) {
        mineReactions = toggleReaction(mineReactions, postId, emoji)
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        posts = posts + TextPost(
            id = "local-$localSeq",
            at = System.currentTimeMillis(),
            authorId = CircleDemo.MY_ID,
            text = text,
            localOnly = true,
        )
        localSeq += 1
        draft = ""
        sendTick += 1
    }

    // The nav's 游戏 cell opens the hub directly (tt#37).
    LaunchedEffect(openGames) { if (openGames > 0) gamesOpen = true }

    LaunchedEffect(sendTick) {
        if (sendTick > 0 && rows.isNotEmpty()) listState.animateScrollToItem(rows.size)
    }

    // 游戏 hub is a pushed page, not a swap: it slides in over the feed (220ms) and pops back in 180ms.
    XpPushHost(
        page = if (gamesOpen) { { GamesHub(onBack = { gamesOpen = false }) } } else null,
        onBack = { gamesOpen = false },
        modifier = Modifier.fillMaxSize(),
    ) {
    NavScaffold(
        title = CircleDemo.CIRCLE_NAME,
        scroll = NavScrollNone,
        demo = true,
        trailing = { NavIconButton(androidx.compose.material.icons.Icons.Outlined.Settings, "设置", onOpenSettings) },
    ) { top ->
        Box(Modifier.fillMaxSize()) {
            val feedIn = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(Unit) {
                feedIn.animateTo(1f, androidx.compose.animation.core.tween(220, easing = com.xiaopiao.app.ui.theme.XpEase.out))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = feedIn.value
                    translationY = (1f - feedIn.value) * 10.dp.toPx()
                },
                contentPadding = PaddingValues(top = top + XpSpace.xs, bottom = ComposerHeight + XpSpace.l),
            ) {
                if (rows.isEmpty()) {
                    item(key = "circle-empty") {
                        EmptyState(
                            icon = Icons.Outlined.Forum,
                            title = "群里还很安静",
                            body = "家人发出第一张小票后，这里就会热闹起来。",
                        )
                    }
                }
                items(items = rows, key = { it.key }) { row ->
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = androidx.compose.animation.core.tween(180, easing = com.xiaopiao.app.ui.theme.XpEase.out),
                            placementSpec = androidx.compose.animation.core.tween(200, easing = com.xiaopiao.app.ui.theme.XpEase.out),
                            fadeOutSpec = androidx.compose.animation.core.tween(140),
                        ),
                    ) {
                    when (row) {
                        is CircleFeedRow.Day -> CircleDayDivider(row.dayStart, now)
                        is CircleFeedRow.Post -> {
                            val authorId = when (val post = row.post) {
                                is ReceiptPost -> post.authorId
                                is TextPost -> post.authorId
                                is SystemPost -> null
                            }
                            CirclePostRow(
                                post = row.post,
                                author = authorId?.let(byId::get),
                                now = now,
                                mineReactions = mineReactions,
                                onToggleReaction = ::toggleReaction,
                                onMemberClick = { shareSheetFor = it },
                                onReceiptOpen = { openedPost = it },
                            )
                        }
                    }
                    }
                }
                item(key = "circle-games") {
                    CircleGamesCard(
                        modifier = Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m),
                        onClick = { gamesOpen = true },
                    )
                }
                item(key = "circle-tail") {
                    Text(
                        "这个群只在这台手机上，发出去的消息不会离开设备。",
                        style = MaterialTheme.typography.labelSmall,
                        color = XpColors.InkTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = XpSpace.xl, vertical = XpSpace.l),
                    )
                }
            }
            CircleComposer(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = ::send,
                modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
            )
        }
    }
    }

    shareSheetFor?.let { selectedId ->
        CircleShareSheet(
            members = members,
            selectedId = selectedId,
            onSelect = { shareSheetFor = it },
            onToggle = ::toggleShare,
            onDismiss = { shareSheetFor = null },
        )
    }
    // Tapping a receipt opens it (tt#27): the day's items, masked exactly like the card.
    openedPost?.let { post ->
        val author = byId[post.authorId]
        CircleReceiptSheet(post = post, author = author, now = now, onDismiss = { openedPost = null })
    }
}

/** The single 游戏 card: one entry for the whole hub, with an emoji block as the non-text anchor. */
@Composable
private fun CircleGamesCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.l))
            .background(XpColors.Card)
            .pressableClick(scaleDown = 0.98f, haptic = true, onClick = onClick)
            .padding(XpSpace.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp)
                .clip(RoundedCornerShape(XpRadius.m))
                .background(XpColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎲", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
            Text("游戏", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Text(
                "${GameContent.games.size} 个小游戏 · 娱乐，不构成建议",
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = XpColors.InkTertiary)
    }
}

@Composable
private fun CircleComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val enabled = draft.isNotBlank()
    Column(modifier.fillMaxWidth().background(XpColors.CanvasGrouped)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(XpColors.Hairline))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .background(XpColors.Fill)
                    .padding(horizontal = XpSpace.m + 2.dp, vertical = XpSpace.s + 2.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { onDraftChange(it.take(140)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = XpColors.Ink),
                    cursorBrush = SolidColor(XpColors.Accent),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (enabled) { onSend(); keyboard?.hide() } }),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    "说点什么… 只保存在本机",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = XpColors.InkSecondary,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.width(XpSpace.s))
            Box(
                Modifier.size(38.dp)
                    .clip(CircleShape)
                    .background(if (enabled) XpColors.Primary else XpColors.Fill)
                    .pressableClick(enabled = enabled, haptic = true) {
                        onSend()
                        keyboard?.hide()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "发送",
                    tint = if (enabled) XpColors.OnPrimary else XpColors.InkTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
