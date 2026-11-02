package com.xiaopiao.app.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SportsEsports
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr

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
    var wheelOpen by rememberSaveable { mutableStateOf(false) }
    val game = openId?.let(GameContent::byId)

    BackHandler(enabled = game == null && !wheelOpen) { onBack() }

    XpPushHost(
        page = when {
            wheelOpen -> { { WheelGame(onBack = { wheelOpen = false }) } }
            game != null -> { { GamePlayer(game = game, onExit = { openId = null }) } }
            else -> null
        },
        onBack = { wheelOpen = false; openId = null },
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
                // 转盘：和其他游戏同一套行样式（GroupedSection + GroupedRow），不另做卡片。
                item(key = "games-wheel") {
                    GroupedSection(header = tr("工具", "Tools"), dividerInset = 58.dp) {
                        item(key = "wheel") {
                            GroupedRow(
                                title = tr("转盘", "Wheel"),
                                subtitle = tr("自己定扇面，转到哪个就是哪个", "Your own slices, the wheel decides"),
                                leading = {
                                    IconTile(
                                        icon = Icons.Outlined.Casino,
                                        tint = XpColors.Accent,
                                        background = XpColors.AccentSoft,
                                    )
                                },
                                chevron = true,
                                onClick = { wheelOpen = true },
                            )
                        }
                    }
                }
                // Every game gets a continuous reveal index, so the whole list reads as one entrance
                // instead of each category starting over at 0.
                val sections = run {
                    var running = 1
                    GameCategory.entries.mapNotNull { category ->
                        val games = GameContent.games.filter { it.category == category }
                        if (games.isEmpty()) {
                            null
                        } else {
                            category to games.map { game -> game to running++ }
                        }
                    }
                }
                sections.forEach { (category, games) ->
                    item(key = "games-${category.name}") {
                        GroupedSection(
                            header = stringResource(category.labelRes()),
                            headerTrailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(6.dp).clip(CircleShape)
                                            .background(category.tint()),
                                    )
                                    Text(
                                        " ${games.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = category.tint(),
                                    )
                                }
                            },
                            dividerInset = 58.dp,
                        ) {
                            games.forEach { (game, reveal) ->
                                item(key = game.id) {
                                    GroupedRow(
                                        title = game.title,
                                        value = stringResource(R.string.games_meta_short, game.minutes),
                                        modifier = Modifier.xpRevealLayer(index = reveal),
                                        leading = {
                                            IconTile(
                                                icon = game.icon(),
                                                tint = category.tint(),
                                                background = category.tileBackground(),
                                            )
                                        },
                                        trailing = if (game.id in PopularGameIds) {
                                            { GameBadge(category.tint()) }
                                        } else null,
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

private val PopularGameIds = setOf("type-16", "attachment-style", "social-battery", "trivia-quickfire")

@Composable
private fun GameBadge(tint: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            "热门",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun GameCategory.labelRes(): Int = when (this) {
    GameCategory.Personality -> R.string.games_group_personality
    GameCategory.Relationship -> R.string.games_group_relationship
    GameCategory.Mind -> R.string.games_group_mind
    GameCategory.Fun -> R.string.games_group_fun
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
    "type-16" -> Icons.Outlined.Psychology
    "attachment-style" -> Icons.Outlined.Favorite
    "love-language" -> Icons.Outlined.FavoriteBorder
    "disc-style" -> Icons.Outlined.Groups
    "social-battery" -> Icons.Outlined.BatterySaver
    "trivia-quickfire" -> Icons.Outlined.Lightbulb
    else -> category.icon()
}

private fun GameCategory.icon(): ImageVector = when (this) {
    GameCategory.Personality -> Icons.Outlined.Psychology
    GameCategory.Relationship -> Icons.Outlined.FavoriteBorder
    GameCategory.Mind -> Icons.Outlined.Lightbulb
    GameCategory.Fun -> Icons.Outlined.SportsEsports
    GameCategory.Guess -> Icons.Outlined.PriceCheck
    GameCategory.Challenge -> Icons.Outlined.Flag
}

private fun GameCategory.tint() = when (this) {
    GameCategory.Personality -> XpColors.Accent
    GameCategory.Relationship -> XpColors.Stamp
    GameCategory.Mind -> XpColors.Info
    GameCategory.Fun -> XpColors.Warning
    GameCategory.Guess -> XpColors.Info
    GameCategory.Challenge -> XpColors.Positive
}

private fun GameCategory.tileBackground() = when (this) {
    GameCategory.Personality -> XpColors.AccentSoft
    GameCategory.Relationship -> XpColors.WarningSoft
    GameCategory.Mind -> XpColors.InfoSoft
    GameCategory.Fun -> XpColors.WarningSoft
    GameCategory.Guess -> XpColors.InfoSoft
    GameCategory.Challenge -> XpColors.PositiveSoft
}
