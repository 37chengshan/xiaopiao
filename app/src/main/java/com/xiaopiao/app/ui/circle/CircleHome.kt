package com.xiaopiao.app.ui.circle

import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.components.pressableClick
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.motion.xpRevealLayer
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import com.xiaopiao.app.ui.theme.XpColors
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.LocalTabBarInset
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpToastHost
import com.xiaopiao.app.ui.components.rememberXpToast
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 圈子 tab as a feed (tt 2026-10-29: 做成朋友圈 / X 发帖那种，去掉群聊): newest post first, each one a 朋友圈-style
 * row with the day's receipt card, ♥ and replies. 排行榜 / 颁奖 / 游戏 sit in a capsule row under the header;
 * 发帖 is a glass button over the feed. The header menu still switches circles, invites and manages members.
 * State lives in [AppContainer.circles] so 「发到圈子」 from the receipt page lands in the same feed.
 */
@Composable
fun CircleHome(container: AppContainer, onOpenSettings: () -> Unit = {}, onOpenGames: () -> Unit = {}) {
    val store = container.circles
    val book by store.book.collectAsStateWithLifecycle()
    val sync by store.sync.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalCircleStore provides store) {
        if (book.circles.isEmpty()) CircleNoCircle(container, sync, onOpenGames) else CircleFeedHome(container, onOpenGames)
    }
}

/** 圈子 before there is a feed: signed out, loading, failed, or simply no circle yet. Never demo data. */
@Composable
private fun CircleNoCircle(container: AppContainer, sync: CircleSync, onOpenGames: () -> Unit) {
    val store = container.circles
    val scope = rememberCoroutineScope()
    var accountOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    // Coming back to the tab re-checks: a circle may have been created from the receipt's 「发到圈子」.
    LaunchedEffect(Unit) { if (sync != CircleSync.SignedOut) store.refresh() }
    NavScaffold(title = stringResource(R.string.circle_title_fallback), scroll = NavScrollNone, trailing = { AppTopActions() }) { top ->
        // 游戏 must stay reachable with no circle yet: the switch sits on this page too.
        CircleGamesSwitch(gamesSelected = false, onSelect = { if (it) onOpenGames() }, modifier = Modifier.padding(top = top + XpSpace.xs))
        Box(Modifier.fillMaxSize().padding(top = top), contentAlignment = Alignment.Center) {
            when (sync) {
                CircleSync.Loading -> androidx.compose.material3.CircularProgressIndicator(color = XpColors.Accent)
                CircleSync.SignedOut -> CircleStateCard(
                    title = stringResource(R.string.circle_signed_out_title),
                    body = stringResource(R.string.circle_signed_out_body),
                    primary = stringResource(R.string.circle_signed_out_action) to { accountOpen = true },
                )
                CircleSync.Failed -> CircleStateCard(
                    title = stringResource(R.string.circle_failed_title),
                    body = stringResource(R.string.circle_failed_body),
                    primary = stringResource(R.string.circle_failed_retry) to { scope.launch { store.refresh() } },
                )
                CircleSync.Ready -> CircleStateCard(
                    title = stringResource(R.string.circle_none_title),
                    body = stringResource(R.string.circle_none_body),
                    primary = stringResource(R.string.circle_none_create) to { creating = true },
                    secondary = stringResource(R.string.circle_none_join) to { joining = true },
                )
            }
        }
    }
    if (accountOpen) com.xiaopiao.app.ui.account.AccountSheet(container.account) { accountOpen = false }
    if (creating) CreateCircleDialog(onDismiss = { creating = false }, onCreate = { name -> store.create(name); creating = false })
    if (joining) CircleJoinSheet(onDismiss = { joining = false }, onJoined = { joining = false })
}

@Composable
private fun CircleStateCard(title: String, body: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.padding(horizontal = XpSpace.page).xpRevealLayer(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Groups, contentDescription = null, tint = XpColors.Accent, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.padding(top = XpSpace.m))
        Text(
            body, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = XpSpace.xs),
        )
        XpButton(primary.first, primary.second, modifier = Modifier.padding(top = XpSpace.l).fillMaxWidth())
        secondary?.let { (label, action) ->
            XpButton(label, action, style = XpButtonStyle.Secondary, modifier = Modifier.padding(top = XpSpace.s).fillMaxWidth())
        }
    }
}

@Composable
private fun CircleFeedHome(container: AppContainer, onOpenGames: () -> Unit) {
    val store = container.circles
    val book by store.book.collectAsStateWithLifecycle()
    val myLikes by store.myLikes.collectAsStateWithLifecycle()
    val likeCounts by store.likeCount.collectAsStateWithLifecycle()
    val circle = book.current()
    val viewerId = CircleDemo.MY_ID
    val now = remember(book) { System.currentTimeMillis() }
    val en = LocalConfiguration.current.locales[0].language == "en"
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var commentsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var openedPost by remember { mutableStateOf<ReceiptPost?>(null) }
    var postedTick by remember { mutableIntStateOf(0) }
    val toast = rememberXpToast()
    val txns by remember { container.ledger.observeAll() }.collectAsState(initial = emptyList())

    // Newest first, like every feed.
    val posts = remember(circle.posts) { circle.posts.sortedByDescending { it.at } }
    val listState = rememberLazyListState()
    val bottomInset = LocalTabBarInset.current
    // The feed is recorded for the glass 发帖 button that floats over it (a sibling, never its parent).
    val feedGlass = rememberLayerBackdrop()
    // Height of the pinned capsule row; the feed starts under it and scrolls beneath it.
    var entriesHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    LaunchedEffect(postedTick) { if (postedTick > 0) listState.animateScrollToItem(0) }

    NavScaffold(
        title = circle.name,
        scroll = NavScrollNone,
        leading = {
            NavIconButton(Icons.Outlined.Groups, stringResource(R.string.circle_home_switch), onClick = { sheet = "menu" })
        },
        trailing = { AppTopActions() },
    ) { top ->
        Box(Modifier.fillMaxSize()) {
            val feedIn = remember { Animatable(0f) }
            LaunchedEffect(Unit) { feedIn.animateTo(1f, tween(220, easing = XpEase.out)) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().layerBackdrop(feedGlass).graphicsLayer {
                    alpha = feedIn.value
                    translationY = (1f - feedIn.value) * 10.dp.toPx()
                },
                contentPadding = PaddingValues(
                    top = top + with(density) { entriesHeight.toDp() },
                    bottom = bottomInset + 96.dp,
                ),
            ) {
                if (circle.pendingRequests > 0) {
                    item(key = "requests") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = XpSpace.page, vertical = XpSpace.s)
                                .clip(RoundedCornerShape(XpRadius.m)).background(XpColors.AccentSoft)
                                .pressableClick { sheet = "requests" }
                                .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (en) "${circle.pendingRequests} join request(s)" else "${circle.pendingRequests} 个加入申请待处理",
                                style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, modifier = Modifier.weight(1f),
                            )
                            Text(if (en) "Review" else "去处理", style = MaterialTheme.typography.labelLarge, color = XpColors.Accent)
                        }
                    }
                }
                if (posts.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.Forum,
                            title = stringResource(R.string.circle_home_empty_title),
                            body = stringResource(R.string.circle_home_empty_body),
                        )
                    }
                }
                items(posts, key = { it.id }) { post ->
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = tween(220, easing = XpEase.out),
                            fadeOutSpec = tween(140),
                        ),
                    ) {
                        CircleFeedPost(
                            post = post,
                            circle = circle,
                            viewerId = viewerId,
                            now = now,
                            liked = post.id in myLikes,
                            likes = likeCounts[post.id] ?: 0,
                            onLike = { store.toggleLike(post.id) },
                            onComment = { commentsFor = post.id },
                            onReceiptOpen = { openedPost = it },
                            onMemberClick = { sheet = "members" },
                        )
                    }
                }
            }
            // Posts passing under the header and between the capsules fade out instead of reading through; the
            // capsules still sample the feed itself, so they stay glass.
            val band = top + with(density) { entriesHeight.toDp() }
            val canvas = XpColors.CanvasGrouped
            Box(
                Modifier.fillMaxWidth().height(band + XpSpace.l).background(
                    Brush.verticalGradient(
                        0f to canvas.copy(alpha = 0.96f),
                        band / (band + XpSpace.l) to canvas.copy(alpha = 0.9f),
                        1f to canvas.copy(alpha = 0f),
                    ),
                ),
            )
            CircleEntryRow(
                onLeaderboard = { sheet = "leaderboard" },
                onAward = { sheet = "award" },
                onGames = onOpenGames,
                backdrop = feedGlass,
                modifier = Modifier.padding(top = top).onSizeChanged { entriesHeight = it.height },
            )
            CirclePostButton(
                backdrop = feedGlass,
                onClick = { sheet = "compose" },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = XpSpace.l, bottom = bottomInset + XpSpace.l),
            )
            XpToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 88.dp), backdrop = feedGlass)
        }
    }

    when {
        sheet == "menu" -> CircleMenuDrawer(
            book = book,
            onBookChange = store::set,
            onClose = { sheet = null },
            onGames = { sheet = null; onOpenGames() },
            onMembers = { sheet = "members" },
            onJoin = { sheet = "join" },
            onRequests = { sheet = "requests" },
            onInvite = { sheet = "invite" },
            onLeaderboard = { sheet = "leaderboard" },
            onAward = { sheet = "award" },
            onShare = { sheet = "compose" },
            viewerId = viewerId,
        )
        sheet == "requests" -> CircleRequestsSheet(circle, onDismiss = { sheet = null })
        sheet == "join" -> CircleJoinSheet(onDismiss = { sheet = null }, onJoined = { name ->
            sheet = null
            toast.show(if (en) "Joined $name" else "已加入「$name」")
        })
        sheet == "members" -> CircleMembersSheet(
            book = book,
            onBookChange = store::set,
            onClose = { sheet = null },
            viewerId = viewerId,
        )
        sheet == "invite" -> CircleInviteSheet(
            book = book,
            onBookChange = store::set,
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
        sheet == "compose" -> CircleComposeSheet(
            store = store,
            viewerId = viewerId,
            todayReceipt = todayReceiptPost(txns, viewerId, System.currentTimeMillis()),
            attachByDefault = false,
            onPosted = { name ->
                toast.show(if (en) "Posted to $name" else "已发到「$name」")
                postedTick++
            },
            onDismiss = { sheet = null },
        )
        sheet?.startsWith("member:") == true -> {
            val memberId = sheet!!.removePrefix("member:")
            CircleVisibilitySheet(
                book = book,
                onBookChange = store::set,
                onClose = { sheet = null },
                viewerId = viewerId,
                initialMemberId = memberId,
            )
        }
    }

    commentsFor?.let { postId ->
        CircleCommentsSheet(store, circle.id, postId, viewerId, onDismiss = { commentsFor = null })
    }

    // Tapping a receipt opens it (tt#27): the day's items, masked exactly like the card.
    openedPost?.let { post ->
        CircleReceiptSheet(post = post, author = circle.member(post.authorId), now = now, onDismiss = { openedPost = null })
    }
}
