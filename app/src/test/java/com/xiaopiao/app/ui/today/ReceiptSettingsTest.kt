package com.xiaopiao.app.ui.today

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DailyReceipt
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptSettingsTest {
    private fun prefs(): ReceiptPrefs {
        val file = File.createTempFile("receipt_settings", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
        return ReceiptPrefs(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    // ---- persistence -------------------------------------------------------

    @Test fun defaultsAreClassicPaperAndTheFullPreset() = runBlocking {
        val s = prefs().observe().first()
        assertEquals(PaperTheme.Classic, s.theme)
        assertNull(s.customInk)
        assertEquals(ReceiptPreset.Full.content, s.content)
    }

    @Test fun themeInkAndContentRoundTrip() = runBlocking {
        val p = prefs()
        p.setTheme(PaperTheme.Sakura)
        p.setCustomInk(0xFF1F3A8A.toInt())
        p.setContent(ReceiptPreset.Brief.content)
        val s = p.observe().first()
        assertEquals(PaperTheme.Sakura, s.theme)
        assertEquals(0xFF1F3A8A.toInt(), s.customInk)
        assertEquals(ReceiptPreset.Brief, s.content.preset)
        p.setCustomInk(null)
        assertNull(p.observe().first().customInk)
    }

    @Test fun contentBitsRoundTripForEveryCombination() {
        for (bits in 0 until (1 shl ReceiptContent.Flag.entries.size)) {
            assertEquals(bits, ReceiptContent.fromBits(bits).toBits())
        }
    }

    @Test fun unknownThemeIdFallsBackToClassic() {
        assertEquals(PaperTheme.Classic, PaperTheme.fromId("neon"))
        assertEquals(PaperTheme.Classic, PaperTheme.fromId(null))
    }

    @Test fun presetsAreDistinctAndMixedSwitchesAreCustom() {
        assertNotEquals(ReceiptPreset.Full.content, ReceiptPreset.Brief.content)
        assertEquals(ReceiptPreset.Full, ReceiptPreset.Full.content.preset)
        assertNull(ReceiptPreset.Full.content.withFlag(ReceiptContent.Flag.Bars, true).preset)
    }

    @Test fun customInkOverridesOnlyInk() {
        val base = PaperColors.resolve(PaperTheme.Kraft, null)
        val inked = PaperColors.resolve(PaperTheme.Kraft, 0xFF123456.toInt())
        assertEquals(base.paper, inked.paper)
        assertNotEquals(base.ink, inked.ink)
        assertTrue(PaperColors.resolve(PaperTheme.Night, null).dark)
        assertFalse(base.dark)
    }

    // ---- what gets printed ---------------------------------------------------

    private fun txn(i: Int, cents: Long, cat: Category) =
        Txn(id = i.toLong(), amountCents = cents, direction = Direction.OUT, merchant = "店$i", category = cat, occurredAt = 0, source = Source.MANUAL)

    private val items = listOf(txn(1, 5000, Category.MEAL), txn(2, 3000, Category.MEAL), txn(3, 2000, Category.TRANSPORT), txn(4, 500, Category.DRINK), txn(5, 100, Category.OTHER))
    private val receipt = DailyReceipt(0L, items, items.sumOf { it.amountCents }, "今天也很棒", null)

    private fun kinds(c: ReceiptContent) = ReceiptLayout.build(receipt, true, false, content = c).map { it.kind }

    @Test fun briefPresetHasBarsAndStampButNoRowsOrRemark() {
        val k = kinds(ReceiptPreset.Brief.content)
        assertTrue(LineKind.TOTAL in k && LineKind.STAMP_SPACE in k)
        assertEquals(3, k.count { it == LineKind.SHARE_BAR })
        assertFalse(LineKind.ITEM in k || LineKind.CATEGORY in k || LineKind.COMMENT in k || LineKind.STATS in k || LineKind.BARCODE in k)
    }

    @Test fun fullPresetHasRowsRemarkAndBarcodeButNoBars() {
        val k = kinds(ReceiptPreset.Full.content)
        assertTrue(LineKind.CATEGORY in k && LineKind.COMMENT in k && LineKind.STATS in k && LineKind.BARCODE in k)
        assertFalse(LineKind.SHARE_BAR in k)
    }

    @Test fun fullIsTallerThanBrief() {
        val full = ReceiptLayout.build(receipt, true, false, content = ReceiptPreset.Full.content)
        val brief = ReceiptLayout.build(receipt, true, false, content = ReceiptPreset.Brief.content)
        assertTrue(ReceiptLayout.heightDp(full) > ReceiptLayout.heightDp(brief))
    }

    @Test fun eachSwitchRemovesExactlyItsLines() {
        val all = ReceiptContent(items = true, total = true, bars = true, remark = true, stamp = true, weekCompare = true, barcode = true)
        assertFalse(LineKind.TOTAL in kinds(all.copy(total = false)))
        assertFalse(LineKind.COMMENT in kinds(all.copy(remark = false)))
        assertFalse(LineKind.STAMP_SPACE in kinds(all.copy(stamp = false)))
        assertFalse(LineKind.STATS in kinds(all.copy(weekCompare = false)))
        assertFalse(LineKind.BARCODE in kinds(all.copy(barcode = false)))
        assertFalse(LineKind.SHARE_BAR in kinds(all.copy(bars = false)))
        assertFalse(LineKind.ITEM in kinds(all.copy(items = false)))
    }

    @Test fun noSwitchCombinationLeavesDoubleDashes() {
        for (bits in 0 until (1 shl ReceiptContent.Flag.entries.size)) {
            ReceiptLayout.build(receipt, true, false, content = ReceiptContent.fromBits(bits))
                .zipWithNext().forEach { (a, b) -> assertFalse("bits=$bits", a.kind == LineKind.DASH && b.kind == LineKind.DASH) }
        }
    }

    @Test fun shareBarsAreTopThreeBiggestFirstWithPercentages() {
        val bars = ReceiptLayout.shareBars(items)
        assertEquals(listOf(75, 19, 5), bars.map { it.extra })
        assertEquals(listOf(8000L, 2000L, 500L), bars.map { it.cents })
    }

    @Test fun serialIsStableAndShaped() {
        val a = ReceiptLayout.serialFor(receipt)
        assertEquals(a, ReceiptLayout.serialFor(receipt))
        assertTrue(Regex("NO\\.\\d{8}-\\d{4}").matches(a))
    }

    @Test fun stripTextFitsSixCharacters() {
        assertEquals("¥0", StripText.format(0))
        assertEquals("¥18", StripText.format(1800))
        assertEquals("¥18.28", StripText.format(1828))
        assertEquals("¥123.4", StripText.format(12_345))
        assertEquals("¥1234", StripText.format(123_456))
        assertEquals("¥1.5万", StripText.format(15_000_00))
        listOf(0L, 5L, 1828L, 99_999L, 12_345_678L).forEach { assertTrue(StripText.format(it).length <= 6) }
    }

    @Test fun pickedCalendarDayKeepsItsDateAcrossUtcAndLocal() {
        val local = java.util.Calendar.getInstance().apply { clear(); set(2026, 9, 28) }.timeInMillis
        assertEquals(local, DayPick.localDayFromUtc(DayPick.utcFromLocalDay(local)))
    }

    // ---- font and category filter (v6) -------------------------------------

    @Test fun fontAndExcludedCategoriesRoundTripAndDefaultToEverything() = runBlocking {
        val p = prefs()
        val def = p.observe().first()
        assertEquals(ReceiptFont.Thermal, def.font)
        assertTrue(def.excluded.isEmpty())
        p.setFont(ReceiptFont.Serif)
        p.setExcluded(setOf("drink", "custom:42"))
        val s = p.observe().first()
        assertEquals(ReceiptFont.Serif, s.font)
        assertEquals(setOf("drink", "custom:42"), s.excluded)
        assertFalse(s.includes("drink"))
        assertTrue(s.includes("meal"))
        p.setExcluded(emptySet())
        assertTrue(p.observe().first().excluded.isEmpty())
    }

    @Test fun unknownFontIdAndCategoryNamesFallBackSafely() {
        assertEquals(ReceiptFont.Thermal, ReceiptFont.fromId("nope"))
        assertEquals(ReceiptFont.Thermal, ReceiptFont.fromId(null))
        // Pre-id settings stored enum names; they come back as the built-in ids.
        assertEquals(setOf("meal", "drink"), ReceiptPrefs.decodeCategories("MEAL,DRINK"))
        assertTrue(ReceiptPrefs.decodeCategories(null).isEmpty())
        assertEquals(setOf("fun", "custom:7"), ReceiptPrefs.decodeCategories(ReceiptPrefs.encodeCategories(setOf("custom:7", "fun"))))
    }

    @Test fun excludedCategoriesLeaveTheReceiptAndItsTotal() {
        val day = 0L
        val all = listOf(txn(1, 5000, Category.MEAL), txn(2, 3000, Category.DRINK), txn(3, 2000, Category.TRANSPORT))
        val full = ReceiptSource.receiptFor(all, day)
        val filtered = ReceiptSource.receiptFor(all, day, excluded = setOf("drink"))
        assertEquals(10000L, full.totalOutCents)
        assertEquals(7000L, filtered.totalOutCents)
        assertTrue(filtered.items.none { it.category == Category.DRINK })
    }
}
