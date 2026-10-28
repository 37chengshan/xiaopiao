package com.xiaopiao.app.ui.chat

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.DemoChip
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.SelectCapsule
import com.xiaopiao.app.ui.components.ShimmerBlock
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.pressableScale
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.today.ReceiptLayout
import com.xiaopiao.app.ui.today.TodayGreeting
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * AI 对话 — the left-most tab. Assistant speaks first, cards live in the same flow, and every answer is a
 * local template over [com.xiaopiao.app.domain.LedgerRepository]. No network, no model calls, no advice.
 *
 * The caller owns the tab-bar / navigation insets: this screen only claims the status bar (its compact
 * header) and the keyboard (so the input bar rides above it). If the host already reserves the tab bar as
 * fixed dp, pass that inset down rather than wrapping this screen in another imePadding.
 */
@Composable
fun ChatScreen(
    container: AppContainer,
    onRecordVoice: () -> Unit = {},
    onRecordManual: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // Remembers what the last question was about so 「那上周呢」 works without repeating the topic.
    val turn = remember { ChatTurn() }
    val history by remember(container) { container.ledger.observeAll() }
        .collectAsStateWithLifecycle(initialValue = null)
    // One pass over the ledger per emission; null means "still loading".
    val snapshot = remember(history) {
        history?.let {
            val now = System.currentTimeMillis()
            val stats = buildChatStats(it, now)
            stats to buildReminder(it, stats, now)
        }
    }
    val stats = snapshot?.first
    val reminder = snapshot?.second

    val hello = remember { TodayGreeting.hello(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    val dayLabel = remember { ReceiptLayout.dayLabel(startOfDay(System.currentTimeMillis())) }

    val conversation = remember { androidx.compose.runtime.mutableStateListOf<ChatItem>() }
    var seq by remember { mutableIntStateOf(0) }
    fun newId(): Long = (++seq).toLong()

    var input by rememberSaveable { mutableStateOf("") }
    var scrollSignal by remember { mutableIntStateOf(0) }
    val haptics = rememberXpHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val reduced = rememberReducedMotion()
    val focusRequester = remember { FocusRequester() }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        conversation.add(ChatItem.User(newId(), text))
        val reply = stats?.let { ChatIntentRouter.route(text, it, turn) }
            ?: ChatReply.Answer("我还没数完今天的账，稍等一秒再说。")
        when (reply) {
            is ChatReply.Expense -> conversation.add(ChatItem.Confirm(newId(), reply.draft))
            is ChatReply.Answer -> conversation.add(ChatItem.Assistant(newId(), reply.text))
        }
        scrollSignal++
        haptics.select()
    }

    fun confirm(card: ChatItem.Confirm) {
        if (card.state != ConfirmState.Pending) return
        conversation.indexOfFirst { it.id == card.id }.takeIf { it >= 0 }?.let {
            conversation[it] = card.copy(state = ConfirmState.Saving)
        }
        scope.launch {
            val id = container.ledger.add(card.draft.toTxn(System.currentTimeMillis()))
            val index = conversation.indexOfFirst { it.id == card.id }
            if (index >= 0) {
                conversation[index] = card.copy(state = if (id != null) ConfirmState.Saved else ConfirmState.Duplicate)
            }
            haptics.confirm()
            conversation.add(
                ChatItem.Assistant(
                    newId(),
                    if (id != null) "记下了：${card.draft.category.label} ${yuanText(card.draft.amountCents)}。今天的小票已经更新。"
                    else "这笔看起来已经记过了，我没有重复入账。",
                ),
            )
            scrollSignal++
        }
    }

    /** 「改一下」 puts the original sentence back in the field so the router re-parses the edit. */
    fun edit(card: ChatItem.Confirm) {
        val index = conversation.indexOfFirst { it.id == card.id }
        if (index < 0) return
        conversation.removeAt(index)
        input = card.draft.sourceText
        runCatching { focusRequester.requestFocus() }
        haptics.select()
    }

    LaunchedEffect(scrollSignal) {
        if (scrollSignal == 0) return@LaunchedEffect
        delay(80)
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) {
            if (reduced) listState.scrollToItem(last) else listState.animateScrollToItem(last)
        }
    }

    Column(Modifier.fillMaxSize().background(XpColors.Canvas).imePadding()) {
        ChatHeader(dayLabel, demo = stats?.hasDemo == true)
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(top = XpSpace.s, bottom = XpSpace.l),
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            item(key = "greeting") { AssistantText(ChatCopy.greeting(stats, hello)) }
            if (stats == null) {
                item(key = "loading") {
                    ShimmerBlock(
                        Modifier.padding(horizontal = XpSpace.l).fillMaxWidth().height(120.dp),
                        RoundedCornerShape(XpRadius.m),
                    )
                }
            } else {
                if (reminder != null) item(key = "reminder") { ReminderLine(reminder) }
                item(key = "receipt") { TodayReceiptCard(stats, dayLabel) }
                item(key = "week") { WeekReportCard(stats) }
            }
            itemsIndexed(conversation, key = { _, item -> item.id }) { _, item ->
                Box(
                    Modifier.then(
                        if (reduced) Modifier
                        else Modifier.animateItem(
                            fadeInSpec = tween(180, easing = XpEase.out),
                            placementSpec = tween(180, easing = XpEase.out),
                            fadeOutSpec = null,
                        ),
                    ),
                ) {
                    when (item) {
                        is ChatItem.Assistant -> AssistantText(item.text)
                        is ChatItem.User -> UserBubble(item.text)
                        is ChatItem.Confirm -> ConfirmCard(
                            item,
                            onConfirm = { confirm(item) },
                            onEdit = { edit(item) },
                        )
                    }
                }
            }
        }
        ChatComposer(
            value = input,
            onValueChange = { input = it },
            onSend = {
                if (input.isNotBlank()) {
                    send(input)
                    input = ""
                }
            },
            onVoice = onRecordVoice,
            focusRequester = focusRequester,
            showSuggestions = conversation.none { it is ChatItem.User },
            onSuggestion = { send(it) },
        )
    }
}

/** Compact tab-root header (no large title): who is speaking + the date; the pill says it all stays local. */
@Composable
private fun ChatHeader(dayLabel: String, demo: Boolean) {
    Column(Modifier.fillMaxWidth().background(XpColors.Canvas)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = XpSpace.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("小票助手", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
            }
            if (demo) DemoChip(Modifier.padding(end = XpSpace.s))
            Pill("本机统计")
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(XpColors.Hairline))
    }
}

/** Assistant text: no bubble, left-aligned ink. */
@Composable
private fun AssistantText(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs),
        style = MaterialTheme.typography.bodyLarge,
        color = XpColors.Ink,
    )
}

/** User text: tinted bubble, right-aligned, primary ink. */
@Composable
private fun UserBubble(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(XpRadius.l))
                .background(XpColors.Primary.copy(alpha = 0.12f))
                .padding(horizontal = XpSpace.m, vertical = XpSpace.s),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = XpColors.Primary)
        }
    }
}

/** The one reminder line under the greeting: subscription due date or weekly-report preview. */
@Composable
private fun ReminderLine(reminder: ChatReminder) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.NotificationsNone,
            contentDescription = null,
            tint = XpColors.InkTertiary,
            modifier = Modifier.padding(top = 2.dp).size(15.dp),
        )
        Text(
            reminder.text,
            Modifier.weight(1f).padding(start = XpSpace.s),
            style = MaterialTheme.typography.bodyMedium,
            color = XpColors.InkSecondary,
        )
        if (reminder.demo) DemoChip(Modifier.padding(start = XpSpace.s))
    }
}

/**
 * Input bar pinned above the tab bar: ＋ (manual form), text field, and either 发送 (primary capsule) or the
 * mic, which opens voice on tap and on long press (both paths call the same callback; long press is the
 * documented gesture, tap keeps it reachable for TalkBack).
 */
@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    focusRequester: FocusRequester,
    showSuggestions: Boolean,
    onSuggestion: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(XpColors.Canvas)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(XpColors.Hairline))
        if (showSuggestions) {
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.s),
                horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            ) {
                ChatSuggestions.forEach { label ->
                    SelectCapsule(label, selected = false, onClick = { onSuggestion(label) })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.m, vertical = XpSpace.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            Box(
                Modifier.weight(1f).height(40.dp)
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .background(XpColors.Fill)
                    .padding(horizontal = XpSpace.m),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = XpColors.Ink),
                    singleLine = true,
                    cursorBrush = SolidColor(XpColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    "说一句「午饭 28」",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = XpColors.InkTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            if (value.isBlank()) {
                RoundButton(Icons.Outlined.Mic, "按住说话", onClick = onVoice, onLongClick = onVoice)
            } else {
                XpButton("发送", onSend, style = XpButtonStyle.Primary, size = XpButtonSize.Small)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberXpHaptics()
    Box(
        Modifier.size(40.dp)
            .pressableScale(source, 0.94f)
            .clip(CircleShape)
            .background(XpColors.Fill)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onLongClickLabel = if (onLongClick != null) description else null,
                onLongClick = onLongClick?.let { action -> { haptics.thud(); action() } },
                onClick = { haptics.select(); onClick() },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = XpColors.Ink, modifier = Modifier.size(20.dp))
    }
}
