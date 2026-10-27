package com.xiaopiao.app.capture

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAddParserTest {
    @Test fun simple() {
        val d = QuickAddParser.parse("午饭 32").single()
        assertEquals(3200L, d.amountCents); assertEquals("午饭", d.merchant)
        assertEquals(Category.MEAL, d.category); assertEquals(0, d.dayOffset); assertEquals(Direction.OUT, d.direction)
    }

    @Test fun twoItemsWithDayCarryForward() {
        val ds = QuickAddParser.parse("昨天打车 18 和奶茶 15")
        assertEquals(2, ds.size)
        assertEquals(1800L, ds[0].amountCents); assertEquals("打车", ds[0].merchant); assertEquals(-1, ds[0].dayOffset)
        assertEquals(Category.TRANSPORT, ds[0].category)
        assertEquals(1500L, ds[1].amountCents); assertEquals("奶茶", ds[1].merchant); assertEquals(-1, ds[1].dayOffset)
        assertEquals(Category.DRINK, ds[1].category)
    }

    @Test fun decimalWithoutSpace() {
        val d = QuickAddParser.parse("星巴克38.5").single()
        assertEquals(3850L, d.amountCents); assertEquals("星巴克", d.merchant); assertEquals(Category.DRINK, d.category)
    }

    @Test fun income() {
        val d = QuickAddParser.parse("收到工资8000").single()
        assertEquals(800000L, d.amountCents); assertEquals(Direction.IN, d.direction); assertEquals("工资", d.merchant)
    }

    @Test fun unitsAndSymbols() {
        assertEquals(1250L, QuickAddParser.parse("咖啡12.5元").single().amountCents)
        assertEquals(2000L, QuickAddParser.parse("买了奶茶20块").single().amountCents)
        assertEquals(990L, QuickAddParser.parse("早餐¥9.9").single().amountCents)
    }

    @Test fun dayChangesMidway() {
        val ds = QuickAddParser.parse("前天地铁 4，今天午饭 25")
        assertEquals(listOf(-2, 0), ds.map { it.dayOffset })
        assertEquals(listOf("地铁", "午饭"), ds.map { it.merchant })
    }

    @Test fun trailingLabel() {
        val d = QuickAddParser.parse("15块奶茶").single()
        assertEquals(1500L, d.amountCents); assertEquals("奶茶", d.merchant)
    }

    @Test fun quantityIsNotAmount() {
        val d = QuickAddParser.parse("2杯奶茶 30").single()
        assertEquals(3000L, d.amountCents)
    }

    @Test fun noNumberYieldsNothing() { assertTrue(QuickAddParser.parse("今天吃了好吃的").isEmpty()) }
    @Test fun blankYieldsNothing() { assertTrue(QuickAddParser.parse("  ").isEmpty()) }
    @Test fun zeroAmountDropped() { assertTrue(QuickAddParser.parse("午饭 0").isEmpty()) }
    @Test fun missingLabelFallsBack() { assertEquals("随手记", QuickAddParser.parse("32").single().merchant) }
}
