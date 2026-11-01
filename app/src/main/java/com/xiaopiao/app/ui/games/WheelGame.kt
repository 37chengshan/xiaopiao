package com.xiaopiao.app.ui.games

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import com.xiaopiao.app.ui.components.NavScroll
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 转盘：通用决策模块（tt 2026-10-30）。
 *
 * 人可以建多个盘，每个盘自己选格数、自己填每一格的文字；配置存在本机，重开还在。
 * 盘只做一件事——把人已经写好的选项随机指一个出来，不给建议、不预置选项。
 */

private const val WheelPrefs = "wheel-v1"
private const val WheelKey = "wheels"
private const val MinSlices = 2
private const val MaxSlices = 12

private data class Wheel(val id: String, val name: String, val slices: List<String>) {
    /** Only filled slices take part in a spin. */
    val live: List<String> get() = slices.map { it.trim() }.filter { it.isNotEmpty() }
}

private class WheelStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(WheelPrefs, Context.MODE_PRIVATE)

    fun load(): List<Wheel> {
        val raw = prefs.getString(WheelKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                val slices = o.optJSONArray("slices")?.let { s -> (0 until s.length()).map { s.optString(it) } } ?: emptyList()
                Wheel(id, o.optString("name", id), slices)
            }
        }.getOrDefault(emptyList())
    }

    fun save(wheels: List<Wheel>) {
        val array = JSONArray()
        wheels.forEach { w ->
            array.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("name", w.name)
                    put("slices", JSONArray(w.slices))
                },
            )
        }
        prefs.edit().putString(WheelKey, array.toString()).apply()
    }
}

/** 转盘页：入口在 游戏 里。 */
@Composable
fun WheelGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WheelStore(context) }
    val reduced = rememberReducedMotion()
    val haptics = rememberXpHaptics()
    val scope = rememberCoroutineScope()

    var wheels by remember { mutableStateOf(store.load().ifEmpty { listOf(newWheel(1)) }) }
    var currentId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentIndex = wheels.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
    val wheel = wheels[currentIndex]

    fun persist(list: List<Wheel>) {
        wheels = list
        store.save(list)
    }

    fun updateCurrent(transform: (Wheel) -> Wheel) {
        persist(wheels.mapIndexed { i, w -> if (i == currentIndex) transform(w) else w })
    }

    val rotation = remember(wheel.id) { Animatable(0f) }
    var landing by remember(wheel.id) { mutableStateOf<String?>(null) }
    var spinning by remember(wheel.id) { mutableStateOf(false) }
    val pop = remember { Animatable(1f) }
    val live = wheel.live
    // The disc shows exactly what a spin can land on: the filled slices. With fewer than two, it shows the
    // raw slices so the wedges still line up with the editor rows.
    val disc = if (live.size >= MinSlices) live else wheel.slices

    fun spin() {
        if (spinning || disc.size < MinSlices) return
        val n = disc.size
        val winner = Random.nextInt(n)
        val slice = 360f / n
        val target = -(winner * slice + slice / 2f)
        // Keep turning forward: at least 4 extra turns, then the shortest way to the winning slice.
        val turns = 4
        val from = rotation.value
        val base = from + turns * 360f
        val delta = ((target - base) % 360f + 360f) % 360f
        val to = base + delta
        landing = null
        spinning = true
        scope.launch {
            if (reduced) {
                rotation.snapTo(to)
            } else {
                var lastSlice = sliceUnder(rotation.value, n)
                rotation.animateTo(
                    to,
                    tween(1900 + Random.nextInt(300), easing = XpEase.out),
                ) {
                    val now = sliceUnder(value, n)
                    if (now != lastSlice) {
                        lastSlice = now
                        haptics.tick()
                    }
                }
            }
            spinning = false
            landing = disc[winner]
            if (!reduced) {
                pop.snapTo(0.92f)
                pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
            }
        }
    }

    NavScaffold(title = tr("转盘", "Wheel"), scroll = NavScrollNone, onBack = onBack) { top ->
        Column(
            Modifier.fillMaxSize().padding(top = top).verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.page),
        ) {
            WheelSwitcher(
                wheels = wheels,
                current = currentIndex,
                onSelect = { currentId = wheels[it].id; landing = null },
                onAdd = {
                    val next = wheels + newWheel(wheels.size + 1)
                    persist(next)
                    currentId = next.last().id
                    landing = null
                },
                onRename = { name ->
                    if (name.isNotBlank()) updateCurrent { it.copy(name = name.trim()) }
                },
                onDelete = {
                    if (wheels.size == 1) {
                        persist(listOf(newWheel(1)))
                    } else {
                        persist(wheels.filterIndexed { i, _ -> i != currentIndex })
                    }
                    currentId = null
                    landing = null
                },
            )

            Spacer(Modifier.height(XpSpace.m))
            // The wheel sits on its own card: a coloured disc on a bare page read as a sticker, not a tool.
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(XpRadius.xl))
                    .background(XpColors.Card)
                    .padding(vertical = XpSpace.l),
                contentAlignment = Alignment.Center,
            ) {
                WheelDisc(
                    slices = disc,
                    rotation = rotation.value,
                    spinning = spinning,
                    modifier = Modifier.size(280.dp).graphicsLayer {
                        val s = pop.value
                        scaleX = s; scaleY = s
                    },
                )
            }
            Spacer(Modifier.height(XpSpace.m))

            // Result / hint. One line, never advice.
            Box(
                Modifier.fillMaxWidth().padding(bottom = XpSpace.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        spinning -> tr("转着…", "Spinning…")
                        landing != null -> landing!!
                        live.size < MinSlices -> tr("先填两格以上，才能转", "Fill in at least two slices to spin")
                        else -> tr("转一下，看落哪格", "Spin and see where it lands")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (landing != null && !spinning) XpColors.OnAccent else XpColors.InkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .then(
                            if (landing != null && !spinning) {
                                Modifier.clip(RoundedCornerShape(XpRadius.pill))
                                    .background(XpColors.Accent)
                                    .padding(horizontal = XpSpace.l, vertical = 8.dp)
                            } else Modifier,
                        ),
                )
            }
            XpButton(
                text = tr("转一下", "Spin"),
                onClick = ::spin,
                enabled = !spinning && live.size >= MinSlices,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(XpSpace.l))
            SliceCountRow(
                count = wheel.slices.size,
                onChange = { n ->
                    updateCurrent { w ->
                        val fixed = when {
                            n > w.slices.size -> w.slices + List(n - w.slices.size) { "" }
                            else -> w.slices.take(n)
                        }
                        w.copy(slices = fixed)
                    }
                    landing = null
                },
            )
            Spacer(Modifier.height(XpSpace.s))
            wheel.slices.forEachIndexed { index, value ->
                SliceRow(
                    index = index,
                    value = value,
                    onValue = { text ->
                        updateCurrent { w -> w.copy(slices = w.slices.toMutableList().also { it[index] = text }) }
                        landing = null
                    },
                )
            }
            Spacer(Modifier.height(XpSpace.l))
            Text(
                tr("盘面上的字只有你自己看得到，也只存在这台手机里。", "Your slices stay on this phone."),
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(bottom = XpSpace.l),
            )
        }
    }
}

private fun newWheel(number: Int) = Wheel(
    id = "w" + System.currentTimeMillis().toString(36) + number,
    name = tr("转盘 $number", "Wheel $number"),
    slices = List(4) { "" },
)

/** Which slice sits under the pointer (top, -90°) for a given wheel rotation. */
private fun sliceUnder(rotation: Float, count: Int): Int {
    if (count <= 0) return 0
    val slice = 360f / count
    val normalized = ((-rotation) % 360f + 360f) % 360f
    return (normalized / slice).toInt().coerceIn(0, count - 1)
}

@Composable
private fun WheelDisc(slices: List<String>, rotation: Float, spinning: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val colors = wheelColors()
    val count = slices.size.coerceAtLeast(1)
    val slice = 360f / count
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // Body
        drawCircle(XpColors.Card, radius = radius, center = center)
        for (i in 0 until count) {
            val start = -90f + i * slice + rotation
            drawArc(
                color = colors[i % colors.size],
                startAngle = start,
                sweepAngle = slice,
                useCenter = true,
            )
            val label = slices.getOrNull(i).orEmpty().trim()
            // An empty slice draws no text at all: a placeholder glyph rotated inside a wedge reads as noise.
            if (label.isNotEmpty()) {
                val angle = start + slice / 2f
                rotate(degrees = angle, pivot = center) {
                    drawSliceLabel(measurer, label, center, radius, count)
                }
            }
        }
        // Rim + pointer
        drawCircle(
            color = XpColors.Ink.copy(alpha = 0.10f),
            radius = radius - 1.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(XpColors.Card, radius = radius * 0.16f, center = center)
        val pointer = radius * 0.12f
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - pointer, center.y - radius - pointer * 0.4f)
                lineTo(center.x + pointer, center.y - radius - pointer * 0.4f)
                lineTo(center.x, center.y - radius + pointer * 1.2f)
                close()
            },
            color = XpColors.Accent,
        )
    }
}

/** Radial label: rotated with its slice, so text always reads outwards. */
private fun DrawScope.drawSliceLabel(measurer: TextMeasurer, text: String, center: Offset, radius: Float, count: Int) {
    val style = TextStyle(
        color = XpColors.Ink,
        fontSize = if (count > 8) 11.sp else 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    val maxWidth = (radius * 0.62f).roundToInt().coerceAtLeast(24)
    val layout = measurer.measure(text, style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth))
    val x = center.x + radius * 0.60f - layout.size.width / 2f
    val y = center.y - layout.size.height / 2f
    drawText(layout, topLeft = Offset(x, y))
}

private fun wheelColors(): List<Color> = listOf(
    if (XpColors.isDark) XpColors.AccentSoft else XpColors.AccentSoft,
    XpColors.InfoSoft,
    XpColors.PositiveSoft,
    XpColors.WarningSoft,
    XpColors.HighlightSoft,
)

@Composable
private fun WheelSwitcher(
    wheels: List<Wheel>,
    current: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf(wheels[current].name) }
    var confirmingDelete by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            wheels.forEachIndexed { index, wheel ->
                val on = index == current
                Text(
                    wheel.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) XpColors.OnAccent else XpColors.Ink,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(XpRadius.pill))
                        .background(if (on) XpColors.Accent else XpColors.Fill)
                        .pressableClick(haptic = false) { onSelect(index) }
                        .padding(horizontal = XpSpace.m, vertical = 6.dp),
                )
            }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(XpColors.Fill)
                    .pressableClick(haptic = false, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = tr("新建转盘", "New wheel"), tint = XpColors.InkSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(XpSpace.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
            Text(
                tr("重命名", "Rename"),
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.InkSecondary,
                modifier = Modifier.pressableClick(haptic = false) {
                    renameDraft = wheels[current].name
                    renaming = true
                }.padding(vertical = 4.dp),
            )
            Text(
                tr("删除这个盘", "Delete this wheel"),
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.Negative,
                modifier = Modifier.pressableClick(haptic = false) { confirmingDelete = true }.padding(vertical = 4.dp),
            )
        }
    }

    if (renaming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr("重命名转盘", "Rename wheel")) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onRename(renameDraft); renaming = false }) {
                    Text(tr("保存", "Save"))
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renaming = false }) { Text(tr("取消", "Cancel")) } },
        )
    }
    if (confirmingDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(tr("删除这个盘？", "Delete this wheel?")) },
            text = { Text(tr("盘面上的内容会一起删掉，其它盘不受影响。", "Its slices go with it. Your other wheels stay.")) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onDelete(); confirmingDelete = false }) {
                    Text(tr("删除", "Delete"), color = XpColors.Negative)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmingDelete = false }) { Text(tr("取消", "Cancel")) } },
        )
    }
}

@Composable
private fun SliceCountRow(count: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("格数", "Slices"), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, modifier = Modifier.weight(1f))
        Stepper(icon = Icons.Outlined.Remove, enabled = count > MinSlices, description = tr("少一格", "One fewer")) {
            onChange((count - 1).coerceIn(MinSlices, MaxSlices))
        }
        Text(
            "$count",
            style = MaterialTheme.typography.titleMedium,
            color = XpColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = XpSpace.m),
        )
        Stepper(icon = Icons.Outlined.Add, enabled = count < MaxSlices, description = tr("多一格", "One more")) {
            onChange((count + 1).coerceIn(MinSlices, MaxSlices))
        }
    }
}

@Composable
private fun Stepper(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(if (enabled) XpColors.Fill else XpColors.Fill.copy(alpha = 0.4f))
            .then(if (enabled) Modifier.pressableClick(haptic = false, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) XpColors.Ink else XpColors.InkTertiary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SliceRow(index: Int, value: String, onValue: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = XpColors.InkTertiary,
            modifier = Modifier.size(20.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            placeholder = { Text(tr("填这格", "Fill this slice"), style = MaterialTheme.typography.bodyMedium, color = XpColors.InkTertiary) },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = XpColors.Ink),
            shape = RoundedCornerShape(XpRadius.m),
        )
    }
}
