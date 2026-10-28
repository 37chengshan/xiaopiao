package com.xiaopiao.app.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.ShimmerBlock
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.motion.MotionMath
import com.xiaopiao.app.ui.motion.rememberSkeletonVisible
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.today.ReceiptLayout
import com.xiaopiao.app.ui.today.ReceiptStrip
import com.xiaopiao.app.ui.today.TodayGreeting
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Characters per second for the streaming reply; dt-based, so refresh rate cannot change it. */
private const val StreamCharsPerSecond = 78f

/** Drawer travel applied to the page while the drawer is open (ChatGPT pushes the content a little). */
private val DrawerPagePush = 24.dp

/**
 * AI page (v6, docs/APP-DESIGN.md §3.4). Top bar: ☰ · segment · receipt strip · new chat. Body: one soft
 * gradient, no cards until you ask something. Bottom: one white input card that grows when you type.
 * Everything is local: the same ledger, the same templates, no network.
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
    var segment by rememberSaveable { mutableStateOf(ChatSegment.Chat) }
    var drawerOpen by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    var streamJob by remember { mutableStateOf<Job?>(null) }

    val sessions = remember { androidx.compose.runtime.mutableStateListOf<ChatSession>() }
    var sessionSeq by remember { mutableIntStateOf(0) }
    var activeSession by remember { mutableIntStateOf(++sessionSeq) }

    val haptics = rememberXpHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val reduced = rememberReducedMotion()
    val focusRequester = remember { FocusRequester() }

    /** Saves the open conversation into the drawer list (no-op for an untouched chat). */
    fun archiveCurrent() {
        if (conversation.none { it is ChatItem.User }) return
        val saved = ChatSession(
            id = activeSession,
            title = SessionGroups.titleOf(conversation),
            items = conversation.toList(),
            updatedAt = System.currentTimeMillis(),
        )
        sessions.removeAll { it.id == activeSession }
        sessions.add(0, saved)
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        streaming = false
        // Finish whatever was mid-write so the caret never stays blinking.
        for (i in conversation.indices) {
            val item = conversation[i]
            if (item is ChatItem.Assistant && item.streaming) conversation[i] = item.copy(streaming = false)
        }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || streaming) return
        conversation.add(ChatItem.User(newId(), text))
        haptics.select()
        val reply = stats?.let { ChatIntentRouter.route(text, it, turn) }
            ?: ChatReply.Answer("我还没数完今天的账，稍等一秒再说。")
        // Tools first: the user sees what was read before the answer appears (tt#23).
        val tools = ChatTools.plan(text, stats, text.contains("家人") || text.contains("圈子"))
        streaming = true
        streamJob = scope.launch {
            try {
                tools.forEach { step ->
                    val toolId = newId()
                    conversation.add(ChatItem.Tool(toolId, step.label, running = true))
                    scrollSignal++
                    delay(if (reduced) 0 else 320)
                    val i = conversation.indexOfFirst { it.id == toolId }
                    if (i >= 0) conversation[i] = ChatItem.Tool(toolId, step.label, step.detail, running = false)
                }
                when (reply) {
                    is ChatReply.Expense -> conversation.add(ChatItem.Confirm(newId(), reply.draft))
                    is ChatReply.Answer -> {
                        // Stream the answer like a model writing: the reveal advances by real dt, so the
                        // text finishes in the same wall-clock time on 60/90/120Hz (never by frame count).
                        val id = newId()
                        conversation.add(ChatItem.Assistant(id, if (reduced) reply.text else "", streaming = !reduced))
                        if (!reduced) {
                            var shown = 0
                            var carry = 0f
                            var lastFrame = 0L
                            while (shown < reply.text.length) {
                                val now = withFrameNanos { it }
                                val dt = MotionMath.dtSeconds(lastFrame, now)
                                lastFrame = now
                                if (dt <= 0f) continue
                                carry += dt * StreamCharsPerSecond
                                val add = carry.toInt()
                                if (add <= 0) continue
                                carry -= add
                                shown = (shown + add).coerceAtMost(reply.text.length)
                                val i = conversation.indexOfFirst { it.id == id }
                                if (i < 0) break
                                conversation[i] = ChatItem.Assistant(
                                    id, reply.text.substring(0, shown), streaming = shown < reply.text.length,
                                )
                                scrollSignal++
                            }
                            val i = conversation.indexOfFirst { it.id == id }
                            if (i >= 0) conversation[i] = ChatItem.Assistant(id, reply.text)
                        }
                    }
                }
                // A chart is information, not decoration: reduced-motion users get it too (just static).
                if (stats != null && (text.contains("周") || text.contains("哪天")) &&
                    !text.contains("家人") && !text.contains("圈子") && !text.contains("妈") && !text.contains("爸")
                ) {
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
            } finally {
                streaming = false
            }
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

    // Skeletons stay up for at least 200ms, so a cache hit never flashes a placeholder.
    val skeletonVisible = rememberSkeletonVisible(stats == null)

    val drawer = remember { Animatable(if (drawerOpen) 1f else 0f) }
    LaunchedEffect(drawerOpen) {
        drawer.animateTo(if (drawerOpen) 1f else 0f, spring(dampingRatio = 1f, stiffness = 420f))
    }

    Box(Modifier.fillMaxSize().background(chatBackdrop())) {
        Column(
            Modifier.fillMaxSize().imePadding()
                .graphicsLayer { translationX = DrawerPagePush.toPx() * drawer.value },
        ) {
            ChatTopBar(
                segment = segment,
                onSegment = { segment = it },
                onMenu = { drawerOpen = true },
                onNewChat = {
                    archiveCurrent()
                    conversation.clear()
                    input = ""
                    activeSession = ++sessionSeq
                    scrollSignal++
                },
            )
            Box(Modifier.weight(1f)) {
                when {
                    segment == ChatSegment.Ledger -> LedgerPanel(
                        stats = stats,
                        dayLabel = dayLabel,
                        skeletonVisible = skeletonVisible,
                        onAsk = { question -> segment = ChatSegment.Chat; send(question) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    conversation.isEmpty() -> ChatEmptyState(
                        hint = emptyHint(stats, hello),
                        onSuggestion = { question -> send(question) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> ConversationList(
                        conversation = conversation,
                        reminder = reminder,
                        listState = listState,
                        reduced = reduced,
                        onConfirm = ::confirm,
                        onEdit = ::edit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            ChatComposer(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    if (ChatInput.canSend(input, streaming)) {
                        send(input)
                        input = ""
                    }
                },
                onVoice = onRecordVoice,
                onStop = ::stopStreaming,
                onPlus = onRecordManual,
                streaming = streaming,
                focusRequester = focusRequester,
            )
        }

        // Edge swipe to open; the drawer itself handles drag-to-close.
        ChatDrawerEdge(onOpen = { drawerOpen = true }, modifier = Modifier.align(Alignment.CenterStart))

        ChatDrawer(
            open = drawerOpen,
            sessions = sessions,
            now = System.currentTimeMillis(),
            onClose = { drawerOpen = false },
            onNewChat = {
                archiveCurrent()
                conversation.clear()
                activeSession = ++sessionSeq
                drawerOpen = false
            },
            onOpenSession = { session ->
                archiveCurrent()
                conversation.clear()
                conversation.addAll(session.items)
                activeSession = session.id
                drawerOpen = false
            },
            onRename = { session, title ->
                val index = sessions.indexOfFirst { it.id == session.id }
                if (index >= 0) sessions[index] = sessions[index].copy(title = title)
            },
            onDelete = { session ->
                sessions.removeAll { it.id == session.id }
                if (activeSession == session.id) {
                    conversation.clear()
                    activeSession = ++sessionSeq
                }
            },
            onOpenSettings = { drawerOpen = false; onOpenSettings() },
            progress = drawer,
        )
    }
}

/** Near-white page with a single soft vertical falloff; no colour blocks, no card backgrounds. */
@Composable
private fun chatBackdrop(): Brush = Brush.verticalGradient(
    listOf(
        XpColors.Canvas,
        XpColors.Canvas,
        XpColors.CanvasGrouped.copy(alpha = if (XpColors.isDark) 1f else 0.9f),
    ),
)

@Composable
private fun ChatTopBar(
    segment: ChatSegment,
    onSegment: (ChatSegment) -> Unit,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = XpSpace.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarCircle(onMenu, Icons.Outlined.Menu, "会话")
        Spacer(Modifier.width(XpSpace.s))
        ChatSegmentPill(segment, onSegment, Modifier.weight(1f))
        Spacer(Modifier.width(XpSpace.s))
        ReceiptStrip()
        Spacer(Modifier.width(XpSpace.s))
        BarCircle(onNewChat, Icons.Outlined.Add, "新对话")
    }
}

/** 44dp white circle with a soft shadow: the reference's round buttons, our neutral surface. */
@Composable
private fun BarCircle(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    Box(
        Modifier.size(44.dp)
            .shadow(6.dp, CircleShape, clip = false, ambientColor = Color(0x0F000000), spotColor = Color(0x14000000))
            .clip(CircleShape).background(XpColors.Card)
            .pressableClick(scaleDown = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = XpColors.Ink, modifier = Modifier.size(20.dp))
    }
}

/** Two-segment pill: selected segment is a white thumb inside the fill track. */
@Composable
private fun ChatSegmentPill(segment: ChatSegment, onSelect: (ChatSegment) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatSegment.entries.forEach { option ->
            val selected = option == segment
            val bg by animateColorAsState(if (selected) XpColors.Card else Color.Transparent, tween(160), label = "segmentBg")
            val fg by animateColorAsState(if (selected) XpColors.Ink else XpColors.InkSecondary, tween(160), label = "segmentFg")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(XpRadius.pill)).background(bg)
                    .pressableClick(scaleDown = 0.98f) { if (!selected) onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = fg,
                )
            }
        }
    }
}

/**
 * One quiet line for the empty state (docs/APP-DESIGN.md §3.4): what the assistant is and what it can see,
 * never a paragraph. The rest of the screen stays blank on purpose.
 */
private fun emptyHint(stats: ChatStats?, hello: String): String = when {
    stats == null -> hello
    stats.hasAnyTxn -> "$hello。今天花了 ${yuanText(stats.todayOutCents)}。"
    else -> "$hello。账本还是空的，说一句就能记一笔。"
}

/** Empty state: one quiet line and three grey question chips. No illustration. */
@Composable
private fun ChatEmptyState(hint: String, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = XpSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            hint,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = XpColors.InkSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(XpSpace.xl))
        Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s), horizontalAlignment = Alignment.CenterHorizontally) {
            ChatSuggestions.take(3).forEach { text ->
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
                        .pressableClick(scaleDown = 0.97f) { onSuggestion(text) }
                        .padding(horizontal = XpSpace.l),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = XpColors.Ink, maxLines = 1)
                }
            }
        }
    }
}

/** The 账本 segment: today + this week, plus the questions people actually ask about it. */
@Composable
private fun LedgerPanel(
    stats: ChatStats?,
    dayLabel: String,
    skeletonVisible: Boolean,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        verticalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        when {
            stats == null -> if (skeletonVisible) {
                ShimmerBlock(Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(XpRadius.m))
                ShimmerBlock(Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(XpRadius.m))
            }

            else -> {
                TodayReceiptCard(stats, dayLabel)
                WeekReportCard(stats)
                Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    listOf("这周哪天花得最多", "今天花了几笔", "家里人这周花了多少").forEach { question ->
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
                                .pressableClick(scaleDown = 0.97f) { onAsk(question) }
                                .padding(horizontal = XpSpace.l),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(question, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = XpColors.Ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationList(
    conversation: List<ChatItem>,
    reminder: ChatReminder?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    reduced: Boolean,
    onConfirm: (ChatItem.Confirm) -> Unit,
    onEdit: (ChatItem.Confirm) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(top = XpSpace.s, bottom = XpSpace.l),
        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        if (reminder != null) item(key = "reminder") { ReminderLine(reminder) }
        itemsIndexed(conversation, key = { _, item -> item.id }) { _, item ->
            Box(
                Modifier.then(
                    if (reduced) Modifier
                    else Modifier.animateItem(
                        fadeInSpec = tween(180),
                        placementSpec = tween(180),
                        fadeOutSpec = null,
                    ),
                ),
            ) {
                when (item) {
                    is ChatItem.Assistant -> AssistantText(item.text, streaming = item.streaming)
                    is ChatItem.User -> UserBubble(item.text)
                    is ChatItem.Tool -> ToolRow(item)
                    is ChatItem.Chart -> ChartCard(item)
                    is ChatItem.Confirm -> ConfirmCard(item, onConfirm = { onConfirm(item) }, onEdit = { onEdit(item) })
                }
            }
        }
    }
}

/** Assistant text: no bubble, left-aligned ink. */
@Composable
private fun AssistantText(text: String, streaming: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs)) {
        Text(
            text,
            Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyLarge,
            color = XpColors.Ink,
        )
        if (streaming) {
            StreamingCursor(Modifier.align(Alignment.Bottom).padding(start = 3.dp, bottom = 3.dp))
        }
    }
}

/** Blinking caret while the reply is still being written: a pulse, never a strobe. dt-driven. */
@Composable
private fun StreamingCursor(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var alpha by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        var phaseMs = 0f
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = MotionMath.dtSeconds(last, now)
            last = now
            phaseMs = (phaseMs + dt * 1000f) % 900f
            alpha = if (phaseMs < 450f) 1f - 0.7f * (phaseMs / 450f) else 0.3f + 0.7f * ((phaseMs - 450f) / 450f)
        }
    }
    Box(
        modifier.width(2.dp).height(15.dp).clip(RoundedCornerShape(1.dp))
            .graphicsLayer { this.alpha = alpha }.background(XpColors.Ink),
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

/** One tool step: a small spinner while it runs, then the one-line result. */
@Composable
private fun ToolRow(item: ChatItem.Tool) {
    val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "tool")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(900, easing = LinearEasing),
        ),
        label = "toolSpin",
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = 6.dp).animateContentSize(tween(160)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        // The step flips from running to done: icon crossfades, the result line slides in under it.
        AnimatedContent(
            targetState = item.running,
            transitionSpec = {
                (fadeIn(tween(140)) + androidx.compose.animation.scaleIn(initialScale = 0.7f)) togetherWith fadeOut(tween(80))
            },
            label = "toolIcon",
        ) { running ->
            if (running) {
                Icon(
                    Icons.Outlined.Autorenew, contentDescription = null,
                    tint = XpColors.Accent, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = angle },
                )
            } else {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = XpColors.Positive, modifier = Modifier.size(14.dp))
            }
        }
        Text(item.label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        AnimatedContent(
            targetState = item.detail,
            transitionSpec = {
                (fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 2 }) togetherWith fadeOut(tween(90))
            },
            label = "toolDetail",
        ) { detail ->
            if (detail == null) Spacer(Modifier.size(0.dp))
            else Text("· $detail", style = MaterialTheme.typography.bodySmall, color = XpColors.InkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Chart card inside the flow: answers carry a picture when they are about a period. */
@Composable
private fun ChartCard(item: ChatItem.Chart) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs)
            .clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Card).padding(XpSpace.l),
    ) {
        Text(item.title, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        com.xiaopiao.app.ui.chat.WeekBars(item.days)
        item.caption?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = XpColors.InkTertiary, modifier = Modifier.padding(top = XpSpace.s))
        }
    }
}
