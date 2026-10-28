package com.xiaopiao.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpSwitch
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

private const val STRIP_DAYS = 30

/** Material's date picker speaks UTC-midnight; the ledger speaks local midnight. Same calendar date, two clocks. */
object DayPick {
    fun localDayFromUtc(utcMs: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
        return Calendar.getInstance().apply { clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
    }

    fun utcFromLocalDay(localMs: Long): Long {
        val l = Calendar.getInstance().apply { timeInMillis = localMs }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
    }
}

/**
 * 小票页: the printer on top with the chosen day's receipt coming out of it (live preview of every setting below),
 * a day strip + calendar for reprinting any earlier day, paper themes with a custom ink, and what to print.
 * Settings save on every change; the resident strip prints with them (docs/APP-DESIGN.md §3.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPage(container: AppContainer, host: ReceiptHost, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings = host.settings
    val all by remember { container.ledger.observeAll() }.collectAsState(initial = emptyList())
    val today = host.today
    var selectedDay by rememberSaveable { mutableLongStateOf(today) }
    var calendarOpen by remember { mutableStateOf(false) }

    // Ink hue slider: previews live while dragging, saved on release.
    var hue by remember { mutableFloatStateOf(220f) }
    var dragging by remember { mutableStateOf(false) }
    val colors = if (dragging) PaperColors.resolve(settings.theme, PaperColors.inkFromHue(hue)) else settings.colors()

    val lines = remember(all, selectedDay, today, settings.content) {
        ReceiptSource.linesFor(all, selectedDay, today, settings.content, System.currentTimeMillis())
    }
    val timeline = remember(lines) { PrintTimeline.build(lines, reduced = true) }
    val stampText = remember(all, selectedDay) { ReceiptLayout.stampText(ReceiptSource.receiptFor(all, selectedDay)) }
    val dayOut = remember(all, selectedDay) {
        all.filter { it.direction == Direction.OUT && it.occurredAt in selectedDay until selectedDay + DAY_MS }.sumOf { it.amountCents }
    }

    val scroll = rememberScrollState()
    NavScaffold("小票", rememberNavScroll(scroll), onBack = onBack) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top + XpSpace.s, bottom = 96.dp)) {
            // Printer with the receipt tucked under its slot.
            CompositionLocalProvider(LocalPaperColors provides colors) {
                Box(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.padding(top = PrinterSlotCentre).heightIn(max = 300.dp).clipToBounds().previewFade()) {
                        val clock = remember(timeline) { PrintClock(timeline, instant = true) { 0L } }
                        val stamp = remember { StampVisuals({ 1f }, { 1f }, { 0f }) }
                        ReceiptPaper(lines, clock, stampText, stamp, tornTop = false)
                    }
                    ReceiptPrinter(
                        lcd = ReceiptLayout.dayLabel(selectedDay).substringBefore(' ') + "  ¥" + (dayOut / 100),
                        printing = host.printing,
                        onPrint = { from -> host.print(selectedDay, from) },
                    )
                }
            }

            Label("日期")
            DayStrip(all, today, selectedDay, onSelect = { selectedDay = it }, onCalendar = { calendarOpen = true })

            Label("纸张")
            ThemeRow(settings.theme) { t -> scope.launch { host.prefs.setTheme(t) } }
            Spacer(Modifier.height(XpSpace.m))
            InkRow(
                custom = settings.customInk,
                onPick = { argb -> scope.launch { host.prefs.setCustomInk(argb) } },
            )
            Slider(
                value = hue, onValueChange = { hue = it; dragging = true },
                onValueChangeFinished = { dragging = false; scope.launch { host.prefs.setCustomInk(PaperColors.inkFromHue(hue)) } },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(thumbColor = XpColors.Ink, activeTrackColor = XpColors.Ink, inactiveTrackColor = XpColors.Fill),
                modifier = Modifier.padding(horizontal = XpSpace.l + 4.dp),
            )

            Label("打印内容")
            Box(Modifier.padding(horizontal = XpSpace.l)) {
                val presets = ReceiptPreset.entries
                val current = settings.content.preset
                Segmented(
                    presets.map { it.label } + "自定义",
                    current?.ordinal ?: presets.size,
                    { i -> presets.getOrNull(i)?.let { p -> scope.launch { host.prefs.setContent(p.content) } } },
                )
            }
            Spacer(Modifier.height(XpSpace.m))
            GroupedSection {
                ReceiptContent.Flag.entries.forEachIndexed { i, flag ->
                    item(i) {
                        val on = flag.get(settings.content)
                        GroupedRow(
                            flag.label,
                            trailing = { XpSwitch(on, { v -> scope.launch { host.prefs.setContent(settings.content.withFlag(flag, v)) } }) },
                            onClick = { scope.launch { host.prefs.setContent(settings.content.withFlag(flag, !on)) } },
                        )
                    }
                }
            }
        }
    }

    if (calendarOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = DayPick.utcFromLocalDay(selectedDay))
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDay = DayPick.localDayFromUtc(it).coerceAtMost(today) }
                    calendarOpen = false
                }) { Text("选择", color = XpColors.Ink) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { calendarOpen = false }) { Text("取消", color = XpColors.InkSecondary) } },
        ) { DatePicker(state) }
    }
}

/** The preview is only a taste: it fades out at the bottom instead of running the whole receipt down the page. */
private fun Modifier.previewFade(): Modifier = this.drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, XpColors.CanvasGrouped), startY = size.height - 70 * density, endY = size.height))
}

@Composable
private fun Label(text: String) {
    Text(
        text, Modifier.padding(start = XpSpace.l + 4.dp, end = XpSpace.l, top = XpSpace.xl, bottom = XpSpace.s),
        style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary,
    )
}

private val Weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")

@Composable
private fun DayStrip(all: List<Txn>, today: Long, selected: Long, onSelect: (Long) -> Unit, onCalendar: () -> Unit) {
    val days = remember(today) { (0 until STRIP_DAYS).map { today - it * DAY_MS }.reversed() }
    val withData = remember(all) { all.map { startOfDay(it.occurredAt) }.toSet() }
    val state = rememberLazyListState()
    LaunchedEffect(selected) {
        val i = days.indexOf(selected)
        if (i >= 0) state.animateScrollToItem((i - 2).coerceAtLeast(0))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = XpSpace.l, end = XpSpace.s), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(days, key = { _, d -> d }) { _, d ->
                val on = d == selected
                val cal = Calendar.getInstance().apply { timeInMillis = d }
                Column(
                    Modifier.width(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) XpColors.Primary else XpColors.Card)
                        .pressableClick { onSelect(d) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1], fontSize = 11.sp, color = if (on) XpColors.OnPrimary.copy(alpha = 0.7f) else XpColors.InkSecondary)
                    Text("${cal.get(Calendar.DAY_OF_MONTH)}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (on) XpColors.OnPrimary else XpColors.Ink)
                    Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (d in withData) (if (on) XpColors.OnPrimary else XpColors.Accent) else Color.Transparent))
                }
            }
        }
        Box(
            Modifier.padding(end = XpSpace.l).size(44.dp).clip(CircleShape).background(XpColors.Fill).pressableClick(onClick = onCalendar),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历：选择任意一天", tint = XpColors.Ink, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun ThemeRow(selected: PaperTheme, onPick: (PaperTheme) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaperTheme.entries.forEach { t ->
            val c = PaperColors.resolve(t, null)
            val on = t == selected
            Column(Modifier.weight(1f).pressableClick { onPick(t) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(8.dp)).background(c.paper)
                        .border(if (on) 2.dp else 0.5.dp, if (on) XpColors.Ink else XpColors.Hairline, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth(0.55f).height(3.dp).background(c.ink.copy(alpha = 0.85f)))
                        Box(Modifier.fillMaxWidth().height(2.dp).background(c.ink.copy(alpha = 0.3f)))
                        Box(Modifier.fillMaxWidth(0.7f).height(2.dp).background(c.ink.copy(alpha = 0.3f)))
                    }
                    Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(c.stamp.copy(alpha = 0.8f)))
                }
                Text(t.label, Modifier.padding(top = 5.dp), fontSize = 11.sp, color = if (on) XpColors.Ink else XpColors.InkSecondary, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

private val InkChoices = listOf<Int?>(null, 0xFF1D1D1F.toInt(), 0xFF1F3A8A.toInt(), 0xFF166534.toInt(), 0xFF7F1D1D.toInt(), 0xFF5B21B6.toInt())

@Composable
private fun InkRow(custom: Int?, onPick: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l + 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("墨色", fontSize = 13.sp, color = XpColors.InkSecondary, modifier = Modifier.width(34.dp))
        InkChoices.forEach { argb ->
            val on = custom == argb
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(argb?.let { Color(it) } ?: XpColors.Fill)
                    .border(if (on) 2.dp else 0.5.dp, if (on) XpColors.Ink else XpColors.Hairline, CircleShape)
                    .pressableClick { onPick(argb) },
                contentAlignment = Alignment.Center,
            ) { if (argb == null) Text("原", fontSize = 11.sp, color = XpColors.InkSecondary) }
        }
    }
}
