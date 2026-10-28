package com.xiaopiao.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

enum class XpButtonStyle { Primary, Secondary, Plain }

/** Apple-style button: coral primary, soft secondary, text-only plain. Springs on press and ticks on tap. */
@Composable
fun XpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: XpButtonStyle = XpButtonStyle.Primary,
    enabled: Boolean = true,
    leading: String? = null,
) {
    val (bg, fg) = when (style) {
        XpButtonStyle.Primary -> XpColors.Accent to XpColors.Card
        XpButtonStyle.Secondary -> XpColors.AccentSoft to XpColors.Accent
        XpButtonStyle.Plain -> androidx.compose.ui.graphics.Color.Transparent to XpColors.Accent
    }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pressableClick(enabled = enabled, onClick = onClick)
            .defaultMinSize(minWidth = 64.dp)
            .height(if (style == XpButtonStyle.Plain) 40.dp else 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = XpSpace.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (leading != null) Text(leading, modifier = Modifier.padding(end = XpSpace.s))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
