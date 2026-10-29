package com.xiaopiao.app.ui.merchant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.merchant.MerchantAi
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueEntry
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.today.DayPick
import com.xiaopiao.app.ui.today.LineKind
import com.xiaopiao.app.ui.today.LocalPaperColors
import com.xiaopiao.app.ui.today.LocalReceiptFont
import com.xiaopiao.app.ui.today.PaperColors
import com.xiaopiao.app.ui.today.PaperDepth
import com.xiaopiao.app.ui.today.PaperTheme
import com.xiaopiao.app.ui.today.PrintClock
import com.xiaopiao.app.ui.today.PrintPhase
import com.xiaopiao.app.ui.today.PrintSession
import com.xiaopiao.app.ui.today.PrintTimeline
import com.xiaopiao.app.ui.today.PrinterHeight
import com.xiaopiao.app.ui.today.PrinterSlotCentre
import com.xiaopiao.app.ui.today.ReceiptFont
import com.xiaopiao.app.ui.today.ReceiptHaptics
import com.xiaopiao.app.ui.today.ReceiptLayout
import com.xiaopiao.app.ui.today.ReceiptLine
import com.xiaopiao.app.ui.today.ReceiptPaper
import com.xiaopiao.app.ui.today.ReceiptPrinter
import com.xiaopiao.app.ui.today.ReceiptSound
import com.xiaopiao.app.ui.today.ReceiptSoundPrefs
import com.xiaopiao.app.ui.today.StampVisuals
import kotlinx.coroutines.delay

/** How far back the ‹ › pager reaches without the calendar (a quarter of the book, like a shop's paper ledger). */
private const val PAGER_DAYS = 90

/** The paper starts at the slot's lower lip: slot centre + half of the printer's 14dp slot. */
private val PaperLift = PrinterHeight - PrinterSlotCentre - 7.dp

/**
 * 经营小票: the shop edition's 小票 page, mirroring the personal one — same downwards-facing printer, a day pager
 * above it, and the daily report feeding out of the slot as the preview. Only the revenue book is read; seeded
 * rows are never marked on the paper (tt#54), and nothing here touches the personal ledger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantReceiptPage(
    entries: List<RevenueEntry>,
    today: Long,
    initialDay: Long,
    onBack: () -> Unit,
    onRecordDay: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val uiHaptics = rememberXpHaptics()

    // The print ceremony's machinery, created here because 小店 has its own shell (no ReceiptHost above it).
    val haptics = remember { ReceiptHaptics(context) }
    val sound = remember { ReceiptSound(context) }
    DisposableEffect(sound) { onDispose { sound.release() } }
    val soundPrefs = remember { ReceiptSoundPrefs(context) }
    val soundOn by soundPrefs.observeEnabled().collectAsState(initial = true)
    val vibrationOn by soundPrefs.observeVibration().collectAsState(initial = true)
    SideEffect { sound.enabled = soundOn; haptics.enabled = vibrationOn }
    val session = remember { PrintSession(scope, haptics, sound) }

    val book = remember(entries) { RevenueBook.normalize(entries) }
    // A quarter back, the first recorded day (CSV imports go further), or the page the shell handed us.
    val earliest = remember(today, initialDay, book) {
        minOf(RevenueBook.plusDays(today, -PAGER_DAYS), initialDay, book.firstOrNull()?.dayStart ?: today).coerceAtMost(today)
    }
    var day by rememberSaveable { mutableLongStateOf(initialDay.coerceIn(earliest, today)) }
    val entry = remember(book, day) { RevenueBook.entryFor(book, day) }
    val lines = remember(book, day, today) { merchantLines(book, day, today) }
    val timeline = remember(lines) { PrintTimeline.build(lines, reduced = false) }
    val paperColors = PaperColors.resolve(PaperTheme.Classic, null)
    val stamp = remember { StampVisuals({ 1f }, { 0f }, { 0f }) }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1800); copied = false }
    }

    val printing = session.phase == PrintPhase.Printing
    val clock = remember(timeline, printing, reduced) {
        PrintClock(timeline, instant = !printing || reduced) { session.elapsed.value.toLong() }
    }
    val shareText = remember(book, day) { merchantReceiptText(book, day) }
    val scroll = rememberScrollState()
    var calendarOpen by remember { mutableStateOf(false) }

    fun selectDay(next: Long) {
        val d = next.coerceIn(earliest, today)
        if (d == day) return
        // Finishing the feed first keeps the sheet's height tied to the day being printed.
        if (session.phase == PrintPhase.Printing) session.skip(timeline)
        day = d
    }

    NavScaffold(
        title = "经营小票",
        scroll = NavScrollNone,
        onBack = onBack,
        backLabel = "返回",
        trailing = { NavIconButton(Icons.Outlined.Settings, "设置", onClick = onOpenSettings) },
    ) { top ->
        Column(
            // The tab bar is hidden on this page (MerchantShell), so the page only clears the nav inset.
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xxl)
                .xpRevealLayer(index = 0),
        ) {
            DayPager(day = day, today = today, earliest = earliest, onSelect = { selectDay(it) }, onCalendar = { calendarOpen = true })

            CompositionLocalProvider(LocalPaperColors provides paperColors, LocalReceiptFont provides ReceiptFont.Thermal) {
                ReceiptPrinter(
                    lcd = "${RevenueBook.shortDate(day)}  ¥" + (entry?.let { it.revenueCents / 100 } ?: "—"),
                    printing = printing,
                    onPrint = { if (!printing) session.open(timeline, reduced) },
                    modifier = Modifier.fillMaxWidth().padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.s),
                    slotAtTop = false,
                )
                Box(
                    Modifier.fillMaxWidth()
                        .height((timeline.totalHeightDp.dp + PaperDepth - PaperLift).coerceAtLeast(0.dp)),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    ReceiptPaper(
                        lines = lines,
                        clock = clock,
                        stampText = "",
                        stamp = stamp,
                        tornTop = false,
                        modifier = Modifier.offset(y = -PaperLift),
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.m)) {
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    XpButton(
                        "分享日报",
                        {
                            uiHaptics.confirm()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "小店经营日报 ${RevenueBook.isoDate(day)}")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "分享经营日报").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    XpButton(
                        if (copied) "已复制" else "复制文字",
                        {
                            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            manager.setPrimaryClip(ClipData.newPlainText("经营日报", shareText))
                            uiHaptics.select()
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                        style = XpButtonStyle.Secondary,
                    )
                }
                if (entry == null) {
                    Text(
                        "这天还没有记录。",
                        style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                        modifier = Modifier.padding(top = XpSpace.s),
                    )
                    XpButton(
                        "补记这一天",
                        { onRecordDay(day) },
                        modifier = Modifier.padding(top = XpSpace.s),
                        style = XpButtonStyle.Secondary,
                    )
                }
            }
        }
    }

    if (calendarOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = DayPick.utcFromLocalDay(day))
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectDay(DayPick.localDayFromUtc(it)) }
                    calendarOpen = false
                }) { Text("选择", color = XpColors.Ink) }
            },
            dismissButton = { TextButton(onClick = { calendarOpen = false }) { Text("取消", color = XpColors.InkSecondary) } },
        ) { DatePicker(state) }
    }
}

/**
 * ‹ 10月28日 周三 › with the calendar behind the date and a swipe on it. Mirrors the personal page's day strip:
 * one day at a time, today on the right edge, the calendar for anything older than the pager's window.
 */
@Composable
private fun DayPager(
    day: Long,
    today: Long,
    earliest: Long,
    onSelect: (Long) -> Unit,
    onCalendar: () -> Unit,
) {
    val haptics = rememberXpHaptics()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagerArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "前一天", enabled = day > earliest) {
            haptics.select(); onSelect(RevenueBook.plusDays(day, -1))
        }
        Box(
            Modifier.weight(1f).height(46.dp)
                .clip(RoundedCornerShape(XpRadius.m))
                .pressableClick(haptic = false, onClick = onCalendar)
                .pointerInput(day, today, earliest) {
                    var travel = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travel = 0f },
                        onDragCancel = { travel = 0f },
                        onHorizontalDrag = { change, drag -> change.consume(); travel += drag },
                        onDragEnd = {
                            val threshold = 44.dp.toPx()
                            when {
                                travel <= -threshold && day < today -> onSelect(RevenueBook.plusDays(day, 1))
                                travel >= threshold && day > earliest -> onSelect(RevenueBook.plusDays(day, -1))
                            }
                            travel = 0f
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    RevenueBook.dayLabel(day),
                    fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
                    color = XpColors.Ink, maxLines = 1,
                )
                Text(
                    if (day == today) "今天" else RevenueBook.isoDate(day),
                    fontSize = 10.sp, lineHeight = 13.sp, color = XpColors.InkSecondary, maxLines = 1,
                )
            }
        }
        PagerArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "后一天", enabled = day < today) {
            haptics.select(); onSelect(RevenueBook.plusDays(day, 1))
        }
        Spacer(Modifier.width(XpSpace.s))
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(XpColors.Fill).pressableClick(onClick = onCalendar),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历：选择任意一天", tint = XpColors.Ink, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun PagerArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).pressableClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = description,
            tint = if (enabled) XpColors.Ink else XpColors.InkTertiary.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The day's report as printable lines: same grammar as the personal receipt (title, dashed rules, total block,
 * rows, one AI line, footer), built only from the revenue book. No demo line: seeded days print like any other.
 */
private fun merchantLines(entries: List<RevenueEntry>, day: Long, today: Long): List<ReceiptLine> {
    val entry = RevenueBook.entryFor(entries, day)
    val out = mutableListOf<ReceiptLine>()
    out += ReceiptLine(LineKind.BLANK)
    out += ReceiptLine(LineKind.TITLE, if (day == today) "今日日报" else "经营日报")
    out += ReceiptLine(LineKind.SUBTITLE, "小店 · 一天一张")
    out += ReceiptLine(LineKind.DASH)
    out += ReceiptLine(LineKind.META, "日期", "${RevenueBook.isoDate(day)} ${RevenueBook.weekdayLabel(day)}")
    out += ReceiptLine(LineKind.DASH)
    if (entry == null) {
        out += ReceiptLine(LineKind.BIG, "这天没有记录")
        out += ReceiptLine(LineKind.NOTICE, "补上数字就能出日报")
    } else {
        out += ReceiptLine(LineKind.TOTAL, "当天营业额", ReceiptLayout.moneyFixed(entry.revenueCents), entry.revenueCents)
        out += ReceiptLine(LineKind.NOTICE, "仅作记录 · 非正式票据")
        out += ReceiptLine(LineKind.DASH)
        out += ReceiptLine(LineKind.HEADER, "经营 / 当日", "数字")
        out += ReceiptLine(LineKind.META, "订单", if (entry.orders > 0) "${entry.orders} 单" else "未填")
        out += ReceiptLine(
            LineKind.META, "客单价",
            if (entry.averageTicketCents > 0) ReceiptLayout.money(entry.averageTicketCents) else "—",
        )
        out += ReceiptLine(LineKind.META, "本周", ReceiptLayout.money(RevenueBook.windowTotal(entries, RevenueBook.weekStart(day), day)))
        out += ReceiptLine(LineKind.DASH)
        val remark = ReceiptLayout.wrap(MerchantAi.receiptLine(entries, day), ReceiptLayout.COMMENT_COLS)
        remark.forEachIndexed { i, text ->
            val open = if (i == 0) "「" else ""
            val close = if (i == remark.lastIndex) "」" else ""
            out += ReceiptLine(LineKind.COMMENT, "$open$text$close")
        }
        out += ReceiptLine(LineKind.DASH)
    }
    out += ReceiptLine(LineKind.FOOTER, "本机生成 · 不上传")
    out += ReceiptLine(LineKind.BLANK)
    return out
}

/** The shareable text version of the paper — what a shop owner would paste into a group chat. */
private fun merchantReceiptText(entries: List<RevenueEntry>, day: Long): String {
    val entry = RevenueBook.entryFor(entries, day)
    val sb = StringBuilder()
    sb.appendLine("小店经营日报 · ${RevenueBook.isoDate(day)} ${RevenueBook.weekdayLabel(day)}")
    if (entry == null) {
        sb.appendLine("这天没有记录")
    } else {
        sb.appendLine("营业额 ${RevenueBook.money(entry.revenueCents)}")
        if (entry.orders > 0) {
            sb.appendLine("订单 ${entry.orders} 单 · 客单价 ${RevenueBook.money(entry.averageTicketCents)}")
        }
        if (entry.note.isNotBlank()) sb.appendLine("备注：${entry.note}")
    }
    sb.appendLine(MerchantAi.receiptLine(entries, day))
    sb.append("本机生成 · 仅记录（非正式票据）")
    return sb.toString()
}
