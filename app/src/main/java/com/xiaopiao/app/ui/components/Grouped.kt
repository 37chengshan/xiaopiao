package com.xiaopiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/** Collects the rows of a [GroupedSection] so hairlines can be drawn between (not after) them. */
class GroupedScope {
    internal val keys = mutableListOf<Any?>()
    internal val rows = mutableListOf<@Composable () -> Unit>()

    /** One row (or any block). [key] keeps state stable when rows are reordered or removed. */
    fun item(key: Any? = null, content: @Composable () -> Unit) {
        keys += key ?: rows.size
        rows += content
    }
}

/**
 * Inset grouped list: one 14dp surface, rows separated by inset hairlines (Apple settings feel).
 * [dividerInset] is where the hairline starts; use 58.dp when rows carry a 30dp [IconTile].
 * The section draws its own [margin] (16dp) on both sides, so place it directly in the page column.
 */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    footer: String? = null,
    dividerInset: Dp = XpSpace.l,
    margin: Dp = XpSpace.l,
    content: GroupedScope.() -> Unit,
) {
    val scope = GroupedScope().apply(content)
    val shape = RoundedCornerShape(XpRadius.m)
    Column(modifier.fillMaxWidth().padding(horizontal = margin)) {
        if (header != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = XpSpace.l, end = XpSpace.s, top = XpSpace.xl, bottom = XpSpace.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(header, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                headerTrailing?.invoke()
            }
        } else {
            Spacer(Modifier.height(XpSpace.l))
        }
        Column(
            Modifier.fillMaxWidth().clip(shape).background(XpColors.Card),
        ) {
            scope.rows.forEachIndexed { i, row ->
                key(scope.keys[i]) {
                    if (i > 0) GroupedDivider(dividerInset)
                    row()
                }
            }
        }
        if (footer != null) {
            Text(
                footer, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                modifier = Modifier.padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.s),
            )
        }
    }
}

@Composable
fun GroupedDivider(inset: Dp = XpSpace.l) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(0.5.dp).background(XpColors.Hairline))
}

/**
 * A grouped-list row: optional [leading] (usually an [IconTile]), title + subtitle, then either a [trailing]
 * slot or a secondary [value] and chevron. Rows are 52dp (76dp with a subtitle), per docs/APP-DESIGN.md §7.5.
 * Pressed = surface-2 background on the very first frame; rows never scale (Apple rows do not move).
 */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color = XpColors.Ink,
    titleMaxLines: Int = 1,
    subtitleMaxLines: Int = 2,
    onClick: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberXpHaptics()
    val clickable = if (onClick != null) {
        Modifier.clickable(source, null) { haptics.select(); onClick() }
    } else Modifier
    Row(
        modifier.fillMaxWidth()
            .background(if (pressed && onClick != null) XpColors.Surface2 else Color.Transparent)
            .then(clickable)
            .defaultMinSize(minHeight = if (subtitle != null) 76.dp else 52.dp)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.s + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(XpSpace.m))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, color = titleColor,
                maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary,
                    maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(
                value, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                modifier = Modifier.padding(start = XpSpace.s),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(XpSpace.s))
            trailing()
        }
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                tint = XpColors.InkTertiary, modifier = Modifier.padding(start = 2.dp).size(20.dp),
            )
        }
    }
}

/** Outlined line icon on a [XpColors.Fill] rounded square. [selected] swaps to the clay-soft treatment. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    selected: Boolean = false,
    tint: Color = XpColors.Ink,
    /** Optional semantic soft background (e.g. [XpColors.InfoSoft]); pair with a matching [tint]. */
    background: Color? = null,
) {
    val bg by animateColorAsState(background ?: if (selected) XpColors.AccentSoft else XpColors.Fill, tween(180, easing = XpEase.out), label = "tileBg")
    val fg by animateColorAsState(if (selected) XpColors.Accent else tint, tween(180, easing = XpEase.out), label = "tileFg")
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.6f))
    }
}

/** Switch tinted with clay when on (selected state), fill when off. */
@Composable
fun XpSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberXpHaptics()
    Switch(
        checked = checked,
        onCheckedChange = { haptics.select(); onCheckedChange(it) },
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = XpColors.OnAccent, checkedTrackColor = XpColors.Accent, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = XpColors.OnAccent, uncheckedTrackColor = XpColors.Fill, uncheckedBorderColor = Color.Transparent,
        ),
    )
}
