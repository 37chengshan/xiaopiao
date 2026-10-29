package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Badge
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DailyReceipt
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptLayoutTest {
    private fun txn(i: Int, cents: Long = 1000, dir: Direction = Direction.OUT, merchant: String = "店$i") =
        Txn(id = i.toLong(), amountCents = cents, direction = dir, merchant = merchant, category = Category.MEAL, occurredAt = 0, source = Source.MANUAL)

    private fun receipt(items: List<Txn>, badge: Badge? = null) =
        DailyReceipt(0L, items, items.filter { it.direction == Direction.OUT }.sumOf { it.amountCents }, "今天也很棒", badge)

    @Test fun cjkAndEmojiTakeTwoColumns() {
        assertEquals(4, ReceiptLayout.displayWidth("吃饭"))
        assertEquals(2, ReceiptLayout.displayWidth("🍚"))
        assertEquals(3, ReceiptLayout.displayWidth("abc"))
        assertEquals(2, ReceiptLayout.displayWidth("🛍️"))
    }

    @Test fun ellipsizeCutsToBudget() {
        val out = ReceiptLayout.ellipsize("非常非常非常长的商家名称", 8)
        assertTrue(out.endsWith("…"))
        assertTrue(ReceiptLayout.displayWidth(out) <= 8)
        assertEquals("短", ReceiptLayout.ellipsize("短", 8))
    }

    @Test fun wrapRespectsWidth() {
        val lines = ReceiptLayout.wrap("一二三四五六七八九十一二三四五六", 10)
        assertTrue(lines.size >= 3)
        assertTrue(lines.all { ReceiptLayout.displayWidth(it) <= 10 })
    }

    @Test fun moneyFormatting() {
        assertEquals("¥12", ReceiptLayout.money(1200))
        assertEquals("¥12.5", ReceiptLayout.money(1250))
        assertEquals("¥12.05", ReceiptLayout.money(1205))
        assertEquals("¥1,234,567.89", ReceiptLayout.money(123_456_789))
        assertEquals("+¥3", ReceiptLayout.money(300, "+"))
    }

    @Test fun emptyDayPrintsNoSpendAndDefaultStamp() {
        val lines = ReceiptLayout.build(receipt(emptyList()), isToday = true, demo = false)
        assertTrue(lines.any { it.kind == LineKind.BIG && it.left.contains("今日无消费") })
        assertEquals("钱包休眠者", lines.first { it.kind == LineKind.STAMP_SPACE }.left)
    }

    @Test fun badgeTitleBecomesStamp() {
        val r = receipt(listOf(txn(1)), Badge("x", "奶茶续命王", "🧋", "r"))
        assertEquals("奶茶续命王", ReceiptLayout.stampText(r))
    }

    @Test fun longReceiptCollapsesAndExpands() {
        val r = receipt((1..12).map { txn(it) })
        val collapsed = ReceiptLayout.build(r, true, false)
        assertEquals(ReceiptLayout.MAX_ROWS, collapsed.count { it.kind == LineKind.ITEM })
        assertEquals(6, collapsed.first { it.kind == LineKind.MORE }.extra)
        val expanded = ReceiptLayout.build(r, true, false, expanded = true)
        assertEquals(12, expanded.count { it.kind == LineKind.ITEM })
        assertTrue(expanded.none { it.kind == LineKind.MORE })
    }

    @Test fun incomeRowsStayInColumns() {
        val r = receipt(listOf(txn(1), txn(2, 50000, Direction.IN, "工资")))
        // tt#27: the receipt no longer prints a 「演示数据」 line.
        val lines = ReceiptLayout.build(r, true, demo = true)
        assertTrue(lines.none { it.kind == LineKind.DEMO })
        assertTrue(lines.first { it.kind == LineKind.ITEM && it.positive }.right.startsWith("+¥"))
        assertEquals("+¥500", lines.first { it.kind == LineKind.INCOME }.right)
    }

    @Test fun hugeAmountStaysInColumns() {
        val r = receipt(listOf(txn(1, 99_999_999_999, merchant = "超级超级超级超级长的名字")))
        val item = ReceiptLayout.build(r, true, false, expanded = true).first { it.kind == LineKind.ITEM }
        assertTrue(ReceiptLayout.displayWidth(item.left) + ReceiptLayout.displayWidth(item.right) <= ReceiptLayout.COLS)
    }

    @Test fun heightAndStampOffsetAreConsistent() {
        val lines = ReceiptLayout.build(receipt(listOf(txn(1))), true, false)
        val stampTop = ReceiptLayout.stampTopDp(lines)
        assertTrue(stampTop > 0 && stampTop < ReceiptLayout.heightDp(lines))
        assertEquals(-1f, ReceiptLayout.stampTopDp(emptyList()))
    }

    @Test fun plainTextFallbackHasTotal() {
        val text = ReceiptLayout.toPlainText(ReceiptLayout.build(receipt(listOf(txn(1))), true, false))
        assertTrue(text.contains("今日支出"))
        assertTrue(text.contains("今日小票"))
    }

    @Test fun dayWithoutBadgeIsOnlyDormantWhenNothingWasSpent() {
        fun out(cents: Long, cat: com.xiaopiao.app.domain.Category) =
            com.xiaopiao.app.domain.Txn(amountCents = cents, merchant = "x", category = cat, occurredAt = 0L, source = Source.MANUAL)
        assertEquals(ReceiptLayout.DEFAULT_STAMP, ReceiptLayout.dayTitle(emptyList()))
        // tt: 「为什么花了 99 还是钱包休眠者」
        assertEquals("干饭人", ReceiptLayout.dayTitle(listOf(out(60_00, com.xiaopiao.app.domain.Category.MEAL), out(39_00, com.xiaopiao.app.domain.Category.DRINK))))
        assertEquals("一笔入魂", ReceiptLayout.dayTitle(listOf(out(99_00, com.xiaopiao.app.domain.Category.SHOPPING))))
        assertEquals("小确幸日", ReceiptLayout.dayTitle(listOf(out(8_00, com.xiaopiao.app.domain.Category.SNACK))))
        assertEquals("豪横的一天", ReceiptLayout.dayTitle(listOf(out(800_00, com.xiaopiao.app.domain.Category.SHOPPING))))
    }
}
