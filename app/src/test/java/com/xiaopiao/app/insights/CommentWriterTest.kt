package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentWriterTest {
    private val writer = TemplateCommentWriter()
    private val shaming = listOf("浪费", "败家", "剁手", "月光", "不该", "太多了", "自制力", "控制一下", "省着点", "乱花")

    private fun stats(day: Long, total: Long = 4500, count: Int = 3, avg: Long? = 3000, badge: com.xiaopiao.app.domain.Badge? = null) =
        DayStats(day, total, count, Category.MEAL, total, avg, badge)

    @Test fun sameDayAndStatsAlwaysGiveTheSameLine() {
        val d = at(2026, 10, 21, 0)
        assertEquals(writer.dayComment(stats(d)), TemplateCommentWriter().dayComment(stats(d)))
    }

    @Test fun differentDaysVary() {
        val lines = (0 until 30).map { writer.dayComment(stats(at(2026, 10, 1, 0) + it * DAY_MS)) }.toSet()
        assertTrue("expected variety, got ${lines.size}", lines.size >= 4)
    }

    @Test fun coversEveryDayBucket() {
        val d = at(2026, 10, 21, 0)
        val buckets = listOf(
            stats(d, total = 0, count = 0),
            stats(d, total = 0, count = 2),
            stats(d, total = 1500),
            stats(d, total = 4500),
            stats(d, total = 30000, avg = 5000),
        )
        buckets.forEach { assertTrue(writer.dayComment(it).isNotBlank()) }
        assertEquals(5, buckets.map { writer.dayComment(it) }.toSet().size)
    }

    @Test fun mentionsTheAmountWhereTemplatesPromiseIt() {
        val light = (0 until 20).map { writer.dayComment(stats(at(2026, 10, 1, 0) + it * DAY_MS, total = 1500)) }
        assertTrue(light.any { it.contains("¥15") })
        assertTrue(light.none { it.contains("{") })
    }

    @Test fun neverShames() {
        val lines = (0 until 60).flatMap { i ->
            val d = at(2026, 9, 1, 0) + i * DAY_MS
            listOf(0L, 1500L, 4500L, 40000L).map { writer.dayComment(stats(d, total = it, count = if (it == 0L) 0 else 3)) } +
                listOf(-40, 0, 40).map { pct ->
                    writer.weekComment(WeekStats(d, 80000, pct, Category.DRINK, 2, 12, emptyList()))
                }
        }
        lines.forEach { l -> shaming.forEach { assertFalse("'$l' contains '$it'", l.contains(it)) } }
    }

    @Test fun weekCommentIsDeterministicAndReflectsDirection() {
        val w = at(2026, 10, 19, 0)
        val down = WeekStats(w, 50000, -30, Category.MEAL, 0, 10, emptyList())
        assertEquals(writer.weekComment(down), writer.weekComment(down))
        assertTrue(writer.weekComment(down).contains("30%"))
        val up = WeekStats(w, 90000, 40, Category.MEAL, 0, 10, emptyList())
        assertTrue(writer.weekComment(up).contains("40%"))
        assertTrue(writer.weekComment(WeekStats(w, 0, null, null, 0, 0, emptyList())).isNotBlank())
    }

    @Test fun badgeNodUsesTheBadgeTitleSometimes() {
        val badge = com.xiaopiao.app.domain.Badge("X", "奶茶战神", "🧋", "r")
        val lines = (0 until 30).map { writer.dayComment(stats(at(2026, 10, 1, 0) + it * DAY_MS, badge = badge)) }
        assertTrue(lines.any { it.contains("奶茶战神") })
    }
}
