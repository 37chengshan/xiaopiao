package com.xiaopiao.app.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.isImeVisible
import com.xiaopiao.app.ui.components.LocalTabBarInset
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
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
import androidx.compose.material.icons.outlined.Settings
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.capture.MerchantCategorizer
import com.xiaopiao.app.domain.ai.ReceiptParser
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.domain.yuan
import java.io.File
import com.xiaopiao.app.ui.components.ShimmerBlock
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.motion.MotionMath
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.today.TodayGreeting
import java.util.Calendar
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Characters per second for the streaming reply; dt-based, so refresh rate cannot change it. */
private const val StreamCharsPerSecond = 78f

/** Drawer travel applied to the page while the drawer is open (ChatGPT pushes the content a little). */
private val DrawerPagePush = 24.dp

/**
 * AI page (v7, docs/APP-DESIGN.md §3.4). Top bar: ☰ · segment · receipt strip · new chat. Body: one soft
 * gradient, no cards until you ask something. Bottom: one white input card with a single round button
 * (voice → send → stop) and a ＋ that stages images / CSV files. Everything stays on this device: OCR and
 * CSV parsing happen locally, the ledger only receives the rows the user confirms.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    onRecordVoice: () -> Unit = {},
    onRecordManual: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** Told whenever the session drawer opens/closes so the shell can hide its tab bar. */
    onDrawerOpenChange: (Boolean) -> Unit = {},
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

    val conversation = remember { androidx.compose.runtime.mutableStateListOf<ChatItem>() }
    var seq by remember { mutableIntStateOf(0) }
    fun newId(): Long = (++seq).toLong()

    var input by rememberSaveable { mutableStateOf("") }
    var scrollSignal by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    var streamGeneration by remember { mutableIntStateOf(0) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    val attachments = remember { androidx.compose.runtime.mutableStateListOf<ChatAttachment>() }
    var attachSheetOpen by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = LocalContext.current
    val aiPrefs = remember(context) { AiChatPrefs(context) }
    val autoEntry by aiPrefs.autoEntry.collectAsStateWithLifecycle(initialValue = false)

    val sessionStore = remember(context) { ChatSessionStore(context.applicationContext) }
    var historyLoaded by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    val accountUser by container.account.user.collectAsStateWithLifecycle()
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
            title = sessions.firstOrNull { it.id == activeSession }?.title ?: SessionGroups.titleOf(conversation),
            items = conversation.toList(),
            updatedAt = sessions.firstOrNull { it.id == activeSession && it.items == conversation.toList() }?.updatedAt ?: System.currentTimeMillis(),
        )
        sessions.removeAll { it.id == activeSession }
        sessions.add(0, saved)
    }

    fun stopStreaming() {
        streamGeneration++
        streamJob?.cancel()
        streamJob = null
        streaming = false
        // Finish whatever was mid-write so the caret never stays blinking.
        for (i in conversation.indices) {
            val item = conversation[i]
            if (item is ChatItem.Assistant && item.streaming) conversation[i] = item.copy(streaming = false)
            if (item is ChatItem.Tool && item.running) conversation[i] = item.copy(running = false, detail = "已停止")
        }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || streaming || !historyLoaded) return
        conversation.add(ChatItem.User(newId(), text))
        haptics.select()
        val reply = stats?.let { ChatIntentRouter.route(text, it, turn) }
            ?: ChatReply.Answer("我还没数完今天的账，稍等一秒再说。")
        // Tools first: the user sees what was read before the answer appears (tt#23).
        val tools = ChatTools.plan(text, stats, text.contains("家人") || text.contains("圈子"))
        val thisGeneration = ++streamGeneration
        streaming = true
        streamJob = scope.launch {
            try {
                if (container.account.configured && accountUser?.verified == true && reply is ChatReply.Answer) {
                    val messages = conversation.filter { it is ChatItem.User || it is ChatItem.Assistant }
                        .filter { it !is ChatItem.Assistant || it.text.isNotBlank() }.takeLast(24)
                    var payload = buildJsonObject {
                        putJsonArray("messages") { messages.forEach { item -> add(buildJsonObject {
                            put("role", if (item is ChatItem.User) "user" else "assistant")
                            put("content", when (item) { is ChatItem.User -> item.text.take(6000); is ChatItem.Assistant -> item.text.take(6000); else -> "" })
                        }) } }
                        put("timezone", java.time.ZoneId.systemDefault().id)
                    }
                    var completed = false
                    var receivedText = false
                    var dataSeen = false
                    try {
                        repeat(4) {
                            var next: JsonObject? = null
                            var assistantId: Long? = null
                            var terminal = false
                            container.account.stream(payload).collect { event ->
                                when (event["type"]?.jsonPrimitive?.content) {
                                    "status" -> {
                                        conversation.indices.forEach { i -> (conversation[i] as? ChatItem.Tool)?.takeIf { it.running }?.let { conversation[i] = it.copy(running = false) } }
                                        conversation.add(ChatItem.Tool(newId(), event.getValue("text").jsonPrimitive.content, running = true))
                                    }
                                    "token" -> {
                                        conversation.indices.forEach { i -> (conversation[i] as? ChatItem.Tool)?.takeIf { it.running }?.let { conversation[i] = it.copy(running = false) } }
                                        receivedText = true
                                        val id = assistantId ?: newId().also { assistantId = it; conversation.add(ChatItem.Assistant(it, "", streaming = true)) }
                                        val index = conversation.indexOfFirst { it.id == id }
                                        if (index >= 0) conversation[index] = (conversation[index] as ChatItem.Assistant).let { it.copy(text = it.text + event.getValue("text").jsonPrimitive.content) }
                                    }
                                    "tool_calls" -> {
                                        conversation.indices.forEach { i -> (conversation[i] as? ChatItem.Tool)?.takeIf { it.running }?.let { conversation[i] = it.copy(running = false) } }
                                        val results = event.getValue("calls").jsonArray.map { rawCall ->
                                            val call = rawCall.jsonObject
                                            val fn = call.getValue("function").jsonObject
                                            val name = fn.getValue("name").jsonPrimitive.content
                                            if (name == "show_view") {
                                                val args = runCatching { Json.parseToJsonElement(fn.getValue("arguments").jsonPrimitive.content.ifBlank { "{}" }).jsonObject }.getOrNull()
                                                val view = args?.let { ChatView.parse(it) }
                                                return@map when {
                                                    view == null -> buildJsonObject { put("id", call.getValue("id")); put("error", "INVALID_VIEW") }
                                                    !dataSeen -> buildJsonObject { put("id", call.getValue("id")); put("error", "NO_DATA_FIRST"); put("message", "先调用取数工具") }
                                                    else -> {
                                                        conversation.add(ChatItem.View(newId(), view))
                                                        scrollSignal++
                                                        buildJsonObject { put("id", call.getValue("id")); put("ok", true); put("rendered", view.kind) }
                                                    }
                                                }
                                            }
                                            dataSeen = true
                                            val toolId = newId()
                                            conversation.add(ChatItem.Tool(toolId, when (name) { "summarize_period" -> "汇总账本"; "find_peak" -> "查找最大支出"; "read_circle" -> "检查共享权限"; else -> "查询账本" }))
                                            val result = withContext(Dispatchers.Default) {
                                                runCatching { LocalAgentTools.execute(name, Json.parseToJsonElement(fn.getValue("arguments").jsonPrimitive.content.ifBlank { "{}" }).jsonObject, history.orEmpty(), System.currentTimeMillis()) }
                                                    .getOrElse { buildJsonObject { put("error", "INVALID_TOOL_ARGUMENTS") } }
                                            }
                                            val index = conversation.indexOfFirst { it.id == toolId }
                                            if (index >= 0) conversation[index] = ChatItem.Tool(toolId, (conversation[index] as ChatItem.Tool).label, "完成", false)
                                            buildJsonObject { put("id", call.getValue("id")); put("data", result) }
                                        }
                                        next = buildJsonObject { put("runId", event.getValue("runId")); put("results", JsonArray(results)) }
                                        terminal = true
                                    }
                                    "view" -> {
                                        event["view"]?.jsonObject?.let { raw -> ChatView.parse(raw) }?.let { view ->
                                            conversation.add(ChatItem.View(newId(), view))
                                            scrollSignal++
                                        }
                                    }
                                    "done" -> {
                                        conversation.indices.forEach { i -> (conversation[i] as? ChatItem.Tool)?.takeIf { it.running }?.let { conversation[i] = it.copy(running = false) } }
                                        completed = true; terminal = true
                                    }
                                    "error" -> throw IllegalStateException(event["message"]?.jsonPrimitive?.content ?: "AI 暂时不可用")
                                }
                                scrollSignal++
                            }
                            assistantId?.let { id ->
                                val index = conversation.indexOfFirst { it.id == id }
                                if (index >= 0) conversation[index] = (conversation[index] as ChatItem.Assistant).copy(streaming = false)
                            }
                            if (!terminal) error("连接中断，请重新发送")
                            if (completed) return@launch
                            payload = next ?: error("没有收到工具结果")
                        }
                        error("本次查询步骤较多，请缩小问题范围后重试")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        conversation.indices.forEach { index ->
                            val item = conversation[index]
                            if (item is ChatItem.Assistant && item.streaming) conversation[index] = item.copy(streaming = false)
                            if (item is ChatItem.Tool && item.running) conversation[index] = item.copy(running = false)
                        }
                        conversation.add(ChatItem.Tool(newId(), if (receivedText) "回答中断，可重新发送" else "云端暂不可用，改用本机统计", running = false))
                        if (receivedText) return@launch
                    }
                }
                tools.forEach { step ->
                    val toolId = newId()
                    conversation.add(ChatItem.Tool(toolId, step.label, running = true))
                    scrollSignal++
                    delay(if (reduced) 0 else 320)
                    val i = conversation.indexOfFirst { it.id == toolId }
                    if (i >= 0) conversation[i] = ChatItem.Tool(toolId, step.label, step.detail, running = false)
                }
                when (reply) {
                    is ChatReply.Expense -> conversation.add(ChatItem.Confirm(newId(), reply.draft, autoRequested = autoEntry))
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
                if (streamGeneration == thisGeneration) {
                    streaming = false
                    streamJob = null
                }
            }
        }
    }

    /** Appends a plain assistant line and scrolls to it. */
    fun assistant(text: String) {
        conversation.add(ChatItem.Assistant(newId(), text))
        scrollSignal++
    }

    /**
     * Sends staged attachments (tt#71). Images are OCR'd **on this device** and turned into confirm cards;
     * a CSV is parsed into a preview that only imports after 确认. Neither the picture nor the file is
     * copied into the ledger — only the structured rows the user accepts.
     */
    fun sendWithAttachments(text: String, items: List<ChatAttachment>) {
        if (items.isEmpty()) {
            send(text)
            return
        }
        val images = items.count { it.isImage }
        val files = items.size - images
        val summary = buildString {
            if (text.isNotBlank()) append(text.trim()).append("  ")
            if (images > 0) append("[$images 张图片]")
            if (files > 0) {
                if (images > 0) append(" ")
                append("[$files 个文件]")
            }
        }
        conversation.add(ChatItem.User(newId(), summary.trim()))
        haptics.select()
        val thisGeneration = ++streamGeneration
        streaming = true
        streamJob = scope.launch {
            try {
                if (images > 0) {
                    val toolId = newId()
                    conversation.add(ChatItem.Tool(toolId, "在本机识别图片", running = true))
                    scrollSignal++
                    val found = mutableListOf<ReceiptParser.Candidate>()
                    items.filter { it.isImage }.forEach { attachment ->
                        val raw = recognizeReceiptText(context, android.net.Uri.parse(attachment.uriString))
                        if (raw != null) found += ReceiptParser.parse(raw, System.currentTimeMillis())
                    }
                    val i = conversation.indexOfFirst { it.id == toolId }
                    if (i >= 0) {
                        conversation[i] = ChatItem.Tool(
                            toolId, "在本机识别图片",
                            if (found.isEmpty()) "没读到金额" else "读到 ${found.size} 笔",
                            running = false,
                        )
                    }
                    if (found.isEmpty()) {
                        assistant("这几张图我没认出账单金额。原图只在这台手机上看了，没有存下来。")
                    } else {
                        assistant(if (autoEntry) "识别到 ${found.size} 笔，正在自动记入，可逐笔撤销。" else "识别到 ${found.size} 笔，确认记入？")
                        found.forEach { candidate ->
                            conversation.add(ChatItem.Confirm(newId(), candidate.toDraft(), autoRequested = autoEntry))
                        }
                    }
                    scrollSignal++
                }
                items.filter { !it.isImage }.forEach { attachment ->
                    val label = attachment.name ?: "CSV 账单"
                    val raw = readCsvText(context, android.net.Uri.parse(attachment.uriString))
                    val preview = raw?.let { ChatCsvPreview.from(label, it) }
                    if (preview == null) {
                        assistant("「$label」我没读懂。现在只支持图片和支付宝/微信导出的 CSV 账单。")
                    } else {
                        assistant("读到 ${preview.rows} 笔，确认导入？")
                        conversation.add(ChatItem.CsvAttachment(newId(), preview))
                    }
                    scrollSignal++
                }
            } finally {
                if (streamGeneration == thisGeneration) {
                    streaming = false
                    streamJob = null
                }
            }
        }
    }

    fun updateChatItem(id: Long, updated: ChatItem) {
        val position = conversation.indexOfFirst { it.id == id }
        if (position >= 0) conversation[position] = updated
        sessions.indices.forEach { index ->
            val session = sessions[index]
            if (session.items.any { it.id == id }) sessions[index] = session.copy(items = session.items.map { if (it.id == id) updated else it })
        }
    }
    fun importCsv(item: ChatItem.CsvAttachment) {
        val live = conversation.firstOrNull { it.id == item.id } as? ChatItem.CsvAttachment ?: return
        if (live.state != CsvAttachmentState.Pending) return
        updateChatItem(live.id, live.copy(state = CsvAttachmentState.Importing))
        val importingSession = activeSession
        scope.launch {
            try {
                val summary = container.csvImporter.importText(live.preview.rawText)
                updateChatItem(live.id, live.copy(state = if (summary.error == null) CsvAttachmentState.Imported else CsvAttachmentState.Pending))
                if (summary.error == null) haptics.confirm()
                if (activeSession == importingSession) conversation.add(ChatItem.Assistant(newId(),
                    if (summary.error != null) "导入没成功：${summary.error}"
                    else "导入了 ${summary.imported} 笔，跳过重复 ${summary.duplicates} 笔。"))
                scrollSignal++
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                updateChatItem(live.id, live.copy(state = CsvAttachmentState.Pending))
                if (activeSession == importingSession) conversation.add(ChatItem.Assistant(newId(), "导入没有完成，可以重试；已存在的记录会跳过。"))
            }
        }
    }
    fun cancelCsv(item: ChatItem.CsvAttachment) {
        val live = conversation.firstOrNull { it.id == item.id } as? ChatItem.CsvAttachment ?: return
        if (live.state == CsvAttachmentState.Pending) updateChatItem(live.id, live.copy(state = CsvAttachmentState.Cancelled))
    }

    fun confirm(card: ChatItem.Confirm) {
        val position = conversation.indexOfFirst { it.id == card.id }
        val live = conversation.getOrNull(position) as? ChatItem.Confirm ?: return
        if (live.state !in listOf(ConfirmState.Pending, ConfirmState.Failed)) return
        conversation[position] = live.copy(state = ConfirmState.Saving)
        val savingSession = activeSession
        scope.launch {
            try {
                val transaction = live.draft.toTxn(System.currentTimeMillis()).copy(dedupeKey = "chat:${savingSession}:${live.id}")
                val id = container.ledger.add(transaction)
                updateChatItem(live.id, live.copy(state = if (id != null) ConfirmState.Saved else ConfirmState.Duplicate, transactionId = id))
                haptics.confirm()
                scrollSignal++
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                updateChatItem(live.id, live.copy(state = ConfirmState.Failed, autoRequested = false))
            }
        }
    }
    fun undo(card: ChatItem.Confirm) {
        val id = card.transactionId ?: return
        scope.launch {
            try {
                container.ledger.delete(id)
                updateChatItem(card.id, card.copy(state = ConfirmState.Undone, autoRequested = false))
            } catch (_: Exception) { conversation.add(ChatItem.Assistant(newId(), "撤销没有完成，请到记账页查看这笔记录。")) }
        }
    }
    val automaticPending = conversation.filterIsInstance<ChatItem.Confirm>().filter { it.autoRequested && it.state == ConfirmState.Pending }
    LaunchedEffect(automaticPending.map { it.id }) { automaticPending.forEach(::confirm) }

    /** 「改一下」 puts the original sentence back in the field so the router re-parses the edit. */
    fun edit(card: ChatItem.Confirm) {
        val index = conversation.indexOfFirst { it.id == card.id }
        if (index < 0) return
        conversation.removeAt(index)
        input = card.draft.sourceText
        runCatching { focusRequester.requestFocus() }
        haptics.select()
    }

    // Read history off the UI thread before enabling send/persistence.
    LaunchedEffect(sessionStore) {
        val saved = withContext(Dispatchers.IO) { sessionStore.load() }
        sessions.addAll(saved.map { session -> session.copy(items = session.items.map { item ->
            when (item) {
                is ChatItem.Confirm -> item.copy(autoRequested = false, state = if (item.state == ConfirmState.Saving) ConfirmState.Failed else item.state)
                is ChatItem.CsvAttachment -> item.copy(state = if (item.state == CsvAttachmentState.Importing) CsvAttachmentState.Pending else item.state)
                else -> item
            }
        }) })
        sessionSeq = saved.maxOfOrNull { it.id } ?: 0
        activeSession = ++sessionSeq
        seq = saved.flatMap { it.items }.maxOfOrNull { it.id }?.toInt() ?: 0
        historyLoaded = true
    }
    LaunchedEffect(historyLoaded) {
        if (!historyLoaded) return@LaunchedEffect
        snapshotFlow { sessions.toList() to conversation.toList() }.debounce(500).collectLatest { (saved, current) ->
            val currentSession = if (current.any { it is ChatItem.User }) ChatSession(activeSession,
                saved.firstOrNull { it.id == activeSession }?.title ?: SessionGroups.titleOf(current),
                current.map { if (it is ChatItem.Assistant) it.copy(streaming = false) else if (it is ChatItem.Tool) it.copy(running = false) else if (it is ChatItem.Confirm) it.copy(autoRequested = false) else it },
                System.currentTimeMillis()) else null
            val all = if (currentSession == null) saved else listOf(currentSession) + saved.filter { it.id != activeSession }
            withContext(Dispatchers.IO) { sessionStore.save(all) }
        }
    }
    // Follow content growth only while the reader is already at the bottom.
    var following by remember { mutableStateOf(true) }
    val readerDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(readerDragging, listState.canScrollForward) {
        if (readerDragging) following = !listState.canScrollForward
    }
    LaunchedEffect(listState) {
        snapshotFlow { scrollSignal }.conflate().collect { signal ->
            if (signal > 0 && following) {
                withFrameNanos { }
                val layout = listState.layoutInfo
                val lastIndex = layout.totalItemsCount - 1
                if (lastIndex >= 0) {
                    if (layout.visibleItemsInfo.lastOrNull()?.index != lastIndex) listState.scrollToItem(lastIndex)
                    withFrameNanos { }
                    val measured = listState.layoutInfo
                    val last = measured.visibleItemsInfo.lastOrNull()
                    if (last != null) {
                        val delta = last.offset + last.size + measured.afterContentPadding - measured.viewportEndOffset
                        if (delta > 0) listState.scrollBy(delta.toFloat())
                    }
                }
            }
        }
    }
    BackHandler(drawerOpen) { drawerOpen = false }
    if (accountOpen) com.xiaopiao.app.ui.account.AccountSheet(container.account) { accountOpen = false }



    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) attachments.add(ChatAttachment(uri.toString(), ChatAttachmentKind.Image, name = "相机照片"))
        cameraUri = null
    }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ChatInput.MAX_IMAGES),
    ) { uris ->
        val staged = uris.map { uri ->
            ChatAttachment(uri.toString(), ChatAttachmentKind.Image, name = uri.lastPathSegment)
        }
        attachments.clear()
        attachments.addAll(ChatInput.stage(attachments.toList(), staged))
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)
            val name = uri.lastPathSegment?.substringAfterLast('/')
            val kind = if (mime?.startsWith("image/") == true) ChatAttachmentKind.Image else ChatAttachmentKind.File
            val attachment = ChatAttachment(uri.toString(), kind, name = name, mime = mime)
            if (!attachment.isImage && !attachment.isCsv) {
                attachSheetOpen = false
                conversation.add(ChatItem.Assistant(newId(), "暂只支持图片和 CSV。这个文件我先不动。"))
                scrollSignal++
            } else {
                attachments.add(attachment)
            }
        }
    }

    LaunchedEffect(drawerOpen) { if (drawerOpen) archiveCurrent(); onDrawerOpenChange(drawerOpen) }

    val drawer = remember { Animatable(if (drawerOpen) 1f else 0f) }
    LaunchedEffect(drawerOpen) {
        if (reduced) drawer.snapTo(if (drawerOpen) 1f else 0f)
        else drawer.animateTo(if (drawerOpen) 1f else 0f, com.xiaopiao.app.ui.theme.XpMotion.snappy())
    }

    Box(Modifier.fillMaxSize().background(chatBackdrop())) {
        // Same glass top bar as 记账 (docs/DESIGN.md §4.1): ☰ on the left, 小票 + 设置 on the right.
        NavScaffold(
            "AI",
            NavScrollNone,
            modifier = Modifier.graphicsLayer { translationX = if (reduced) 0f else DrawerPagePush.toPx() * drawer.value },
            leading = { NavIconButton(Icons.Outlined.Menu, "会话", onClick = { drawerOpen = true }) },
            trailing = { AppTopActions() },
        ) { top ->
        // The conversation runs under the floating input and the glass tab bar (docs/DESIGN.md §4.3); with the
        // keyboard up the bar is covered, so only the input's own height is reserved.
        val imeUp = WindowInsets.isImeVisible
        val barInset = if (imeUp) 0.dp else LocalTabBarInset.current
        val density = LocalDensity.current
        var composerH by remember { mutableStateOf(72.dp) }
        Box(Modifier.fillMaxSize().background(chatBackdrop()).padding(top = top).imePadding()) {
            Box(Modifier.fillMaxSize()) {
                when {
                    conversation.isEmpty() -> ChatEmptyState(
                        hint = emptyHint(stats, hello),
                        onSuggestion = { question -> send(question) },
                        onManual = onRecordManual,
                        modifier = Modifier.fillMaxSize().padding(bottom = composerH + barInset),
                    )

                    else -> ConversationList(
                        conversation = conversation,
                        reminder = reminder,
                        listState = listState,
                        reduced = reduced,
                        onConfirm = ::confirm,
                        onEdit = ::edit,
                        onUndo = ::undo,
                        onImportCsv = ::importCsv,
                        onCancelCsv = ::cancelCsv,
                        bottomPadding = composerH + barInset,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (!following && conversation.isNotEmpty()) androidx.compose.material3.TextButton(
                onClick = { following = true; scrollSignal++ },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = composerH + barInset),
            ) { Text("回到最新消息 ↓", color = XpColors.Accent) }
            ChatComposer(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    if (ChatInput.canSend(input, streaming, attachments.size)) {
                        val text = input
                        input = ""
                        if (attachments.isEmpty()) {
                            send(text)
                        } else {
                            sendWithAttachments(text, attachments.toList())
                            attachments.clear()
                        }
                    }
                },
                onVoice = onRecordVoice,
                onStop = ::stopStreaming,
                onPlus = { attachSheetOpen = true },
                streaming = streaming,
                attachments = attachments,
                onRemoveAttachment = { attachments.remove(it) },
                focusRequester = focusRequester,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barInset)
                    .onSizeChanged { composerH = with(density) { it.height.toDp() } },
            )
        }
        }

        if (attachSheetOpen) {
            AttachmentSheet(
                onDismiss = { attachSheetOpen = false },
                onCamera = {
                    val file = File(File(context.cacheDir, "chat").apply { mkdirs() }, "shot-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraUri = uri
                    takePicture.launch(uri)
                },
                onGallery = {
                    pickImages.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onFile = { pickFile.launch(arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel", "image/*")) },
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
                stopStreaming()
                archiveCurrent()
                conversation.clear()
                activeSession = ++sessionSeq
                drawerOpen = false
            },
            onOpenSession = { session ->
                stopStreaming()
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
                if (activeSession == session.id) stopStreaming()
                sessions.removeAll { it.id == session.id }
                if (activeSession == session.id) {
                    conversation.clear()
                    activeSession = ++sessionSeq
                }
            },
            onOpenSettings = { drawerOpen = false; onOpenSettings() },
            progress = drawer,
            activeSessionId = activeSession,
            accountLabel = accountUser?.username ?: "登录你的账号",
            onOpenAccount = { drawerOpen = false; accountOpen = true },
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
private fun ChatEmptyState(
    hint: String,
    onSuggestion: (String) -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Text(
                "或者手动记一笔",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = XpColors.Accent,
                modifier = Modifier.padding(top = XpSpace.xs)
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .pressableClick(scaleDown = 0.97f, onClick = onManual)
                    .padding(horizontal = XpSpace.m, vertical = XpSpace.xs),
            )
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
    onUndo: (ChatItem.Confirm) -> Unit,
    onImportCsv: (ChatItem.CsvAttachment) -> Unit,
    onCancelCsv: (ChatItem.CsvAttachment) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val blocks = remember(conversation.toList()) {
        buildList<List<ChatItem>> {
            conversation.forEach { item ->
                if (item is ChatItem.Tool && lastOrNull()?.firstOrNull() is ChatItem.Tool) {
                    val previous = removeAt(lastIndex)
                    add(previous + item)
                } else add(listOf(item))
            }
        }
    }
    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(top = XpSpace.s, bottom = XpSpace.l + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        if (reminder != null) item(key = "reminder") { ReminderLine(reminder) }
        itemsIndexed(blocks, key = { _, block -> block.first().id }) { _, block ->
            val item = block.first()
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
                    is ChatItem.Tool -> ThoughtPanel(block.filterIsInstance<ChatItem.Tool>())
                    is ChatItem.Chart -> ChartCard(item)
                    is ChatItem.View -> ChatViewCard(item.view)
                    is ChatItem.Confirm -> ConfirmCard(item, onConfirm = { onConfirm(item) }, onEdit = { onEdit(item) }, onUndo = { onUndo(item) })
                    is ChatItem.CsvAttachment -> CsvPreviewCard(
                        item,
                        onImport = { onImportCsv(item) },
                        onCancel = { onCancelCsv(item) },
                    )
                }
            }
        }
    }
}

/** Assistant text: no bubble, left-aligned ink. */
@Composable
private fun AssistantText(text: String, streaming: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs)) {
        MessageMarkdown(text, Modifier.weight(1f))
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

/** A receipt candidate becomes the same confirm card a typed sentence gets; the user can still edit it. */
private fun ReceiptParser.Candidate.toDraft(): ExpenseDraft = ExpenseDraft(
    merchant = merchant,
    category = MerchantCategorizer.categorize(merchant),
    amountCents = amountCents,
    // 「改一下」 puts this back into the input field, so it must read like something the user typed.
    sourceText = "$merchant ${amountCents.yuan()}",
    occurredAt = occurredAt,
)
