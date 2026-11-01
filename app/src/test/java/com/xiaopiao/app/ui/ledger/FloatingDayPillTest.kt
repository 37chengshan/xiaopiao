package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The floating day pill used to read the day straight from the list key that crossed the top bar. Once a card became
 * one list item per bill (2026-10-31 perf work) the item at the bar is usually a row, so the pill disappeared
 * entirely — a user-visible regression. These cases pin both key shapes.
 */
class FloatingDayPillTest {

    private val day1 = DayGroup(
        dayStart = 1_000L,
        header = "10/31 今日",
        items = listOf(txn(11L), txn(12L)),
        outCents = 5_000,
        inCents = 0,
    )

    private val day2 = DayGroup(
        dayStart = 2_000L,
        header = "10/30 昨天",
        items = listOf(txn(21L)),
        outCents = 1_000,
        inCents = 0,
    )

    private val groups = listOf(day1, day2)
    private val byDayStart = groups.associateBy { it.dayStart }
    private val dayOfTxn = groups
        .flatMap { g -> g.items.map { it.id to g.dayStart } }
        .toMap()

    @Test
    fun `a row under the bar names its own day`() {
        val shown = dayAtBar(
            key = "txn-12",
            itemOffsetPx = 0f,
            linePx = 200f,
            headerPx = 44f,
            byDayStart = byDayStart,
            dayOfTxn = dayOfTxn,
        )
        assertEquals(day1, shown)
    }

    @Test
    fun `a row of the next day names the next day`() {
        val shown = dayAtBar(
            key = "txn-21",
            itemOffsetPx = 0f,
            linePx = 200f,
            headerPx = 44f,
            byDayStart = byDayStart,
            dayOfTxn = dayOfTxn,
        )
        assertEquals(day2, shown)
    }

    @Test
    fun `a header still below the bar keeps the pill hidden`() {
        val shown = dayAtBar(
            key = "day-1000",
            itemOffsetPx = 180f,
            linePx = 200f,
            headerPx = 44f,
            byDayStart = byDayStart,
            dayOfTxn = dayOfTxn,
        )
        assertNull(shown)
    }

    @Test
    fun `a header scrolled under the bar names its day`() {
        val shown = dayAtBar(
            key = "day-1000",
            itemOffsetPx = 100f,
            linePx = 200f,
            headerPx = 44f,
            byDayStart = byDayStart,
            dayOfTxn = dayOfTxn,
        )
        assertEquals(day1, shown)
    }

    @Test
    fun `summary and empty placeholders name nothing`() {
        listOf("summary", "empty", null).forEach { key ->
            assertNull(
                dayAtBar(
                    key = key,
                    itemOffsetPx = 0f,
                    linePx = 200f,
                    headerPx = 44f,
                    byDayStart = byDayStart,
                    dayOfTxn = dayOfTxn,
                ),
            )
        }
    }

    @Test
    fun `an unknown row id names nothing`() {
        assertNull(
            dayAtBar(
                key = "txn-999",
                itemOffsetPx = 0f,
                linePx = 200f,
                headerPx = 44f,
                byDayStart = byDayStart,
                dayOfTxn = dayOfTxn,
            ),
        )
    }

    private fun txn(id: Long) = Txn(
        id = id,
        amountCents = 5_000,
        direction = Direction.OUT,
        merchant = "吃饭",
        category = Category.MEAL,
        occurredAt = 1_700_000_000_000L,
        source = Source.MANUAL,
    )
}
