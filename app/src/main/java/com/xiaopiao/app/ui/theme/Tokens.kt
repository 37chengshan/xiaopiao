package com.xiaopiao.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One complete set of colors. See docs/APP-DESIGN.md §1 — names mirror the web tokens. */
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
    /** Filled button colour (ink; the accent is reserved for the one primary action). */
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
    /** Warm yellow for emphasis surfaces (e.g. the ledger's summary card). */
    val highlight: Color,
    val highlightSoft: Color,
    /** A raised element inside a track (segmented thumb, toggle knob): always lighter than the track. */
    val raised: Color,
    /** Switch knob: white in both modes, as on iOS (a dark knob on the dark track is invisible). */
    val knob: Color,
)

// One visual system (Apple-derived: system grays, SF-like type); colour themes in Accent.kt layer on top.
val LightPalette = XpPalette(
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
    accent = Color(0xFF0071E3),
    accentSoft = Color(0xFFDBEBFB),
    onAccent = Color(0xFFFFFFFF),
    primary = Color(0xFF1D1D1F),
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
    highlight = Color(0xFFFFC93C),
    highlightSoft = Color(0xFFFFF6D6),
    raised = Color(0xFFFFFFFF),
    knob = Color(0xFFFFFFFF),
)

val DarkPalette = XpPalette(
    dark = true,
    canvas = Color(0xFF000000),
    // The grouped page is the black canvas and the card is #1C1C1E on top of it. When both were #1C1C1E the
    // ledger / stats / settings cards had no edge at all in dark mode — only their hairline was left.
    canvasGrouped = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    surface2 = Color(0xFF2C2C2E),
    fill = Color(0xFF3A3A3C),
    ink = Color(0xFFF5F5F7),
    ink2 = Color(0xFF98989D),
    ink3 = Color(0xFF6E6E73),
    hairline = Color(0xFF38383A),
    accent = Color(0xFF2997FF),
    accentSoft = Color(0xFF1F3A54),
    onAccent = Color(0xFF1D1D1F),
    primary = Color(0xFFF5F5F7),
    onPrimary = Color(0xFF1D1D1F),
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
    highlight = Color(0xFFFFD60A),
    highlightSoft = Color(0xFF3A3110),
    // iOS dark segmented knob: lighter than the #3A3A3C track. Card (#1C1C1E) would sit *below* the track and
    // read as a hole cut in it.
    raised = Color(0xFF636366),
    knob = Color(0xFFFFFFFF),
)

/** Current palette, swapped by [XiaopiaoTheme]. Snapshot state, so composition and draw scopes that read it update. */
internal object XpPaletteState {
    var base by mutableStateOf(LightPalette)
    var current by mutableStateOf(LightPalette)
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
        accent = green, accentSoft = greenSoft, onAccent = Contrast.onAccent(green), primary = green, onPrimary = Color(0xFFFFFFFF),
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

fun paletteFor(dark: Boolean): XpPalette = if (dark) DarkPalette else LightPalette

/**
 * Design tokens (docs/APP-DESIGN.md). Screens must use these instead of raw colors.
 * Every value follows the active light/dark palette and colour theme.
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
    /** The one interactive colour: the user's colour theme accent (blue by default). */
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
    val Highlight get() = p.highlight
    val HighlightSoft get() = p.highlightSoft
    /** Thumb/knob inside a Fill track (segmented control); darker themes raise it instead of lowering it. */
    val Raised get() = p.raised
    /** Switch knob — white in both modes. */
    val Knob get() = p.knob
    /** Translucent bar background for nav / tab bars. */
    val Material get() = p.material

    /**
     * Brand coral (docs/BRAND.md): the launcher tile, splash and sign-in mark only. Never a UI accent — the
     * accent stays the theme's blue. Same in every theme, like the launcher icon.
     */
    val Brand = androidx.compose.ui.graphics.Color(0xFFFF6B4A)

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
    /** Page gutter for the ledger / report screens (§3.7): 20dp, not 16. */
    val page = 20.dp
    /** Gap between two cards in the same page (§3.7): 14dp. */
    val cardGap = 14.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii: 8/11/18. */
object XpRadius {
    val s = 8.dp
    val m = 11.dp
    val l = 18.dp
    /** List cards (§3.7): 10dp — the ledger's day cards. */
    val listCard = 10.dp
    /** Content cards sitting on the page (§3.7): summary, filter blocks. */
    val contentCard = 14.dp
    val xl = 28.dp
    /** Full-screen sheets (settings sub-pages, pickers): measured from the wallet reference. */
    val sheet = 32.dp
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
}
