package com.xiaopiao.app.ui.today

import kotlin.math.roundToLong

/** When one printed [ReceiptLine] starts emerging from the slot, how long it takes, and where it sits on the paper (dp). */
data class PrintStep(
    val index: Int,
    val kind: LineKind,
    val startMs: Long,
    val durationMs: Long,
    val topDp: Float,
    val heightDp: Float,
) {
    val endMs: Long get() = startMs + durationMs
    val ticks: Boolean get() = kind != LineKind.BLANK && kind != LineKind.DASH && kind != LineKind.STAMP_SPACE
}

/**
 * Pure schedule for the thermal print. The paper feeds at a constant [SPEED_DP_PER_S] (the one linear motion in
 * the app); only the total and the stamp area slow the feed so the NumberTicker and the stamp have room.
 * With [reduced] motion every step collapses to t=0 so the receipt appears in its final state.
 */
class PrintTimeline private constructor(
    val steps: List<PrintStep>,
    val feedEndMs: Long,
    val stampStartMs: Long,
    val totalMs: Long,
    val totalHeightDp: Float,
) {
    /** Paper length (dp) that has been fed out at time [t]. */
    fun revealedHeightDp(t: Long): Float {
        var h = 0f
        for (s in steps) {
            if (t < s.startMs) break
            h = s.topDp + s.heightDp * progress(s, t)
        }
        return h
    }

    /** 0..1: how much of [s] has come out of the slot at [t]. */
    fun progress(s: PrintStep, t: Long): Float =
        if (s.durationMs <= 0L) (if (t >= s.startMs) 1f else 0f) else ((t - s.startMs).toFloat() / s.durationMs).coerceIn(0f, 1f)

    /** Index of the step currently printing at [t], or -1 before the first. */
    fun activeIndex(t: Long): Int = steps.indexOfLast { t >= it.startMs }

    /** Start times of printing lines, thinned so consecutive entries are at least [minGapMs] apart (haptic + sound beats). */
    fun tickTimes(minGapMs: Long): List<Long> {
        val out = mutableListOf<Long>()
        for (s in steps) {
            if (!s.ticks) continue
            if (out.isEmpty() || s.startMs - out.last() >= minGapMs) out += s.startMs
        }
        return out
    }

    companion object {
        const val SPEED_DP_PER_S = 230f
        private const val STAMP_DELAY_MS = 220L
        const val SLAM_MS = 700L

        /** Kinds that deliberately feed slower than the standard speed. */
        private fun minDuration(kind: LineKind): Long = when (kind) {
            LineKind.TOTAL -> 760
            LineKind.BIG -> 420
            LineKind.STATS -> 160
            LineKind.STAMP_SPACE -> 220
            else -> 0
        }

        fun durationFor(line: ReceiptLine): Long =
            maxOf((line.kind.heightDp / SPEED_DP_PER_S * 1000f).roundToLong(), minDuration(line.kind))

        fun build(lines: List<ReceiptLine>, reduced: Boolean = false): PrintTimeline {
            var t = 0L
            var y = 0f
            val steps = lines.mapIndexed { i, line ->
                val d = if (reduced) 0L else durationFor(line)
                val s = PrintStep(i, line.kind, t, d, y, line.kind.heightDp)
                t += d
                y += line.kind.heightDp
                s
            }
            val feedEnd = steps.lastOrNull()?.endMs ?: 0L
            val stampStart = if (reduced) 0L else feedEnd + STAMP_DELAY_MS
            val total = if (reduced) 0L else stampStart + SLAM_MS
            return PrintTimeline(steps, feedEnd, stampStart, total, y)
        }
    }
}
