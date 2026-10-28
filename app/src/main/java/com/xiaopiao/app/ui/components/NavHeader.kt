package com.xiaopiao.app.ui.components

import androidx.compose.foundation.interaction.collectIsPressedAsState
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

private val BarHeight = 44.dp

/**
 * Apple-style navigation chrome. Content is laid out full-bleed and receives the height it must reserve
 * on top ([content]'s `top`); put a [LargeTitle] as its first child. As the content scrolls, a compact
 * translucent bar fades in with a centred small title and a hairline. [trailing] actions stay put in both states.
 */
@Composable
fun NavScaffold(
    title: String,
    scroll: NavScroll,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "返回",
    demo: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.(top: Dp) -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Box(modifier.fillMaxSize().background(XpColors.CanvasGrouped)) {
        content(topInset + BarHeight)
        Box(
            Modifier.fillMaxWidth()
                .drawBehind {
                    val p = 1f
                    drawRect(XpColors.Material.copy(alpha = 0.94f * p))
                    drawRect(
                        XpColors.Hairline.copy(alpha = XpColors.Hairline.alpha * p),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx()),
                    )
                }
                .statusBarsPadding()
                .height(BarHeight)
                .padding(horizontal = XpSpace.m),
        ) {
            Text(
                title, Modifier.align(Alignment.Center).padding(horizontal = 96.dp)
                    .graphicsLayer { alpha = 1f },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (onBack != null) {
                Row(
                    Modifier.align(Alignment.CenterStart).pressableClick(onClick = onBack).padding(vertical = XpSpace.s, horizontal = XpSpace.xs),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "返回", tint = XpColors.Ink, modifier = Modifier.size(16.dp))
                    Text(backLabel, style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink)
                }
            }
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            ) {
                trailing()
            }
        }
    }
}

/** 34dp round raised button with a line icon, for nav bar actions. */
@Composable
fun NavIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberXpHaptics()
    Box(
        modifier.size(34.dp).pressableScale(source)
            .graphicsLayer { alpha = if (pressed) 0.8f else 1f }
            .clip(CircleShape).background(XpColors.Ink.copy(alpha = 0.07f))
            .clickable(source, null) { haptics.select(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = XpColors.Ink, modifier = Modifier.size(18.dp))
    }
}
