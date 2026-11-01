package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.xiaopiao.app.ui.theme.XpColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.isSpecified

/**
 * The one liquid-glass recipe (tt 2026-10-30: 把液态玻璃的样板固定下来). Every glass surface in the app —
 * tab bar capsules, the ⊕, segmented thumbs, pills, toasts, sheets, drawers — is drawn by [liquidGlass],
 * so glass never drifts into five slightly different recipes again.
 *
 * What the sample is, in order of drawing:
 * 1. `vibrancy` + `blur` — the page behind comes through, colour intact.
 * 2. `lens` — a bevel at the edge bends the backdrop, the way a real thick pane would.
 * 3. `Highlight` rim — the lit edge that says “glass” rather than “frosted card”.
 * 4. A film over the surface for text contrast.
 * 5. **A finger light** ([GlassLight]): a soft additive glow that follows the touch, plus a small swell and
 *    a 3D tilt towards the finger, springing back with a little overshoot. No drop shadow — depth comes
 *    from the light and the rim, so a pressed control reads as pressed into the page rather than lifted off it.
 *
 * [backdrop] must record a *sibling* of this element (a page's list, never a parent that contains this
 * element); null draws the opaque fallback, so callers never need two code paths.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop?,
    shape: Shape,
    film: Color = XpColors.Card.copy(alpha = if (XpColors.isDark) 0.40f else 0.55f),
    rim: Color = Color.Black.copy(alpha = if (XpColors.isDark) 0.24f else 0.06f),
    blur: Dp = 10.dp,
    lensHeight: Dp = 8.dp,
    lensAmount: Dp = 12.dp,
    /** The lens bends the backdrop. Turn it off when the backdrop is text that must not move. */
    lens: Boolean = true,
    /** Colour fringing at the bevel. Only the hero ⊕ earns it. */
    chromatic: Boolean = false,
    /** A bright inner edge, for glass that sits proud of the page (the accent ⊕). */
    innerRim: Color? = null,
    /** Floating separation. Selection controls keep 0: their depth is the rim and the finger light. */
    shadow: Dp = 0.dp,
    /** The bright rim along the edge that makes it read as glass rather than a frosted card. */
    highlight: Highlight? = Highlight.Default,
    fallback: Color = XpColors.Card,
    /**
     * Every glass surface reacts to a finger by default: a light follows the touch, the surface swells a
     * touch and tilts towards it. Panels that are only looked at (a drawer, a toast) pass [interactive] =
     * false. A parent that already tracks the whole bar (the tab bar) passes its own light with
     * [track] = false and [origin] set to this element's top-left.
     */
    interactive: Boolean = true,
    /** Share one light between siblings (the tab bar's capsules). */
    light: GlassLight? = null,
    /** Whether this element listens for its own finger. False when [light] belongs to a parent. */
    track: Boolean = true,
    /** This element's top-left inside the [light] coordinate space (only used when [track] is false). */
    origin: () -> Offset = { Offset.Zero },
    /** How much the surface grows while pressed (1 = none). */
    swell: Float = 1.03f,
    /** How far it tilts in 3D towards the finger, in degrees. */
    maxTilt: Float = 6f,
): Modifier {
    if (backdrop == null) return this.clip(shape).background(fallback)
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val own = rememberGlassLight()
    val active = light ?: if (interactive) own else null
    val tracked = if (active != null && track) {
        this.pointerInput(active) { trackGlassLight(active, scope) }
    } else this
    return tracked.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blur.toPx())
            if (lens) lens(lensHeight.toPx(), lensAmount.toPx(), chromaticAberration = chromatic)
        },
        highlight = { highlight },
        shadow = { Shadow(radius = shadow, color = rim) },
        innerShadow = { InnerShadow(radius = if (innerRim != null) 6.dp else 0.dp, color = innerRim ?: Color.Transparent) },
        layerBlock = {
            if (active != null && !reduced) pressLayer(active, origin(), swell, maxTilt)
        },
        onDrawSurface = { drawRect(film) },
        onDrawFront = { active?.draw(this, origin()) },
    )
}

/**
 * Swell + 3D tilt + lift, driven by how lit this element is. Only the element under the finger moves, so a
 * bar with several capsules lights one of them rather than all of them. `cameraDistance` keeps the lens
 * from stretching like a rubber sheet when it tilts.
 */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.pressLayer(
    light: GlassLight,
    origin: Offset,
    swell: Float,
    maxTilt: Float,
) {
    val k = light.strength
    if (k <= 0.002f || !light.at.isSpecified) return
    val local = light.at - origin
    // Outside this element (with a little slack) it stays still.
    val slack = 8.dp.toPx()
    if (local.x < -slack || local.y < -slack || local.x > size.width + slack || local.y > size.height + slack) return
    val nx = if (size.width > 1f) ((local.x / size.width) - 0.5f) * 2f else 0f
    val ny = if (size.height > 1f) ((local.y / size.height) - 0.5f) * 2f else 0f
    val e = k.coerceIn(0f, 1.15f)
    // Big panels (the login form) tilt less: 6° on a 450dp slab swings its edge ~20dp and the refracted
    // backdrop visibly slips under it. Controls up to [TiltFullSize] keep the full tilt.
    val full = TiltFullSize.toPx()
    val fitX = (full / size.width.coerceAtLeast(1f)).coerceAtMost(1f).let { it * it }
    val fitY = (full / size.height.coerceAtLeast(1f)).coerceAtMost(1f).let { it * it }
    // Pressed at the right edge: that edge leans away from the finger, the way a soft slab would.
    rotationY = nx * maxTilt * e * fitX
    rotationX = -ny * maxTilt * e * fitY
    rotationZ = nx * maxTilt * 0.25f * e * fitX * fitY
    cameraDistance = 14f * density
    // The swell never grows an edge by more than a few dp, whatever the size.
    val grow = ((swell - 1f) * e).coerceAtMost(MaxSwell.toPx() / maxOf(size.width, size.height, 1f))
    val s = 1f + grow
    scaleX = s
    scaleY = s
    translationY = -0.8.dp.toPx() * e
}

/**
 * Where the last finger is on a glass surface (in that surface's coordinates) and how lit it is (0..1,
 * briefly above 1 on the way in — that overshoot is the elasticity). One instance per glass surface, or
 * one per bar whose capsules share it.
 */
class GlassLight {
    var at by mutableStateOf(Offset.Unspecified)
    internal val glow = Animatable(0f)
    val strength: Float get() = glow.value

    /** Additive radial light at the finger, drawn on top of a glass element whose top-left is [origin]. */
    fun draw(scope: DrawScope, origin: Offset) = with(scope) {
        val k = glow.value
        if (k < 0.01f || !at.isSpecified) return
        val c = at - origin
        // Finger-sized on big panels: sized to the whole element it flooded a login form white.
        val r = (size.height.coerceAtLeast(size.width * 0.35f) * 1.3f).coerceAtMost(MaxLightRadius.toPx())
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.50f * k), 0.45f to Color.White.copy(alpha = 0.18f * k), 1f to Color.Transparent,
                center = c, radius = r,
            ),
            radius = r, center = c, blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
        )
    }
}

private val TiltFullSize = 280.dp
private val MaxSwell = 4.dp
private val MaxLightRadius = 140.dp

@Composable
fun rememberGlassLight(): GlassLight = remember { GlassLight() }

/**
 * Follows the finger: the light comes on fast with a springy overshoot (that is the “微微凸出一点点有点弹性”
 * part), tracks the touch while it is held, and fades slowly on release. Watched on the **Initial** pass and
 * never consumed, so buttons under the glass still get their clicks.
 */
suspend fun PointerInputScope.trackGlassLight(light: GlassLight, scope: CoroutineScope) {
    val host = scope
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        light.at = down.position
        host.launch { light.glow.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 1200f)) }
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            light.at = change.position
            if (change.changedToUpIgnoreConsumed()) break
        }
        host.launch { light.glow.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 320f)) }
    }
}

/** Unused guard so the import list stays honest for callers that only need the flow. */
internal val GlassLight.isLive: Boolean get() = glow.value > 0.01f
