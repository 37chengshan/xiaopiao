package com.xiaopiao.app.ui.today

import kotlin.math.ceil

/** When one printed [ReceiptLine] starts, how long it takes, and where it sits on the paper (dp). */
data class PrintStep(
    val index: Int,
    val kind: LineKind,
    val textLength: Int,
    val startMs: Long,
    val durationMs: Long,
    val topDp: Float,
    val heightDp: Float,
) {
    val endMs: Long get() = startMs + durationMs
    val ticks: Boolean get() = kind != LineKind.BLANK && kind != LineKind.DASH && kind != LineKind.STAMP_SPACE
}

/**
 * Pure schedule for the thermal print: each line first feeds out ([FEED_MS]) then its text types in.
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
            h = s.topDp + s.heightDp * feedProgress(s, t)
        }
        return h
    }

    fun feedProgress(s: PrintStep, t: Long): Float {
        val feed = minOf(FEED_MS, s.durationMs)
        return if (feed <= 0L) (if (t >= s.startMs) 1f else 0f) else ((t - s.startMs).toFloat() / feed).coerceIn(0f, 1f)
    }

    fun textProgress(s: PrintStep, t: Long): Float =
        if (s.durationMs <= 0L) (if (t >= s.startMs) 1f else 0f) else ((t - s.startMs).toFloat() / s.durationMs).coerceIn(0f, 1f)

    /** Index of the step currently printing at [t], or -1 before the first. */
    fun activeIndex(t: Long): Int = steps.indexOfLast { t >= it.startMs }

    companion object {
        const val FEED_MS = 140L
        private const val GAP_MS = 45L
        private const val STAMP_DELAY_MS = 260L
        private const val SLAM_MS = 900L

        fun typedChars(length: Int, progress: Float): Int =
            if (progress >= 1f) length else ceil(length * progress).toInt().coerceIn(0, length)

        fun durationFor(line: ReceiptLine): Long = when (line.kind) {
            LineKind.BLANK -> 100
            LineKind.TITLE -> 420
            LineKind.SUBTITLE -> 260
            LineKind.DEMO -> 220
            LineKind.DASH -> 120
            LineKind.ITEM -> 300
            LineKind.MORE -> 220
            LineKind.TOTAL -> 1000
            LineKind.INCOME -> 220
            LineKind.COMMENT -> (line.left.length * 38L).coerceIn(320L, 1200L)
            LineKind.BIG -> 600
            LineKind.STAMP_SPACE -> 260
            LineKind.FOOTER -> 360
            LineKind.BARCODE -> 500
        }

        fun build(lines: List<ReceiptLine>, reduced: Boolean = false): PrintTimeline {
            var t = 0L
            var y = 0f
            val steps = lines.mapIndexed { i, line ->
                val d = if (reduced) 0L else durationFor(line)
                val s = PrintStep(i, line.kind, line.left.codePointCount(0, line.left.length), t, d, y, line.kind.heightDp)
                if (!reduced) t += d + if (line.kind == LineKind.DASH) 0L else GAP_MS
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
