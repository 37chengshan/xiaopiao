package com.xiaopiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * Exactly **two** button levels, following the Apple / Claude grammar (references/design-systems/):
 * - [Primary] one filled capsule per screen — the thing the user came to do.
 * - [Secondary] the system fill capsule — every other action. No border, no tint, no shadow.
 * - [Plain] is a text link, not a level: use it inside dense rows and for 「了解更多」-style escapes.
 *
 * [Clay] and [Destructive] are compatibility aliases that resolve into the two levels above.
 */
enum class XpButtonStyle { Primary, Secondary, Plain, Clay, Destructive }

enum class XpButtonSize(val height: Int) { Large(44), Small(34) }

@Composable
fun XpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: XpButtonStyle = XpButtonStyle.Primary,
    enabled: Boolean = true,
    leading: String? = null,
    size: XpButtonSize = XpButtonSize.Large,
) {
    val bgTarget: Color
    val fgTarget: Color
    when (style) {
        XpButtonStyle.Primary, XpButtonStyle.Clay -> { bgTarget = XpColors.Primary; fgTarget = XpColors.OnPrimary }
        XpButtonStyle.Secondary -> { bgTarget = XpColors.Fill; fgTarget = XpColors.Ink }
        XpButtonStyle.Destructive -> { bgTarget = XpColors.Fill; fgTarget = XpColors.Negative }
        XpButtonStyle.Plain -> { bgTarget = Color.Transparent; fgTarget = XpColors.Accent }
    }
    val bg by animateColorAsState(bgTarget, tween(160, easing = XpEase.out), label = "btnBg")
    val fg by animateColorAsState(fgTarget, tween(160, easing = XpEase.out), label = "btnFg")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberXpHaptics()
    // Press shows as a hair of scale + light, not a colour flip: the fill stays the fill.
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120, easing = XpEase.out), label = "btnPress")
    val shape = RoundedCornerShape(XpRadius.pill)
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(shape)
            .background(bg)
            .clickable(source, null, enabled = enabled) { haptics.select(); onClick() }
            .defaultMinSize(minWidth = 56.dp)
            .height(if (style == XpButtonStyle.Plain) 36.dp else size.height.dp)
            .padding(horizontal = if (style == XpButtonStyle.Plain) XpSpace.s else if (size == XpButtonSize.Small) XpSpace.l else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) Text(leading, modifier = Modifier.padding(end = XpSpace.s))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
        )
    }
}
