package com.xiaopiao.app.ui.games

import androidx.compose.material.icons.outlined.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.xiaopiao.app.domain.games.GameContent
import com.xiaopiao.app.domain.games.GameDefinition
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.motion.MotionMath
import com.xiaopiao.app.ui.motion.XpPushHost
import com.xiaopiao.app.ui.motion.XpReveal
import com.xiaopiao.app.ui.motion.XpTiming
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 游戏 hub, opened from the single 游戏 card at the bottom of the 圈子 feed (docs/APP-DESIGN.md §5.3).
 * Two-column grid of [GameContent.games]; tapping one swaps this screen for [GamePlayer] in the same tab.
 */
@Composable
fun GamesHub(onBack: () -> Unit, onOpenSettings: (() -> Unit)? = null) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val game = openId?.let(GameContent::byId)

    BackHandler(enabled = game == null) { onBack() }
    // Cards stagger in once; anything that scrolls into view later arrives already visible.
    var introDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MotionMath.STAGGER_MAX_MS.toLong() + XpTiming.Enter + 80L)
        introDone = true
    }

    // Playing a game is a pushed page: (220ms in, 180ms back) so the grid stays underneath.
    XpPushHost(
        page = game?.let { selected -> { GamePlayer(game = selected, onExit = { openId = null }) } },
        onBack = { openId = null },
        modifier = Modifier.fillMaxSize(),
    ) {
    NavScaffold(
        title = "游戏", scroll = NavScrollNone, onBack = onBack,
        trailing = {
            if (onOpenSettings != null) com.xiaopiao.app.ui.components.NavIconButton(androidx.compose.material.icons.Icons.Outlined.Settings, "设置", onClick = onOpenSettings)
        },
    ) { top ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = XpSpace.l,
                end = XpSpace.l,
                top = top + XpSpace.s,
                bottom = XpSpace.xl,
            ),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { XpReveal(index = 0) { GamesHero() } }
            itemsIndexed(GameContent.games, key = { _, game -> game.id }) { index, item ->
                GameCard(game = item, index = index, animate = !introDone && index < 8) { openId = item.id }
            }
        }
    }
    }
}

/** Big count + emoji cluster: the one number that tells you how much there is to play. */
@Composable
private fun GamesHero() {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.l))
            .background(XpColors.Card)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${GameContent.games.size}",
                    style = MaterialTheme.typography.displayMedium,
                    color = XpColors.Ink,
                )
                Text(
                    " 个小游戏",
                    style = MaterialTheme.typography.titleSmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                "点开就玩 · 单局 1–5 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text("🎲👛⚡", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun GameCard(game: GameDefinition, index: Int, animate: Boolean = true, onClick: () -> Unit) {
    val tints = listOf(XpColors.InfoSoft, XpColors.PositiveSoft, XpColors.WarningSoft, XpColors.AccentSoft)
    val tint = tints[index % tints.size]
    Column(
        Modifier.fillMaxWidth()
            .xpRevealLayer(index = index + 1, animate = animate)
            .height(160.dp)
            .clip(RoundedCornerShape(XpRadius.m))
            .background(XpColors.Card)
            .pressableClick(haptic = true, onClick = onClick)
            .padding(XpSpace.m),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(XpRadius.m))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Text(game.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "%02d".format(index + 1),
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            game.title,
            style = MaterialTheme.typography.titleSmall,
            color = XpColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "约 ${game.minutes} 分钟 · ${game.rounds} 题",
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
