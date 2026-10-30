package com.xiaopiao.app.ui.circle

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
    val myLikes by store.myLikes.collectAsStateWithLifecycle()
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
                contentPadding = PaddingValues(top = top, bottom = bottomInset + 96.dp),
            ) {
                item(key = "entries") {
                    CircleEntryRow(
                        onLeaderboard = { sheet = "leaderboard" },
                        onAward = { sheet = "award" },
                        onGames = onOpenGames,
                    )
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
                            fadeInSpec = tween(180, easing = XpEase.out),
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
                            onLike = { store.toggleLike(post.id) },
                            onComment = { commentsFor = post.id },
                            onReceiptOpen = { openedPost = it },
                            onMemberClick = { id -> sheet = "member:$id" },
                        )
                    }
                }
            }
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
            onInvite = { sheet = "invite" },
            onLeaderboard = { sheet = "leaderboard" },
            onAward = { sheet = "award" },
            onShare = { sheet = "compose" },
            viewerId = viewerId,
        )
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
