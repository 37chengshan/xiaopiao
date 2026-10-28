package com.xiaopiao.app.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RevenueCsvTest {
    private fun day(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, 0, 0, 0)
    }.timeInMillis

    private val today = day(2026, 10, 28)

    @Test fun parsesHeaderAndEveryDateShape() {
        val csv = """
            日期,营收,订单
            2026-10-24,1280.5,86
            2026/10/25,980,64
            2026.10.26,"¥1,120.50",70
            10/27,760
        """.trimIndent()
        val preview = RevenueCsv.parse(csv, today)
        assertEquals(4, preview.importable.size)
        assertEquals(0, preview.invalid.size)

        val rows = preview.importable
        assertEquals(day(2026, 10, 24), rows[0].dayStart)
        assertEquals(128_050L, rows[0].revenueCents)
        assertEquals(86, rows[0].orders)
        assertEquals(day(2026, 10, 26), rows[2].dayStart)
        assertEquals(112_050L, rows[2].revenueCents)
        assertEquals(day(2026, 10, 27), rows[3].dayStart)
        assertEquals(0, rows[3].orders)
    }

    @Test fun toleratesBomCommentsBlankLinesAndCrlf() {
        val csv = "\uFEFF# 十月营业额\r\n\r\n2026-10-26,1000,10\r\n2026-10-27,2000,20\r\n"
        val preview = RevenueCsv.parse(csv, today)
        assertEquals(2, preview.importable.size)
        assertEquals(listOf(1_000_00L, 2_000_00L), preview.importable.map { it.revenueCents })
        assertEquals(3, preview.importable[0].line)
    }

    @Test fun moneyParsingRoundsToTheNearestCent() {
        assertEquals(128_050L, RevenueCsv.parseMoney("1280.5"))
        assertEquals(128_050L, RevenueCsv.parseMoney("1,280.50"))
        assertEquals(128_000L, RevenueCsv.parseMoney("¥1280"))
        assertEquals(128_050L, RevenueCsv.parseMoney("1280.5 "))
        assertEquals(128_057L, RevenueCsv.parseMoney("1280.567"))
        assertEquals(0L, RevenueCsv.parseMoney("0"))
        assertNull(RevenueCsv.parseMoney(""))
        assertNull(RevenueCsv.parseMoney("一千二"))
        assertNull(RevenueCsv.parseMoney("12.34.5"))
    }

    @Test fun reportsBadRowsWithoutDroppingThem() {
        val csv = """
            date,revenue,orders
            2026-13-01,100,1
            2026-02-31,100,1
            2026-10-27,abc,1
            2026-10-27,100,10.5
            2026-10-27
            2026-10-27,-100,3
            2026-10-29,100,3
        """.trimIndent()
        val preview = RevenueCsv.parse(csv, today)
        assertEquals(0, preview.importable.size)
        assertEquals(7, preview.invalid.size)
        assertTrue(preview.invalid.all { it.error != null && it.raw.isNotBlank() })
        assertEquals("日期在未来", preview.invalid.last().error)
        assertTrue(preview.invalid.any { it.error == "营收看不懂（用 1280.5 这种写法）" })
    }

    @Test fun duplicateDaysAgainstTheBookAreSkipped() {
        val csv = """
            2026-10-26,1000,10
            2026-10-27,2000,20
            2026-10-28,3000,30
        """.trimIndent()
        val preview = RevenueCsv.parse(csv, today, existingDays = setOf(day(2026, 10, 27)))
        assertEquals(listOf(day(2026, 10, 26), day(2026, 10, 28)), preview.importable.map { it.dayStart })
        assertEquals(listOf(day(2026, 10, 27)), preview.duplicates.map { it.dayStart })
    }

    @Test fun repeatedDayInsideOneFileWarnsOnlyTheLaterRow() {
        val csv = """
            2026-10-26,1000
            2026-10-26,1200
            2026-10-27,1300
        """.trimIndent()
        val preview = RevenueCsv.parse(csv, today)
        assertEquals(2, preview.importable.size)
        assertEquals(1, preview.duplicates.size)
        assertEquals(120_000L, preview.duplicates.single().revenueCents)
        assertEquals(2, preview.duplicates.single().line)
    }

    @Test fun quotedFieldsAndNoteColumnSurvive() {
        val csv = "2026-10-27,\"1,280.5\",86,\"国庆, 补账\""
        val row = RevenueCsv.parse(csv, today).importable.single()
        assertEquals(128_050L, row.revenueCents)
        assertEquals(86, row.orders)
        assertEquals("国庆, 补账", row.note)
    }

    @Test fun entriesAreUserDataMarkedAsCsv() {
        val preview = RevenueCsv.parse("2026-10-27,1280.5,86\nbad,row", today)
        val entry = RevenueCsv.entries(preview).single()
        assertEquals(day(2026, 10, 27), entry.dayStart)
        assertEquals(128_050L, entry.revenueCents)
        assertEquals(RevenueSource.CSV, entry.source)
        assertFalse(entry.seeded)
    }

    @Test fun previewTotalsOnlyCountImportableRows() {
        val preview = RevenueCsv.parse(
            "2026-10-26,1000\n2026-10-27,2000\n2026-10-27,2000\n2026-10-28,x\n",
            today,
        )
        assertEquals(2, preview.importDays)
        assertEquals(3_000_00L, preview.importTotalCents)
        assertEquals(1, preview.duplicates.size)
        assertEquals(1, preview.invalid.size)
    }

    @Test fun emptyFileIsEmptyNotAnError() {
        val preview = RevenueCsv.parse("", today)
        assertTrue(preview.isEmpty)
        assertTrue(RevenueCsv.entries(preview).isEmpty())
    }

    @Test fun dayParserRejectsImpossibleDatesAndAcceptsShortOnes() {
        assertEquals(day(2026, 2, 28), RevenueCsv.parseDay("2026-02-28", today))
        assertNull(RevenueCsv.parseDay("2026-02-30", today))
        assertNull(RevenueCsv.parseDay("明天", today))
        assertNotNull(RevenueCsv.parseDay("10/1", today))
        assertEquals(day(2026, 10, 1), RevenueCsv.parseDay("10/1", today))
    }
}
