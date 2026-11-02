package com.xiaopiao.app.ui.games.wheel

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.tr
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 转盘：通用决策模块（tt 2026-10-30，重做 2026-11-02）。
 *
 * 人可以建多个盘，每个盘自己填每一格的文字；配置存在本机，重开还在。
 * 盘只做一件事——把人已经写好的选项随机指一个出来，不给建议、不预设答案。
 *
 * 这个文件只有**纯数据与纯几何**：画在哪、什么时候转、转多久，都由界面层决定。
 * 瞄准哪一格、命中判断、文案截断、数量夹取、存档编解码都能在 JVM 单测里直接跑。
 */

/** 存档位置：沿用旧版的 prefs 名与 key，旧版本存下的盘一个都不丢。 */
const val WheelPrefsName = "wheel-v1"
const val WheelPrefsKey = "wheels"

const val MinWheelSlices = 2
const val MaxWheelSlices = 12

/** 盘面上的字最多这么多个字，超出用省略号——扇面角度决定它能放下多少。 */
const val MaxWheelLabelChars = 8

/** 3.6–4.6s 的减速曲线：起步快、长尾自然收住、不冲过头（tt: 转动不自然）。 */
val WheelEase: Easing = CubicBezierEasing(0.12f, 0.75f, 0.18f, 1f)

/** One wheel. [slices] keeps blank entries on purpose: an empty wedge in the editor is a row waiting for text. */
data class Wheel(val id: String, val name: String, val slices: List<String>) {
    /** Only filled slices take part in a spin, and only they can win. */
    val live: List<String> get() = slices.map { it.trim() }.filter { it.isNotEmpty() }
}

fun newWheel(number: Int): Wheel = Wheel(
    id = "w" + System.currentTimeMillis().toString(36) + number,
    name = tr("转盘 $number", "Wheel $number"),
    slices = List(4) { "" },
)

/** 2..12, clamped — the editor and the stepper both go through here. */
fun clampSliceCount(count: Int): Int = count.coerceIn(MinWheelSlices, MaxWheelSlices)

/** Growing keeps existing text and appends blank rows; shrinking drops the tail. */
fun resizeSlices(slices: List<String>, count: Int): List<String> {
    val wanted = clampSliceCount(count)
    return if (wanted <= slices.size) slices.take(wanted) else slices + List(wanted - slices.size) { "" }
}

// ── geometry ─────────────────────────────────────────────────────────────────────────────────────────

/** One slice's angular width. */
fun sliceSweep(count: Int): Float = 360f / count.coerceAtLeast(1)

/**
 * The wheel-local angle sitting under the pointer (12 o'clock), measured **clockwise from the top**.
 *
 * The disc draws slice 0 starting at the top and running clockwise, so a rotation of `r` degrees (screen
 * clockwise) puts local angle `-r` under the pointer.
 */
fun angleUnderPointer(rotation: Float): Float = ((-rotation % 360f) + 360f) % 360f

/** Which slice sits under the pointer for a given wheel rotation. */
fun sliceUnder(rotation: Float, count: Int): Int {
    if (count <= 0) return 0
    return floor(angleUnderPointer(rotation) / sliceSweep(count)).toInt().coerceIn(0, count - 1)
}

/** Where slice [index] begins, in wheel-local degrees clockwise from the top. */
fun sliceStart(index: Int, count: Int): Float = index * sliceSweep(count)

/** The widest safe ring inside a slice: never closer than [marginDegrees] to either separator. */
fun landingMargin(count: Int, marginDegrees: Float = 2.5f): Float = min(sliceSweep(count) * 0.12f, marginDegrees)

/**
 * The rotation to animate to so that [winner] is under the pointer.
 *
 * The winner is decided **before** the animation (not read off the stop position), then the angle is built
 * from it: at least [turns] full turns past [from], landing on a random point strictly inside the slice and
 * never on a separator. Always forwards, so the wheel never looks like it reconsiders.
 */
fun landingRotation(
    from: Float,
    winner: Int,
    count: Int,
    random: Random = Random,
    turns: Int = 4,
): Float {
    val sweep = sliceSweep(count)
    val margin = landingMargin(count)
    val low = winner * sweep + margin
    val high = (winner + 1) * sweep - margin
    val local = low + random.nextFloat() * (high - low)
    val base = from + turns * 360f
    val delta = (((-local - base) % 360f) + 360f) % 360f
    return base + delta
}

/** A flick spins further: 4 turns from a tap, up to 8 with a hard throw. */
fun spinTurns(flingVelocity: Float): Int = 4 + (abs(flingVelocity) / 900f).roundToInt().coerceIn(0, 4)

/** Real wall-clock length of the spin, inside the 3.6–4.6s band the design asks for. */
fun spinDurationMs(turns: Int): Int = (3400 + turns * 180).coerceIn(3600, 4600)

/** The pointer ticks at most this often, so a fast spin buzzes instead of rattling. */
fun shouldTick(lastTickMs: Long, nowMs: Long, intervalMs: Long = 45L): Boolean = nowMs - lastTickMs >= intervalMs

/** Disc labels: trimmed, at most [MaxWheelLabelChars] characters, ellipsis instead of clipping at the rim. */
fun shortLabel(text: String): String {
    val trimmed = text.trim()
    if (trimmed.length <= MaxWheelLabelChars) return trimmed
    return trimmed.take(MaxWheelLabelChars - 1).trimEnd() + "…"
}

/** Fewer slices → larger type, so a 2-way wheel is not two tiny words on a big disc. */
fun labelSizeSp(count: Int): Float = when {
    count <= 4 -> 15f
    count <= 6 -> 13.5f
    count <= 8 -> 12f
    count <= 10 -> 11f
    else -> 10f
}

/** Studs on the rim: two per slice, kept inside the 12..24 band. */
fun studCount(count: Int): Int = (count * 2).coerceIn(12, 24)

// ── storage ──────────────────────────────────────────────────────────────────────────────────────────

/**
 * The on-disk shape is frozen: `[{id,name,slices:[String]}]` under `wheel-v1/wheels`. Wheels written by the
 * previous version keep loading, and an older build can still read what this one writes.
 *
 * Decoding runs on the JVM in tests (no Android JSONObject), which is why it is kotlinx-serialization.
 */
object WheelCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class StoredWheel(
        val id: String = "",
        val name: String = "",
        val slices: List<String> = emptyList(),
    )

    fun encode(wheels: List<Wheel>): String =
        json.encodeToString(wheels.map { StoredWheel(id = it.id, name = it.name, slices = it.slices) })

    fun decode(raw: String?): List<Wheel> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredWheel>>(raw)
                .filter { it.id.isNotBlank() }
                .map { Wheel(it.id, it.name.ifBlank { it.id }, it.slices) }
        }.getOrDefault(emptyList())
    }
}

/** Thin prefs wrapper; all the shape knowledge lives in [WheelCodec]. */
class WheelStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(WheelPrefsName, Context.MODE_PRIVATE)

    fun load(): List<Wheel> = WheelCodec.decode(prefs.getString(WheelPrefsKey, null))

    fun save(wheels: List<Wheel>) {
        prefs.edit().putString(WheelPrefsKey, WheelCodec.encode(wheels)).apply()
    }
}

// ── presets ─────────────────────────────────────────────────────────────────────────────────────────

/**
 * 四个空盘起手：只放通用选项，不出现任何品牌、不预填用户的消费。
 * 点一下就是一张新盘（不覆盖已有的），之后随便改。
 */
data class WheelPreset(val zh: String, val en: String, val options: List<Pair<String, String>>) {
    fun title(): String = tr(zh, en)
    fun slices(): List<String> = options.map { tr(it.first, it.second) }
}

object WheelPresets {
    val all: List<WheelPreset> = listOf(
        WheelPreset(
            zh = "吃什么", en = "What to eat",
            options = listOf(
                "面条" to "Noodles", "米饭" to "Rice", "饺子" to "Dumplings",
                "火锅" to "Hot pot", "沙拉" to "Salad", "随便" to "Anything",
            ),
        ),
        WheelPreset(
            zh = "谁买单", en = "Who pays",
            options = listOf("我" to "Me", "你" to "You", "平分" to "Split", "赢的人" to "Winner"),
        ),
        WheelPreset(
            zh = "周末去哪", en = "Weekend plan",
            options = listOf(
                "在家躺着" to "Stay in", "公园" to "Park", "书店" to "Bookshop",
                "看场电影" to "A film", "逛菜市场" to "Market", "睡到中午" to "Sleep in",
            ),
        ),
        WheelPreset(
            zh = "是或否", en = "Yes or no",
            options = listOf("是" to "Yes", "否" to "No"),
        ),
    )

    /** A brand-new wheel filled from a preset, named after it. */
    fun wheelFor(preset: WheelPreset, number: Int): Wheel = Wheel(
        id = "w" + System.currentTimeMillis().toString(36) + number,
        name = preset.title(),
        slices = preset.slices(),
    )
}

// ── palette ─────────────────────────────────────────────────────────────────────────────────────────

/**
 * The slice palette. This is the **only** place in the wheel that names colours directly: six calm tones
 * derived from the active theme, alternating so two neighbouring wedges never share one (6 tones ≥ any
 * run of adjacent slices, and 12 slices wrap back to a different tone).
 *
 * Text on every slice is [XpColors.Ink], so the mix stays near the soft end of each tone in both modes.
 */
@Composable
fun wheelPalette(): List<Color> {
    val mix = if (XpColors.isDark) 0.18f else 0.10f
    return listOf(
        lerp(XpColors.AccentSoft, XpColors.Accent, mix),
        lerp(XpColors.InfoSoft, XpColors.Info, mix),
        lerp(XpColors.PositiveSoft, XpColors.Positive, mix),
        lerp(XpColors.WarningSoft, XpColors.Warning, mix),
        lerp(XpColors.HighlightSoft, XpColors.Highlight, mix),
        lerp(XpColors.AccentSoft, XpColors.Info, mix * 0.9f),
    )
}

fun sliceColor(palette: List<Color>, index: Int): Color =
    if (palette.isEmpty()) Color.Transparent else palette[index % palette.size]

/** One line of spin history under the wheel. */
data class WheelHistoryEntry(val text: String, val spin: Int)
