package com.xiaopiao.app.ui.circle

import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import com.xiaopiao.app.ui.components.LocalTabBarInset
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.R
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.games.GameContent
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

private val ComposerHeight = 88.dp

/**
 * 圈子 tab: several family/roommate circles, each its own group chat (docs/APP-DESIGN.md §5).
 * The header holds two things: the menu (switch / create / invite / members & visibility /
 * leaderboards / awards / games) and “share to circle”, which posts today’s receipt into the
 * chosen circles instead of copying text. Everything is local demo state.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CircleHome(container: AppContainer, onOpenSettings: () -> Unit = {}, onOpenGames: () -> Unit = {}) {
    // The feed runs under the glass tab bar; the composer floats above it (0 once the keyboard covers the bar).
    val BottomGlassInset = if (WindowInsets.isImeVisible) 0.dp else LocalTabBarInset.current
    val now = remember { System.currentTimeMillis() }
    var book by remember { mutableStateOf(CircleDemo.book(now)) }
    val circle = book.current()
    val viewerId = CircleDemo.MY_ID
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var mineReactions by remember(book.currentId) { mutableStateOf(emptySet<String>()) }
    var openedPost by remember { mutableStateOf<ReceiptPost?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var localSeq by rememberSaveable { mutableStateOf(0) }
    var sendTick by remember { mutableStateOf(0) }

    val txns by remember { container.ledger.observeAll() }.collectAsState(initial = emptyList())

    val rows = remember(circle.posts) { circleFeedRows(circle.posts) }
    val byId = remember(circle.members) { circle.members.associateBy { it.id } }
    val listState = rememberLazyListState()

    fun update(block: (CircleBook) -> CircleBook) {
        book = block(book)
    }

    fun toggleShare(memberId: String, item: CircleShare, visible: Boolean) {
        book = setShare(book, book.currentId, memberId, item, visible).book
    }

    fun toggleReaction(postId: String, emoji: String) {
        mineReactions = toggleReaction(mineReactions, postId, emoji)
    }

    fun addPost(post: CirclePost) {
        update { current ->
            current.copy(circles = current.circles.map { c ->
                if (c.id == current.currentId) c.copy(posts = c.posts + post) else c
            })
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        addPost(TextPost("local-$localSeq", System.currentTimeMillis(), viewerId, text, localOnly = true))
        localSeq += 1
        draft = ""
        sendTick += 1
    }

    LaunchedEffect(sendTick) {
        if (sendTick > 0 && rows.isNotEmpty()) listState.animateScrollToItem(rows.size)
    }

    NavScaffold(
        title = circle.name,
        scroll = NavScrollNone,
        demo = true,
        leading = {
            NavIconButton(Icons.Outlined.Groups, stringResource(R.string.circle_home_switch), onClick = { sheet = "menu" })
        },
        trailing = { AppTopActions() },
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
                // Runs under the glass header; the composer floats above the bar.
                contentPadding = PaddingValues(top = top + XpSpace.xs, bottom = ComposerHeight + BottomGlassInset + XpSpace.s),
            ) {
                item(key = "circle-award") {
                    CircleAwardCard(
                        circle = circle,
                        now = now,
                        viewerId = viewerId,
                        modifier = Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m),
                        onOpen = { sheet = "award" },
                    )
                }
                if (rows.isEmpty()) {
                    item(key = "circle-empty") {
                        EmptyState(
                            icon = Icons.Outlined.Forum,
                            title = stringResource(R.string.circle_home_empty_title),
                            body = stringResource(R.string.circle_home_empty_body),
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
                                    onMemberClick = { id -> sheet = "member:$id" },
                                    onReceiptOpen = { openedPost = it },
                                )
                            }
                        }
                    }
                }
                item(key = "circle-games") {
                    CircleGamesCard(
                        modifier = Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m),
                        onClick = onOpenGames,
                    )
                }
                item(key = "circle-tail") {
                    Text(
                        stringResource(R.string.circle_home_local_note),
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
                onPlus = { sheet = "send" },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BottomGlassInset).imePadding(),
            )
        }
    }

    when {
        sheet == "menu" -> CircleMenuDrawer(
            book = book,
            onBookChange = { book = it },
            onClose = { sheet = null },
            onGames = { sheet = null; onOpenGames() },
            onMembers = { sheet = "members" },
            onInvite = { sheet = "invite" },
            onLeaderboard = { sheet = "leaderboard" },
            onAward = { sheet = "award" },
            onShare = { sheet = "send" },
            viewerId = viewerId,
        )
        sheet == "members" -> CircleMembersSheet(
            book = book,
            onBookChange = { book = it },
            onClose = { sheet = null },
            viewerId = viewerId,
        )
        sheet == "invite" -> CircleInviteSheet(
            book = book,
            onBookChange = { book = it },
            onClose = { sheet = null },
        )
        sheet == "leaderboard" -> CircleLeaderboardSheet(
            book = book,
            now = now,
            viewerId = viewerId,
            onClose = { sheet = null },
            onInvite = { sheet = "invite" },
        )
        sheet == "award" -> CircleAwardSheet(
            circle = circle,
            now = now,
            viewerId = viewerId,
            onClose = { sheet = null },
        )
        sheet == "send" -> {
            val today = startOfDay(now)
            val items = txns
                .filter { it.occurredAt >= today && it.direction == Direction.OUT }
                .map { CircleItem(it.merchant, it.amountCents, it.category) }
            CircleSendSheet(
                book = book,
                viewerId = viewerId,
                onBookChange = { book = it },
                onClose = { sheet = null },
                post = ReceiptPost("share-${System.currentTimeMillis()}", System.currentTimeMillis(), viewerId, today, items),
            )
        }
        sheet?.startsWith("member:") == true -> {
            val memberId = sheet!!.removePrefix("member:")
            CircleVisibilitySheet(
                book = book,
                onBookChange = { book = it },
                onClose = { sheet = null },
                viewerId = viewerId,
                initialMemberId = memberId,
            )
        }
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
            Text(stringResource(R.string.circle_home_games_title), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Text(
                stringResource(R.string.circle_home_games_meta, GameContent.games.size),
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
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val enabled = draft.isNotBlank()
    val shape = RoundedCornerShape(30.dp)
    // Same recipe as the AI composer (ui/chat/ChatComposer.kt): one elevated white card, plus on the
    // left, accent circle on the right. The feed keeps scrolling underneath it.
    Box(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(10.dp, shape, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
                .clip(shape)
                .background(XpColors.Card)
                .padding(horizontal = XpSpace.s, vertical = XpSpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).pressableClick(scaleDown = 0.92f, onClick = onPlus),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.circle_home_share), tint = XpColors.Ink, modifier = Modifier.size(22.dp))
            }
            BasicTextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(140)) },
                modifier = Modifier.weight(1f).padding(horizontal = XpSpace.s),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink, fontSize = 17.sp, lineHeight = 24.sp),
                cursorBrush = SolidColor(XpColors.Accent),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (enabled) { onSend(); keyboard?.hide() } }),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                stringResource(R.string.circle_home_composer),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                                color = XpColors.InkSecondary,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (enabled) XpColors.Accent else XpColors.Fill)
                    .pressableClick(enabled = enabled, scaleDown = 0.94f, haptic = true) {
                        onSend()
                        keyboard?.hide()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ArrowUpward,
                    contentDescription = stringResource(R.string.circle_home_send),
                    tint = if (enabled) XpColors.OnAccent else XpColors.InkTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
