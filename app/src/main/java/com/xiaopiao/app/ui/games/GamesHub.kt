package com.xiaopiao.app.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.PriceCheck
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.domain.games.GameCategory
import com.xiaopiao.app.domain.games.GameDefinition
import com.xiaopiao.app.domain.games.GameContent
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.LocalTabBarInset
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.motion.XpPushHost
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 游戏 hub（docs/APP-DESIGN.md §5.3）。
 *
 * 液态玻璃的分组列表：页面铺 [NavScaffold] 的 glassGround，卡片由 [GroupedSection] 自动变成玻璃，
 * 每行一个游戏（图标 + 名称 + 一句说明 + 时长）。之前的渐变大卡片、装饰纹样和 emoji 全部去掉——
 * 它们在这套界面里只抢视觉、不带信息。
 */
@Composable
fun GamesHub(onBack: () -> Unit, onOpenSettings: (() -> Unit)? = null) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val game = openId?.let(GameContent::byId)

    BackHandler(enabled = game == null) { onBack() }

    XpPushHost(
        page = game?.let { selected -> { GamePlayer(game = selected, onExit = { openId = null }) } },
        onBack = { openId = null },
        modifier = Modifier.fillMaxSize(),
    ) {
        NavScaffold(
            title = stringResource(R.string.games_title),
            scroll = NavScrollNone,
            trailing = { AppTopActions() },
        ) { top ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = top + XpSpace.xs,
                    bottom = LocalTabBarInset.current + XpSpace.l,
                ),
            ) {
                item(key = "games-hero") {
                    GamesHeader(count = GameContent.games.size, modifier = Modifier.padding(horizontal = XpSpace.page))
                }
                GameCategory.entries.forEach { category ->
                    val games = GameContent.games.filter { it.category == category }
                    if (games.isEmpty()) return@forEach
                    item(key = "games-${category.name}") {
                        GroupedSection(
                            header = stringResource(category.labelRes()),
                            dividerInset = 58.dp,
                        ) {
                            games.forEach { game ->
                                item(key = game.id) {
                                    GroupedRow(
                                        title = game.title,
                                        // 一行说明已经够；长描述留给 intro，360dp×1.3 才不会挤成两行撞到时长。
                                        // 只留时长：360dp×1.3 时「10 题 · 3 分钟」会把标题挤成省略号。
                                        value = stringResource(R.string.games_meta_short, game.minutes),
                                        leading = {
                                            IconTile(
                                                icon = game.icon(),
                                                tint = category.tint(),
                                                background = category.tileBackground(),
                                            )
                                        },
                                        chevron = true,
                                        onClick = { openId = game.id },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "games-note") {
                    Text(
                        stringResource(R.string.games_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = XpColors.InkTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = XpSpace.page, vertical = XpSpace.l),
                    )
                }
            }
        }
    }
}

/** 一行说明：有多少可玩、单局多长。没有大数字、没有 emoji 排排站。 */
@Composable
private fun GamesHeader(count: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.games_hub_count, count),
        style = MaterialTheme.typography.titleMedium,
        color = XpColors.Ink,
        modifier = modifier.padding(top = XpSpace.s, bottom = 2.dp),
    )
    Text(
        stringResource(R.string.games_hub_sub),
        style = MaterialTheme.typography.bodySmall,
        color = XpColors.InkSecondary,
        modifier = modifier.padding(bottom = XpSpace.s),
    )
}

private fun GameCategory.labelRes(): Int = when (this) {
    GameCategory.Personality -> R.string.games_group_personality
    GameCategory.Guess -> R.string.games_group_guess
    GameCategory.Challenge -> R.string.games_group_challenge
}

/** 每个游戏自己的图标；同类游戏不再共用一张脸。 */
private fun GameDefinition.icon(): ImageVector = when (id) {
    "wallet-persona" -> Icons.Outlined.AccountBalanceWallet
    "spend-profile" -> Icons.Outlined.PieChart
    "saving-style" -> Icons.Outlined.Savings
    "big-five" -> Icons.Outlined.Psychology
    "spend-duel" -> Icons.Outlined.CallSplit
    "receipt-guess" -> Icons.Outlined.ReceiptLong
    "blind-box" -> Icons.Outlined.CardGiftcard
    "daily-tasks" -> Icons.Outlined.Checklist
    "save-challenge" -> Icons.Outlined.Flag
    "ledger-quiz" -> Icons.Outlined.MenuBook
    "spend-radar" -> Icons.Outlined.Radar
    else -> category.icon()
}

private fun GameCategory.icon(): ImageVector = when (this) {
    GameCategory.Personality -> Icons.Outlined.Psychology
    GameCategory.Guess -> Icons.Outlined.PriceCheck
    GameCategory.Challenge -> Icons.Outlined.Flag
}

private fun GameCategory.tint() = when (this) {
    GameCategory.Personality -> XpColors.Accent
    GameCategory.Guess -> XpColors.Info
    GameCategory.Challenge -> XpColors.Positive
}

private fun GameCategory.tileBackground() = when (this) {
    GameCategory.Personality -> XpColors.AccentSoft
    GameCategory.Guess -> XpColors.InfoSoft
    GameCategory.Challenge -> XpColors.PositiveSoft
}
