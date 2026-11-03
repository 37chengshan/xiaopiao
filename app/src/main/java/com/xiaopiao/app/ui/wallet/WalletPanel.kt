package com.xiaopiao.app.ui.wallet

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.Grabber
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.glassRim
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberWindowBlurBehind
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.motion.XpMotion
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
import com.xiaopiao.app.ui.tr

/** 收起的卡片留下的页面高度：底部的导航胶囊仍然看得见（tt：卡包不要铺满）。 */
/** Page left showing above the wallet sheet, so it reads as a layer over the page. */
private val SheetTopGap = 40.dp

/**
 * 卡包浮层（docs/APP-DESIGN.md §5.5）：层叠卡片 + 卡详情 + 添加卡。
 * 从底部滑上来的整宽面板（tt 2026-11-03：从下面滑出来），顶部露出一截页面，背后是系统模糊；
 * 点外面或向下拖关闭。面板自带 FLAG_SECURE（禁截屏/最近任务快照）；卡面颜色是全局唯一的大面积色块。
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
    // Remembered: a fresh Flow per recomposition would re-subscribe to the whole ledger on every frame of the open spring.
    val txnFlow = remember(container) { container.ledger.observeAll() }
    val txns by txnFlow.collectAsStateWithLifecycle(initialValue = emptyList<Txn>())
    val auth = rememberDeviceAuthGuard()

    var hidden by rememberSaveable { mutableStateOf(false) }
    var segment by rememberSaveable { mutableStateOf(0) }
    // 哪张卡在最前面（3D 卡堆的顶卡）；不再是"点一下展开"。
    var frontBankId by rememberSaveable { mutableStateOf<String?>(null) }
    var frontMemberId by rememberSaveable { mutableStateOf<String?>(null) }
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
        // Debug builds can lift it from 设置 → 数据 so the wallet can be screenshotted for visual QA; release never can.
        if (!(com.xiaopiao.app.BuildConfig.DEBUG && WalletDebug.allowCapture)) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    val memberFront = segment == 1
    DisposableEffect(memberFront) {
        val window = context.findActivityOrNull()?.window
        if (memberFront) window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        onDispose { if (memberFront) window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }

    // 0 = 藏在屏幕下沿，1 = 面板落定。从底部滑上来（临界阻尼弹簧，不过冲，可被打断）。
    val progress = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, XpMotion.gentle())
                onDismiss()
            }
        }
    }
    LaunchedEffect(Unit) {
        // The first frame builds the dialog window and the whole panel (~80ms on the test phone). Start the spring
        // after it, or the opening skips its first third and reads as a jump.
        withFrameNanos { }
        withFrameNanos { }
        progress.animateTo(1f, XpMotion.gentle())
    }
    // 浮层卡片背后的页面交给系统模糊（跨窗口 blur），近几年的机器都能看到；看不到就退回不透明卡片。
    val blurred = rememberWindowBlurBehind()
    val shape = remember { RoundedCornerShape(topStart = XpRadius.sheet, topEnd = XpRadius.sheet) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val banks = wallet.banks
    val members = wallet.members
    val cardSpend = remember(txns, banks, now / 60_000) { CardLedger.monthCardSpend(txns, now, banks) }
    val memberSpend = remember(txns, members, now / 60_000) { members.sumOf { CardLedger.monthSpendAt(txns, now, it.storeName).cents } }

    Box(Modifier.fillMaxSize()) {
        // 卡片自己在下面留出页面：关掉 Dialog 自带的那层暗色，只留我们自己画的一层浅遮罩。
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        // 点卡片外面收起。遮罩很浅：卡片下面的页面本来就要露出来。
        Box(
            Modifier.matchParentSize()
                // Read in the draw phase: reading progress here in composition rebuilt the whole panel every frame.
                .drawBehind { drawRect(XpColors.Scrim.copy(alpha = XpColors.Scrim.alpha * 0.55f * progress.value.coerceIn(0f, 1f))) }
                .pressableClick(haptic = false, onClick = close),
        )
        Column(
            Modifier.fillMaxSize()
                // 整宽底部面板，顶上露出一截页面，看得出是盖在页面上的一层。
                .padding(top = topInset + SheetTopGap)
                .graphicsLayer {
                    translationY = (1f - progress.value) * size.height + dragY.value
                }
                .clip(shape)
                .background(
                    if (blurred) XpColors.CanvasGrouped.copy(alpha = 0.86f) else XpColors.CanvasGrouped,
                    shape,
                )
                .then(if (blurred) Modifier.glassRim(shape) else Modifier),
        ) {
            // ── 顶部：拖动手柄 / 标题 + × ────────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY.value > 96.dp.toPx()) close()
                            else scope.launch { dragY.animateTo(0f, XpMotion.snappy()) }
                        },
                        onDragCancel = { scope.launch { dragY.animateTo(0f, XpMotion.snappy()) } },
                    ) { _, dy ->
                        scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) }
                    }
                },
            ) {
                Grabber()
                Row(
                    Modifier.fillMaxWidth().padding(start = XpSpace.l, end = XpSpace.s, bottom = XpSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr("卡包", "Wallet"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = XpColors.Ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).pressableClick(onClick = close),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = tr("关闭卡包", "Close wallet"), tint = XpColors.Ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("本月卡上支出", "Spent on cards this month"), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                    Spacer(Modifier.width(XpSpace.xs))
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .pressableClick(haptic = false) { hidden = !hidden },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = tr(if (hidden) "显示金额" else "隐藏金额", if (hidden) "Show amounts" else "Hide amounts"),
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
                Segmented(listOf(tr("银行卡", "Bank cards"), tr("会员卡", "Member cards")), segment, onSelect = { segment = it })
                Spacer(Modifier.height(XpSpace.cardGap))
            }

            // ── 卡堆 + 卡详情（3D：前卡完整，后卡露 1/4–1/3 卡高）──────────────────────
            Column(
                Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding(),
            ) {
                if (segment == 0) {
                    if (banks.isEmpty()) {
                        Box(Modifier.padding(horizontal = XpSpace.s, vertical = XpSpace.xl)) {
                            EmptyState(
                                icon = Icons.Outlined.CreditCard,
                                title = tr("还没有银行卡", "No bank cards yet"),
                                body = tr("录入一张卡，通知里的尾号会自动归到这张卡上。", "Add a card and payment notifications match it by its last four digits."),
                            )
                        }
                    } else {
                        val frontIndex = banks.indexOfFirst { it.id == frontBankId }.takeIf { it >= 0 } ?: (banks.size - 1)
                        val front = banks[frontIndex]
                        CardStack(
                            items = banks,
                            frontIndex = frontIndex,
                            onBringToFront = { frontBankId = it.id },
                            frontHeight = BankCardHeight,
                            peek = CardPeek,
                            label = { c -> tr("把「${c.displayName}」拿到前面", "Bring “${c.displayName}” to the front") },
                            front = { card ->
                                BankCardFront(
                                    card = card,
                                    spend = CardLedger.monthSpend(txns, now, card),
                                    hidden = hidden,
                                    revealedPan = if (card.id == front.id) revealedPan else null,
                                    onReveal = {
                                        if (revealedPan != null) {
                                            revealedPan = null
                                            revealedAt = 0L
                                        } else {
                                            auth.request(tr("验证以显示卡号", "Verify to show the card number"), tr("验证后 30 秒自动遮住", "Hidden again after 30 seconds")) { ok ->
                                                if (ok) {
                                                    revealedPan = container.cards.revealPan(card)
                                                    revealedAt = System.currentTimeMillis()
                                                }
                                            }
                                        }
                                    },
                                    onCopy = {
                                        auth.request(tr("验证以复制卡号", "Verify to copy the card number"), tr("验证后 60 秒自动清空剪贴板", "Clipboard clears after 60 seconds")) { ok ->
                                            if (ok) {
                                                container.cards.revealPan(card)?.let { pan ->
                                                    SensitiveClipboard.copy(context, tr("卡号", "Card number"), pan)
                                                    scope.launch { SensitiveClipboard.autoClear(context, pan) }
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                            peekContent = { card ->
                                BankCardPeek(card, CardLedger.monthSpend(txns, now, card), hidden)
                            },
                        )
                        Spacer(Modifier.height(XpSpace.cardGap))
                        BankDetailCard(
                            card = front,
                            spend = CardLedger.monthSpend(txns, now, front),
                            recent = CardLedger.recent(txns, front),
                            onDelete = { container.cards.deleteBank(front.id); frontBankId = null },
                        )
                    }
                } else {
                    if (members.isEmpty()) {
                        Box(Modifier.padding(horizontal = XpSpace.s, vertical = XpSpace.xl)) {
                            EmptyState(
                                icon = Icons.Outlined.CreditCard,
                                title = tr("还没有会员卡", "No member cards yet"),
                                body = tr("录入会员号，结账时把条码亮出来就行。", "Add the membership number and show the barcode at the till."),
                            )
                        }
                    } else {
                        val frontIndex = members.indexOfFirst { it.id == frontMemberId }.takeIf { it >= 0 } ?: (members.size - 1)
                        val front = members[frontIndex]
                        CardStack(
                            items = members,
                            frontIndex = frontIndex,
                            onBringToFront = { frontMemberId = it.id },
                            frontHeight = BankCardHeight,
                            peek = CardPeek,
                            label = { c -> tr("把「${c.storeName}」拿到前面", "Bring “${c.storeName}” to the front") },
                            front = { card ->
                                MemberCardFront(card, CardLedger.monthSpendAt(txns, now, card.storeName), hidden)
                            },
                            peekContent = { card ->
                                MemberCardPeek(card, CardLedger.monthSpendAt(txns, now, card.storeName), hidden)
                            },
                        )
                        Spacer(Modifier.height(XpSpace.cardGap))
                        MemberDetailCard(
                            card = front,
                            spend = CardLedger.monthSpendAt(txns, now, front.storeName),
                            recent = CardLedger.recentAt(txns, front.storeName),
                            onDelete = { container.cards.deleteMember(front.id); frontMemberId = null },
                        )
                    }
                }
            }

            // ── 底部：添加卡（唯一主行动）───────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page, vertical = XpSpace.m)) {
                XpButton(
                    text = if (segment == 0) tr("添加银行卡", "Add bank card") else tr("添加会员卡", "Add member card"),
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
                frontBankId = saved.id
                addOpen = false
            },
            onSaveMember = { card ->
                val saved = container.cards.saveMember(card)
                segment = 1
                frontBankId = saved.id
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

/** 前卡：整张卡 + 详情区（卡号、本月、账单日…）。 */
@Composable
private fun BankCardFront(
    card: BankCard,
    spend: CardSpend,
    hidden: Boolean,
    revealedPan: String?,
    onReveal: () -> Unit,
    onCopy: () -> Unit,
) {
    CardCanvas(card.face, Modifier.fillMaxWidth().height(BankCardHeight)) {
        Column(Modifier.fillMaxSize()) {
            CardStrip(
                title = card.displayName,
                amountText = if (hidden) "¥ ···" else "¥ ${spend.cents.yuan()}",
                caption = tr("尾号 ${card.last4.ifEmpty { "••••" }} · ${card.type.label}",
                    "•••• ${card.last4.ifEmpty { "••••" }} · ${card.type.label}"),
                face = card.face,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                BankCardBody(
                    card = card, revealedPan = revealedPan, spend = spend, hidden = hidden,
                    onReveal = onReveal, onCopy = onCopy,
                )
            }
        }
    }
}

/** 后卡：只画卡头那一条，卡堆把它裁到 ~1/3 卡高。 */
@Composable
private fun BankCardPeek(card: BankCard, spend: CardSpend, hidden: Boolean) {
    CardCanvas(card.face, Modifier.fillMaxWidth().height(BankCardHeight)) {
        Column(Modifier.fillMaxSize()) {
            CardStrip(
                title = card.displayName,
                amountText = if (hidden) "¥ ···" else "¥ ${spend.cents.yuan()}",
                caption = tr("尾号 ${card.last4.ifEmpty { "••••" }} · ${card.type.label}",
                    "•••• ${card.last4.ifEmpty { "••••" }} · ${card.type.label}"),
                face = card.face,
            )
        }
    }
}

@Composable
private fun MemberCardFront(card: MemberCard, spend: CardSpend, hidden: Boolean) {
    CardCanvas(card.face, Modifier.fillMaxWidth().height(BankCardHeight)) {
        Column(Modifier.fillMaxSize()) {
            CardStrip(
                title = card.storeName,
                amountText = if (hidden) "¥ ···" else "¥ ${spend.cents.yuan()}",
                caption = if (card.points > 0) tr("${card.points} 分", "${card.points} pts") else card.codeFormat.label,
                face = card.face,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) { MemberCardBody(card) }
        }
    }
}

@Composable
private fun MemberCardPeek(card: MemberCard, spend: CardSpend, hidden: Boolean) {
    CardCanvas(card.face, Modifier.fillMaxWidth().height(BankCardHeight)) {
        Column(Modifier.fillMaxSize()) {
            CardStrip(
                title = card.storeName,
                amountText = if (hidden) "¥ ···" else "¥ ${spend.cents.yuan()}",
                caption = if (card.points > 0) tr("${card.points} 分", "${card.points} pts") else card.codeFormat.label,
                face = card.face,
            )
        }
    }
}

private val BankCardHeight = 240.dp
private val CardPeek = 68.dp

/** 卡详情：只陈述事实（尾号、本月、账单日、最近几笔），没有推荐与权益比较。 */
@Composable
private fun BankDetailCard(card: BankCard, spend: CardSpend, recent: List<Txn>, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DetailCard(title = tr("卡详情", "Card details")) {
        DetailRow(tr("尾号", "Last four"), card.last4.ifEmpty { "—" }, copyable = true) {
            scope.launch {
                SensitiveClipboard.copy(context, tr("尾号", "Last four"), card.last4)
                SensitiveClipboard.autoClear(context, card.last4)
            }
        }
        DetailRow(tr("本月支出", "Spent this month"), tr("¥ ${spend.cents.yuan()} · ${spend.count} 笔", "¥ ${spend.cents.yuan()} · ${spend.count} entries"))
        if (card.type == com.xiaopiao.app.wallet.BankCardType.Credit) {
            DetailRow(tr("账单日", "Statement day"), if (card.statementDay > 0) tr("${card.statementDay} 日", "Day ${card.statementDay}") else tr("未设置", "Not set"))
            DetailRow(tr("还款日", "Due day"), if (card.dueDay > 0) tr("${card.dueDay} 日", "Day ${card.dueDay}") else tr("未设置", "Not set"))
        }
        RecentRows(recent)
        DeleteRow(onDelete)
    }
}

@Composable
private fun MemberDetailCard(card: MemberCard, spend: CardSpend, recent: List<Txn>, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DetailCard(title = tr("会员详情", "Membership details")) {
        DetailRow(tr("会员号", "Member number"), card.memberNo, copyable = true) {
            scope.launch {
                SensitiveClipboard.copy(context, tr("会员号", "Member number"), card.memberNo)
                SensitiveClipboard.autoClear(context, card.memberNo)
            }
        }
        if (card.points > 0) DetailRow(tr("积分", "Points"), "${card.points}")
        if (card.balanceCents > 0) DetailRow(tr("余额", "Balance"), "¥ ${card.balanceCents.yuan()}")
        if (card.expiryAt > 0) {
            val days = ((card.expiryAt - startOfDay(System.currentTimeMillis())) / 86_400_000L).toInt()
            DetailRow(tr("到期", "Expires"), if (days >= 0) tr("还有 $days 天", "in $days days") else tr("已过期", "expired"))
        }
        DetailRow(tr("本月在这家店", "Here this month"), tr("¥ ${spend.cents.yuan()} · ${spend.count} 笔", "¥ ${spend.cents.yuan()} · ${spend.count} entries"))
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
                Icon(Icons.Outlined.ContentCopy, contentDescription = tr("复制", "Copy"), tint = XpColors.InkSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RecentRows(recent: List<Txn>) {
    if (recent.isEmpty()) {
        Text(
            tr("这张卡还没有账单", "No entries on this card yet"),
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        )
        return
    }
    Text(
        tr("最近 ${recent.size} 笔", "Last ${recent.size} entries"),
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
        tr("删除这张卡", "Delete this card"),
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

/** Debug-only switch (设置 → 数据): lets the wallet be screenshotted for QA. In memory only, off after a restart. */
object WalletDebug {
    var allowCapture by androidx.compose.runtime.mutableStateOf(false)
}
