package com.xiaopiao.app.work

import com.xiaopiao.app.insights.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimesTest {
    @Test fun dailyIsTodayWhenStillAhead() =
        assertEquals(at(2026, 10, 27, 21, 30), ReminderTimes.nextDailyReceipt(at(2026, 10, 27, 9)))

    @Test fun dailyRollsToTomorrowWhenPassedOrExactlyNow() {
        assertEquals(at(2026, 10, 28, 21, 30), ReminderTimes.nextDailyReceipt(at(2026, 10, 27, 22)))
        assertEquals(at(2026, 10, 28, 21, 30), ReminderTimes.nextDailyReceipt(at(2026, 10, 27, 21, 30)))
    }

    @Test fun weeklyLandsOnSundayEight() {
        // 2026-10-27 is a Tuesday -> Sunday is 11-01
        assertEquals(at(2026, 11, 1, 20, 0), ReminderTimes.nextWeeklyAwards(at(2026, 10, 27, 9)))
        // Sunday before 20:00 -> same day
        assertEquals(at(2026, 11, 1, 20, 0), ReminderTimes.nextWeeklyAwards(at(2026, 11, 1, 19)))
        // Sunday after 20:00 -> next week
        assertEquals(at(2026, 11, 8, 20, 0), ReminderTimes.nextWeeklyAwards(at(2026, 11, 1, 21)))
    }

    @Test fun subscriptionCheckIsMorning() =
        assertEquals(at(2026, 10, 28, 10), ReminderTimes.nextSubscriptionCheck(at(2026, 10, 27, 10, 0)))

    @Test fun chargeReminderFiresExactlyThreeDaysBefore() {
        val now = at(2026, 10, 27, 10)
        assertTrue(ReminderTimes.isChargeReminderDay(at(2026, 10, 30, 1), now))
        assertFalse(ReminderTimes.isChargeReminderDay(at(2026, 10, 29, 23), now))
        assertFalse(ReminderTimes.isChargeReminderDay(at(2026, 10, 31, 1), now))
    }
}
