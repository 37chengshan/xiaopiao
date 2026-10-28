package com.xiaopiao.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One complete set of colors. See docs/DESIGN.md §2 — names mirror the web tokens. */
data class XpPalette(
    val dark: Boolean,
    val canvas: Color,
    val canvasGrouped: Color,
    val card: Color,
    val surface2: Color,
    val fill: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val hairline: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val stamp: Color,
    val paper: Color,
    val paperShade: Color,
    val paperInk: Color,
    val scrim: Color,
    val material: Color,
    val printerBody: Color,
    val printerHighlight: Color,
    val printerSlit: Color,
)

val LightPalette = XpPalette(
    dark = false,
    canvas = Color(0xFFFAF9F5),
    canvasGrouped = Color(0xFFF3F1EA),
    card = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0EEE6),
    fill = Color(0xFFE9E6DC),
    ink = Color(0xFF141413),
    ink2 = Color(0xFF63625C),
    ink3 = Color(0xFF8A8880),
    hairline = Color(0x17141413),
    accent = Color(0xFFB04A2E),
    accentSoft = Color(0xFFF7EAE3),
    onAccent = Color(0xFFFFFFFF),
    positive = Color(0xFF3F7D4E),
    negative = Color(0xFFB3412F),
    warning = Color(0xFFB7791F),
    stamp = Color(0xFFB8322A),
    paper = Color(0xFFFBFAF6),
    paperShade = Color(0xFFEFEADF),
    paperInk = Color(0xFF2A2724),
    scrim = Color(0x52141413),
    material = Color(0xC7FAF9F5),
    printerBody = Color(0xFF1F1E1C),
    printerHighlight = Color(0xFF3A3935),
    printerSlit = Color(0xFF0A0A09),
)

val DarkPalette = XpPalette(
    dark = true,
    canvas = Color(0xFF1F1E1C),
    canvasGrouped = Color(0xFF1A1918),
    card = Color(0xFF2A2926),
    surface2 = Color(0xFF34332F),
    fill = Color(0xFF3D3C37),
    ink = Color(0xFFF5F4EE),
    ink2 = Color(0xFFB4B1A7),
    ink3 = Color(0xFF7C7A72),
    hairline = Color(0x1AF5F4EE),
    accent = Color(0xFFD97757),
    accentSoft = Color(0xFF33241E),
    onAccent = Color(0xFF1F1E1C),
    positive = Color(0xFF7DB38A),
    negative = Color(0xFFE0705C),
    warning = Color(0xFFE0A84E),
    stamp = Color(0xFFC8463B),
    paper = Color(0xFFE9E6DD),
    paperShade = Color(0xFFDAD5C8),
    paperInk = Color(0xFF24211E),
    scrim = Color(0x8C000000),
    material = Color(0xB81F1E1C),
    printerBody = Color(0xFF0E0E0D),
    printerHighlight = Color(0xFF2A2926),
    printerSlit = Color(0xFF000000),
)

/** Current palette, swapped by [XiaopiaoTheme]. Snapshot state, so composition and draw scopes that read it update. */
internal object XpPaletteState {
    var current by mutableStateOf(LightPalette)
}

/**
 * Design tokens (docs/DESIGN.md). Screens must use these instead of raw colors.
 * Every value follows the active light/dark palette.
 */
object XpColors {
    private val p get() = XpPaletteState.current

    val isDark get() = p.dark
    val Canvas get() = p.canvas
    val CanvasGrouped get() = p.canvasGrouped
    val Card get() = p.card
    val Surface2 get() = p.surface2
    val Fill get() = p.fill
    val Ink get() = p.ink
    val InkSecondary get() = p.ink2
    val InkTertiary get() = p.ink3
    val Hairline get() = p.hairline
    /** Cinnabar (印泥朱砂, same family as the stamp). Scarce: primary action, selected tab, the one highlighted datum. */
    val Accent get() = p.accent
    val AccentSoft get() = p.accentSoft
    val OnAccent get() = p.onAccent
    val Positive get() = p.positive
    val Negative get() = p.negative
    val Warning get() = p.warning
    val Stamp get() = p.stamp
    /** Translucent bar background for nav / tab bars. */
    val Material get() = p.material

    /** Receipt paper stays paper-coloured in dark mode, just dimmer. */
    val Paper get() = p.paper
    val PaperShade get() = p.paperShade
    val PaperInk get() = p.paperInk
    val PrinterBody get() = p.printerBody
    val PrinterHighlight get() = p.printerHighlight
    val PrinterSlit get() = p.printerSlit
    val Scrim get() = p.scrim
    /** Toasts invert the theme: ink capsule with canvas text. */
    val ToastBackground get() = p.ink.copy(alpha = 0.95f)
    val ToastText get() = p.canvas
    val Shimmer get() = p.surface2
    val ShimmerHighlight get() = p.fill
}

object XpSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object XpRadius {
    val s = 10.dp
    /** Grouped list containers. */
    val m = 14.dp
    val l = 20.dp
    val xl = 28.dp
    val pill = 999.dp
}

/** Shared spring recipes (docs/DESIGN.md §5). Default UI motion is critically damped — no bounce. */
object XpMotion {
    /** Default for position/size: damping 1.0, response ≈ 0.35s. */
    fun <T> snappy() = androidx.compose.animation.core.spring<T>(dampingRatio = 1f, stiffness = 400f)
    /** Momentum / ceremony only (flick, tear, stamp, awards). */
    fun <T> bouncy() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.72f, stiffness = 380f)
    /** Sheets and drawers. */
    fun <T> gentle() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.85f, stiffness = 300f)
}

/** Easing curves (docs/DESIGN.md §5). Strong ease-out for color/opacity and anything entering. */
object XpEase {
    val out = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
}
