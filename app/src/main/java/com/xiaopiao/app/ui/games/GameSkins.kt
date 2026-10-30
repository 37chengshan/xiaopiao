package com.xiaopiao.app.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.theme.ThemeTone
import com.xiaopiao.app.ui.theme.XpColors

/**
 * 一个游戏的「身份」：一句玩法说明和唯一强调色。
 *
 * 之前这里还带着渐变、纹样和一句 hub 标语——游戏页改成液态玻璃列表后，渐变卡片与装饰纹样全部删掉，
 * 只留真正在用的两样：intro 的玩法说明、结果/进度/浮层的强调色。
 * 数据仍然集中在这张表里，[com.xiaopiao.app.ui.games.GamePlayer] 不 switch 游戏 id。
 */
data class GameSkin(
    val id: String,
    /** One or two sentences on the intro card. */
    val howTo: Int,
    /** 唯一的身份色；只用于小面积强调（进度条、雷达多边形、选中态）。 */
    val accent: ThemeTone,
)

object GameSkins {
    private val skins: List<GameSkin> = listOf(
        GameSkin(
            id = "wallet-persona",
            howTo = R.string.games_howto_wallet_persona,
            accent = ThemeTone(0xFF5B46C4, 0xFFA896FF),
        ),
        GameSkin(
            id = "spend-profile",
            howTo = R.string.games_howto_spend_profile,
            accent = ThemeTone(0xFF3F5A2E, 0xFF9CCB7B),
        ),
        GameSkin(
            id = "big-five",
            howTo = R.string.games_howto_big_five,
            accent = ThemeTone(0xFF4A3D7A, 0xFFB6A7FF),
        ),
        GameSkin(
            id = "saving-style",
            howTo = R.string.games_howto_saving_style,
            accent = ThemeTone(0xFF1D5F63, 0xFF7FD4D8),
        ),
        GameSkin(
            id = "spend-duel",
            howTo = R.string.games_howto_spend_duel,
            accent = ThemeTone(0xFFC2410C, 0xFFFF9A6B),
        ),
        GameSkin(
            id = "receipt-guess",
            howTo = R.string.games_howto_receipt_guess,
            accent = ThemeTone(0xFF0B7A6E, 0xFF4FD9C6),
        ),
        GameSkin(
            id = "blind-box",
            howTo = R.string.games_howto_blind_box,
            accent = ThemeTone(0xFFB3245C, 0xFFFF8FB6),
        ),
        GameSkin(
            id = "daily-tasks",
            howTo = R.string.games_howto_daily_tasks,
            accent = ThemeTone(0xFF2F7A3A, 0xFF7BD88A),
        ),
        GameSkin(
            id = "save-challenge",
            howTo = R.string.games_howto_save_challenge,
            accent = ThemeTone(0xFF96621A, 0xFFF0BE66),
        ),
        GameSkin(
            id = "ledger-quiz",
            howTo = R.string.games_howto_ledger_quiz,
            accent = ThemeTone(0xFF2F52B8, 0xFF93A8FF),
        ),
        GameSkin(
            id = "spend-radar",
            howTo = R.string.games_howto_spend_radar,
            accent = ThemeTone(0xFF0E6E9E, 0xFF6FD2F5),
        ),
    )

    private val byId: Map<String, GameSkin> = skins.associateBy { it.id }

    /** Every game in [com.xiaopiao.app.domain.games.GameContent] has a skin; unknown ids fall back to the first. */
    fun of(id: String): GameSkin = byId[id] ?: skins.first()
}

/** The game's identity colour for the active light/dark palette. */
@Composable
fun GameSkin.accentColor(): Color = accent.color(XpColors.isDark)

/** A tinted surface for selected answers; kept faint so the glass stays the surface. */
@Composable
fun GameSkin.washColor(): Color = lerp(XpColors.Card, accentColor(), if (XpColors.isDark) 0.32f else 0.16f)
