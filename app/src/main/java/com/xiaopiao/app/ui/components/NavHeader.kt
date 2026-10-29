package com.xiaopiao.app.ui.components

import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** Scroll position of a screen's content, as a 0..1 "large title is gone" progress. Reads are draw-phase only. */
@Stable
class NavScroll internal constructor(private val offsetPx: () -> Float) {
    /** 0 = large title fully visible, 1 = compact bar fully shown (reached after [COLLAPSE_DP] of scrolling). */
    fun progress(density: Float): Float = (offsetPx() / (COLLAPSE_DP * density)).coerceIn(0f, 1f)

    companion object { const val COLLAPSE_DP = 44f }
}

@Composable
fun rememberNavScroll(state: ScrollState): NavScroll = remember(state) { NavScroll { state.value.toFloat() } }

/** The large title must be the first item of the list; anything past it counts as fully collapsed. */
@Composable
fun rememberNavScroll(state: LazyListState): NavScroll = remember(state) {
    NavScroll { if (state.firstVisibleItemIndex > 0) 10_000f else state.firstVisibleItemScrollOffset.toFloat() }
}

/** A screen with no scrolling title (always expanded). */
val NavScrollNone = NavScroll { 0f }

/** Height of the glass pill and of the title band: what the page reserves, so it never changes here. */
val NavBarHeight = 44.dp

/**
 * Minimum touch target of every top-bar control (mobile rules §2: ≥48dp). Nothing is 48dp tall visually —
 * the bar's hit band is simply taller than its pill, and each control centres its 44dp visual inside it.
 */
val NavTouchTarget = 48.dp

/**
 * Apple-style navigation chrome. Content is laid out full-bleed and receives the height it must reserve
 * on top ([content]'s `top`). There is no bar: the title sits on a soft edge fade and [leading] / [trailing] / back float as glass capsules.
 */
@Composable
fun NavScaffold(
    title: String,
    scroll: NavScroll,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "返回",
    demo: Boolean = false,
    /** Root pages can put one action on the left (e.g. 记账页的卡包按钮); hidden while [onBack] is set. */
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    /** Lay the page on a theme-coloured [GlassGround] so its grouped cards turn into liquid glass (设置). */
    glassGround: Boolean = false,
    content: @Composable BoxScope.(top: Dp) -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // Liquid-glass top bar: the page is recorded here and the bar (a sibling above it) samples what scrolls under.
    val glass = rememberLayerBackdrop()
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(glass).background(XpColors.CanvasGrouped)) {
            if (glassGround) GlassGround(Modifier.matchParentSize()) { Box(Modifier.fillMaxSize()) { content(topInset + NavBarHeight) } }
            else content(topInset + NavBarHeight)
        }
        // No bar: a soft edge fade keeps the title legible, and each group of controls floats in its own glass capsule.
        val edge = XpColors.CanvasGrouped
        Box(
            Modifier.fillMaxWidth().height(topInset + NavBarHeight + EdgeFade)
                .background(Brush.verticalGradient(0f to edge.copy(alpha = 0.92f), 0.6f to edge.copy(alpha = 0.7f), 1f to edge.copy(alpha = 0f))),
        )
        // The strip is [NavTouchTarget] tall so its controls can own a 48dp hit area, but everything visible —
        // title, label, pills, glyphs — stays inside [NavBarHeight] at the top, exactly where it was.
        Box(
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .height(NavTouchTarget)
                .padding(horizontal = XpSpace.m),
        ) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(NavBarHeight), contentAlignment = Alignment.Center) {
                Text(
                    title, Modifier.padding(horizontal = 112.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (onBack != null) {
                GlassCapsule(glass, Modifier.align(Alignment.TopStart)) {
                    // The tap box is 48dp tall; the label row keeps the pill's own 44dp so it stays optically centred.
                    Box(Modifier.height(NavTouchTarget).pressableClick(onClick = onBack), contentAlignment = Alignment.TopCenter) {
                        Row(
                            Modifier.height(NavBarHeight).padding(start = 10.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "返回", tint = XpColors.Ink, modifier = Modifier.size(16.dp))
                            Text(backLabel, style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink)
                        }
                    }
                }
            }
            if (leading != null && onBack == null) {
                GlassCapsule(glass, Modifier.align(Alignment.TopStart), content = leading)
            }
            GlassCapsule(glass, Modifier.align(Alignment.TopEnd), content = trailing)
        }
    }
}

/** Whether a [NavIconButton] sits inside a [GlassCapsule]: the capsule is the surface, so the button drops its own disc. */
private val LocalInCapsule = staticCompositionLocalOf { false }

/**
 * One floating liquid-glass pill for a group of top controls, same recipe as the tab bar's capsules
 * (vibrancy, light scatter, edge lens, ~40% film, soft drop shadow). Renders nothing when the group is empty.
 * The node is [NavTouchTarget] tall so its controls can hit 48dp; the glass itself is drawn in the node's top
 * [NavBarHeight], so the pill keeps exactly its old geometry and the extra 4dp is invisible touch room.
 */
@Composable
private fun GlassCapsule(backdrop: LayerBackdrop, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val dark = XpColors.isDark
    val film = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.42f)
    val drop = Color.Black.copy(alpha = if (dark) 0.32f else 0.10f)
    CompositionLocalProvider(LocalInCapsule provides true) {
        Layout(
            content = {
                Row(
                    Modifier.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.Top,
                    content = content,
                )
            },
            modifier = modifier.height(NavTouchTarget).drawBackdrop(
                backdrop = backdrop,
                shape = { CapsuleShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(10.dp.toPx(), 14.dp.toPx())
                },
                shadow = { Shadow(radius = 16.dp, color = drop) },
                onDrawSurface = { drawRect(film) },
            ),
        ) { measurables, constraints ->
            val row = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            // An empty group (e.g. a page with no trailing actions) gets no capsule at all.
            if (row.width <= 4.dp.roundToPx()) return@Layout layout(0, 0) {}
            layout(row.width, constraints.maxHeight) { row.place(0, (constraints.maxHeight - row.height) / 2) }
        }
    }
}

/**
 * Round line-icon button for nav actions: a 34dp raised disc on its own, a bare glyph inside a [GlassCapsule].
 * The node is [NavTouchTarget] square so a finger never has to find the 20dp glyph; the 40dp slot — icon, press
 * veil and all — keeps its old size, centred in the pill's [NavBarHeight].
 */
@Composable
fun NavIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberXpHaptics()
    val inCapsule = LocalInCapsule.current
    Box(
        modifier.size(NavTouchTarget).clickable(source, null) { haptics.select(); onClick() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxWidth().height(NavBarHeight), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(if (inCapsule) 40.dp else 34.dp).clip(CircleShape).pressableScale(source)
                    .graphicsLayer { alpha = if (pressed) 0.8f else 1f }
                    .then(if (inCapsule) Modifier else Modifier.background(XpColors.Ink.copy(alpha = 0.07f))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = description, tint = XpColors.Ink, modifier = Modifier.size(if (inCapsule) 20.dp else 18.dp))
            }
        }
    }
}

/**
 * The pill's outline, drawn in the top [NavBarHeight] of a [NavTouchTarget]-tall node: the glass keeps its old
 * size while the node below it carries the taller touch areas.
 */
private val CapsuleShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val h = minOf(with(density) { NavBarHeight.toPx() }, size.height)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, h, CornerRadius(h / 2f)))
    }
}

/** How far the edge fade reaches below the controls. */
private val EdgeFade = 18.dp
