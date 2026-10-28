package com.xiaopiao.app.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Session drawer (v6): 82% wide, slides in from the left, pushes the page 24dp and dims it. Search at the
 * top, 新对话, sessions grouped by 今天 / 昨天 / 7 天内 / 更早, long-press a row to rename or delete, and the
 * settings entry lives at the bottom with the avatar (the top bar is reserved for ☰ / segment / 新对话).
 *
 * Drag: horizontal drag on the panel closes it (velocity-aware). Opening is a tap on ☰ or an edge swipe
 * handled by [ChatDrawerEdge]; both share the same spring, so an interrupted drag resumes from where it is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatDrawer(
    open: Boolean,
    sessions: List<ChatSession>,
    now: Long,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSession: (ChatSession) -> Unit,
    onRename: (ChatSession, String) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onOpenSettings: () -> Unit,
    progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ChatSession?>(null) }
    var renamingText by remember { mutableStateOf("") }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelWidth = maxWidth * 0.82f
        val density = LocalDensity.current
        val widthPx = with(density) { panelWidth.toPx() }

        if (progress.value > 0.001f) {
            Box(
                Modifier.fillMaxSize()
                    .background(XpColors.Scrim.copy(alpha = XpColors.Scrim.alpha * progress.value))
                    .clickable(remember { MutableInteractionSource() }, null) { onClose() },
            )
        }

        val filtered = remember(sessions, query) {
            if (query.isBlank()) sessions
            else sessions.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
        val groups = remember(filtered, now) { SessionGroups.group(filtered, now) }

        Column(
            Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .offset { IntOffset((-widthPx + widthPx * progress.value).roundToInt(), 0) }
                .background(XpColors.Card)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { progress.snapTo((progress.value + delta / widthPx).coerceIn(0f, 1f)) }
                    },
                    onDragStopped = { velocity ->
                        val target = if (velocity < -600f || progress.value < 0.6f) 0f else 1f
                        progress.animateTo(target, spring(dampingRatio = 1f, stiffness = 420f))
                        if (target == 0f) onClose()
                    },
                )
                .statusBarsPadding()
                // Reserve the floating tab bar the shell draws above every page, so the account row and
                // settings stay tappable (AppShell owns that bar; see the lane report).
                .padding(start = XpSpace.l, end = XpSpace.m, top = XpSpace.m, bottom = 88.dp),
        ) {
            SearchField(query) { query = it }
            NewChatRow(onNewChat)
            LazyColumn(Modifier.weight(1f).padding(top = XpSpace.xs)) {
                if (groups.isEmpty()) {
                    item("empty") {
                        Text(
                            if (query.isBlank()) "还没有历史会话" else "没有匹配的会话",
                            style = MaterialTheme.typography.bodySmall,
                            color = XpColors.InkTertiary,
                            modifier = Modifier.padding(vertical = XpSpace.m),
                        )
                    }
                }
                groups.forEach { (bucket, list) ->
                    item("bucket-${bucket.name}") {
                        Text(
                            bucket.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = XpColors.InkTertiary,
                            modifier = Modifier.padding(start = XpSpace.xs, top = XpSpace.m, bottom = XpSpace.xs),
                        )
                    }
                    items(list, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            onOpen = { onOpenSession(session) },
                            onRename = { renaming = session; renamingText = session.title },
                            onDelete = { onDelete(session) },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = XpSpace.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(XpColors.Fill),
                    contentAlignment = Alignment.Center,
                ) { Text("票", style = MaterialTheme.typography.titleSmall, color = XpColors.Ink) }
                Column(Modifier.weight(1f).padding(start = XpSpace.s)) {
                    Text("本机账本", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                    Text("数据只在这台手机", style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).pressableClick { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = XpColors.Ink, modifier = Modifier.size(20.dp)) }
            }
        }
    }

    // Rename: one field, one save — no nested sheets.
    renaming?.let { session ->
        Dialog(onDismissRequest = { renaming = null }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.l)).background(XpColors.Card)
                    .padding(XpSpace.l),
                verticalArrangement = Arrangement.spacedBy(XpSpace.m),
            ) {
                Text("重命名会话", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                BasicTextField(
                    value = renamingText,
                    onValueChange = { renamingText = it.take(40) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.m))
                        .background(XpColors.Fill).padding(horizontal = XpSpace.m, vertical = XpSpace.s),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink),
                    cursorBrush = SolidColor(XpColors.Accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    XpButton("取消", { renaming = null }, Modifier.weight(1f), style = XpButtonStyle.Secondary, size = XpButtonSize.Small)
                    XpButton(
                        "保存",
                        {
                            val text = renamingText.trim()
                            if (text.isNotEmpty()) onRename(session, text)
                            renaming = null
                        },
                        Modifier.weight(1f),
                        size = XpButtonSize.Small,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(XpRadius.pill))
            .background(XpColors.Fill).padding(horizontal = XpSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = XpColors.InkTertiary, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).padding(start = XpSpace.s),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = XpColors.Ink),
            cursorBrush = SolidColor(XpColors.Accent),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("搜索会话", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = XpColors.InkTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun NewChatRow(onNewChat: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.s)
            .clip(RoundedCornerShape(XpRadius.m))
            .pressableClick(scaleDown = 0.98f) { onNewChat() }
            .padding(horizontal = XpSpace.xs, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = XpColors.Ink, modifier = Modifier.size(20.dp))
        Text(
            "新对话",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = XpColors.Ink,
            modifier = Modifier.padding(start = XpSpace.s),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: ChatSession,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.m))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                    onLongClick = { menu = true },
                )
                .padding(horizontal = XpSpace.xs, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = XpColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${session.rounds} 轮 · ${SessionGroups.bucketOf(session.updatedAt, System.currentTimeMillis()).label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkTertiary,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("重命名") },
                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("删除", color = XpColors.Negative) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = XpColors.Negative, modifier = Modifier.size(18.dp)) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/** Invisible 20dp strip on the left edge: a real drag (not a tap) opens the drawer. */
@Composable
fun ChatDrawerEdge(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var travelled by remember { mutableStateOf(0f) }
    Box(
        modifier
            .width(20.dp)
            .fillMaxHeight()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> travelled += delta },
                onDragStarted = { travelled = 0f },
                onDragStopped = { velocity ->
                    if (travelled > 24f || velocity > 400f) onOpen()
                    travelled = 0f
                },
            ),
    )
}

