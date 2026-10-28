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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.GroupedRow
import androidx.compose.foundation.clickable

/**
 * AI 对话 — the left-most tab. Assistant speaks first, cards live in the same flow, and every answer is a
 * local template over [com.xiaopiao.app.domain.LedgerRepository]. No network, no model calls, no advice.
 *
 * The caller owns the tab-bar / navigation insets: this screen only claims the status bar (its compact
 * header) and the keyboard (so the input bar rides above it). If the host already reserves the tab bar as
 * fixed dp, pass that inset down rather than wrapping this screen in another imePadding.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    var historyOpen by remember { mutableStateOf(false) }
    // Past conversations live in memory for this session; archiving keeps their items so switching restores them.
    val sessions = remember { androidx.compose.runtime.mutableStateListOf<ChatSession>() }
    var sessionSeq by remember { mutableIntStateOf(0) }
    val haptics = rememberXpHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val reduced = rememberReducedMotion()
    val focusRequester = remember { FocusRequester() }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        conversation.add(ChatItem.User(newId(), text))
        haptics.select()
        val reply = stats?.let { ChatIntentRouter.route(text, it, turn) }
            ?: ChatReply.Answer("我还没数完今天的账，稍等一秒再说。")
        // Tools first: the user sees what was read before the answer appears (tt#23).
        val tools = ChatTools.plan(text, stats, text.contains("家人") || text.contains("圈子"))
        scope.launch {
            tools.forEach { step ->
                val toolId = newId()
                conversation.add(ChatItem.Tool(toolId, step.label, running = true))
                scrollSignal++
                delay(if (reduced) 0 else 320)
                val i = conversation.indexOfFirst { it.id == toolId }
                if (i >= 0) conversation[i] = ChatItem.Tool(toolId, step.label, step.detail, running = false)
            }
            when (reply) {
                is ChatReply.Expense -> {
                    conversation.add(ChatItem.Confirm(newId(), reply.draft))
                }
                is ChatReply.Answer -> {
                    // Stream the answer in chunks so it reads like a model writing, not a paste.
                    val id = newId()
                    conversation.add(ChatItem.Assistant(id, if (reduced) reply.text else ""))
                    if (!reduced) {
                        val steps = 26
                        val size = (reply.text.length / steps).coerceAtLeast(1)
                        var shown = 0
                        while (shown < reply.text.length) {
                            shown = (shown + size).coerceAtMost(reply.text.length)
                            val i = conversation.indexOfFirst { it.id == id }
                            if (i < 0) break
                            conversation[i] = ChatItem.Assistant(id, reply.text.substring(0, shown))
                            scrollSignal++
                            delay(16)
                        }
                    }
                }
            }
            // Rich rendering: a week question also gets the chart, not just a sentence.
            if (!reduced && stats != null && (text.contains("周") || text.contains("哪天")) &&
                !text.contains("家人") && !text.contains("圈子") && !text.contains("妈") && !text.contains("爸")) {
                conversation.add(
                    ChatItem.Chart(
                        newId(),
                        "本周每日支出",
                        stats.week.dayTotals,
                        "合计 ${yuanText(stats.week.totalOutCents)} · 共 ${stats.week.txnCount} 笔",
                    ),
                )
            }
            scrollSignal++
        }
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
        ChatHeader(dayLabel, demo = stats?.hasDemo == true, onOpenSettings = onOpenSettings, onOpenHistory = { historyOpen = true })
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
                        is ChatItem.Tool -> ToolRow(item)
                        is ChatItem.Chart -> ChartCard(item)
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
        // Conversation drawer: slides in from the LEFT edge (tt#29), scrim closes it.
        if (historyOpen) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(XpColors.Scrim).clickable { historyOpen = false })
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.slideInHorizontally(
                        androidx.compose.animation.core.spring(0.9f, 320f),
                    ) { -it } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)),
                ) {
                    Column(
                        Modifier.fillMaxHeight().width(300.dp).background(XpColors.Card)
                            .statusBarsPadding().padding(XpSpace.l),
                    ) {
                        Text("会话", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                        XpButton(
                            "新对话", {
                                val firstUser = conversation.firstOrNull { it is ChatItem.User } as? ChatItem.User
                                if (firstUser != null) {
                                    sessions.add(0, ChatSession(++sessionSeq, firstUser.text.take(18), conversation.toList()))
                                }
                                conversation.clear()
                                turn.period = ChatPeriod.Week
                                turn.category = null
                                historyOpen = false
                            },
                            Modifier.fillMaxWidth().padding(top = XpSpace.m),
                        )
                        Spacer(Modifier.height(XpSpace.s))
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                            sessions.forEach { item ->
                                item(item.id) {
                                    GroupedRow(
                                        title = item.title,
                                        subtitle = "${item.items.count { it is ChatItem.User }} 轮",
                                        modifier = Modifier.clickable {
                                            conversation.clear()
                                            conversation.addAll(item.items)
                                            historyOpen = false
                                        },
                                    )
                                }
                            }
                            if (sessions.isEmpty()) {
                                item {
                                    Text(
                                        "还没有别的会话。开一个新对话，刚才这段会留在这里。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = XpColors.InkTertiary,
                                        modifier = Modifier.padding(vertical = XpSpace.m),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ChatSession(val id: Int, val title: String, val items: List<ChatItem>)

/** Compact tab-root header (no large title): who is speaking + the date; the pill says it all stays local. */
@Composable
private fun ChatHeader(dayLabel: String, demo: Boolean, onOpenSettings: () -> Unit, onOpenHistory: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(XpColors.Canvas)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = XpSpace.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIconButton(Icons.Outlined.Menu, "会话管理", onOpenHistory)
            androidx.compose.foundation.layout.Spacer(Modifier.width(XpSpace.s))
            Column(Modifier.weight(1f)) {
                Text("小票助手", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
            }
            Pill("本机统计")
            androidx.compose.foundation.layout.Spacer(Modifier.width(XpSpace.s))
            com.xiaopiao.app.ui.today.ReceiptStrip()
            androidx.compose.foundation.layout.Spacer(Modifier.width(XpSpace.s))
            NavIconButton(Icons.Outlined.Settings, "设置", onOpenSettings)
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
                    ChatChip(label) { onSuggestion(label) }
                }
            }
        }
        // One capsule: field + action live together, so the composer is a single object (tt#29).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s)
                .height(48.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
                .padding(start = XpSpace.l, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink),
                    singleLine = true,
                    cursorBrush = SolidColor(XpColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    "说一句「午饭 28」",
                                    style = MaterialTheme.typography.bodyLarge,
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
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { onVoice() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Mic, contentDescription = "语音", tint = XpColors.InkSecondary, modifier = Modifier.size(20.dp)) }
            } else {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(XpColors.Primary).clickable { onSend() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发送", tint = XpColors.OnPrimary, modifier = Modifier.size(18.dp)) }
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

/** One tool step: a small spinner while it runs, then the one-line result. */
@Composable
private fun ToolRow(item: ChatItem.Tool) {
    val spin = rememberInfiniteTransition(label = "tool")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "toolSpin",
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        if (item.running) {
            Icon(
                Icons.Outlined.Autorenew, contentDescription = null,
                tint = XpColors.Accent, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = angle },
            )
        } else {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = XpColors.Positive, modifier = Modifier.size(14.dp))
        }
        Text(item.label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        item.detail?.let {
            Text("· $it", style = MaterialTheme.typography.bodySmall, color = XpColors.InkTertiary, maxLines = 1)
        }
    }
}

/** Chart card inside the flow: answers carry a picture when they are about a period. */
@Composable
private fun ChartCard(item: ChatItem.Chart) {
    XpCard(Modifier.padding(top = XpSpace.s)) {
        Text(item.title, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        WeekBars(item.days)
        item.caption?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = XpColors.InkTertiary, modifier = Modifier.padding(top = XpSpace.s))
        }
    }
}

/** Quick-reply chip: smaller and quieter than a selectable capsule (tt#30). */
@Composable
private fun ChatChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
            .clickable { onClick() }.padding(horizontal = XpSpace.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = XpColors.Ink, maxLines = 1)
    }
}
