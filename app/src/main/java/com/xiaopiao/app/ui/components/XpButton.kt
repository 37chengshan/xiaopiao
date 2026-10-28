package com.xiaopiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * Primary = ink on canvas (the default black button). Clay = the one accent-filled action per screen.
 * Secondary = fill capsule. Plain = text only. Destructive = negative text on fill.
 */
enum class XpButtonStyle { Primary, Secondary, Plain, Clay, Destructive }

enum class XpButtonSize(val height: Int) { Large(48), Small(36) }

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
        XpButtonStyle.Primary -> { bgTarget = XpColors.Ink; fgTarget = XpColors.Canvas }
        XpButtonStyle.Clay -> { bgTarget = XpColors.Accent; fgTarget = XpColors.OnAccent }
        XpButtonStyle.Secondary -> { bgTarget = XpColors.Fill; fgTarget = XpColors.Ink }
        XpButtonStyle.Destructive -> { bgTarget = XpColors.Fill; fgTarget = XpColors.Negative }
        XpButtonStyle.Plain -> { bgTarget = Color.Transparent; fgTarget = XpColors.Accent }
    }
    val bg by animateColorAsState(bgTarget, tween(180, easing = XpEase.out), label = "btnBg")
    val fg by animateColorAsState(fgTarget, tween(180, easing = XpEase.out), label = "btnFg")
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pressableClick(enabled = enabled, onClick = onClick)
            .defaultMinSize(minWidth = 64.dp)
            .height(if (style == XpButtonStyle.Plain) 40.dp else size.height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = if (size == XpButtonSize.Small) XpSpace.l else XpSpace.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) Text(leading, modifier = Modifier.padding(end = XpSpace.s))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
