package com.xiaopiao.app.ui.circle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 圈子侧栏 (tt 2026-10-29): the left button expands a left-anchored panel for switching circles and
 * reaching the管理 actions. Each management row closes the panel and opens its own bottom sheet —
 * the panel never grows a second page inside itself.
 */
@Composable
fun CircleMenuDrawer(
    book: CircleBook,
    onBookChange: (CircleBook) -> Unit,
    onClose: () -> Unit,
    onGames: () -> Unit,
    onMembers: () -> Unit,
    onInvite: () -> Unit,
    onLeaderboard: () -> Unit,
    onAward: () -> Unit,
    onShare: () -> Unit,
    viewerId: String = CircleDemo.MY_ID,
) {
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.matchParentSize().background(XpColors.Scrim)
                .pressableClick(haptic = false, onClick = onClose),
        )
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(240, easing = XpEase.out)) +
                fadeIn(tween(160)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Surface(
                Modifier.fillMaxHeight().widthIn(max = 332.dp).fillMaxWidth(0.88f),
                color = XpColors.Card,
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            ) {
                Column(
                    Modifier.statusBarsPadding().navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = XpSpace.s),
                ) {
                    Column(Modifier.padding(start = XpSpace.page, end = XpSpace.page, top = XpSpace.l, bottom = XpSpace.m)) {
                        Text(stringResource(R.string.circle_menu_title), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
                        Text(
                            stringResource(R.string.circle_menu_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = XpColors.InkSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    DrawerLabel(stringResource(R.string.circle_menu_circles))
                    book.circles.forEach { circle ->
                        val current = circle.id == book.currentId
                        DrawerRow(
                            leading = {
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(XpColors.AccentSoft),
                                    contentAlignment = Alignment.Center,
                                ) { Text(circle.name.take(1), style = MaterialTheme.typography.labelLarge, color = XpColors.Ink) }
                            },
                            title = circle.name,
                            subtitle = stringResource(R.string.circle_home_member_count, circle.members.size, CIRCLE_MAX_MEMBERS) +
                                " · " + roleLabel(circle, viewerId),
                            trailing = if (current) {
                                { Text("✓", style = MaterialTheme.typography.titleMedium, color = XpColors.Accent) }
                            } else null,
                            onClick = {
                                onBookChange(switchCircle(book, circle.id))
                                onClose()
                            },
                        )
                    }
                    DrawerRow(
                        leading = { IconTileInline(Icons.Outlined.Add) },
                        title = stringResource(R.string.circle_home_new),
                        subtitle = null,
                        onClick = { creating = true },
                    )

                    Spacer(Modifier.height(XpSpace.s))
                    DrawerHairline()
                    DrawerLabel(stringResource(R.string.circle_menu_manage))
                    DrawerRow({ IconTileInline(Icons.Outlined.Group) }, stringResource(R.string.circle_home_members), null, onClick = onMembers)
                    DrawerRow({ IconTileInline(Icons.Outlined.QrCode2) }, stringResource(R.string.circle_home_invite), null, onClick = onInvite)
                    DrawerRow({ IconTileInline(Icons.Outlined.Share) }, stringResource(R.string.circle_menu_share), stringResource(R.string.circle_menu_share_sub), onClick = onShare)
                    DrawerRow({ IconTileInline(Icons.Outlined.EmojiEvents) }, stringResource(R.string.circle_home_leaderboard), null, onClick = onLeaderboard)
                    DrawerRow({ IconTileInline(Icons.Outlined.Lock) }, stringResource(R.string.circle_home_award), stringResource(R.string.circle_home_award_sub), onClick = onAward)
                    DrawerRow({ IconTileInline(Icons.Outlined.SportsEsports) }, stringResource(R.string.circle_home_games_title), stringResource(R.string.circle_home_games_meta, 8), onClick = onGames)
                }
            }
        }
    }

    if (creating) {
        CreateCircleDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                val me = book.current().member(viewerId) ?: CircleDemo.members().first()
                val result = createCircle(book, name, me, System.currentTimeMillis())
                if (result.error == null) {
                    onBookChange(result.book)
                    creating = false
                    onClose()
                }
            },
        )
    }
}

@Composable
private fun roleLabel(circle: Circle, viewerId: String): String = when (circle.member(viewerId)?.role) {
    CircleRole.Owner -> stringResource(R.string.circle_home_role_owner)
    CircleRole.Admin -> stringResource(R.string.circle_home_role_admin)
    else -> stringResource(R.string.circle_home_role_member)
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium),
        color = XpColors.InkTertiary,
        modifier = Modifier.padding(start = XpSpace.page, end = XpSpace.page, top = XpSpace.m, bottom = XpSpace.xs),
    )
}

@Composable
private fun DrawerHairline() {
    Box(Modifier.padding(horizontal = XpSpace.page).fillMaxWidth().height(1.dp).background(XpColors.Hairline))
}

@Composable
private fun IconTileInline(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(XpRadius.s)).background(XpColors.Surface2),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = XpColors.Ink, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun DrawerRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .pressableClick(scaleDown = 0.97f, haptic = true, onClick = onClick)
            .padding(start = XpSpace.page, end = XpSpace.m, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = XpColors.Ink, maxLines = 1)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing() else Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = XpColors.InkTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CreateCircleDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.circle_home_new), color = XpColors.Ink) },
        text = {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Fill)
                    .padding(horizontal = XpSpace.m, vertical = 12.dp),
            ) {
                if (name.isEmpty()) {
                    Text(stringResource(R.string.circle_home_name_hint), style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(CIRCLE_MAX_NAME) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink),
                    cursorBrush = SolidColor(XpColors.Accent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.circle_home_create), color = if (name.isNotBlank()) XpColors.Accent else XpColors.InkTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.circle_home_cancel), color = XpColors.InkSecondary) }
        },
        containerColor = XpColors.Card,
    )
}
