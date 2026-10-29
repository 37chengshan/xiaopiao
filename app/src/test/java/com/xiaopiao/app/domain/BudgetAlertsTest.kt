package com.xiaopiao.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BudgetAlertsTest {
    private val originalTz: TimeZone = TimeZone.getDefault()

    @After fun restoreTimeZone() = TimeZone.setDefault(originalTz)

    private fun at(y: Int, m: Int, d: Int, h: Int = 15): Long = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d, h, 0, 0)
    }.timeInMillis

    private fun txn(cents: Long, at: Long) = Txn(
        amountCents = cents, direction = Direction.OUT, merchant = "x", category = Category.MEAL,
        occurredAt = at, source = Source.NOTIFICATION,
    )

    /** 时间必须在 setDefault 之后算，否则默认时区一换，窗口就跑到别的日子去了。 */
    private fun now() = at(2026, 10, 28)
    private val settings = BudgetSettings(dailyLimit = 10_000)

    @Test fun eightyPercentFiresExactlyOnce() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        val txns = listOf(txn(8_200, at(2026, 10, 28, 9)))
        val first = BudgetAlerts.pending(settings, txns, now(), notified = emptySet())
        assertEquals(1, first.size)
        assertEquals("日预算已用 82%", first.single().title)
        assertEquals("今天已花 ¥82，日预算 ¥100", first.single().body)

        val again = BudgetAlerts.pending(settings, txns, now(), notified = first.single().keysToMark)
        assertTrue(again.isEmpty())
    }

    @Test fun crossingStraightToOneHundredOnlySendsTheHigherAlert() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        val txns = listOf(txn(12_000, at(2026, 10, 28, 9)))
        val alerts = BudgetAlerts.pending(settings, txns, now(), notified = emptySet())
        assertEquals(1, alerts.size)
        assertEquals("日预算已用完", alerts.single().title)
        assertEquals("今天已花 ¥120，日预算 ¥100，超出 ¥20", alerts.single().body)
        // The 80% key is marked too, so it cannot arrive later.
        assertEquals(2, alerts.single().keysToMark.size)

        assertTrue(BudgetAlerts.pending(settings, txns, now(), notified = alerts.single().keysToMark).isEmpty())
    }

    @Test fun aNewDayGetsANewReminder() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        val txns = listOf(txn(12_000, at(2026, 10, 28, 9)), txn(12_000, at(2026, 10, 29, 9)))
        val first = BudgetAlerts.pending(settings, txns, now(), emptySet()).single()
        val nextDay = at(2026, 10, 29)
        val second = BudgetAlerts.pending(settings, txns, nextDay, first.keysToMark)
        assertEquals(1, second.size)
        assertFalse(first.key == second.single().key)
    }

    @Test fun unsetOrUnreachedPeriodsStaySilent() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        val txns = listOf(txn(5_000, at(2026, 10, 28, 9)))
        assertTrue(BudgetAlerts.pending(BudgetSettings(), txns, now(), emptySet()).isEmpty())
        assertTrue(BudgetAlerts.pending(settings, txns, now(), emptySet()).isEmpty())
    }

    @Test fun eachPeriodHasItsOwnWindowAndWording() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        val cfg = BudgetSettings(dailyLimit = 10_000, weeklyLimit = 20_000, monthlyLimit = 100_000)
        // One 25k spend on Wednesday: day over, week over, month at 25%.
        val txns = listOf(txn(25_000, at(2026, 10, 28, 9)))
        val alerts = BudgetAlerts.pending(cfg, txns, now(), emptySet())
        assertEquals(listOf("日预算已用完", "周预算已用完"), alerts.map { it.title })
        assertTrue(alerts[1].body.startsWith("本周已花 ¥250"))
    }
}
