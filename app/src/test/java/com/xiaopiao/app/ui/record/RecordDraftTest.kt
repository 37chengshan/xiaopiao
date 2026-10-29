package com.xiaopiao.app.ui.record

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecordDraftTest {

    private fun type(vararg keys: String): RecordDraft {
        var draft = RecordDraft()
        keys.forEach { draft = RecordDraftMath.press(draft, it) }
        return draft
    }

    @Test
    fun digitsBuildTheAmount() {
        val draft = type("1", "2", ".", "5")
        assertEquals("12.5", draft.amount)
        assertEquals("12.5", draft.expression)
        assertEquals(1250L, RecordDraftMath.cents(draft))
    }

    @Test
    fun plusStartsATwoTermSum() {
        val draft = type("1", "2", RecordDraftMath.PLUS)
        assertEquals("12", draft.left)
        assertEquals('+', draft.op)
        assertEquals("", draft.amount)
        assertEquals("12＋", draft.expression)
        // The unfinished term counts as 0, so the big number still reads as a real amount.
        assertEquals(1200L, RecordDraftMath.cents(draft))
    }

    @Test
    fun plusAddsTheSecondTerm() {
        val draft = type("1", "2", RecordDraftMath.PLUS, "8")
        assertEquals("12＋8", draft.expression)
        assertEquals(2000L, RecordDraftMath.cents(draft))
    }

    @Test
    fun chainedOperatorsFoldTheFinishedTerm() {
        val draft = type("1", "2", RecordDraftMath.PLUS, "8", RecordDraftMath.PLUS, "5")
        assertEquals("20＋5", draft.expression)
        assertEquals(2500L, RecordDraftMath.cents(draft))
    }

    @Test
    fun minusSubtractsAndNeverGoesBelowZero() {
        val subtract = type("5", RecordDraftMath.MINUS, "2")
        assertEquals("5−2", subtract.expression)
        assertEquals(300L, RecordDraftMath.cents(subtract))

        val floored = type("3", RecordDraftMath.MINUS, "5")
        assertEquals(0L, RecordDraftMath.cents(floored))
    }

    @Test
    fun backspaceOnAnEmptyTermCancelsTheOperator() {
        val draft = type("1", "2", RecordDraftMath.PLUS, RecordAmount.BACKSPACE)
        assertEquals(null, draft.op)
        assertEquals(null, draft.left)
        assertEquals("12", draft.amount)
        assertEquals(1200L, RecordDraftMath.cents(draft))
    }

    @Test
    fun backspaceOnlyEditsTheCurrentTerm() {
        val draft = type("1", "2", RecordDraftMath.PLUS, "8", "0", RecordAmount.BACKSPACE)
        assertEquals("12＋8", draft.expression)
        assertEquals(2000L, RecordDraftMath.cents(draft))
    }

    @Test
    fun aDecimalTermFoldsBackIntoATypableString() {
        val draft = type("1", "2", ".", "5", RecordDraftMath.PLUS, "2", RecordDraftMath.PLUS)
        assertEquals("14.5＋", draft.expression)
        assertEquals(1450L, RecordDraftMath.cents(draft))
    }

    @Test
    fun composerRefusesZeroAndBuildsTheTxn() {
        assertNull(RecordComposer.txn(RecordDraft(), Direction.OUT, Category.MEAL, "", at = 1_000L))
        assertNull(
            RecordComposer.txn(
                type("3", RecordDraftMath.MINUS, "3"), Direction.OUT, Category.MEAL, "", at = 1_000L,
            ),
        )
        val txn = RecordComposer.txn(
            type("1", "2", RecordDraftMath.PLUS, "8"), Direction.IN, Category.SOCIAL, "工资", at = 1_000L,
        )
        assertNotNull(txn)
        assertEquals(2000L, txn!!.amountCents)
        assertEquals(Direction.IN, txn.direction)
        assertEquals("工资", txn.merchant)
    }

    @Test
    fun saveAndAgainStartsACleanDraftButKeepsTheCategory() {
        // 再记一笔: the pane keeps its category/direction and drops the money + note.
        val first = type("8", RecordDraftMath.PLUS, "2")
        val firstTxn = RecordComposer.txn(first, Direction.OUT, Category.DRINK, "奶茶", at = 1_000L)
        assertEquals(1000L, firstTxn!!.amountCents)

        val cleared = RecordDraft()
        assertEquals(0L, RecordDraftMath.cents(cleared))
        assertEquals("0", cleared.expression)
        assertNull(RecordComposer.txn(cleared, Direction.OUT, Category.DRINK, "", at = 2_000L))

        val second = RecordDraftMath.press(cleared, "6")
        val secondTxn = RecordComposer.txn(second, Direction.OUT, Category.DRINK, "", at = 2_000L)
        assertEquals(600L, secondTxn!!.amountCents)
        // The note was cleared with the draft, so the merchant falls back to the category label.
        assertEquals(Category.DRINK.label, secondTxn.merchant)
    }

    @Test
    fun dayOffsetKeepsTodayAndSnapsEarlierDaysToMidnight() {
        val now = 1_700_000_600_000L
        assertEquals(now, offsetDayStart(now, 0))
        val twoDays = offsetDayStart(now, 2)
        assertEquals(twoDays, com.xiaopiao.app.domain.startOfDay(twoDays))
        val distanceDays = (com.xiaopiao.app.domain.startOfDay(now) - twoDays).toDouble() /
            (24 * 3600_000.0)
        org.junit.Assert.assertTrue("expected 1–3 days back, was $distanceDays", distanceDays in 1.0..3.0)
    }
}
