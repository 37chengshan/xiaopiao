package com.xiaopiao.app.ui.wallet

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.motion.rememberDtProgress
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.wallet.BankCard
import com.xiaopiao.app.wallet.CardBin
import com.xiaopiao.app.wallet.CardLedger
import com.xiaopiao.app.wallet.CardSpend
import com.xiaopiao.app.wallet.MemberCard
import com.xiaopiao.app.wallet.RevealWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 静默常量：面板上滑 280ms（§5.5 要求 250–300ms 强 ease-out）。 */
private const val PANEL_MS = 280

/**
 * 卡包全屏面板（docs/APP-DESIGN.md §5.5）：层叠卡片 + 卡详情 + 添加卡。
 * 面板自带 FLAG_SECURE（禁截屏/最近任务快照）与下滑关闭；卡面颜色是全局唯一的大面积色块。
 */
@Composable
fun WalletPanel(
    container: AppContainer,
    now: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallet by container.cards.wallet.collectAsStateWithLifecycle()
    val txns by container.ledger.observeAll().collectAsStateWithLifecycle(initialValue = emptyList<Txn>())
    val auth = rememberDeviceAuthGuard()

    var hidden by rememberSaveable { mutableStateOf(false) }
    var segment by rememberSaveable { mutableStateOf(0) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var revealedPan by remember { mutableStateOf<String?>(null) }
    var revealedAt by remember { mutableLongStateOf(0L) }

    // 30 秒后自动遮回（§5.5）；离开面板由 DisposableEffect 兜底。
    LaunchedEffect(revealedAt) {
        if (revealedAt > 0L) {
            delay(RevealWindow.VISIBLE_MS)
            revealedPan = null
            revealedAt = 0L
        }
    }
    // 卡包页禁截屏 + 禁最近任务缩略图；会员码展示时把亮度拉满。
    DisposableEffect(Unit) {
        val activity = context.findActivityOrNull()
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    val memberFront = segment == 1 && expandedId != null
    DisposableEffect(memberFront) {
        val window = context.findActivityOrNull()?.window
        if (memberFront) window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        onDispose { if (memberFront) window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }

    val progress = rememberDtProgress(key = Unit, durationMs = PANEL_MS)
    val dragY = remember { Animatable(0f) }

    val banks = wallet.banks
    val members = wallet.members
    val cardSpend = remember(txns, banks, now / 60_000) { CardLedger.monthCardSpend(txns, now, banks) }
    val memberSpend = remember(txns, members, now / 60_000) { members.sumOf { CardLedger.monthSpendAt(txns, now, it.storeName).cents } }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - progress) * size.height + dragY.value
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY.value > 120.dp.toPx()) onDismiss()
                            else scope.launch { dragY.animateTo(0f, tween(180)) }
                        },
                        onDragCancel = { scope.launch { dragY.animateTo(0f, tween(180)) } },
                    ) { _, dy ->
                        scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) }
                    }
                }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(XpColors.CanvasGrouped)
                .statusBarsPadding(),
        ) {
            // ── 顶部：× + 本月卡上支出 ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(start = XpSpace.s, top = XpSpace.s, end = XpSpace.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).pressableClick { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭卡包", tint = XpColors.Ink, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本月卡上支出", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                    Spacer(Modifier.width(XpSpace.xs))
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .pressableClick(haptic = false) { hidden = !hidden },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (hidden) "显示金额" else "隐藏金额",
                            tint = XpColors.InkSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Text(
                    if (hidden) "¥ ···" else "¥ ${(cardSpend.cents + memberSpend).yuan()}",
                    style = TextStyle(fontFamily = XpFonts.sans, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, fontFeatureSettings = "tnum"),
                    color = XpColors.Ink,
                )
                Spacer(Modifier.height(XpSpace.m))
                Segmented(listOf("银行卡", "会员卡"), segment, onSelect = { segment = it; expandedId = null })
                Spacer(Modifier.height(XpSpace.cardGap))
            }

            // ── 卡片层叠 + 卡详情 ─────────────────────────────────────────────────────────
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (segment == 0) {
                    if (banks.isEmpty()) {
                        item("empty") {
                            Box(Modifier.padding(horizontal = XpSpace.s, vertical = XpSpace.xl)) {
                                EmptyState(
                                    icon = Icons.Outlined.CreditCard,
                                    title = "还没有银行卡",
                                    body = "录入一张卡，通知里的尾号会自动归到这张卡上。",
                                )
                            }
                        }
                    }
                    val front = banks.lastOrNull { it.id == expandedId } ?: banks.lastOrNull()
                    itemsIndexed(banks, key = { _, card -> card.id }) { index, card ->
                        BankStackItem(
                            modifier = Modifier.xpRevealLayer(index = index),
                            card = card,
                            isFront = card.id == front?.id,
                            compact = expandedId != null && card.id != front?.id,
                            spend = CardLedger.monthSpend(txns, now, card),
                            hidden = hidden,
                            revealedPan = if (card.id == front?.id) revealedPan else null,
                            onToggle = { expandedId = if (expandedId == card.id) null else card.id },
                            onReveal = {
                                if (revealedPan != null) {
                                    revealedPan = null
                                    revealedAt = 0L
                                } else {
                                    auth.request("验证以显示卡号", "验证后 30 秒自动遮住") { ok ->
                                        if (ok) {
                                            revealedPan = container.cards.revealPan(card)
                                            revealedAt = System.currentTimeMillis()
                                        }
                                    }
                                }
                            },
                            onCopy = {
                                auth.request("验证以复制卡号", "验证后 60 秒自动清空剪贴板") { ok ->
                                    if (ok) {
                                        container.cards.revealPan(card)?.let { pan ->
                                            SensitiveClipboard.copy(context, "卡号", pan)
                                            scope.launch { SensitiveClipboard.autoClear(context, pan) }
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (front != null) {
                        item("detail-${front.id}") {
                            BankDetailCard(
                                card = front,
                                spend = CardLedger.monthSpend(txns, now, front),
                                recent = CardLedger.recent(txns, front),
                                onDelete = { container.cards.deleteBank(front.id); expandedId = null },
                            )
                        }
                    }
                } else {
                    if (members.isEmpty()) {
                        item("empty") {
                            Box(Modifier.padding(horizontal = XpSpace.s, vertical = XpSpace.xl)) {
                                EmptyState(
                                    icon = Icons.Outlined.CreditCard,
                                    title = "还没有会员卡",
                                    body = "录入会员号，结账时把条码亮出来就行。",
                                )
                            }
                        }
                    }
                    val front = members.lastOrNull { it.id == expandedId } ?: members.lastOrNull()
                    itemsIndexed(members, key = { _, card -> card.id }) { index, card ->
                        MemberStackItem(
                            modifier = Modifier.xpRevealLayer(index = index),
                            card = card,
                            isFront = card.id == front?.id,
                            compact = expandedId != null && card.id != front?.id,
                            spend = CardLedger.monthSpendAt(txns, now, card.storeName),
                            hidden = hidden,
                            onToggle = { expandedId = if (expandedId == card.id) null else card.id },
                        )
                    }
                    if (front != null) {
                        item("detail-${front.id}") {
                            MemberDetailCard(
                                card = front,
                                spend = CardLedger.monthSpendAt(txns, now, front.storeName),
                                recent = CardLedger.recentAt(txns, front.storeName),
                                onDelete = { container.cards.deleteMember(front.id); expandedId = null },
                            )
                        }
                    }
                }
            }

            // ── 底部：添加卡（唯一主行动）───────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page, vertical = XpSpace.m)) {
                XpButton(
                    text = if (segment == 0) "添加银行卡" else "添加会员卡",
                    onClick = { addOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (addOpen) {
        AddCardSheet(
            initialKind = if (segment == 0) AddCardKind.Bank else AddCardKind.Member,
            onDismiss = { addOpen = false },
            onSaveBank = { card, pan ->
                val saved = container.cards.saveBank(card, pan)
                segment = 0
                expandedId = saved.id
                addOpen = false
            },
            onSaveMember = { card ->
                val saved = container.cards.saveMember(card)
                segment = 1
                expandedId = saved.id
                addOpen = false
            },
        )
    }
}

private fun android.content.Context.findActivityOrNull(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun BankStackItem(
    modifier: Modifier = Modifier,
    card: BankCard,
    isFront: Boolean,
    compact: Boolean,
    spend: CardSpend,
    hidden: Boolean,
    revealedPan: String?,
    onToggle: () -> Unit,
    onReveal: () -> Unit,
    onCopy: () -> Unit,
) {
    val height by animateDpAsState(if (isFront) 200.dp else if (compact) 40.dp else 71.dp, tween(232), label = "cardHeight")
    CardCanvas(card.face, modifier.fillMaxWidth().height(height).pressableClick(haptic = false, onClick = onToggle)) {
        Column(Modifier.fillMaxSize()) {
            if (compact) {
                CardStripCompact(
                    title = card.displayName,
                    amountText = if (hidden) "¥ ···" else "¥ ${spend.cents.yuan()}",
                    face = card.face,
                )
            } else {
                CardStrip(
                    title = card.displayName,
                    amountText = if (hidden) "¥ ···" else "¥ ${spend.cents.yuan()}",
                    caption = "尾号 ${card.last4.ifEmpty { "••••" }} · ${card.type.label}",
                    face = card.face,
                )
            }
            if (isFront) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    BankCardBody(
                        card = card, revealedPan = revealedPan, spend = spend, hidden = hidden,
                        onReveal = onReveal, onCopy = onCopy,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberStackItem(
    modifier: Modifier = Modifier,
    card: MemberCard,
    isFront: Boolean,
    compact: Boolean,
    spend: CardSpend,
    hidden: Boolean,
    onToggle: () -> Unit,
) {
    val height by animateDpAsState(if (isFront) 232.dp else if (compact) 40.dp else 71.dp, tween(232), label = "memberHeight")
    CardCanvas(card.face, modifier.fillMaxWidth().height(height).pressableClick(haptic = false, onClick = onToggle)) {
        Column(Modifier.fillMaxSize()) {
            if (compact) {
                CardStripCompact(
                    title = card.storeName,
                    amountText = if (hidden) "¥ ···" else "¥ ${spend.cents.yuan()}",
                    face = card.face,
                )
            } else {
                CardStrip(
                    title = card.storeName,
                    amountText = if (hidden) "¥ ···" else "¥ ${spend.cents.yuan()}",
                    caption = if (card.points > 0) "${card.points} 分" else card.codeFormat.label,
                    face = card.face,
                )
            }
            if (isFront) {
                Box(Modifier.fillMaxWidth().weight(1f)) { MemberCardBody(card) }
            }
        }
    }
}

/** 卡详情：只陈述事实（尾号、本月、账单日、最近几笔），没有推荐与权益比较。 */
@Composable
private fun BankDetailCard(card: BankCard, spend: CardSpend, recent: List<Txn>, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DetailCard(title = "卡详情") {
        DetailRow("尾号", card.last4.ifEmpty { "—" }, copyable = true) {
            scope.launch {
                SensitiveClipboard.copy(context, "尾号", card.last4)
                SensitiveClipboard.autoClear(context, card.last4)
            }
        }
        DetailRow("本月支出", "¥ ${spend.cents.yuan()} · ${spend.count} 笔")
        if (card.type == com.xiaopiao.app.wallet.BankCardType.Credit) {
            DetailRow("账单日", if (card.statementDay > 0) "${card.statementDay} 日" else "未设置")
            DetailRow("还款日", if (card.dueDay > 0) "${card.dueDay} 日" else "未设置")
        }
        RecentRows(recent)
        DeleteRow(onDelete)
    }
}

@Composable
private fun MemberDetailCard(card: MemberCard, spend: CardSpend, recent: List<Txn>, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DetailCard(title = "会员详情") {
        DetailRow("会员号", card.memberNo, copyable = true) {
            scope.launch {
                SensitiveClipboard.copy(context, "会员号", card.memberNo)
                SensitiveClipboard.autoClear(context, card.memberNo)
            }
        }
        if (card.points > 0) DetailRow("积分", "${card.points}")
        if (card.balanceCents > 0) DetailRow("余额", "¥ ${card.balanceCents.yuan()}")
        if (card.expiryAt > 0) {
            val days = ((card.expiryAt - startOfDay(System.currentTimeMillis())) / 86_400_000L).toInt()
            DetailRow("到期", if (days >= 0) "还有 $days 天" else "已过期")
        }
        DetailRow("本月在这家店", "¥ ${spend.cents.yuan()} · ${spend.count} 笔")
        RecentRows(recent)
        DeleteRow(onDelete)
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = XpSpace.cardGap).clip(RoundedCornerShape(XpRadius.contentCard))
            .background(XpColors.Card),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = XpColors.Ink,
            modifier = Modifier.padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.l, bottom = XpSpace.s),
        )
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String, copyable: Boolean = false, onCopy: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, modifier = Modifier.width(112.dp))
        Text(
            value,
            style = TextStyle(fontFamily = XpFonts.sans, fontSize = 14.sp, fontFeatureSettings = "tnum"),
            color = XpColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (copyable) {
            Box(Modifier.size(32.dp).clip(CircleShape).pressableClick(haptic = false, onClick = onCopy), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "复制", tint = XpColors.InkSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RecentRows(recent: List<Txn>) {
    if (recent.isEmpty()) {
        Text(
            "这张卡还没有账单",
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        )
        return
    }
    Text(
        "最近 ${recent.size} 笔",
        style = MaterialTheme.typography.bodySmall,
        color = XpColors.InkSecondary,
        modifier = Modifier.padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.s, bottom = 2.dp),
    )
    recent.forEach { txn ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(txn.merchant, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shortDate(txn.occurredAt), style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
            }
            Text(
                "${if (txn.direction == com.xiaopiao.app.domain.Direction.OUT) "-" else "+"}${txn.amountCents.yuan()}",
                style = TextStyle(fontFamily = XpFonts.sans, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = if (txn.direction == com.xiaopiao.app.domain.Direction.IN) XpColors.Positive else XpColors.Ink,
            )
        }
    }
    Spacer(Modifier.height(XpSpace.s))
}

@Composable
private fun DeleteRow(onDelete: () -> Unit) {
    Text(
        "删除这张卡",
        style = MaterialTheme.typography.bodyMedium,
        color = XpColors.Negative,
        modifier = Modifier.fillMaxWidth().pressableClick(haptic = false, onClick = onDelete)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
    )
}

private fun shortDate(at: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
    return "%02d/%02d".format(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
}
