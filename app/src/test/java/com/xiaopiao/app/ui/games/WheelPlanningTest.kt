package com.xiaopiao.app.ui.games

import com.xiaopiao.app.ui.games.wheel.MaxWheelLabelChars
import com.xiaopiao.app.ui.games.wheel.MaxWheelSlices
import com.xiaopiao.app.ui.games.wheel.MinWheelSlices
import com.xiaopiao.app.ui.games.wheel.Wheel
import com.xiaopiao.app.ui.games.wheel.WheelCodec
import com.xiaopiao.app.ui.games.wheel.angleUnderPointer
import com.xiaopiao.app.ui.games.wheel.clampSliceCount
import com.xiaopiao.app.ui.games.wheel.labelSizeSp
import com.xiaopiao.app.ui.games.wheel.landingMargin
import com.xiaopiao.app.ui.games.wheel.landingRotation
import com.xiaopiao.app.ui.games.wheel.resizeSlices
import com.xiaopiao.app.ui.games.wheel.shortLabel
import com.xiaopiao.app.ui.games.wheel.shouldTick
import com.xiaopiao.app.ui.games.wheel.sliceStart
import com.xiaopiao.app.ui.games.wheel.sliceSweep
import com.xiaopiao.app.ui.games.wheel.sliceUnder
import com.xiaopiao.app.ui.games.wheel.spinDurationMs
import com.xiaopiao.app.ui.games.wheel.spinTurns
import com.xiaopiao.app.ui.games.wheel.studCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 转盘的纯逻辑：几何、夹取、文案、存档。界面不在这里测——但「转完停在哪一格」必须能被证明，
 * 因为它决定了结果、高亮和用户的信任。
 */
class WheelPlanningTest {

    @Test
    fun landingAngleAlwaysStopsInsideTheChosenSlice() {
        for (count in MinWheelSlices..MaxWheelSlices) {
            val sweep = sliceSweep(count)
            val margin = landingMargin(count)
            for (seed in 0 until 40) {
                val random = Random(seed * 31 + count)
                val winner = seed % count
                val from = seed * 137.5f
                val target = landingRotation(from = from, winner = winner, count = count, random = random, turns = 4)

                assertEquals(
                    "seed $seed, $count slices: landed on the wrong slice",
                    winner.toLong(),
                    sliceUnder(target, count).toLong(),
                )
                val local = angleUnderPointer(target)
                val low = sliceStart(winner, count) + margin
                val high = sliceStart(winner, count) + sweep - margin
                assertTrue(
                    "seed $seed, $count slices: $local should sit inside ($low, $high]",
                    local >= low && local < high,
                )
                assertTrue("always turns forwards", target > from + 4 * 360f - 0.001f)
                assertTrue("never more than five turns", target < from + 5 * 360f)
            }
        }
    }

    @Test
    fun landingMarginIsMeaningfulEvenWithTwelveSlices() {
        for (count in MinWheelSlices..MaxWheelSlices) {
            val margin = landingMargin(count)
            assertTrue("$count slices need room for a margin", margin > 0.4f)
            assertTrue("$count slices must not shrink the margin", margin <= sliceSweep(count) / 2f)
        }
    }

    @Test
    fun sliceUnderMatchesSliceStarts() {
        for (count in MinWheelSlices..MaxWheelSlices) {
            val sweep = sliceSweep(count)
            for (index in 0 until count) {
                // A rotation that puts this slice's middle under the pointer.
                val midRotation = -(sliceStart(index, count) + sweep / 2f)
                assertEquals(index, sliceUnder(midRotation, count))
                // The exact start of a slice belongs to that slice, not the previous one.
                val atStart = -(sliceStart(index, count) + 0.01f)
                assertEquals(index, sliceUnder(atStart, count))
            }
            // Negative and multi-turn rotations normalise into range.
            assertEquals(sliceUnder(37f, count), sliceUnder(37f - 3 * 360f, count))
        }
    }

    @Test
    fun optionCountIsClampedToTwoThroughTwelve() {
        assertEquals(MinWheelSlices, clampSliceCount(0))
        assertEquals(MinWheelSlices, clampSliceCount(1))
        assertEquals(2, clampSliceCount(2))
        assertEquals(7, clampSliceCount(7))
        assertEquals(MaxWheelSlices, clampSliceCount(12))
        assertEquals(MaxWheelSlices, clampSliceCount(13))
        assertEquals(MaxWheelSlices, clampSliceCount(999))
    }

    @Test
    fun resizingKeepsWhatWasTyped() {
        val original = listOf("面", "饭", "饺子")
        assertEquals(listOf("面", "饭"), resizeSlices(original, 2))
        assertEquals(listOf("面", "饭", "饺子", "", ""), resizeSlices(original, 5))
        assertEquals(original + List(9) { "" }, resizeSlices(original, 99))
        assertEquals(listOf("面", "饭"), resizeSlices(original, 0))
        // Shrinking then growing again does not resurrect the dropped text.
        assertEquals(listOf("面", "饭", ""), resizeSlices(resizeSlices(original, 1), 3))
    }

    @Test
    fun onlyFilledSlicesTakePartInASpin() {
        val wheel = Wheel(id = "w", name = "测试", slices = listOf(" 面 ", "", "饭", "   "))
        assertEquals(listOf("面", "饭"), wheel.live)
    }

    @Test
    fun flickThrowsFurtherAndEverythingStaysInsideTheTimingBand() {
        assertEquals(4, spinTurns(0f))
        assertEquals(4, spinTurns(120f))
        assertTrue(spinTurns(5000f) > spinTurns(900f))
        assertEquals("a hard throw still stops at eight turns", 8L, spinTurns(100_000f).toLong())
        for (turns in 4..8) {
            val duration = spinDurationMs(turns)
            assertTrue("$turns turns took ${duration}ms", duration in 3600..4600)
            if (turns > 4) assertTrue(spinDurationMs(turns) >= spinDurationMs(turns - 1))
        }
    }

    @Test
    fun labelsAreShortenedInsteadOfClippedAtTheRim() {
        assertEquals("吃饭", shortLabel("  吃饭  "))
        val exactly = "一二三四五六七八"
        assertEquals(MaxWheelLabelChars, exactly.length)
        assertEquals(exactly, shortLabel(exactly))
        val longer = shortLabel("一二三四五六七八九十")
        assertEquals(MaxWheelLabelChars, longer.length)
        assertTrue(longer.endsWith("…"))
    }

    @Test
    fun typeShrinksAsSlicesGrowAndStudsStayEven() {
        val sizes = (MinWheelSlices..MaxWheelSlices).map { labelSizeSp(it) }
        for (i in 1 until sizes.size) assertTrue(sizes[i] <= sizes[i - 1])
        assertTrue(sizes.first() > sizes.last())
        for (count in MinWheelSlices..MaxWheelSlices) {
            assertTrue(studCount(count) in 12..24)
            assertEquals(0, studCount(count) % 2)
        }
    }

    @Test
    fun ticksAreThrottledSoAFastSpinDoesNotBuzzContinuously() {
        assertTrue(shouldTick(0, 45))
        assertTrue(shouldTick(1_000, 1_045))
        assertTrue(!shouldTick(1_000, 1_044))
        assertTrue(!shouldTick(1_000, 1_000))
    }

    @Test
    fun storedWheelsRoundTripAndOldOnesSurvive() {
        // Exactly what the previous version wrote: name + slices, blanks included.
        val legacy = """[{"id":"w1","name":"转盘 1","slices":["吃面","","吃饭",""]},""" +
            """{"id":"w2","name":"周末","slices":["公园","书店"]}]"""
        val decoded = WheelCodec.decode(legacy)
        assertEquals(2, decoded.size)
        assertEquals(Wheel("w1", "转盘 1", listOf("吃面", "", "吃饭", "")), decoded[0])
        assertEquals(listOf("公园", "书店"), decoded[1].slices)

        // Encode → decode is lossless, including the blank rows.
        val again = WheelCodec.decode(WheelCodec.encode(decoded))
        assertEquals(decoded, again)

        // The shape other builds read must not change.
        val json = WheelCodec.encode(listOf(Wheel("w9", "名字", listOf("a", "b"))))
        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("\"slices\""))
        assertNotEquals("", json)
    }

    @Test
    fun brokenOrMissingStorageDegradesToNoWheels() {
        assertEquals(emptyList<Wheel>(), WheelCodec.decode(null))
        assertEquals(emptyList<Wheel>(), WheelCodec.decode(""))
        assertEquals(emptyList<Wheel>(), WheelCodec.decode("not json"))
        assertEquals(emptyList<Wheel>(), WheelCodec.decode("{}"))
        // An entry without an id is dropped instead of crashing the page.
        assertEquals(1, WheelCodec.decode("""[{"name":"x","slices":[]},{"id":"good","name":"ok","slices":["a"]}]""").size)
    }
}
