package com.xiaopiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import com.xiaopiao.app.ui.motion.XpMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpSpace
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity

/** A horizontal run on the bar, in dp from the bar's left edge. */
data class NavSpan(val start: Float, val end: Float) {
    val width: Float get() = end - start
    val center: Float get() = (start + end) / 2f
}

/**
 * Where everything sits, as a pure function of the bar width (dp) so it can be unit-tested at 320 / 360 / 411 / 480.
 *
 * Two states share five icons. State 0 (记账): `a b ⊕ c` left with ⊕ on the exact screen centre, `entry` alone right.
 * State 1 (社交): `fold ⊕` left, `sa entry sb` right. Slot width is derived from the width, never hard-coded.
 */
object NavBarGeometry {
    const val MARGIN = 16f
    const val PAD = 4f
    /** Clear air between the two capsules (the 圈子 capsule sits a little apart from the main one). */
    const val GAP = 12f
    const val PLUS = 50f
    const val MAX_SLOT = 60f
    const val HEIGHT = 56f
    /** Headroom above the capsules so the raised ⊕ can stand proud of them and still take touches. */
    const val TOP = 10f
    /** How far ⊕ rises above its resting centre in the 记账 state. */
    const val RAISE = 10f
    /** Ring around the raised ⊕, in the capsule colour, so it reads as cut out of the bar. */
    const val RING = 3f

    class Layout(
        val slot: Float,
        val leftCap0: NavSpan, val rightCap0: NavSpan,
        val leftCap1: NavSpan, val rightCap1: NavSpan,
        val a0: Float, val b0: Float, val c0: Float, val plus0: Float, val entry0: Float,
        val fold1: Float, val plus1: Float, val sa1: Float, val entry1: Float, val sb1: Float,
    )

    /** Widest slot such that the ledger-state left capsule (⊕ centred) still clears the right capsule by [GAP]. */
    fun slot(width: Float): Float =
        minOf(MAX_SLOT, (width / 2f - MARGIN - 3f * PAD - PLUS / 2f - GAP) / 2f).coerceAtLeast(0f)

    fun layout(width: Float): Layout {
        val s = slot(width)
        val cx = width / 2f
        val sa1 = width - MARGIN - PAD - 2.5f * s
        return Layout(
            slot = s,
            leftCap0 = NavSpan(cx - PLUS / 2f - 2f * s - PAD, cx + PLUS / 2f + s + PAD),
            rightCap0 = NavSpan(width - MARGIN - s - 2f * PAD, width - MARGIN),
            leftCap1 = NavSpan(MARGIN, MARGIN + 2f * s + 2f * PAD),
            rightCap1 = NavSpan(width - MARGIN - 3f * s - 2f * PAD, width - MARGIN),
            a0 = cx - PLUS / 2f - 1.5f * s, b0 = cx - PLUS / 2f - 0.5f * s, c0 = cx + PLUS / 2f + 0.5f * s,
            plus0 = cx, entry0 = width - MARGIN - PAD - s / 2f,
            fold1 = MARGIN + PAD + s / 2f, plus1 = MARGIN + PAD + 1.5f * s,
            sa1 = sa1, entry1 = sa1 + s, sb1 = sa1 + 2f * s,
        )
    }
}

/** A tab: its id, caption and the hand-drawn glyph that animates its own state change. */
class NavBarItem(val id: String, val label: String, val glyph: NavGlyph)

/** What the bar shows. Left of ⊕ are [a] [b], right of it [c]; [fold] replaces them in the social state. */
class NavBarSpec(
    val a: NavBarItem, val b: NavBarItem, val c: NavBarItem,
    val fold: NavBarItem,
    val entry: NavBarItem,
    val sa: NavBarItem, val sb: NavBarItem,
    val plusLabel: String, val plusDescription: String,
)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * How much of the bottom edge the floating tab bar covers on a page that runs underneath it (docs/DESIGN.md §4.3).
 * 0 when the page stops above the bar. Pages pad their last row / float their input by this much.
 */
val LocalTabBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/**
 * The floating tab bar (docs/APP-DESIGN.md §2): five icons, always. One spring progress carries the whole 记账 ⇄ 社交
 * change, so ⊕ is a single element that shrinks, loses its fill and slides sideways rather than fading out and back in.
 * Selection is an accent colour and a small independent pop on the icon — no pill, no ripple.
 */
@Composable
fun XpNavBar(
    spec: NavBarSpec,
    social: Boolean,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onFold: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    onPlusLongPress: (() -> Unit)? = null,
    /** While the long press is held: finger travel from the press point (px, negative = up). */
    onPlusHold: ((Float) -> Unit)? = null,
    /** The long press ended (finger lifted or the gesture was taken away). */
    onPlusRelease: (() -> Unit)? = null,
    hidden: Boolean = false,
    /** Page content to sample. Non-null turns the capsules and ⊕ into liquid glass; null keeps the opaque bar. */
    backdrop: Backdrop? = null,
) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (social) 1f else 0f) }
    LaunchedEffect(social) {
        val target = if (social) 1f else 0f
        if (reduced) progress.snapTo(target) else progress.animateTo(target, XpMotion.snappy())
    }
    val pos = progress.value
    val p = pos.coerceIn(0f, 1f)
    val hide by animateFloatAsState(if (hidden) 1f else 0f, XpMotion.snappy(0.001f), label = "navHide")

    val dark = XpColors.isDark
    val capsuleColor = if (dark) XpColors.Surface2.copy(alpha = 0.96f) else XpColors.Card.copy(alpha = 0.96f)
    val shape = RoundedCornerShape(NavBarGeometry.HEIGHT.dp / 2)
    // iOS-style float: soft, low shadow (y≈6dp, blur≈14–20dp, 8–10% black). Dark mode swaps it for a 1dp highlight rim.
    val lift = if (dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    else Modifier.shadow(14.dp, shape, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))

    BoxWithConstraints(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = XpSpace.s)
            .graphicsLayer { translationY = hide * (size.height + 24.dp.toPx()); alpha = 1f - hide },
    ) {
        val g = remember(maxWidth.value) { NavBarGeometry.layout(maxWidth.value) }
        val s = g.slot
        val interactive = !hidden
        // Finger-follow light (glass only): watched on the Initial pass so the tabs still get their clicks.
        val light = remember { GlassLight() }
        val scope = rememberCoroutineScope()

        Box(
            Modifier.fillMaxWidth().height((NavBarGeometry.HEIGHT + NavBarGeometry.TOP).dp)
                .then(if (backdrop != null && !reduced) Modifier.pointerInput(Unit) { trackGlassLight(light, scope) } else Modifier),
        ) {
            Capsule(lerpF(g.leftCap0.start, g.leftCap1.start, pos), lerpF(g.leftCap0.end, g.leftCap1.end, pos), shape, lift, capsuleColor, backdrop, light)
            Capsule(lerpF(g.rightCap0.start, g.rightCap1.start, pos), lerpF(g.rightCap0.end, g.rightCap1.end, pos), shape, lift, capsuleColor, backdrop, light)

            // 记账 side: a · b · c converge on the folded 账单 slot while fading.
            val fadeOut = 1f - p
            Slot(lerpF(g.a0, g.fold1, pos), s, fadeOut) { NavBarTab(spec.a, selectedId == spec.a.id, interactive && !social) { onSelect(spec.a.id) } }
            Slot(lerpF(g.b0, g.fold1, pos), s, fadeOut) { NavBarTab(spec.b, selectedId == spec.b.id, interactive && !social) { onSelect(spec.b.id) } }
            Slot(lerpF(g.c0, g.fold1, pos), s, fadeOut) { NavBarTab(spec.c, selectedId == spec.c.id, interactive && !social) { onSelect(spec.c.id) } }
            Slot(g.fold1, s, p) { NavBarTab(spec.fold, false, interactive && social, onClick = onFold) }

            // 社交 side: AI and 游戏 unfold from behind the entry item, which slides to the middle of the three.
            Slot(lerpF(g.entry0, g.sa1, pos), s, p) { NavBarTab(spec.sa, selectedId == spec.sa.id, interactive && social) { onSelect(spec.sa.id) } }
            Slot(lerpF(g.entry0, g.sb1, pos), s, p) { NavBarTab(spec.sb, selectedId == spec.sb.id, interactive && social) { onSelect(spec.sb.id) } }
            Slot(lerpF(g.entry0, g.entry1, pos), s, 1f) { NavBarTab(spec.entry, selectedId == spec.entry.id, interactive) { onSelect(spec.entry.id) } }

            // ⊕: one element for both states.
            // Taller than the capsule: the raised disc must stay inside the touch area.
            Slot(lerpF(g.plus0, g.plus1, pos), lerpF(NavBarGeometry.PLUS, s, pos), 1f, top = 0f, height = NavBarGeometry.HEIGHT + NavBarGeometry.TOP) {
                NavBarPlus(spec, p, interactive, capsuleColor, onPlus, onPlusLongPress, backdrop, onPlusHold, onPlusRelease)
            }
        }
    }
}

@Composable
private fun Capsule(
    start: Float, end: Float, shape: RoundedCornerShape, lift: Modifier, color: Color,
    backdrop: Backdrop?, light: GlassLight,
) {
    val frame = Modifier.offset(x = start.dp, y = NavBarGeometry.TOP.dp).width((end - start).coerceAtLeast(0f).dp).height(NavBarGeometry.HEIGHT.dp)
    if (backdrop == null) {
        Box(frame.then(lift).clip(shape).background(color))
        return
    }
    val dark = XpColors.isDark
    val density = LocalDensity.current
    val film = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.42f)
    // The one glass recipe (LiquidGlass.kt): vibrancy + scatter + edge lens + film + lit rim, and the
    // finger light with its swell. No drop shadow — the capsule is a selection surface, not a floating card.
    Box(
        frame.liquidGlass(
            backdrop = backdrop,
            shape = shape,
            film = film,
            rim = Color.Black.copy(alpha = if (dark) 0.28f else 0.10f),
            blur = 4.dp,
            lensHeight = 18.dp,
            lensAmount = 24.dp,
            light = light,
            track = false,
            origin = { Offset(with(density) { start.dp.toPx() }, with(density) { NavBarGeometry.TOP.dp.toPx() }) },
            swell = 1.03f,
            maxTilt = 4f,
        ),
    )
}

/** Positions [content] by its centre x (dp) and fades it; the slot is as tall as the bar. */
@Composable
private fun Slot(
    center: Float, width: Float, alpha: Float, top: Float = NavBarGeometry.TOP, height: Float = NavBarGeometry.HEIGHT,
    content: @Composable () -> Unit,
) {
    if (alpha <= 0.001f) return
    Box(
        Modifier.offset(x = (center - width / 2f).dp, y = top.dp).width(width.dp).height(height.dp)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val LabelSize = 10.sp
private val LabelLine = 12.sp

@Composable
private fun NavBarLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    // One line, never wrapped: a 2-char label must not turn into a vertical stack on a narrow slot or a big font.
    Text(
        text, fontSize = LabelSize, lineHeight = LabelLine, fontWeight = FontWeight.Medium, color = color,
        maxLines = 1, softWrap = false, modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
    )
}

/**
 * Tab item: no pill, no ripple. Selecting recolours to the accent, fills the glyph and plays its own motion
 * (lines written, paper printed, bars grown…) with a slight 1 → 1.08 → 1 lift; tapping it again replays.
 */
@Composable
private fun NavBarTab(item: NavBarItem, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val reduced = rememberReducedMotion()
    val haptics = rememberXpHaptics()
    val scope = rememberCoroutineScope()
    val color by animateColorAsState(if (selected) XpColors.Accent else XpColors.InkSecondary, XpMotion.fade(), label = "tabColor")
    val scale = remember { Animatable(1f) }
    var replay by remember { mutableStateOf(0) }
    suspend fun lift() {
        if (reduced) return
        scale.snapTo(1f)
        scale.animateTo(1.08f, XpMotion.pressIn())
        scale.animateTo(1f, XpMotion.bouncy())
    }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (first) first = false else if (selected) lift()
    }
    Column(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
            haptics.select()
            if (selected) { replay++; scope.launch { lift() } }
            onClick()
        }.semantics(mergeDescendants = true) { contentDescription = item.label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        AnimatedNavGlyph(
            item.glyph, selected = selected, tint = color, reduced = reduced, replay = replay,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
        NavBarLabel(item.label, color)
    }
}

/**
 * ⊕ at progress [p] (0 = 记账 centre, 1 = folded beside 账单): a 56dp accent disc with a 28dp glyph becomes a plain
 * 24dp glyph with a caption, in InkSecondary. Tap = record, long-press = voice.
 */
@Composable
private fun NavBarPlus(
    spec: NavBarSpec, p: Float, enabled: Boolean, ring: Color, onClick: () -> Unit, onLongPress: (() -> Unit)?,
    backdrop: Backdrop? = null,
    onHold: ((Float) -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
) {
    val haptics = rememberXpHaptics()
    var pressed by remember { mutableStateOf(false) }
    val hold by rememberUpdatedState(onHold)
    val release by rememberUpdatedState(onRelease)
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, if (pressed) XpMotion.pressIn() else XpMotion.pressOut(), label = "plusPress")
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongPress)
    val live by rememberUpdatedState(enabled)
    Box(
        Modifier.fillMaxSize().graphicsLayer { scaleX = press; scaleY = press }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!live) return@awaitEachGesture
                    pressed = true
                    val up = awaitLongPressOrCancellation(down.id)
                    if (up == null) {
                        pressed = false
                        // Released (or cancelled) before the long-press timeout: a tap only if the finger actually lifted on us.
                        haptics.select()
                        click()
                    } else if (long != null) {
                        haptics.thud()
                        long?.invoke()
                        // Hold-to-talk: follow the finger until it lifts, so sliding up can cancel.
                        val origin = up.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == up.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            hold?.invoke(change.position.y - origin.y)
                        }
                        release?.invoke()
                        pressed = false
                    } else {
                        waitForUpOrCancellation()
                        pressed = false
                        haptics.select()
                        click()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The slot is TOP taller than the capsule, so its middle sits TOP/2 above the capsule's: shift back down by TOP/2,
        // then lift by RAISE while the disc is showing (p = 0) and settle at the capsule's centre as a plain glyph (p = 1).
        val rest = NavBarGeometry.TOP / 2f
        val lift = NavBarGeometry.RAISE * (1f - p)
        val disc = (1f - p * 1.4f).coerceIn(0f, 1f)
        val discScale = Modifier.graphicsLayer { scaleX = 1f - 0.45f * p; scaleY = 1f - 0.45f * p; alpha = disc }
        if (backdrop != null) {
            // The one glass that is accent-tinted: colour fringing at the bevel and a lit inner edge. It is
            // still the shared recipe, so it gets the same finger light and the same press swell.
            val accent = XpColors.Accent
            val plusLight = rememberGlassLight()
            Box(
                Modifier.offset(y = (rest - lift).dp).size(NavBarGeometry.PLUS.dp).then(discScale)
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = CircleShape,
                        film = accent.copy(alpha = 0.80f),
                        rim = accent.copy(alpha = 0.45f),
                        blur = 2.dp,
                        lensHeight = 12.dp,
                        lensAmount = 18.dp,
                        chromatic = true,
                        innerRim = Color.White.copy(alpha = 0.35f),
                        fallback = accent,
                        light = plusLight,
                        swell = 1.07f,
                        maxTilt = 8f,
                    ),
            )
        } else {
            val outer = NavBarGeometry.PLUS + NavBarGeometry.RING * 2
            Box(
                Modifier.offset(y = (rest - lift).dp).size(outer.dp).then(discScale)
                    .shadow(10.dp, CircleShape, clip = false, ambientColor = XpColors.Accent.copy(alpha = 0.35f), spotColor = XpColors.Accent.copy(alpha = 0.45f))
                    .clip(CircleShape).background(ring).padding(NavBarGeometry.RING.dp)
                    .clip(CircleShape).background(XpColors.Accent),
            )
        }
        Icon(
            Icons.Outlined.Add, contentDescription = spec.plusDescription,
            tint = lerp(XpColors.OnAccent, XpColors.InkSecondary, p),
            modifier = Modifier.offset(y = (rest - lift - 6.dp.value * p).dp).size(lerpDp(26.dp, 22.dp, p)),
        )
        NavBarLabel(spec.plusLabel, XpColors.InkSecondary, Modifier.offset(y = (rest + 12f).dp).graphicsLayer { alpha = p })
    }
}

private fun lerpDp(a: Dp, b: Dp, t: Float): Dp = a + (b - a) * t
