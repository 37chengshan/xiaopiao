package com.xiaopiao.app.ui.games

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.domain.games.GameContent
import com.xiaopiao.app.domain.games.GameDefinition
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.AppTopActions
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
import kotlinx.coroutines.delay

/**
 * 游戏 hub, opened from the 游戏 entry in the 圈子 tab (docs/APP-DESIGN.md §5.3).
 *
 * A two-column wall of designed cards: each game carries its own accent, generated motif, emoji, tagline
 * and length, so the wall reads as eight different things rather than eight rows of text. Tapping a card
 * pushes [GamePlayer] into the same tab; back stays where it was.
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

    // Playing a game is a pushed page: (220ms in, 180ms back) so the wall stays underneath.
    XpPushHost(
        page = game?.let { selected -> { GamePlayer(game = selected, onExit = { openId = null }) } },
        onBack = { openId = null },
        modifier = Modifier.fillMaxSize(),
    ) {
        NavScaffold(
            title = stringResource(R.string.games_title),
            scroll = NavScrollNone,
            onBack = onBack,
            backLabel = stringResource(R.string.games_back),
            trailing = { AppTopActions() },
        ) { top ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = XpSpace.l,
                    end = XpSpace.l,
                    top = top + XpSpace.s,
                    bottom = 104.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
                verticalArrangement = Arrangement.spacedBy(XpSpace.m),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { XpReveal(index = 0) { GamesHero() } }
                itemsIndexed(GameContent.games, key = { _, item -> item.id }) { index, item ->
                    GameCard(game = item, index = index, animate = !introDone) { openId = item.id }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.games_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = XpColors.InkTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = XpSpace.s),
                    )
                }
            }
        }
    }
}

/** The wall's header: how much there is to play, and the emoji of the first few games as a sample. */
@Composable
private fun GamesHero() {
    Column(
        Modifier.fillMaxWidth()
            .padding(start = XpSpace.xs, end = XpSpace.xs, top = XpSpace.xs, bottom = XpSpace.s),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${GameContent.games.size}",
                    style = MaterialTheme.typography.displayLarge,
                    color = XpColors.Ink,
                )
                Text(
                    stringResource(R.string.games_count_unit),
                    style = MaterialTheme.typography.titleSmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(start = XpSpace.xs, bottom = 9.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameContent.games.take(4).forEach { sample ->
                    Box(
                        Modifier.size(30.dp)
                            .clip(CircleShape)
                            .background(GameSkins.of(sample.id).washColor()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(sample.emoji, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.games_hub_sub),
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun GameCard(game: GameDefinition, index: Int, animate: Boolean, onClick: () -> Unit) {
    val skin = GameSkins.of(game.id)
    val accent = skin.accentColor()
    Box(
        Modifier.fillMaxWidth()
            .xpRevealLayer(index = index + 1, animate = animate)
            .height(188.dp)
            .clip(RoundedCornerShape(XpRadius.l))
            .background(skin.cardGradient())
            .pressableClick(scaleDown = 0.96f, haptic = true, onClick = onClick),
    ) {
        GameMotifGraphic(skin = skin, modifier = Modifier.matchParentSize(), strength = 0.2f)
        Column(Modifier.fillMaxSize().padding(XpSpace.m)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(46.dp)
                        .clip(RoundedCornerShape(XpRadius.m))
                        .background(XpColors.Card),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(game.emoji, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "%02d".format(index + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
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
                stringResource(skin.tagline),
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                stringResource(R.string.games_meta, game.rounds, game.minutes),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
