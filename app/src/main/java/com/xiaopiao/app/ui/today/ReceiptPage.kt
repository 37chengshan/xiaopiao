package com.xiaopiao.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.category.CategoryTile
import com.xiaopiao.app.ui.components.glassCard
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.LedgerSideLeading
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.SnapPicker
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
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
 * 小票 tab (tt 2026-10-29): the printer heads the page and everything scrolls together, so the page slides up under
 * the glass top capsules. Below it one compact card of snap pickers — 日期 / 纸张 / 墨色 / 字体 / 内容, each swiped
 * left/right and settling on the centred choice — then the categories to print, drawn with the ledger's own
 * category tiles (custom ones included), so 小票 and 账单 always agree. Settings save on every change; the printer
 * key and the top-bar receipt button print with them (docs/APP-DESIGN.md §3.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPage(container: AppContainer, host: ReceiptHost) {
    val scope = rememberCoroutineScope()
    val settings = host.settings
    val all by remember { container.ledger.observeAll() }.collectAsState(initial = emptyList())
    val defs by remember { container.categories.observeDefs() }.collectAsState(initial = CategoryDefaults.all)
    val today = host.today
    var selectedDay by rememberSaveable { mutableLongStateOf(today) }
    var calendarOpen by remember { mutableStateOf(false) }

    // Ink hue slider: previews live while dragging, saved on release.
    var hue by remember { mutableFloatStateOf(220f) }
    var dragging by remember { mutableStateOf(false) }
    val colors = if (dragging) PaperColors.resolve(settings.theme, PaperColors.inkFromHue(hue)) else settings.colors()

    val dayOut = remember(all, defs, selectedDay, settings.excluded) {
        all.filter {
            it.direction == Direction.OUT && it.occurredAt in selectedDay until selectedDay + DAY_MS &&
                ReceiptSource.prints(it, settings.excluded, defs)
        }.sumOf { it.amountCents }
    }

    val scroll = rememberScrollState()
    NavScaffold(
        "小票", rememberNavScroll(scroll),
        leading = { LedgerSideLeading() },
        trailing = { AppTopActions() },
        glassGround = true,
    ) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = 120.dp)) {
            CompositionLocalProvider(LocalPaperColors provides colors, LocalReceiptFont provides settings.font) {
                ReceiptPrinter(
                    lcd = ReceiptLayout.dayLabel(selectedDay).substringBefore(' ') + "  ¥" + (dayOut / 100),
                    printing = host.printing,
                    onPrint = { from -> host.print(selectedDay, from) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = XpSpace.l),
                    slotAtTop = false,
                )
            }

            SettingsCard(Modifier.padding(top = XpSpace.l)) {
                PickerRow("日期") {
                    DayPicker(all, today, selectedDay, Modifier.weight(1f)) { selectedDay = it }
                    Box(
                        Modifier.padding(end = XpSpace.s).size(36.dp).clip(CircleShape).background(XpColors.Fill)
                            .pressableClick(onClick = { calendarOpen = true }),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历：选择任意一天", tint = XpColors.Ink, modifier = Modifier.size(18.dp)) }
                }
                Rule()
                PickerRow("纸张") {
                    val themes = PaperTheme.entries
                    SnapPicker(
                        themes, themes.indexOf(settings.theme), { i -> scope.launch { host.prefs.setTheme(themes[i]) } },
                        Modifier.weight(1f), itemWidth = 52.dp, label = { it.label },
                    ) { t, on -> PaperSwatch(t, on) }
                }
                Rule()
                PickerRow("墨色") {
                    val inkIndex = InkChoices.indexOf(settings.customInk).let { if (it < 0) InkChoices.size else it }
                    SnapPicker(
                        InkChoices + listOf(CustomInk), inkIndex,
                        { i ->
                            val argb = if (i < InkChoices.size) InkChoices[i] else PaperColors.inkFromHue(hue)
                            scope.launch { host.prefs.setCustomInk(argb) }
                        },
                        Modifier.weight(1f), itemWidth = 32.dp, spacing = 10.dp,
                        label = { argb -> if (argb == null) "原色" else if (argb == CustomInk) "自调颜色" else "墨色" },
                    ) { argb, on -> InkDot(argb, on, hue) }
                }
                if (settings.customInk != null && settings.customInk !in InkChoices) {
                    Slider(
                        value = hue, onValueChange = { hue = it; dragging = true },
                        onValueChangeFinished = { dragging = false; scope.launch { host.prefs.setCustomInk(PaperColors.inkFromHue(hue)) } },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(thumbColor = XpColors.Ink, activeTrackColor = XpColors.Ink, inactiveTrackColor = XpColors.Fill),
                        modifier = Modifier.padding(start = LabelWidth + XpSpace.l, end = XpSpace.l).height(32.dp),
                    )
                }
                Rule()
                PickerRow("字体") {
                    val fonts = ReceiptFont.entries
                    SnapPicker(
                        fonts, settings.font.ordinal, { i -> scope.launch { host.prefs.setFont(fonts[i]) } },
                        Modifier.weight(1f), itemWidth = 80.dp, label = { it.label },
                    ) { f, on -> Pill(f.label, on) }
                }
                Rule()
                val presets = ReceiptPreset.entries
                var custom by rememberSaveable { mutableStateOf(settings.content.preset == null) }
                PickerRow("内容") {
                    val labels = presets.map { it.label } + "自定义"
                    SnapPicker(
                        labels, if (custom) presets.size else settings.content.preset?.ordinal ?: presets.size,
                        { i ->
                            custom = i >= presets.size
                            presets.getOrNull(i)?.let { p -> scope.launch { host.prefs.setContent(p.content) } }
                        },
                        Modifier.weight(1f), itemWidth = 80.dp, label = { it },
                    ) { l, on -> Pill(l, on) }
                }
                if (custom || settings.content.preset == null) {
                    ContentFlags(settings.content) { next -> scope.launch { host.prefs.setContent(next) } }
                }
            }

            CategoryCard(defs, settings.excluded) { next -> scope.launch { host.prefs.setExcluded(next) } }
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

/** Width of the left-hand labels in the settings card; pickers start right after it. */
private val LabelWidth = 40.dp

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.l)
            .glassCard(RoundedCornerShape(XpRadius.contentCard))
            .padding(vertical = 4.dp),
    ) { content() }
}

/** One setting: an 11sp label on the left, its picker filling the rest of the row. */
@Composable
private fun PickerRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, Modifier.padding(start = XpSpace.l).width(LabelWidth - 8.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Medium, color = XpColors.InkSecondary, maxLines = 1,
        )
        content()
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().padding(start = XpSpace.l + LabelWidth).height(0.5.dp).background(XpColors.Hairline))
}

private val Weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")

/** Last [STRIP_DAYS] days, oldest first; the centred day is the one printed. Dots mark days with entries. */
@Composable
private fun DayPicker(all: List<Txn>, today: Long, selected: Long, modifier: Modifier, onSelect: (Long) -> Unit) {
    val days = remember(today) { (0 until STRIP_DAYS).map { today - it * DAY_MS }.reversed() }
    val withData = remember(all) { all.map { startOfDay(it.occurredAt) }.toSet() }
    SnapPicker(
        days, days.indexOf(selected).coerceAtLeast(0), { i -> onSelect(days[i]) },
        modifier, itemWidth = 38.dp, spacing = 6.dp,
        label = { d -> ReceiptLayout.dayLabel(d) },
    ) { d, on ->
        val cal = Calendar.getInstance().apply { timeInMillis = d }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(if (on) XpColors.Primary else Color.Transparent).padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(Weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1], fontSize = 10.sp, color = if (on) XpColors.OnPrimary.copy(alpha = 0.7f) else XpColors.InkSecondary)
            Text("${cal.get(Calendar.DAY_OF_MONTH)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) XpColors.OnPrimary else XpColors.Ink)
            Box(Modifier.padding(top = 2.dp).size(3.dp).clip(CircleShape).background(if (d in withData) (if (on) XpColors.OnPrimary else XpColors.Accent) else Color.Transparent))
        }
    }
}

/** A tiny sheet of the paper: two ink lines and the stamp dot, outlined in ink when it is the choice. */
@Composable
private fun PaperSwatch(t: PaperTheme, on: Boolean) {
    val c = PaperColors.resolve(t, null)
    val shape = RoundedCornerShape(6.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(34.dp, 30.dp).clip(shape).background(c.paper)
                .border(if (on) 1.5.dp else 0.5.dp, if (on) XpColors.Ink else XpColors.Hairline, shape).padding(5.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth(0.6f).height(2.dp).background(c.ink.copy(alpha = 0.85f)))
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(c.ink.copy(alpha = 0.3f)))
            }
            Box(Modifier.align(Alignment.BottomEnd).size(7.dp).clip(CircleShape).background(c.stamp.copy(alpha = 0.8f)))
        }
        Text(t.label, Modifier.padding(top = 2.dp), fontSize = 9.sp, color = if (on) XpColors.Ink else XpColors.InkSecondary, maxLines = 1)
    }
}

private val InkChoices = listOf<Int?>(null, 0xFF1D1D1F.toInt(), 0xFF1F3A8A.toInt(), 0xFF166534.toInt(), 0xFF7F1D1D.toInt(), 0xFF5B21B6.toInt())

/** Sentinel for the last ink slot: a hue picked on the slider. */
private const val CustomInk = 0x00C0FFEE

@Composable
private fun InkDot(argb: Int?, on: Boolean, hue: Float) {
    val ring = if (on) 2.dp else 0.5.dp
    Box(
        Modifier.size(26.dp).clip(CircleShape)
            .background(
                when (argb) {
                    null -> XpColors.Fill
                    CustomInk -> Color(PaperColors.inkFromHue(hue))
                    else -> Color(argb)
                },
            )
            .border(ring, if (on) XpColors.Ink else XpColors.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (argb) {
            null -> Text("原", fontSize = 10.sp, color = XpColors.InkSecondary)
            CustomInk -> Icon(Icons.Outlined.Palette, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/** Two-level text pill: filled when centred, bare text otherwise. */
@Composable
private fun Pill(text: String, on: Boolean) {
    Box(
        Modifier.height(28.dp).clip(RoundedCornerShape(XpRadius.pill))
            .background(if (on) XpColors.Primary else Color.Transparent).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) XpColors.OnPrimary else XpColors.Ink, maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}

/** 自定义 content: each printable part as a toggle chip, only while 自定义 is chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentFlags(content: ReceiptContent, onChange: (ReceiptContent) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = XpSpace.l + LabelWidth, end = XpSpace.l, bottom = XpSpace.m),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReceiptContent.Flag.entries.forEach { flag ->
            val on = flag.get(content)
            Box(
                Modifier.height(28.dp).clip(RoundedCornerShape(XpRadius.pill))
                    .background(if (on) XpColors.Primary else XpColors.Fill)
                    .pressableClick(haptic = true) { onChange(content.withFlag(flag, !on)) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(flag.label, fontSize = 12.sp, color = if (on) XpColors.OnPrimary else XpColors.InkSecondary, maxLines = 1) }
        }
    }
}

/**
 * Which categories go on the paper, as the ledger's own tiles (custom categories included). Tap to leave one off;
 * at least one stays on; 全部 resets. Off tiles fade back but keep their colour, so it is clear what they are.
 */
@Composable
private fun CategoryCard(defs: List<CategoryDef>, excluded: Set<String>, onChange: (Set<String>) -> Unit) {
    val spend = remember(defs) { defs.filter { it.fits(Direction.OUT) && !it.hidden }.sortedBy { it.sortOrder } }
    Column(
        Modifier.fillMaxWidth().padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.m)
            .glassCard(RoundedCornerShape(XpRadius.contentCard)).padding(vertical = XpSpace.m),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l), verticalAlignment = Alignment.CenterVertically) {
            Text("印哪些分类", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = XpColors.Ink)
            val printed = spend.count { it.id !in excluded }
            Text(
                if (excluded.isEmpty()) "全部" else "$printed / ${spend.size} · 全部",
                Modifier.clip(RoundedCornerShape(XpRadius.pill)).pressableClick { onChange(emptySet()) }.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 12.sp, color = if (excluded.isEmpty()) XpColors.InkSecondary else XpColors.Accent,
            )
        }
        Text(
            "没选中的不印在小票上，账本里照常保留。", Modifier.padding(horizontal = XpSpace.l, vertical = 2.dp),
            fontSize = 11.sp, color = XpColors.InkTertiary,
        )
        LazyRow(
            Modifier.fillMaxWidth().padding(top = XpSpace.s),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = XpSpace.l),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(spend, key = { it.id }) { def ->
                val on = def.id !in excluded
                Column(
                    Modifier.width(54.dp).graphicsLayer { alpha = if (on) 1f else 0.35f }
                        .pressableClick(haptic = true) {
                            val next = if (on) excluded + def.id else excluded - def.id
                            // Keep at least one category on the paper.
                            if (spend.any { it.id !in next }) onChange(next)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CategoryTile(def, size = 36.dp, selected = on)
                    Text(def.label, Modifier.padding(top = 3.dp), fontSize = 10.sp, color = XpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
