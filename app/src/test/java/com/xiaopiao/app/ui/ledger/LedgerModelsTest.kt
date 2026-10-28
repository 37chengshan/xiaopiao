package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.capture.QuickDraft
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerModelsTest {
    private val now = startOfDay(System.currentTimeMillis()) + 15 * 3_600_000L

    private fun txn(cents: Long, at: Long, dir: Direction = Direction.OUT) = Txn(
        amountCents = cents, direction = dir, merchant = "x", category = Category.OTHER,
        occurredAt = at, source = Source.MANUAL,
    )

    @Test fun groupsByDayNewestFirstWithTotals() {
        val groups = LedgerGrouping.group(
            listOf(
                txn(1500, now), txn(2500, now - 3_600_000L),
                txn(800000, now, Direction.IN),
                txn(900, now - DAY_MS),
            ),
            now,
        )
        assertEquals(listOf("今天", "昨天"), groups.map { it.label })
        assertEquals(4000L, groups[0].outCents)
        assertEquals(800000L, groups[0].inCents)
        assertEquals(3, groups[0].items.size)
        assertTrue(groups[0].items.first().occurredAt >= groups[0].items.last().occurredAt)
    }

    @Test fun olderDaysUseDateLabel() {
        val label = LedgerGrouping.dayLabel(startOfDay(now - 10 * DAY_MS), now)
        assertTrue(label.contains("月") && label.contains("周"))
    }

    @Test fun emptyLedgerHasNoGroups() = assertTrue(LedgerGrouping.group(emptyList(), now).isEmpty())

    @Test fun draftYesterdayLandsOnYesterday() {
        val t = QuickDraft(1800, "打车", Category.TRANSPORT, -1, Direction.OUT).toTxn(now)
        assertEquals("昨天", LedgerGrouping.dayLabel(startOfDay(t.occurredAt), now))
        assertEquals(Source.QUICK_ADD, t.source)
    }

    @Test fun draftTodayKeepsNow() {
        assertEquals(now, QuickDraft(100, "x", Category.OTHER, 0, Direction.OUT).toTxn(now).occurredAt)
    }

    @Test fun moveToDayKeepsClock() {
        val moved = moveToDay(now, startOfDay(now - DAY_MS))
        assertEquals(LedgerGrouping.clock(now), LedgerGrouping.clock(moved))
        assertEquals("昨天", LedgerGrouping.dayLabel(startOfDay(moved), now))
    }

    @Test fun keypadBuildsAmounts() {
        fun type(vararg keys: String) = keys.fold("") { acc, k -> AmountEntry.press(acc, k) }
        assertEquals(3850L, AmountEntry.toCents(type("3", "8", ".", "5")))
        assertEquals("0.", type("."))
        assertEquals("5", type("0", "5"))
        assertEquals("12.34", type("1", "2", ".", "3", "4", "5"))
        assertEquals("1", type("1", "2", AmountEntry.BACKSPACE))
        assertEquals("1.5", type("1", ".", ".", "5"))
        assertEquals(500L, AmountEntry.toCents("5."))
        assertEquals(0L, AmountEntry.toCents(""))
        assertEquals("0", AmountEntry.display(""))
    }

    @Test fun keypadCapsIntegerDigits() {
        assertEquals("1234567", "123456789".fold("") { acc, k -> AmountEntry.press(acc, k.toString()) })
    }

    @Test fun fromCentsRoundTrips() {
        assertEquals("32", AmountEntry.fromCents(3200))
        assertEquals("38.5", AmountEntry.fromCents(3850))
        assertEquals("0.05", AmountEntry.fromCents(5))
        assertEquals(3850L, AmountEntry.toCents(AmountEntry.fromCents(3850)))
    }
}
