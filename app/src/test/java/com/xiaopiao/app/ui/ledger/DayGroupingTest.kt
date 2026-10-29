package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Day cards group by calendar day and carry both subtotals (task A.3, docs/APP-DESIGN.md §3.2). */
class DayGroupingTest {

    private val now = at(2026, 10, 28, 21, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun txn(cents: Long, direction: Direction, at: Long) = Txn(
        amountCents = cents,
        direction = direction,
        merchant = "m",
        category = Category.MEAL,
        occurredAt = at,
        source = Source.MANUAL,
    )

    private val txns = listOf(
        txn(1850, Direction.OUT, at(2026, 10, 28, 9, 10)),
        txn(5000, Direction.IN, at(2026, 10, 28, 12, 0)),
        txn(1490, Direction.OUT, at(2026, 10, 27, 19, 40)),
        txn(200, Direction.OUT, at(2026, 10, 26, 23, 5)),
        txn(300, Direction.OUT, at(2026, 10, 20, 8, 0)),
    )

    @Test
    fun `groups one card per day, newest first`() {
        val groups = LedgerGrouping.group(txns, now)
        assertEquals(4, groups.size)
        val expected = listOf(
            startOfDay(at(2026, 10, 28, 12, 0)),
            startOfDay(at(2026, 10, 27, 12, 0)),
            startOfDay(at(2026, 10, 26, 12, 0)),
            startOfDay(at(2026, 10, 20, 12, 0)),
        )
        assertEquals(expected, groups.map { it.dayStart })
    }

    @Test
    fun `each day carries its own out and in subtotals`() {
        val groups = LedgerGrouping.group(txns, now)
        assertEquals(1850, groups[0].outCents)
        assertEquals(5000, groups[0].inCents)
        assertEquals(2, groups[0].items.size)
        assertEquals(1490, groups[1].outCents)
        assertEquals(0, groups[1].inCents)
        assertEquals(300, groups[3].outCents)
        assertEquals(0, groups[3].inCents)
    }

    @Test
    fun `header keeps the date first and the relative word second`() {
        val groups = LedgerGrouping.group(txns, now)
        val date = { day: Long -> LedgerGrouping.shortDate(day).substringBefore(' ') }
        assertEquals("${date(groups[0].dayStart)} 今天", groups[0].header)
        assertEquals("${date(groups[1].dayStart)} 昨天", groups[1].header)
        assertTrue(groups[2].header.startsWith(date(groups[2].dayStart)))
        assertTrue(
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                .any { groups[2].header.endsWith(it) },
        )
        // Older than a week: the date plus the weekday, never 「零消费」 or a missing suffix.
        assertEquals("${date(groups[3].dayStart)} 周二", groups[3].header)
    }

    @Test
    fun `row clock is HH mm`() {
        assertEquals("09:10", LedgerGrouping.clock(at(2026, 10, 28, 9, 10)))
        assertEquals("23:05", LedgerGrouping.clock(at(2026, 10, 26, 23, 5)))
    }

    @Test
    fun `summary counts today once and the month in both directions`() {
        val summary = LedgerTotals.summary(txns, now)
        assertEquals(1850, summary.todayOutCents)
        assertEquals(1850 + 1490 + 200 + 300, summary.monthOutCents)
        assertEquals(5000, summary.monthInCents)
    }
}
