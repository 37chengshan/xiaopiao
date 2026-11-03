package com.xiaopiao.app.ui.record

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import org.junit.Assert.assertEquals
import org.junit.Test

class NotePhrasesTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun txn(note: String, category: Category, daysAgo: Int, categoryId: String = "") = Txn(
        amountCents = 100,
        merchant = note,
        category = category,
        categoryId = categoryId,
        occurredAt = now - daysAgo * day,
        source = Source.MANUAL,
    )

    @Test
    fun categoryMatchesComeFirstThenGlobalFillsIn() {
        val txns = listOf(
            txn("地铁", Category.TRANSPORT, 1), txn("地铁", Category.TRANSPORT, 2), txn("地铁", Category.TRANSPORT, 3),
            txn("午饭", Category.MEAL, 1), txn("外卖", Category.MEAL, 5),
        )
        assertEquals(listOf("午饭", "外卖", "地铁"), NotePhrases.rank(txns, "meal", now))
    }

    @Test
    fun frequencyBeatsASingleUseAndRecencyBreaksTies() {
        val txns = listOf(
            txn("咖啡", Category.DRINK, 40), txn("咖啡", Category.DRINK, 41),
            txn("奶茶", Category.DRINK, 0),
            txn("果汁", Category.DRINK, 60),
        )
        assertEquals(listOf("咖啡", "奶茶", "果汁"), NotePhrases.rank(txns, "drink", now))
    }

    @Test
    fun moreUsesWinEvenWhenTheyAreOlder() {
        val txns = listOf(
            txn("便利店", Category.MEAL, 80), txn("便利店", Category.MEAL, 81), txn("便利店", Category.MEAL, 82),
            txn("食堂", Category.MEAL, 0), txn("食堂", Category.MEAL, 1),
        )
        assertEquals(listOf("便利店", "食堂"), NotePhrases.rank(txns, "meal", now))
    }

    @Test
    fun dropsBlankNumericLongAndOldNotes() {
        val txns = listOf(
            txn("  ", Category.MEAL, 1),
            txn("12.5", Category.MEAL, 1),
            txn("这是一条特别特别特别特别长的备注文字", Category.MEAL, 1),
            txn("早饭", Category.MEAL, 120),
            txn(" 早饭 ", Category.MEAL, 2),
        )
        assertEquals(listOf("早饭"), NotePhrases.rank(txns, "meal", now))
    }

    @Test
    fun explicitCategoryIdWinsOverTheLegacyEnum() {
        val txns = listOf(txn("房租", Category.OTHER, 1, categoryId = "housing"))
        assertEquals(listOf("房租"), NotePhrases.rank(txns, "housing", now).take(1))
        assertEquals(8, NotePhrases.rank(List(20) { txn("备注$it", Category.MEAL, it) }, "meal", now).size)
    }
}
