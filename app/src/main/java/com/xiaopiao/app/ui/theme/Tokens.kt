package com.xiaopiao.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The two visual systems we ship (docs/APP-DESIGN.md §1). Switchable in 我的 → 外观. */
enum class DesignStyle(val label: String) { Apple("Apple"), Claude("Claude") }

/** One complete set of colors. See docs/APP-DESIGN.md §1 — names mirror the web tokens. */
data class XpPalette(
    val style: DesignStyle,
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
    /** Filled button colour. Apple = Action Blue; Claude = warm coral. */
    val primary: Color,
    val onPrimary: Color,
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
    val info: Color,
    val infoSoft: Color,
    val positiveSoft: Color,
    val warningSoft: Color,
)

// Apple — values from the Apple DESIGN.md analysis: system grays, one Action Blue, SF-like type.
val AppleLightPalette = XpPalette(
    style = DesignStyle.Apple,
    dark = false,
    canvas = Color(0xFFFFFFFF),
    canvasGrouped = Color(0xFFF5F5F7),
    card = Color(0xFFFFFFFF),
    surface2 = Color(0xFFFAFAFC),
    fill = Color(0xFFEDEDF0),
    ink = Color(0xFF1D1D1F),
    ink2 = Color(0xFF6E6E73),
    ink3 = Color(0xFFA1A1A6),
    hairline = Color(0xFFE0E0E0),
    accent = Color(0xFF0066CC),
    accentSoft = Color(0xFFE9F1FB),
    onAccent = Color(0xFFFFFFFF),
    primary = Color(0xFF0066CC),
    onPrimary = Color(0xFFFFFFFF),
    positive = Color(0xFF248A3D),
    negative = Color(0xFFD70015),
    warning = Color(0xFFB25000),
    stamp = Color(0xFFC8402F),
    paper = Color(0xFFFFFFFF),
    paperShade = Color(0xFFF0F0F2),
    paperInk = Color(0xFF1D1D1F),
    scrim = Color(0x521D1D1F),
    material = Color(0xE6FFFFFF),
    printerBody = Color(0xFF1D1D1F),
    printerHighlight = Color(0xFF48484A),
    printerSlit = Color(0xFF000000),
    info = Color(0xFF0066CC),
    infoSoft = Color(0xFFE9F1FB),
    positiveSoft = Color(0xFFE7F6EC),
    warningSoft = Color(0xFFFFF4E5),
)

val AppleDarkPalette = XpPalette(
    style = DesignStyle.Apple,
    dark = true,
    canvas = Color(0xFF000000),
    canvasGrouped = Color(0xFF1C1C1E),
    card = Color(0xFF1C1C1E),
    surface2 = Color(0xFF2C2C2E),
    fill = Color(0xFF3A3A3C),
    ink = Color(0xFFF5F5F7),
    ink2 = Color(0xFF98989D),
    ink3 = Color(0xFF6E6E73),
    hairline = Color(0xFF38383A),
    accent = Color(0xFF2997FF),
    accentSoft = Color(0xFF12263D),
    onAccent = Color(0xFFFFFFFF),
    primary = Color(0xFF0A84FF),
    onPrimary = Color(0xFFFFFFFF),
    positive = Color(0xFF30D158),
    negative = Color(0xFFFF453A),
    warning = Color(0xFFFF9F0A),
    stamp = Color(0xFFFF6961),
    paper = Color(0xFFEDEDEF),
    paperShade = Color(0xFFD8D8DC),
    paperInk = Color(0xFF1D1D1F),
    scrim = Color(0x8C000000),
    material = Color(0xE61C1C1E),
    printerBody = Color(0xFF0A0A0B),
    printerHighlight = Color(0xFF2C2C2E),
    printerSlit = Color(0xFF000000),
    info = Color(0xFF64D2FF),
    infoSoft = Color(0xFF0E2A3A),
    positiveSoft = Color(0xFF0F2A1A),
    warningSoft = Color(0xFF33260C),
)

// Claude — the warm-canvas editorial system: cream surfaces, coral CTA, serif display.
val ClaudeLightPalette = XpPalette(
    style = DesignStyle.Claude,
    dark = false,
    canvas = Color(0xFFFAF9F5),
    canvasGrouped = Color(0xFFF5F0E8),
    card = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF5F0E8),
    fill = Color(0xFFE8E0D2),
    ink = Color(0xFF141413),
    ink2 = Color(0xFF6C6A64),
    ink3 = Color(0xFF8E8B82),
    hairline = Color(0xFFE6DFD8),
    accent = Color(0xFFCC785C),
    accentSoft = Color(0xFFF7EAE3),
    onAccent = Color(0xFFFFFFFF),
    primary = Color(0xFFCC785C),
    onPrimary = Color(0xFFFFFFFF),
    positive = Color(0xFF5DB872),
    negative = Color(0xFFC64545),
    warning = Color(0xFFD4A017),
    stamp = Color(0xFFC64545),
    paper = Color(0xFFFBFAF6),
    paperShade = Color(0xFFEFEADF),
    paperInk = Color(0xFF2A2724),
    scrim = Color(0x52141413),
    material = Color(0xC7FAF9F5),
    printerBody = Color(0xFF1F1E1C),
    printerHighlight = Color(0xFF3A3935),
    printerSlit = Color(0xFF0A0A09),
    info = Color(0xFF3A6EA5),
    infoSoft = Color(0xFFE6EEF6),
    positiveSoft = Color(0xFFE5F0E8),
    warningSoft = Color(0xFFF8EEDC),
)

val ClaudeDarkPalette = XpPalette(
    style = DesignStyle.Claude,
    dark = true,
    canvas = Color(0xFF181715),
    canvasGrouped = Color(0xFF1F1E1B),
    card = Color(0xFF252320),
    surface2 = Color(0xFF2C2A26),
    fill = Color(0xFF35322D),
    ink = Color(0xFFFAF9F5),
    ink2 = Color(0xFFA09D96),
    ink3 = Color(0xFF6C6A64),
    hairline = Color(0xFF33302B),
    accent = Color(0xFFD97757),
    accentSoft = Color(0xFF33241E),
    onAccent = Color(0xFF181715),
    primary = Color(0xFFD97757),
    onPrimary = Color(0xFFFFFFFF),
    positive = Color(0xFF7DB38A),
    negative = Color(0xFFE0705C),
    warning = Color(0xFFE0A84E),
    stamp = Color(0xFFD96156),
    paper = Color(0xFFE9E6DD),
    paperShade = Color(0xFFDAD5C8),
    paperInk = Color(0xFF24211E),
    scrim = Color(0x8C000000),
    material = Color(0xB81F1E1B),
    printerBody = Color(0xFF0E0E0D),
    printerHighlight = Color(0xFF2A2926),
    printerSlit = Color(0xFF000000),
    info = Color(0xFF7FA8D6),
    infoSoft = Color(0xFF1F2A37),
    positiveSoft = Color(0xFF1F2E24),
    warningSoft = Color(0xFF33291A),
)

/** Current palette, swapped by [XiaopiaoTheme]. Snapshot state, so composition and draw scopes that read it update. */
internal object XpPaletteState {
    var base by mutableStateOf(AppleLightPalette)
    var current by mutableStateOf(AppleLightPalette)
    var style by mutableStateOf(DesignStyle.Apple)
    /** Non-null while a sub-product (小店版) is open: overrides the user's palette wholesale. */
    var override by mutableStateOf<XpPalette?>(null)

    fun apply() { current = override ?: base }

    fun applyBase(p: XpPalette) { base = p; apply() }

    fun applyOverride(p: XpPalette?) { override = p; apply() }
}

/** Derives the 小店版 skin from the active palette: same system, different accent and canvas weight. */
fun merchantPalette(base: XpPalette): XpPalette {
    val green = if (base.dark) Color(0xFF4FBF8B) else Color(0xFF2F6B57)
    val greenSoft = if (base.dark) Color(0xFF14301F) else Color(0xFFE4F1EA)
    return base.copy(
        accent = green, accentSoft = greenSoft, primary = green, onPrimary = Color(0xFFFFFFFF),
        info = green, infoSoft = greenSoft,
        canvas = if (base.dark) Color(0xFF141815) else Color(0xFFF6F7F5),
        canvasGrouped = if (base.dark) Color(0xFF1A1F1C) else Color(0xFFEFF1EE),
    )
}

/** Runs [content] with a palette override (used by the 小店 global mode). */
@Composable
fun XpPaletteOverride(palette: XpPalette, content: @Composable () -> Unit) {
    androidx.compose.runtime.DisposableEffect(palette) {
        XpPaletteState.applyOverride(palette)
        onDispose { XpPaletteState.applyOverride(null) }
    }
    content()
}

fun paletteFor(style: DesignStyle, dark: Boolean): XpPalette = when (style) {
    DesignStyle.Apple -> if (dark) AppleDarkPalette else AppleLightPalette
    DesignStyle.Claude -> if (dark) ClaudeDarkPalette else ClaudeLightPalette
}

/**
 * Design tokens (docs/APP-DESIGN.md). Screens must use these instead of raw colors.
 * Every value follows the active light/dark palette and design style.
 */
object XpColors {
    private val p get() = XpPaletteState.current

    val style get() = p.style
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
    /** The one interactive colour: Action Blue (Apple) or coral (Claude). */
    val Accent get() = p.accent
    val AccentSoft get() = p.accentSoft
    val OnAccent get() = p.onAccent
    /** Filled button pair — use these instead of hardcoding ink. */
    val Primary get() = p.primary
    val OnPrimary get() = p.onPrimary
    val Positive get() = p.positive
    val Negative get() = p.negative
    val Warning get() = p.warning
    val Stamp get() = p.stamp
    /** Semantic tints for icon blocks: a hue + its soft background. */
    val Info get() = p.info
    val InfoSoft get() = p.infoSoft
    val PositiveSoft get() = p.positiveSoft
    val WarningSoft get() = p.warningSoft
    /** Translucent bar background for nav / tab bars. */
    val Material get() = p.material

    /** Receipt paper keeps its own surface so the printed object never becomes UI chrome. */
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

/** Corner radii follow the active style: Apple 8/11/18, Claude 10/14/20. */
object XpRadius {
    private val apple get() = XpColors.style == DesignStyle.Apple
    val s get() = if (apple) 8.dp else 10.dp
    val m get() = if (apple) 11.dp else 14.dp
    val l get() = if (apple) 18.dp else 20.dp
    val xl = 28.dp
    val pill = 999.dp
}

/** Shared spring recipes (docs/APP-DESIGN.md §7). Default UI motion is critically damped — no bounce. */
object XpMotion {
    /** Default for position/size: damping 1.0, response ≈ 0.35s. */
    fun <T> snappy() = androidx.compose.animation.core.spring<T>(dampingRatio = 1f, stiffness = 400f)
    /** Momentum / ceremony only (flick, tear, stamp, awards). */
    fun <T> bouncy() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.72f, stiffness = 380f)
    /** Sheets and drawers. */
    fun <T> gentle() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.85f, stiffness = 300f)
}

/** Easing curves (docs/APP-DESIGN.md §7). Strong ease-out for color/opacity and anything entering. */
object XpEase {
    val out = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
}

/** Inter carries Latin text and every digit; CJK falls through to the system font. */
object XpFonts {
    val sans = androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Font(com.xiaopiao.app.R.font.inter_regular, androidx.compose.ui.text.font.FontWeight.Normal),
        androidx.compose.ui.text.font.Font(com.xiaopiao.app.R.font.inter_medium, androidx.compose.ui.text.font.FontWeight.Medium),
        androidx.compose.ui.text.font.Font(com.xiaopiao.app.R.font.inter_semibold, androidx.compose.ui.text.font.FontWeight.SemiBold),
    )
    /** Serif display — Claude style only. */
    val serif = androidx.compose.ui.text.font.FontFamily.Serif
}
