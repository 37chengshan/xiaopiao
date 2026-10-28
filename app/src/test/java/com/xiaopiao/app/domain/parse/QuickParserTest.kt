package com.xiaopiao.app.domain.parse

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic L0 parser tests. [now] is Wednesday 2026-10-28 15:30 local time, so
 * "上周三" is exactly seven days earlier and "周一" is two days earlier.
 */
class QuickParserTest {

    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 28, 15, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayStart(daysAgo: Int): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -daysAgo)
        }.timeInMillis,
    )

    private fun one(input: String): ParsedEntry {
        val entries = QuickParser.parse(input, now)
        assertEquals("expected exactly one entry for <$input>", 1, entries.size)
        return entries[0]
    }

    private fun assertCategory(input: String, category: Category) {
        val entries = QuickParser.parse(input, now)
        assertEquals("expected exactly one entry for <$input>", 1, entries.size)
        assertEquals("<$input> category", category, entries[0].category)
    }

    // ------------------------------------------------------------- amounts

    @Test
    fun `bare arabic amount becomes cents`() {
        val entry = one("午饭32")
        assertEquals(3_200L, entry.amountCents)
        assertEquals(Category.MEAL, entry.category)
        assertEquals("午饭", entry.merchant)
        assertEquals(Direction.OUT, entry.direction)
        assertEquals(now, entry.occurredAt)
    }

    @Test
    fun `decimal amount becomes exact cents`() {
        assertEquals(1_850L, one("午饭18.5").amountCents)
        assertEquals(1_855L, one("午饭18.55").amountCents)
    }

    @Test
    fun `three decimals round once into cents`() {
        assertEquals(1_900L, one("午饭18.999").amountCents)
    }

    @Test
    fun `yuan suffix`() {
        assertEquals(800_000L, one("房租8000元").amountCents)
    }

    @Test
    fun `kuai suffix`() {
        assertEquals(200L, one("咖啡2块").amountCents)
        assertEquals(200L, one("咖啡2块钱").amountCents)
    }

    @Test
    fun `kuai with jiao digit`() {
        assertEquals(350L, one("咖啡3块5").amountCents)
    }

    @Test
    fun `kuai with jiao word`() {
        assertEquals(350L, one("咖啡3块5毛").amountCents)
        assertEquals(350L, one("咖啡3元5角").amountCents)
    }

    @Test
    fun `kuai with fen tail`() {
        assertEquals(355L, one("咖啡3块5毛5分").amountCents)
    }

    @Test
    fun `k multiplier`() {
        assertEquals(120_000L, one("房租1.2k").amountCents)
    }

    @Test
    fun `wan multiplier`() {
        assertEquals(2_000_000L, one("房租2w").amountCents)
    }

    @Test
    fun `qian multiplier`() {
        assertEquals(300_000L, one("房租3千").amountCents)
    }

    @Test
    fun `mao only amount is fifty cents`() {
        assertEquals(50L, one("咖啡5毛").amountCents)
    }

    @Test
    fun `fen only amount is fifty cents`() {
        assertEquals(50L, one("咖啡50分").amountCents)
    }

    @Test
    fun `full width digits`() {
        assertEquals(3_200L, one("午饭３２").amountCents)
    }

    @Test
    fun `yen symbol prefix`() {
        assertEquals(3_200L, one("¥32").amountCents)
    }

    // --------------------------------------------------- chinese numerals

    @Test
    fun `chinese twelve`() {
        assertEquals(1_200L, one("午饭十二").amountCents)
    }

    @Test
    fun `chinese thirty five`() {
        assertEquals(3_500L, one("午饭三十五").amountCents)
    }

    @Test
    fun `chinese hundred shorthand`() {
        assertEquals(12_000L, one("午饭一百二").amountCents)
    }

    @Test
    fun `chinese hundred full form`() {
        assertEquals(12_000L, one("午饭一百二十").amountCents)
    }

    @Test
    fun `chinese hundred with zero`() {
        assertEquals(10_200L, one("午饭一百零二").amountCents)
    }

    @Test
    fun `chinese two hundred`() {
        assertEquals(20_000L, one("午饭两百").amountCents)
    }

    @Test
    fun `chinese two thousand five hundred shorthand`() {
        assertEquals(250_000L, one("午饭两千五").amountCents)
    }

    @Test
    fun `chinese ten thousand shorthand`() {
        assertEquals(1_200_000L, one("房租一万二").amountCents)
    }

    @Test
    fun `chinese fifteen with kuai`() {
        assertEquals(1_500L, one("午饭十五块").amountCents)
    }

    @Test
    fun `chinese one point five`() {
        assertEquals(150L, one("午饭一块五").amountCents)
    }

    @Test
    fun `chinese three and a half`() {
        assertEquals(350L, one("午饭三块半").amountCents)
    }

    @Test
    fun `chinese twelve fifty`() {
        assertEquals(1_250L, one("午饭十二块五").amountCents)
    }

    @Test
    fun `chinese thirty five fifty`() {
        assertEquals(3_550L, one("午饭三十五块五毛").amountCents)
    }

    // ------------------------------------------------------------ multiple

    @Test
    fun `two entries in one sentence`() {
        val entries = QuickParser.parse("午饭32 骑车2", now)
        assertEquals(2, entries.size)
        assertEquals(3_200L, entries[0].amountCents)
        assertEquals(Category.MEAL, entries[0].category)
        assertEquals("午饭", entries[0].merchant)
        assertEquals(200L, entries[1].amountCents)
        assertEquals(Category.TRANSPORT, entries[1].category)
        assertEquals("骑车", entries[1].merchant)
    }

    @Test
    fun `three entries in one sentence`() {
        val entries = QuickParser.parse("午饭32 骑车2 电影45", now)
        assertEquals(3, entries.size)
        assertEquals(listOf(3_200L, 200L, 4_500L), entries.map { it.amountCents })
        assertEquals(listOf(Category.MEAL, Category.TRANSPORT, Category.FUN), entries.map { it.category })
    }

    @Test
    fun `entries with conjunction`() {
        val entries = QuickParser.parse("午饭32 和奶茶15", now)
        assertEquals(2, entries.size)
        assertEquals(1_500L, entries[1].amountCents)
        assertEquals(Category.DRINK, entries[1].category)
        assertEquals("奶茶", entries[1].merchant)
    }

    @Test
    fun `number before a label still gets the label`() {
        val entry = one("32块钱午饭")
        assertEquals(3_200L, entry.amountCents)
        assertEquals(Category.MEAL, entry.category)
        assertEquals("午饭", entry.merchant)
    }

    // --------------------------------------------------------------- dates

    @Test
    fun `today keeps now`() {
        assertEquals(now, one("今天午饭32").occurredAt)
    }

    @Test
    fun `yesterday`() {
        assertEquals(dayStart(1), one("昨天午饭32").occurredAt)
    }

    @Test
    fun `day before yesterday`() {
        assertEquals(dayStart(2), one("前天午饭32").occurredAt)
    }

    @Test
    fun `last week wednesday`() {
        assertEquals(dayStart(7), one("上周三午饭32").occurredAt)
    }

    @Test
    fun `weekday rolls back to the latest occurrence`() {
        assertEquals(dayStart(2), one("周一午饭32").occurredAt)
    }

    @Test
    fun `date words apply per entry`() {
        val entries = QuickParser.parse("昨天午饭32 今天骑车2", now)
        assertEquals(2, entries.size)
        assertEquals(dayStart(1), entries[0].occurredAt)
        assertEquals(now, entries[1].occurredAt)
    }

    @Test
    fun `no date word means now`() {
        assertEquals(now, one("午饭32").occurredAt)
    }

    // ------------------------------------------------------------- income

    @Test
    fun `salary is income`() {
        val entry = one("工资8000")
        assertEquals(Direction.IN, entry.direction)
        assertEquals(800_000L, entry.amountCents)
        assertEquals("工资", entry.merchant)
    }

    @Test
    fun `received red packet is income`() {
        val entry = one("收到红包200")
        assertEquals(Direction.IN, entry.direction)
        assertEquals(Category.SOCIAL, entry.category)
        assertEquals(20_000L, entry.amountCents)
    }

    @Test
    fun `fa le is income`() {
        assertEquals(Direction.IN, one("发了5000").direction)
    }

    @Test
    fun `bonus is income`() {
        assertEquals(Direction.IN, one("奖金3000").direction)
    }

    @Test
    fun `refund is income`() {
        assertEquals(Direction.IN, one("报销120").direction)
    }

    @Test
    fun `red packet received short form is income`() {
        val entry = one("红包收200")
        assertEquals(Direction.IN, entry.direction)
        assertEquals(Category.SOCIAL, entry.category)
    }

    @Test
    fun `income word is income`() {
        assertEquals(Direction.IN, one("收入5000").direction)
    }

    @Test
    fun `red packet without income word is spending`() {
        assertEquals(Direction.OUT, one("红包200").direction)
    }

    // ----------------------------------------------------------- category

    @Test
    fun `coffee maps to drink`() = assertCategory("咖啡18", Category.DRINK)

    @Test
    fun `milk tea maps to drink`() = assertCategory("奶茶15", Category.DRINK)

    @Test
    fun `luckin maps to drink`() = assertCategory("瑞幸9.9", Category.DRINK)

    @Test
    fun `snack maps to snack`() = assertCategory("薯片6", Category.SNACK)

    @Test
    fun `supermarket maps to grocery`() = assertCategory("超市56", Category.GROCERY)

    @Test
    fun `buying vegetables maps to grocery not shopping`() = assertCategory("买菜23", Category.GROCERY)

    @Test
    fun `subway maps to transport`() = assertCategory("地铁3", Category.TRANSPORT)

    @Test
    fun `taxi maps to transport`() = assertCategory("打车18", Category.TRANSPORT)

    @Test
    fun `bike maps to transport`() = assertCategory("骑车2", Category.TRANSPORT)

    @Test
    fun `fuel maps to transport`() = assertCategory("加油300", Category.TRANSPORT)

    @Test
    fun `taobao maps to shopping`() = assertCategory("淘宝199", Category.SHOPPING)

    @Test
    fun `clothes map to shopping`() = assertCategory("买衣服299", Category.SHOPPING)

    @Test
    fun `movie maps to fun`() = assertCategory("电影45", Category.FUN)

    @Test
    fun `game maps to fun`() = assertCategory("游戏68", Category.FUN)

    @Test
    fun `ktv maps to fun`() = assertCategory("KTV200", Category.FUN)

    @Test
    fun `membership maps to subscription`() = assertCategory("会员15", Category.SUBSCRIPTION)

    @Test
    fun `rent maps to housing`() = assertCategory("房租2500", Category.HOUSING)

    @Test
    fun `utilities map to housing`() = assertCategory("水电120", Category.HOUSING)

    @Test
    fun `medicine maps to health`() = assertCategory("药38", Category.HEALTH)

    @Test
    fun `hospital maps to health`() = assertCategory("医院200", Category.HEALTH)

    @Test
    fun `red packet maps to social`() = assertCategory("红包200", Category.SOCIAL)

    @Test
    fun `gift maps to social`() = assertCategory("礼物99", Category.SOCIAL)

    @Test
    fun `book maps to learning`() = assertCategory("书58", Category.LEARNING)

    @Test
    fun `course maps to learning`() = assertCategory("课程299", Category.LEARNING)

    @Test
    fun `unknown phrase maps to other`() = assertCategory("理发50", Category.OTHER)

    // ----------------------------------------------------- no amount cases

    @Test
    fun `sentence without amount is ignored`() {
        assertTrue(QuickParser.parse("今天天气不错", now).isEmpty())
    }

    @Test
    fun `category word alone is ignored`() {
        assertTrue(QuickParser.parse("午饭", now).isEmpty())
    }

    @Test
    fun `count after number is not an amount`() {
        assertTrue(QuickParser.parse("今天3笔", now).isEmpty())
        assertTrue(QuickParser.parse("2杯咖啡", now).isEmpty())
    }

    @Test
    fun `year is not an amount`() {
        assertTrue(QuickParser.parse("2026年", now).isEmpty())
    }

    @Test
    fun `zero amount is ignored`() {
        assertTrue(QuickParser.parse("0元", now).isEmpty())
    }

    @Test
    fun `blank input is ignored`() {
        assertTrue(QuickParser.parse("", now).isEmpty())
        assertTrue(QuickParser.parse("   ", now).isEmpty())
    }

    @Test
    fun `quantity plus a real amount keeps only the amount`() {
        val entries = QuickParser.parse("2杯咖啡32", now)
        assertEquals(1, entries.size)
        assertEquals(3_200L, entries[0].amountCents)
        assertEquals(Category.DRINK, entries[0].category)
    }

    @Test
    fun `chinese quantity plus amount keeps only the amount`() {
        val entries = QuickParser.parse("两杯咖啡32", now)
        assertEquals(1, entries.size)
        assertEquals(3_200L, entries[0].amountCents)
    }

    @Test
    fun `people count does not become the entry`() {
        val entries = QuickParser.parse("3个人吃饭120", now)
        assertEquals(1, entries.size)
        assertEquals(12_000L, entries[0].amountCents)
        assertEquals(Category.MEAL, entries[0].category)
    }

    // ------------------------------------------------------------ merchant

    @Test
    fun `leading verb is cleaned from merchant`() {
        val entry = one("买了咖啡32")
        assertEquals("咖啡", entry.merchant)
        assertEquals(Category.DRINK, entry.category)
    }

    @Test
    fun `location prefix is kept when it is a shop`() {
        val entry = one("在超市56")
        assertEquals("超市", entry.merchant)
        assertEquals(Category.GROCERY, entry.category)
    }

    @Test
    fun `merchant falls back to category label`() {
        assertEquals(Category.OTHER.label, one("花了32").merchant)
    }

    @Test
    fun `parsed note is empty`() {
        assertEquals("", one("午饭32").note)
    }

    @Test
    fun `parsing is deterministic`() {
        val first = QuickParser.parse("昨天午饭32 骑车2 工资8000", now)
        val second = QuickParser.parse("昨天午饭32 骑车2 工资8000", now)
        assertEquals(first, second)
        assertEquals(3, first.size)
    }
}
