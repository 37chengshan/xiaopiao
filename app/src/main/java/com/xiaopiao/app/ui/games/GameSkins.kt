package com.xiaopiao.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.theme.ThemeTone
import com.xiaopiao.app.ui.theme.XpColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small generated graphic per game (docs/APP-DESIGN.md §5.3). Every motif is drawn from Compose
 * primitives — no bitmaps, no assets — so the hub and the intro stay sharp at any density.
 */
enum class GameMotif { Blob, Rays, Grid, Confetti, Sparks, Rings, Waves, Orbit }

/**
 * A game's visual identity and the two copy keys the chrome needs. This is data: [GamesHub] and
 * [GamePlayer] read it and never switch on a game id themselves, so adding a game means one entry here
 * plus its copy — the player stays generic.
 */
data class GameSkin(
    val id: String,
    /** One line on the hub card. */
    val tagline: Int,
    /** One or two sentences on the intro card. */
    val howTo: Int,
    /** Main identity colour; `accentEnd` is the neighbouring hue the gradients drift to. */
    val accent: ThemeTone,
    val accentEnd: ThemeTone,
    val motif: GameMotif,
)

object GameSkins {
    private val skins: List<GameSkin> = listOf(
        GameSkin(
            id = "wallet-persona",
            tagline = R.string.games_tag_wallet_persona,
            howTo = R.string.games_howto_wallet_persona,
            accent = ThemeTone(0xFF5B46C4, 0xFFA896FF),
            accentEnd = ThemeTone(0xFF8B6BE8, 0xFFC9B8FF),
            motif = GameMotif.Blob,
        ),
        GameSkin(
            id = "spend-duel",
            tagline = R.string.games_tag_spend_duel,
            howTo = R.string.games_howto_spend_duel,
            accent = ThemeTone(0xFFC2410C, 0xFFFF9A6B),
            accentEnd = ThemeTone(0xFFE86A2E, 0xFFFFC09C),
            motif = GameMotif.Rays,
        ),
        GameSkin(
            id = "receipt-guess",
            tagline = R.string.games_tag_receipt_guess,
            howTo = R.string.games_howto_receipt_guess,
            accent = ThemeTone(0xFF0B7A6E, 0xFF4FD9C6),
            accentEnd = ThemeTone(0xFF2BA695, 0xFF9BF0E2),
            motif = GameMotif.Grid,
        ),
        GameSkin(
            id = "blind-box",
            tagline = R.string.games_tag_blind_box,
            howTo = R.string.games_howto_blind_box,
            accent = ThemeTone(0xFFB3245C, 0xFFFF8FB6),
            accentEnd = ThemeTone(0xFFDB4F86, 0xFFFFC1D4),
            motif = GameMotif.Confetti,
        ),
        GameSkin(
            id = "daily-tasks",
            tagline = R.string.games_tag_daily_tasks,
            howTo = R.string.games_howto_daily_tasks,
            accent = ThemeTone(0xFF2F7A3A, 0xFF7BD88A),
            accentEnd = ThemeTone(0xFF5AA85F, 0xFFB5EEBF),
            motif = GameMotif.Sparks,
        ),
        GameSkin(
            id = "save-challenge",
            tagline = R.string.games_tag_save_challenge,
            howTo = R.string.games_howto_save_challenge,
            accent = ThemeTone(0xFF96621A, 0xFFF0BE66),
            accentEnd = ThemeTone(0xFFC08A3A, 0xFFFFDCA0),
            motif = GameMotif.Rings,
        ),
        GameSkin(
            id = "ledger-quiz",
            tagline = R.string.games_tag_ledger_quiz,
            howTo = R.string.games_howto_ledger_quiz,
            accent = ThemeTone(0xFF2F52B8, 0xFF93A8FF),
            accentEnd = ThemeTone(0xFF5A78E0, 0xFFC3D0FF),
            motif = GameMotif.Waves,
        ),
        GameSkin(
            id = "spend-radar",
            tagline = R.string.games_tag_spend_radar,
            howTo = R.string.games_howto_spend_radar,
            accent = ThemeTone(0xFF0E6E9E, 0xFF6FD2F5),
            accentEnd = ThemeTone(0xFF3296C4, 0xFFB3E8FF),
            motif = GameMotif.Orbit,
        ),
    )

    private val byId: Map<String, GameSkin> = skins.associateBy { it.id }

    /** Every game in [com.xiaopiao.app.domain.games.GameContent] has a skin; unknown ids fall back to the first. */
    fun of(id: String): GameSkin = byId[id] ?: skins.first()
}

/** The game's identity colour for the active light/dark palette. */
@Composable
fun GameSkin.accentColor(): Color = accent.color(XpColors.isDark)

/** The second gradient stop. */
@Composable
fun GameSkin.accentEndColor(): Color = accentEnd.color(XpColors.isDark)

/** A tinted surface for emoji blocks and selected answers. */
@Composable
fun GameSkin.washColor(): Color = lerp(XpColors.Card, accentColor(), if (XpColors.isDark) 0.32f else 0.16f)

/** Card / hero background: the accent washed over the card surface, never a saturated block. */
@Composable
fun GameSkin.cardGradient(): Brush {
    val dark = XpColors.isDark
    val top = lerp(XpColors.Card, accent.color(dark), if (dark) 0.30f else 0.20f)
    val bottom = lerp(XpColors.Card, accentEnd.color(dark), if (dark) 0.14f else 0.07f)
    return Brush.linearGradient(listOf(top, bottom))
}

/** Intro header: stronger wash that dissolves into the grouped page background. */
@Composable
fun GameSkin.heroGradient(): Brush =
    Brush.verticalGradient(listOf(washColor(), XpColors.CanvasGrouped))

/**
 * Draws [GameSkin.motif] across [modifier]'s bounds with the game's accent. Purely decorative: it sits
 * behind content, carries no meaning and is clipped by the surface that hosts it.
 */
@Composable
fun GameMotifGraphic(skin: GameSkin, modifier: Modifier = Modifier, strength: Float = 0.18f) {
    val accent = skin.accentColor()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val strong = accent.copy(alpha = strength)
        val soft = accent.copy(alpha = strength * 0.55f)
        val hair = 1.5.dp.toPx()
        when (skin.motif) {
            GameMotif.Blob -> {
                drawCircle(strong, radius = w * 0.42f, center = Offset(w * 0.96f, h * -0.04f))
                drawCircle(soft, radius = w * 0.26f, center = Offset(w * 0.68f, h * 0.46f))
                drawCircle(soft, radius = w * 0.14f, center = Offset(w * 0.16f, h * 0.92f))
            }
            GameMotif.Rays -> {
                val origin = Offset(w * 1.08f, h * -0.12f)
                for (i in 0 until 6) {
                    val angle = (16.0 + i * 13.0) * PI / 180.0
                    val length = h * (0.78f - i * 0.08f)
                    drawLine(
                        color = if (i % 2 == 0) strong else soft,
                        start = origin,
                        end = Offset(
                            origin.x - (length * cos(angle)).toFloat(),
                            origin.y + (length * sin(angle)).toFloat(),
                        ),
                        strokeWidth = (8 - i).coerceAtLeast(2).dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            GameMotif.Grid -> {
                for (row in 0 until 4) {
                    for (col in 0 until 5) {
                        val x = w * (0.14f + col * 0.19f)
                        val y = h * (0.18f + row * 0.23f)
                        drawCircle(
                            color = if ((row + col) % 2 == 0) strong else soft,
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }
            }
            GameMotif.Confetti -> {
                for (i in 0 until 16) {
                    val x = w * (((i * 37) % 100) / 100f)
                    val y = h * (((i * 61 + 13) % 100) / 100f)
                    drawCircle(
                        color = if (i % 3 == 0) strong else soft,
                        radius = (2f + (i % 3)).dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
            GameMotif.Sparks -> {
                for (i in 0 until 5) {
                    val x = w * (0.16f + (i % 3) * 0.33f)
                    val y = h * (0.22f + (i / 3) * 0.42f)
                    val arm = (6 + (i % 3) * 2).dp.toPx()
                    drawLine(strong, Offset(x - arm, y), Offset(x + arm, y), strokeWidth = hair, cap = StrokeCap.Round)
                    drawLine(strong, Offset(x, y - arm), Offset(x, y + arm), strokeWidth = hair, cap = StrokeCap.Round)
                }
            }
            GameMotif.Rings -> {
                val center = Offset(w * 0.9f, h * 0.12f)
                for (i in 1..3) {
                    drawCircle(
                        color = if (i % 2 == 0) soft else strong,
                        radius = w * 0.17f * i,
                        center = center,
                        style = Stroke(width = hair),
                    )
                }
            }
            GameMotif.Waves -> {
                for (i in 0 until 3) {
                    val path = Path()
                    val baseline = h * (0.2f + i * 0.26f)
                    var x = -w * 0.1f
                    var first = true
                    while (x <= w * 1.1f) {
                        val y = baseline + sin((x / w) * 2f * PI.toFloat() + i * 1.3f) * h * 0.05f
                        if (first) {
                            path.moveTo(x, y)
                            first = false
                        } else {
                            path.lineTo(x, y)
                        }
                        x += w / 24f
                    }
                    drawPath(path, if (i == 1) strong else soft, style = Stroke(width = hair))
                }
            }
            GameMotif.Orbit -> {
                val center = Offset(w * 0.84f, h * 0.24f)
                drawCircle(soft, radius = w * 0.16f, center = center, style = Stroke(width = hair))
                drawOval(
                    color = strong,
                    topLeft = Offset(center.x - w * 0.3f, center.y - h * 0.13f),
                    size = Size(w * 0.6f, h * 0.26f),
                    style = Stroke(width = hair),
                )
                drawCircle(strong, radius = 3.dp.toPx(), center = Offset(center.x + w * 0.3f, center.y))
            }
        }
    }
}
