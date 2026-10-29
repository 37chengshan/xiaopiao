@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.xiaopiao.app.ui.circle

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.SelectCapsule
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.XpSwitch
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/**
 * 圈子管理（docs/APP-DESIGN.md §5.4）：菜单、成员、可见度、邀请。
 *
 * Every screen here is a page inside **one** [XpSheet], so opening 成员 → 可见度 never stacks a second
 * modal on top of the first. The domain rules live in CircleDomain.kt; this file only renders state and
 * passes [CircleResult] back through [onBookChange], showing any [CircleError] inline next to the action.
 */

/** Pages of the circle menu. Sub-pages reuse the same sheet, so going back never re-opens a modal. */
private enum class CirclePage { Menu, Members, Visibility, Invite, Leaderboard }

// --- public entry points -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleMenuSheet(
    book: CircleBook,
    onBookChange: (CircleBook) -> Unit,
    onOpenGames: () -> Unit,
    onClose: () -> Unit,
    viewerId: String = CircleDemo.MY_ID,
    onShare: (() -> Unit)? = null,
) {
    XpSheet(onDismiss = onClose) {
        CircleMenuContent(book, viewerId, onBookChange, onOpenGames, onClose, onShare)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleMembersSheet(
    book: CircleBook,
    onBookChange: (CircleBook) -> Unit,
    onClose: () -> Unit,
    viewerId: String = CircleDemo.MY_ID,
) {
    var visibilityFor by remember { mutableStateOf<String?>(null) }
    XpSheet(onDismiss = onClose) {
        val target = visibilityFor
        if (target == null) {
            CircleMembersContent(
                book = book,
                viewerId = viewerId,
                onBookChange = onBookChange,
                onClose = onClose,
                onBack = null,
                onOpenVisibility = { visibilityFor = it },
            )
        } else {
            CircleVisibilityContent(
                book = book,
                viewerId = viewerId,
                initialMemberId = target,
                onBookChange = onBookChange,
                onClose = onClose,
                onBack = { visibilityFor = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleVisibilitySheet(
    book: CircleBook,
    onBookChange: (CircleBook) -> Unit,
    onClose: () -> Unit,
    viewerId: String = CircleDemo.MY_ID,
    initialMemberId: String? = null,
) {
    XpSheet(onDismiss = onClose) {
        CircleVisibilityContent(
            book = book,
            viewerId = viewerId,
            initialMemberId = initialMemberId ?: viewerId,
            onBookChange = onBookChange,
            onClose = onClose,
            onBack = null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleInviteSheet(
    book: CircleBook,
    onBookChange: (CircleBook) -> Unit,
    onClose: () -> Unit,
) {
    XpSheet(onDismiss = onClose) {
        CircleInviteContent(book, onBookChange, onClose, onBack = null)
    }
}

// --- menu -------------------------------------------------------------------------------------

@Composable
internal fun CircleMenuContent(
    book: CircleBook,
    viewerId: String,
    onBookChange: (CircleBook) -> Unit,
    onOpenGames: () -> Unit,
    onClose: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
    val circle = book.current()
    val isOwner = circle.ownerId == viewerId
    val isAdmin = circle.member(viewerId)?.role == CircleRole.Admin
    val meName = stringResource(R.string.circle_me_name)
    val now = remember { System.currentTimeMillis() }
    var page by remember(book.currentId) { mutableStateOf(CirclePage.Menu) }
    var visibilityFor by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDissolve by remember { mutableStateOf(false) }
    var showAward by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<CircleError?>(null) }

    when (page) {
        CirclePage.Menu -> CircleMenuList(
            book = book,
            viewerId = viewerId,
            isOwner = isOwner,
            isAdmin = isAdmin,
            error = error,
            onSwitch = { id ->
                error = null
                onBookChange(switchCircle(book, id))
            },
            onNew = { showNew = true },
            onMembers = { page = CirclePage.Members },
            onInvite = { page = CirclePage.Invite },
            onLeaderboard = { page = CirclePage.Leaderboard },
            onAward = { showAward = true },
            onShare = onShare?.let { open -> { onClose(); open() } },
            onGames = {
                onClose()
                onOpenGames()
            },
            onRename = { showRename = true },
            onDissolve = { showDissolve = true },
            onClose = onClose,
        )
        CirclePage.Members -> CircleMembersContent(
            book = book,
            viewerId = viewerId,
            onBookChange = onBookChange,
            onClose = onClose,
            onBack = { page = CirclePage.Menu },
            onOpenVisibility = { memberId ->
                visibilityFor = memberId
                page = CirclePage.Visibility
            },
        )
        CirclePage.Visibility -> CircleVisibilityContent(
            book = book,
            viewerId = viewerId,
            initialMemberId = visibilityFor ?: viewerId,
            onBookChange = onBookChange,
            onClose = onClose,
            onBack = { page = CirclePage.Members },
        )
        CirclePage.Invite -> CircleInviteContent(
            book = book,
            onBookChange = onBookChange,
            onClose = onClose,
            onBack = { page = CirclePage.Menu },
        )
        CirclePage.Leaderboard -> CircleLeaderboardContent(
            book = book,
            now = now,
            viewerId = viewerId,
            onClose = onClose,
            onBack = { page = CirclePage.Menu },
            onInvite = { page = CirclePage.Invite },
        )
    }

    if (showNew) {
        CircleNameDialog(
            title = stringResource(R.string.circle_new_title),
            initial = "",
            placeholder = stringResource(R.string.circle_new_placeholder),
            confirmLabel = stringResource(R.string.circle_new_confirm),
            maxLength = CIRCLE_MAX_NAME,
            onCancel = { showNew = false },
            onSubmit = { name ->
                val at = System.currentTimeMillis()
                val id = "circle-$at"
                val fresh = Circle(
                    id = id,
                    name = name,
                    ownerId = viewerId,
                    members = listOf(
                        CircleMember(
                            id = viewerId,
                            name = meName,
                            initial = meName.take(1),
                            tone = CircleTone.Accent,
                            isMe = true,
                            role = CircleRole.Owner,
                        ),
                    ),
                    posts = emptyList(),
                    invite = newInvite(at, seed = at),
                )
                error = null
                onBookChange(book.copy(circles = book.circles + fresh, currentId = id))
                null
            },
        )
    }

    if (showRename) {
        CircleNameDialog(
            title = stringResource(R.string.circle_rename_title),
            initial = circle.name,
            placeholder = stringResource(R.string.circle_rename_placeholder),
            confirmLabel = stringResource(R.string.circle_sheet_save),
            maxLength = CIRCLE_MAX_NAME,
            onCancel = { showRename = false },
            onSubmit = { name ->
                val result = renameCircle(book, circle.id, name)
                if (result.error == null) {
                    error = null
                    onBookChange(result.book)
                }
                result.error
            },
        )
    }

    if (showDissolve) {
        CircleConfirmDialog(
            title = stringResource(R.string.circle_dissolve_title),
            message = stringResource(R.string.circle_dissolve_body),
            confirmLabel = stringResource(R.string.circle_dissolve_confirm),
            destructive = true,
            onConfirm = {
                val rest = book.circles.filterNot { it.id == circle.id }
                if (rest.isEmpty()) {
                    error = CircleError.NotAllowed
                } else {
                    error = null
                    val nextCurrent = if (book.currentId == circle.id) rest.first().id else book.currentId
                    onBookChange(CircleBook(rest, nextCurrent))
                }
                showDissolve = false
            },
            onDismiss = { showDissolve = false },
        )
    }

    if (showAward) {
        val postTitle = stringResource(R.string.circle_award_post_title)
        val awardLines = circle.members.map { member ->
            val badge = weeklyTitle(circle, member, now)
            member.displayName(viewerId, circle.ownerId) to "${badge.emoji} ${badgeLabel(badge)}"
        }
        CircleAwardDialog(
            lines = awardLines,
            onConfirm = {
                val at = System.currentTimeMillis()
                val post = SystemPost(
                    id = "award-${weekStart(now)}-$at",
                    at = at,
                    title = postTitle,
                    body = awardLines.joinToString(" · ") { (name, title) -> "$name $title" },
                )
                val updated = circle.copy(posts = circle.posts + post)
                onBookChange(book.copy(circles = book.circles.map { if (it.id == circle.id) updated else it }))
                showAward = false
            },
            onDismiss = { showAward = false },
        )
    }
}

@Composable
private fun CircleMenuList(
    book: CircleBook,
    viewerId: String,
    isOwner: Boolean,
    isAdmin: Boolean,
    error: CircleError?,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onMembers: () -> Unit,
    onInvite: () -> Unit,
    onLeaderboard: () -> Unit,
    onAward: () -> Unit,
    onGames: () -> Unit,
    onShare: (() -> Unit)? = null,
    onRename: () -> Unit,
    onDissolve: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XpSpace.page)
            .padding(bottom = XpSpace.l),
    ) {
        CircleSheetHeader(
            title = stringResource(R.string.circle_menu_title),
            subtitle = stringResource(R.string.circle_menu_subtitle),
        )
        CircleErrorLine(error)

        GroupedSection(
            header = stringResource(R.string.circle_menu_circles),
            margin = 0.dp,
            dividerInset = 62.dp,
        ) {
            book.circles.forEach { entry ->
                val current = entry.id == book.currentId
                item(entry.id) {
                    val role = entry.member(viewerId)?.role ?: CircleRole.Member
                    val count = stringResource(R.string.circle_count, entry.members.size, CIRCLE_MAX_MEMBERS)
                    val currentMark: (@Composable () -> Unit)? = if (current) {
                        {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = XpColors.Accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    }
                    GroupedRow(
                        title = entry.name,
                        subtitle = stringResource(R.string.circle_sheet_meta, count, roleLabel(role)),
                        leading = { CircleInitialBadge(entry.name, current) },
                        trailing = currentMark,
                        onClick = { onSwitch(entry.id) },
                    )
                }
            }
            item("new") {
                GroupedRow(
                    title = stringResource(R.string.circle_menu_new),
                    leading = { IconTile(Icons.Outlined.Add, size = 30.dp) },
                    onClick = onNew,
                )
            }
        }

        GroupedSection(header = stringResource(R.string.circle_menu_manage), margin = 0.dp) {
            item("members") {
                GroupedRow(
                    title = stringResource(R.string.circle_menu_members),
                    leading = { IconTile(Icons.Outlined.Group) },
                    chevron = true,
                    onClick = onMembers,
                )
            }
            item("invite") {
                GroupedRow(
                    title = stringResource(R.string.circle_menu_invite),
                    leading = { IconTile(Icons.Outlined.QrCode2) },
                    chevron = true,
                    onClick = onInvite,
                )
            }
            if (onShare != null) {
                item("share") {
                    GroupedRow(
                        title = stringResource(R.string.circle_menu_share),
                        subtitle = stringResource(R.string.circle_menu_share_sub),
                        leading = { IconTile(Icons.Outlined.Share) },
                        chevron = true,
                        onClick = onShare,
                    )
                }
            }
            item("leaderboard") {
                GroupedRow(
                    title = stringResource(R.string.circle_menu_leaderboard),
                    leading = { IconTile(Icons.Outlined.EmojiEvents) },
                    chevron = true,
                    onClick = onLeaderboard,
                )
            }
            if (isOwner || isAdmin) {
                item("award") {
                    GroupedRow(
                        title = stringResource(R.string.circle_menu_award),
                        subtitle = stringResource(R.string.circle_menu_award_sub),
                        leading = { IconTile(Icons.Outlined.EmojiEvents) },
                        onClick = onAward,
                    )
                }
            }
            item("games") {
                GroupedRow(
                    title = stringResource(R.string.circle_menu_games),
                    subtitle = stringResource(R.string.circle_menu_games_sub),
                    leading = { IconTile(Icons.Outlined.SportsEsports) },
                    chevron = true,
                    onClick = onGames,
                )
            }
        }

        if (isOwner) {
            GroupedSection(header = stringResource(R.string.circle_menu_owner), margin = 0.dp) {
                item("rename") {
                    GroupedRow(
                        title = stringResource(R.string.circle_menu_rename),
                        leading = { IconTile(Icons.Outlined.Edit) },
                        onClick = onRename,
                    )
                }
                item("dissolve") {
                    val canDissolve = book.circles.size > 1
                    GroupedRow(
                        title = stringResource(R.string.circle_menu_dissolve),
                        titleColor = if (canDissolve) XpColors.Negative else XpColors.InkTertiary,
                        value = if (canDissolve) null else stringResource(R.string.circle_err_last_circle),
                        leading = {
                            IconTile(
                                Icons.Outlined.DeleteOutline,
                                tint = if (canDissolve) XpColors.Negative else XpColors.InkTertiary,
                            )
                        },
                        onClick = if (canDissolve) onDissolve else null,
                    )
                }
            }
        }

        CircleNote(stringResource(R.string.circle_sheet_local_note))
        Spacer(Modifier.height(XpSpace.l))
        XpButton(
            text = stringResource(R.string.circle_sheet_done),
            onClick = onClose,
            style = XpButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(XpSpace.s))
    }
}

// --- members ----------------------------------------------------------------------------------

@Composable
internal fun CircleMembersContent(
    book: CircleBook,
    viewerId: String,
    onBookChange: (CircleBook) -> Unit,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenVisibility: (String) -> Unit,
) {
    val circle = book.current()
    val isOwner = circle.ownerId == viewerId
    val adminFull = circle.adminCount >= CIRCLE_MAX_ADMINS
    var expandedId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<CircleError?>(null) }
    var nicknameFor by remember { mutableStateOf<CircleMember?>(null) }
    var remarkFor by remember { mutableStateOf<CircleMember?>(null) }
    var removeFor by remember { mutableStateOf<CircleMember?>(null) }

    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XpSpace.page)
            .padding(bottom = XpSpace.l),
    ) {
        CircleSheetHeader(
            title = stringResource(R.string.circle_members_title),
            subtitle = stringResource(R.string.circle_members_subtitle, circle.members.size, CIRCLE_MAX_MEMBERS),
            onBack = onBack,
        )
        CircleErrorLine(error)

        GroupedSection(
            header = stringResource(R.string.circle_members_header),
            margin = 0.dp,
            dividerInset = 62.dp,
        ) {
            circle.members.forEach { member ->
                item(member.id) {
                    CircleMemberRow(
                        circle = circle,
                        member = member,
                        viewerId = viewerId,
                        isOwner = isOwner,
                        expanded = expandedId == member.id,
                        adminFull = adminFull,
                        onToggle = { expandedId = if (expandedId == member.id) null else member.id },
                        onNickname = { nicknameFor = member },
                        onRemark = { remarkFor = member },
                        onToggleAdmin = {
                            val target = if (member.role == CircleRole.Admin) CircleRole.Member else CircleRole.Admin
                            val result = setRole(book, circle.id, member.id, target)
                            if (result.error != null) {
                                error = result.error
                            } else {
                                error = null
                                onBookChange(result.book)
                            }
                        },
                        onRemove = { removeFor = member },
                        onVisibility = { onOpenVisibility(member.id) },
                    )
                }
            }
        }

        CircleNote(stringResource(R.string.circle_members_footer))
        Spacer(Modifier.height(XpSpace.l))
        XpButton(
            text = stringResource(R.string.circle_sheet_done),
            onClick = onClose,
            style = XpButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(XpSpace.s))
    }

    nicknameFor?.let { member ->
        CircleNameDialog(
            title = stringResource(R.string.circle_nickname_title),
            initial = member.nickname.orEmpty(),
            placeholder = stringResource(R.string.circle_nickname_placeholder),
            confirmLabel = stringResource(R.string.circle_sheet_save),
            maxLength = MEMBER_MAX_NICKNAME,
            allowEmpty = true,
            onCancel = { nicknameFor = null },
            onSubmit = { raw ->
                val result = setNickname(book, circle.id, member.id, raw)
                if (result.error == null) {
                    error = null
                    onBookChange(result.book)
                }
                result.error
            },
        )
    }

    remarkFor?.let { member ->
        CircleNameDialog(
            title = stringResource(R.string.circle_remark_title),
            initial = member.remark.orEmpty(),
            placeholder = stringResource(R.string.circle_remark_placeholder),
            confirmLabel = stringResource(R.string.circle_sheet_save),
            maxLength = MEMBER_MAX_NICKNAME,
            allowEmpty = true,
            onCancel = { remarkFor = null },
            onSubmit = { raw ->
                val result = setRemark(book, circle.id, member.id, raw)
                if (result.error == null) {
                    error = null
                    onBookChange(result.book)
                }
                result.error
            },
        )
    }

    removeFor?.let { member ->
        CircleConfirmDialog(
            title = stringResource(R.string.circle_members_remove_title),
            message = stringResource(R.string.circle_members_remove_body),
            confirmLabel = stringResource(R.string.circle_members_remove),
            destructive = true,
            onConfirm = {
                val result = removeMember(book, circle.id, member.id)
                if (result.error != null) {
                    error = result.error
                } else {
                    error = null
                    onBookChange(result.book)
                }
                removeFor = null
            },
            onDismiss = { removeFor = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CircleMemberRow(
    circle: Circle,
    member: CircleMember,
    viewerId: String,
    isOwner: Boolean,
    expanded: Boolean,
    adminFull: Boolean,
    onToggle: () -> Unit,
    onNickname: () -> Unit,
    onRemark: () -> Unit,
    onToggleAdmin: () -> Unit,
    onRemove: () -> Unit,
    onVisibility: () -> Unit,
) {
    val self = member.id == viewerId
    val shown = member.displayName(viewerId, circle.ownerId)
    val secondary = if (isOwner && member.remark != null && member.nickname != null) {
        stringResource(R.string.circle_members_nickname_prefix, member.nickname.orEmpty())
    } else {
        null
    }
    val reduced = rememberReducedMotion()
    val enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn()
    val exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut()

    Column(
        Modifier.fillMaxWidth()
            .background(if (expanded) XpColors.Surface2 else Color.Transparent),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .pressableClick(scaleDown = 0.99f, onClick = onToggle)
                .padding(horizontal = XpSpace.l, vertical = XpSpace.s + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAvatar(member, size = 36.dp)
            Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shown,
                        style = MaterialTheme.typography.titleMedium,
                        color = XpColors.Ink,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (self) {
                        Spacer(Modifier.width(XpSpace.xs + 1.dp))
                        CircleMeTag()
                    }
                }
                if (secondary != null) {
                    Text(
                        secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = XpColors.InkSecondary,
                        maxLines = 2,
                    )
                }
            }
            CircleRoleChip(member.role)
        }

        AnimatedVisibility(visible = expanded, enter = enter, exit = exit) {
            Column(Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = XpSpace.l, end = XpSpace.l, bottom = XpSpace.m),
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                    verticalArrangement = Arrangement.spacedBy(XpSpace.s),
                ) {
                    CircleActionChip(
                        text = stringResource(
                            if (self) R.string.circle_members_my_shares else R.string.circle_members_view_shares,
                        ),
                        onClick = onVisibility,
                    )
                    if (self) {
                        CircleActionChip(
                            text = stringResource(R.string.circle_members_nickname_action),
                            onClick = onNickname,
                        )
                    }
                    if (isOwner && member.id != circle.ownerId) {
                        val isAdmin = member.role == CircleRole.Admin
                        CircleActionChip(
                            text = stringResource(
                                if (isAdmin) R.string.circle_members_unmake_admin else R.string.circle_members_make_admin,
                            ),
                            enabled = isAdmin || !adminFull,
                            onClick = onToggleAdmin,
                        )
                        CircleActionChip(
                            text = stringResource(R.string.circle_members_remark_action),
                            onClick = onRemark,
                        )
                        CircleActionChip(
                            text = stringResource(R.string.circle_members_remove),
                            tone = XpColors.Negative,
                            onClick = onRemove,
                        )
                    }
                }
                if (isOwner && member.id != circle.ownerId && member.role != CircleRole.Admin && adminFull) {
                    Text(
                        stringResource(R.string.circle_members_admin_limit),
                        style = MaterialTheme.typography.labelSmall,
                        color = XpColors.InkTertiary,
                        modifier = Modifier.padding(start = XpSpace.l, end = XpSpace.l, bottom = XpSpace.m),
                    )
                }
            }
        }
    }
}

// --- visibility -------------------------------------------------------------------------------

@Composable
internal fun CircleVisibilityContent(
    book: CircleBook,
    viewerId: String,
    initialMemberId: String,
    onBookChange: (CircleBook) -> Unit,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val circle = book.current()
    var selectedId by remember(initialMemberId) { mutableStateOf(initialMemberId) }
    val selected = circle.member(selectedId) ?: circle.member(viewerId) ?: circle.members.first()
    val editable = canEditShares(viewerId, selected.id)
    var error by remember { mutableStateOf<CircleError?>(null) }

    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XpSpace.page)
            .padding(bottom = XpSpace.l),
    ) {
        CircleSheetHeader(
            title = stringResource(R.string.circle_vis_title),
            subtitle = stringResource(R.string.circle_vis_subtitle),
            onBack = onBack,
        )
        CircleErrorLine(error)

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            circle.members.forEach { member ->
                SelectCapsule(
                    text = member.displayName(viewerId, circle.ownerId),
                    selected = member.id == selected.id,
                    onClick = {
                        selectedId = member.id
                        error = null
                    },
                )
            }
        }
        Spacer(Modifier.height(XpSpace.m))

        GroupedSection(
            header = if (editable) {
                stringResource(R.string.circle_vis_self_header)
            } else {
                stringResource(R.string.circle_vis_other_header, selected.displayName(viewerId, circle.ownerId))
            },
            margin = 0.dp,
        ) {
            CircleShare.entries.forEach { share ->
                item(share) {
                    if (editable) {
                        GroupedRow(
                            title = shareLabel(share),
                            subtitle = shareHint(share),
                            trailing = {
                                XpSwitch(
                                    checked = selected.shows(share),
                                    onCheckedChange = { visible ->
                                        val result = setShare(book, circle.id, selected.id, share, visible)
                                        if (result.error != null) {
                                            error = result.error
                                        } else {
                                            error = null
                                            onBookChange(result.book)
                                        }
                                    },
                                )
                            },
                        )
                    } else {
                        GroupedRow(
                            title = shareLabel(share),
                            subtitle = shareHint(share),
                            value = stringResource(
                                if (selected.shows(share)) R.string.circle_vis_shared else R.string.circle_vis_not_shared,
                            ),
                        )
                    }
                }
            }
        }

        if (!editable) {
            CircleNote(stringResource(R.string.circle_vis_readonly, selected.displayName(viewerId, circle.ownerId)))
        }
        CircleNote(stringResource(R.string.circle_vis_footer))
        Spacer(Modifier.height(XpSpace.l))
        XpButton(
            text = stringResource(R.string.circle_sheet_done),
            onClick = onClose,
            style = XpButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(XpSpace.s))
    }
}

// --- invite -----------------------------------------------------------------------------------

@Composable
internal fun CircleInviteContent(
    book: CircleBook,
    onBookChange: (CircleBook) -> Unit,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val circle = book.current()
    val invite = circle.invite
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(invite.code, invite.createdAt, invite.uses) {
        tick = System.currentTimeMillis()
        while (true) {
            delay(1000)
            tick = System.currentTimeMillis()
        }
    }
    val expired = !invite.isValid(tick)
    val usedUp = invite.uses >= invite.maxUses
    var copied by remember(invite.code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }
    var showJoin by remember { mutableStateOf(false) }
    var joinedName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val haptics = rememberXpHaptics()

    fun copyCode() {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText(invite.code, invite.code))
        copied = true
        haptics.tick()
    }

    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XpSpace.page)
            .padding(bottom = XpSpace.l),
    ) {
        CircleSheetHeader(
            title = stringResource(R.string.circle_invite_title),
            subtitle = stringResource(R.string.circle_invite_subtitle),
            onBack = onBack,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            invite.code.forEach { char ->
                Box(
                    Modifier.weight(1f).height(58.dp)
                        .clip(RoundedCornerShape(XpRadius.m))
                        .background(XpColors.Fill),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        char.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                        ),
                        color = if (expired) XpColors.InkTertiary else XpColors.Ink,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = XpSpace.m)) {
            if (expired) {
                Text(
                    stringResource(
                        if (usedUp) R.string.circle_invite_used_up else R.string.circle_invite_expired,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.Negative,
                )
            } else {
                Text(
                    stringResource(
                        R.string.circle_invite_expires,
                        durationLabel((invite.expiresAt() - tick).coerceAtLeast(0)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                )
            }
            Text(
                stringResource(R.string.circle_invite_remaining, invite.remainingUses),
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                stringResource(R.string.circle_invite_members, circle.members.size, CIRCLE_MAX_MEMBERS),
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = XpSpace.l),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            XpButton(
                text = stringResource(if (copied) R.string.circle_invite_copied else R.string.circle_invite_copy),
                onClick = { copyCode() },
                style = XpButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
            XpButton(
                text = stringResource(R.string.circle_invite_refresh),
                onClick = {
                    joinedName = null
                    haptics.tick()
                    onBookChange(regenerateInvite(book, circle.id, System.currentTimeMillis()))
                },
                style = XpButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
        }

        val qr = remember(invite.code) { inviteQrBitmap("xiaopiao://circle/join?code=${invite.code}", 512) }
        if (qr != null) {
            Column(
                Modifier.fillMaxWidth().padding(top = XpSpace.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(168.dp)
                        .clip(RoundedCornerShape(XpRadius.m))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = stringResource(R.string.circle_invite_qr_desc),
                        modifier = Modifier.size(152.dp),
                    )
                }
                Text(
                    stringResource(R.string.circle_invite_qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = XpSpace.s),
                )
            }
        }

        joinedName?.let { name ->
            Text(
                stringResource(R.string.circle_invite_joined, name),
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.Positive,
                modifier = Modifier.padding(top = XpSpace.m),
            )
        }
        XpButton(
            text = stringResource(R.string.circle_invite_join),
            onClick = { showJoin = true },
            style = XpButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().padding(top = XpSpace.m),
        )

        CircleNote(stringResource(R.string.circle_invite_footer))
        Spacer(Modifier.height(XpSpace.l))
        XpButton(
            text = stringResource(R.string.circle_sheet_done),
            onClick = onClose,
            style = XpButtonStyle.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(XpSpace.s))
    }

    if (showJoin) {
        CircleJoinDialog(
            book = book,
            onDismiss = { showJoin = false },
            onJoined = { name, updated ->
                haptics.confirm()
                joinedName = name
                onBookChange(updated)
                showJoin = false
            },
        )
    }
}

// --- shared building blocks -------------------------------------------------------------------

@Composable
internal fun CircleSheetHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.xs, bottom = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(XpColors.Fill)
                    .pressableClick(scaleDown = 0.92f, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.circle_sheet_back),
                    tint = XpColors.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(XpSpace.m))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink, maxLines = 2)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Quiet footnote used at the bottom of every management page. */
@Composable
internal fun CircleNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = XpColors.InkSecondary,
        modifier = modifier.padding(top = XpSpace.m),
    )
}

/** The 「我」 tag: AccentSoft pill, same grammar as the leaderboard's own-row label. */
@Composable
internal fun CircleMeTag() {
    Box(
        Modifier.clip(RoundedCornerShape(XpRadius.s))
            .background(XpColors.AccentSoft)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            stringResource(R.string.circle_members_me_tag),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.Accent,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CircleRoleChip(role: CircleRole) {
    val background = when (role) {
        CircleRole.Owner -> XpColors.AccentSoft
        CircleRole.Admin -> XpColors.InfoSoft
        CircleRole.Member -> XpColors.Fill
    }
    val ink = when (role) {
        CircleRole.Owner -> XpColors.Accent
        CircleRole.Admin -> XpColors.Info
        CircleRole.Member -> XpColors.InkSecondary
    }
    Box(
        Modifier.clip(RoundedCornerShape(XpRadius.pill))
            .background(background)
            .padding(horizontal = XpSpace.s, vertical = 2.dp),
    ) {
        Text(roleLabel(role), style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1)
    }
}

@Composable
private fun CircleActionChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tone: Color = XpColors.Ink,
) {
    Box(
        Modifier.clip(RoundedCornerShape(XpRadius.pill))
            .background(XpColors.Fill)
            .then(if (enabled) Modifier.pressableClick(scaleDown = 0.95f, onClick = onClick) else Modifier)
            .padding(horizontal = XpSpace.m, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tone else XpColors.InkTertiary,
            maxLines = 1,
        )
    }
}

/** First glyph of a circle name as its icon: no images, no uploads (docs/APP-DESIGN.md §5.4). */
@Composable
private fun CircleInitialBadge(name: String, selected: Boolean) {
    Box(
        Modifier.size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) XpColors.Accent else XpColors.Fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) XpColors.OnAccent else XpColors.Ink,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CircleErrorLine(error: CircleError?, limit: Int? = null, modifier: Modifier = Modifier) {
    if (error == null) return
    Text(
        circleErrorMessage(error, limit),
        style = MaterialTheme.typography.bodySmall,
        color = XpColors.Negative,
        modifier = modifier.padding(top = XpSpace.s),
    )
}

@Composable
private fun circleErrorMessage(error: CircleError, limit: Int?): String = when (error) {
    CircleError.EmptyName -> stringResource(R.string.circle_err_empty_name)
    CircleError.NameTooLong -> if (limit != null) {
        stringResource(R.string.circle_err_name_too_long, limit)
    } else {
        stringResource(R.string.circle_err_too_long)
    }
    CircleError.CircleFull -> stringResource(R.string.circle_err_full, CIRCLE_MAX_MEMBERS)
    CircleError.InvalidCode -> stringResource(R.string.circle_err_invalid_code)
    CircleError.ExpiredCode -> stringResource(R.string.circle_err_expired_code)
    CircleError.CodeUsedUp -> stringResource(R.string.circle_err_used_up)
    CircleError.NotAllowed -> stringResource(R.string.circle_err_not_allowed)
    CircleError.AlreadyMember -> stringResource(R.string.circle_err_already_member)
}

@Composable
private fun roleLabel(role: CircleRole): String = when (role) {
    CircleRole.Owner -> stringResource(R.string.circle_role_owner)
    CircleRole.Admin -> stringResource(R.string.circle_role_admin)
    CircleRole.Member -> stringResource(R.string.circle_role_member)
}

// --- dialogs ----------------------------------------------------------------------------------

/**
 * One-field dialog (name, nickname, remark, invite code). Lives in its own window on purpose: a second
 * bottom sheet would fight the one underneath it, and the keyboard needs a surface it can push.
 */
@Composable
private fun CircleInputDialogCore(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    confirmLabel: String,
    error: CircleError?,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    errorLimit: Int? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XpColors.Card,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(XpRadius.m))
                        .background(XpColors.Fill)
                        .padding(horizontal = XpSpace.m, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink),
                    cursorBrush = SolidColor(XpColors.Accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = XpColors.InkTertiary,
                                    maxLines = 2,
                                )
                            }
                            inner()
                        }
                    },
                )
                CircleErrorLine(error, errorLimit)
            }
        },
        confirmButton = {
            XpButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                size = XpButtonSize.Small,
            )
        },
        dismissButton = {
            XpButton(
                text = stringResource(R.string.circle_sheet_cancel),
                onClick = onDismiss,
                style = XpButtonStyle.Plain,
                size = XpButtonSize.Small,
            )
        },
    )
}

@Composable
private fun CircleNameDialog(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    maxLength: Int,
    allowEmpty: Boolean = false,
    onCancel: () -> Unit,
    onSubmit: (String) -> CircleError?,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<CircleError?>(null) }
    CircleInputDialogCore(
        title = title,
        value = value,
        onValueChange = {
            value = it.take(maxLength)
            error = null
        },
        placeholder = placeholder,
        confirmLabel = confirmLabel,
        error = error,
        confirmEnabled = allowEmpty || value.trim().isNotEmpty(),
        errorLimit = maxLength,
        onConfirm = {
            val failure = onSubmit(value)
            if (failure == null) onCancel() else error = failure
        },
        onDismiss = onCancel,
    )
}

@Composable
private fun CircleJoinDialog(
    book: CircleBook,
    onDismiss: () -> Unit,
    onJoined: (String, CircleBook) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<CircleError?>(null) }
    val joinerName = stringResource(R.string.circle_invite_joiner_name)
    CircleInputDialogCore(
        title = stringResource(R.string.circle_invite_join_title),
        value = code,
        onValueChange = {
            code = normalizeInviteCode(it).take(INVITE_LENGTH)
            error = null
        },
        placeholder = stringResource(R.string.circle_invite_join_placeholder),
        confirmLabel = stringResource(R.string.circle_invite_join_confirm),
        error = error,
        confirmEnabled = code.length == INVITE_LENGTH,
        onConfirm = {
            val now = System.currentTimeMillis()
            val target = book.circles.firstOrNull { it.invite.code == code }
            val joiner = CircleMember(
                id = "joined-$now",
                name = joinerName,
                initial = joinerName.take(1),
                tone = CircleTone.Info,
            )
            val result = redeemInvite(book, code, now, joiner)
            if (result.error != null) {
                error = result.error
            } else {
                onJoined(target?.name.orEmpty(), result.book)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun CircleConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XpColors.Card,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
        },
        text = {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
        },
        confirmButton = {
            XpButton(
                text = confirmLabel,
                onClick = onConfirm,
                style = if (destructive) XpButtonStyle.Destructive else XpButtonStyle.Primary,
                size = XpButtonSize.Small,
            )
        },
        dismissButton = {
            XpButton(
                text = stringResource(R.string.circle_sheet_cancel),
                onClick = onDismiss,
                style = XpButtonStyle.Plain,
                size = XpButtonSize.Small,
            )
        },
    )
}

@Composable
private fun CircleAwardDialog(
    lines: List<Pair<String, String>>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XpColors.Card,
        title = {
            Text(stringResource(R.string.circle_award_title), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.circle_award_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = XpColors.InkSecondary,
                )
                Spacer(Modifier.height(XpSpace.m))
                lines.forEach { (name, title) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = XpColors.Ink,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(XpSpace.s))
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = XpColors.InkSecondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            XpButton(
                text = stringResource(R.string.circle_award_confirm),
                onClick = onConfirm,
                size = XpButtonSize.Small,
            )
        },
        dismissButton = {
            XpButton(
                text = stringResource(R.string.circle_sheet_cancel),
                onClick = onDismiss,
                style = XpButtonStyle.Plain,
                size = XpButtonSize.Small,
            )
        },
    )
}

// --- shared copy ------------------------------------------------------------------------------

/** Display label for a [CircleShare] item; the enum's own `label` is the model's Chinese default. */
@Composable
internal fun shareLabel(share: CircleShare): String = stringResource(
    when (share) {
        CircleShare.TodayTotal -> R.string.circle_share_today_total
        CircleShare.CategoryMix -> R.string.circle_share_category_mix
        CircleShare.WeeklyTitles -> R.string.circle_share_weekly_titles
        CircleShare.TxnDetail -> R.string.circle_share_txn_detail
        CircleShare.ReceiptFull -> R.string.circle_share_receipt_full
    },
)

@Composable
internal fun shareHint(share: CircleShare): String = stringResource(
    when (share) {
        CircleShare.TodayTotal -> R.string.circle_share_today_total_hint
        CircleShare.CategoryMix -> R.string.circle_share_category_mix_hint
        CircleShare.WeeklyTitles -> R.string.circle_share_weekly_titles_hint
        CircleShare.TxnDetail -> R.string.circle_share_txn_detail_hint
        CircleShare.ReceiptFull -> R.string.circle_share_receipt_full_hint
    },
)

/** Badge titles are demo data (Chinese in the domain pool); map the known ones so English stays English. */
@Composable
internal fun badgeLabel(badge: CircleBadge): String {
    val key = when (badge.title) {
        "第一天" -> R.string.circle_badge_first_day
        "三张连打" -> R.string.circle_badge_three_receipts
        "吃饭规律" -> R.string.circle_badge_regular_meals
        "咖啡续命" -> R.string.circle_badge_coffee
        "囤货达人" -> R.string.circle_badge_stock_up
        "通勤战士" -> R.string.circle_badge_commute
        "早睡省钱" -> R.string.circle_badge_early_night
        "预算守门" -> R.string.circle_badge_budget_guard
        "冷静选手" -> R.string.circle_badge_cool_head
        "最会过日子" -> R.string.circle_badge_best_life
        else -> null
    }
    return if (key != null) stringResource(key) else badge.title
}

// --- helpers ----------------------------------------------------------------------------------

@Composable
private fun durationLabel(remainingMs: Long): String {
    val totalMinutes = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.circle_dur_hm, hours, minutes)
    } else {
        stringResource(R.string.circle_dur_m, minutes)
    }
}

/** Local, offline QR for the invite code (zxing core); returns null if encoding fails. */
private fun inviteQrBitmap(content: String, sizePx: Int): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()
