package com.xiaopiao.app.merchant

import kotlin.random.Random

/**
 * SYNTHETIC revenue book used until the owner records their own weeks. Every row keeps
 * [RevenueSource.SEED], so screens can label it and a real entry for the same day simply replaces it.
 */
object DemoRevenue {
    const val SEED = 20261028L
    const val DAYS = 42

    /** Day-start rows ending today (today included, so the 今日 screen has a number to amend). */
    fun generate(todayStart: Long, days: Int = DAYS): List<RevenueEntry> {
        val rnd = Random(SEED)
        // Two closed days in the recent past make gaps visible in the chart and the month totals.
        val closed = setOf(days - 9, days - 23)
        return (0 until days).mapNotNull { offset ->
            if (offset in closed) return@mapNotNull null
            val day = RevenueBook.plusDays(todayStart, offset - (days - 1))
            val weekday = RevenueBook.weekdayIndex(day)
            val factor = when (weekday) {
                0 -> 0.92; 1 -> 0.84; 2 -> 0.86; 3 -> 0.97; 4 -> 1.12; 5 -> 1.38; else -> 1.24
            }
            val noise = 0.90 + rnd.nextDouble() * 0.20
            val revenue = (2_650_00 * factor * noise).toLong().roundToYuan()
            val ticket = 2_800 + rnd.nextInt(900) // ¥28–36.99
            RevenueEntry(
                dayStart = day,
                revenueCents = revenue,
                orders = (revenue / ticket).toInt().coerceAtLeast(1),
                source = RevenueSource.SEED,
            )
        }
    }

    private fun Long.roundToYuan(): Long = (this / 100) * 100
}
