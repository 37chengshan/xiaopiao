package com.xiaopiao.app.ui.record

import com.xiaopiao.app.domain.CHANNEL_MANUAL
import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.CategoryHue
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** The record sheet's defaults (task B.5): today + the current clock, and the row it writes. */
class RecordDefaultsTest {

    private val now = Calendar.getInstance().apply {
        clear()
        set(2026, 9, 28, 19, 14, 0)
    }.timeInMillis

    @Test
    fun `date pill defaults to today`() {
        assertEquals("今天", dayLabelOf(startOfDay(now), now))
    }

    @Test
    fun `date pill names yesterday and the day before`() {
        assertEquals("昨天", dayLabelOf(dayOffset(now, 1), now))
        assertEquals("前天", dayLabelOf(dayOffset(now, 2), now))
        assertTrue(dayLabelOf(dayOffset(now, 9), now).endsWith("日"))
    }

    @Test
    fun `time pill defaults to the current clock and keeps it on another day`() {
        val at = atDayAndTime(startOfDay(now), 19, 14)
        val calendar = Calendar.getInstance().apply { timeInMillis = at }
        assertEquals(19, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(14, calendar.get(Calendar.MINUTE))

        val yesterday = atDayAndTime(dayOffset(now, 1), 8, 5)
        val y = Calendar.getInstance().apply { timeInMillis = yesterday }
        assertEquals(8, y.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, y.get(Calendar.MINUTE))
        assertTrue(yesterday < startOfDay(now))
    }

    @Test
    fun `composer stores the category id, the manual channel and the category name`() {
        val def = CategoryDefaults.custom("猫粮", "pets", CategoryHue.PINK, Direction.OUT, now = 1L, sortOrder = 20)
        val txn = RecordComposer.txn(
            draft = RecordDraft(amount = "128.5"),
            direction = Direction.OUT,
            category = def,
            merchant = "  ",
            at = now,
        )
        assertEquals(12850, txn!!.amountCents)
        assertEquals(def.id, txn.categoryId)
        assertEquals(CHANNEL_MANUAL, txn.channel)
        assertEquals(Source.MANUAL, txn.source)
        assertEquals("猫粮", txn.merchant)
    }

    @Test
    fun `composer refuses an empty amount`() {
        val def = CategoryDefaults.find("meal")!!
        assertNull(RecordComposer.txn(RecordDraft(), Direction.OUT, def, "", at = now))
    }

    private fun dayOffset(from: Long, days: Int): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = from
            add(Calendar.DAY_OF_MONTH, -days)
        }.timeInMillis,
    )
}
