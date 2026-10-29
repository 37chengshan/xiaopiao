package com.xiaopiao.app.ui.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlin.math.max
import kotlin.math.roundToInt

private val TornDepth = 5.dp
private val TornTooth = 10.dp

/** Initial-only avatar: a tinted circle, never an image (all members are local demo data). */
@Composable
fun CircleAvatar(
    member: CircleMember,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
) {
    val (background, ink) = when (member.tone) {
        CircleTone.Accent -> XpColors.AccentSoft to XpColors.Accent
        CircleTone.Positive -> XpColors.PositiveSoft to XpColors.Positive
        CircleTone.Info -> XpColors.InfoSoft to XpColors.Info
        CircleTone.Warning -> XpColors.WarningSoft to XpColors.Warning
    }
    val click = if (onClick != null) {
        Modifier.pressableClick(scaleDown = 0.94f, onClick = onClick)
            .semantics { contentDescription = "查看${member.name}的共享范围" }
    } else Modifier
    Box(
        modifier.size(size).clip(CircleShape).background(background).then(click),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            member.initial,
            style = if (size >= 34.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * Receipt card: rounded top, torn zigzag bottom, hairline silhouette so the tear stays visible even
 * when the card and the page share a colour (Apple dark). Sits on [XpColors.Card]; content is clipped.
 */
@Composable
fun TornReceiptCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /**
     * 0..1 of the paper: the card "prints out" from the top, its torn bottom edge travelling down with the
     * reveal. Layout height is unchanged, so a sheet using it never re-measures mid-animation.
     */
    reveal: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .drawWithContent {
                val shown = (size.height * reveal).coerceIn(0f, size.height)
                if (shown <= 1f) return@drawWithContent
                val path = tornCardPath(
                    width = size.width,
                    height = shown,
                    tooth = TornTooth.toPx(),
                    depth = TornDepth.toPx(),
                    radius = XpRadius.m.toPx(),
                )
                clipPath(path) {
                    drawRect(XpColors.Card)
                    this@drawWithContent.drawContent()
                }
                drawPath(path, XpColors.Hairline, style = Stroke(width = 1f))
            }
            .padding(
                start = XpSpace.l,
                end = XpSpace.l,
                top = XpSpace.m + 2.dp,
                bottom = XpSpace.l + TornDepth,
            ),
        content = content,
    )
}

/** Quiet 21dp chip for category slices and masked labels. */
@Composable
fun CircleChip(text: String, modifier: Modifier = Modifier, tone: Color = XpColors.InkSecondary) {
    Box(
        modifier.clip(RoundedCornerShape(XpRadius.s)).background(XpColors.Fill)
            .padding(horizontal = XpSpace.s, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tone, maxLines = 1)
    }
}

/** Header entry into the per-member visibility sheet. */
@Composable
fun ShareEntryChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
            .pressableClick(onClick = onClick)
            .padding(horizontal = XpSpace.m, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = XpColors.InkSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(XpSpace.xs + 1.dp))
        Text("谁能看到什么", style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary, maxLines = 1)
    }
}

/**
 * Card silhouette: a round-topped rectangle whose bottom edge is cut into paper teeth. One path so the
 * clip and the hairline outline agree.
 */
private fun tornCardPath(width: Float, height: Float, tooth: Float, depth: Float, radius: Float): Path {
    val bodyBottom = (height - depth).coerceAtLeast(1f)
    val teeth = max(1, (width / tooth).roundToInt())
    val step = width / teeth
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = bodyBottom,
                topLeftCornerRadius = CornerRadius(radius),
                topRightCornerRadius = CornerRadius(radius),
                bottomLeftCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
        moveTo(0f, bodyBottom)
        for (i in 0 until teeth) {
            lineTo(step * (i + 0.5f), height)
            lineTo(step * (i + 1f), bodyBottom)
        }
        close()
    }
}
