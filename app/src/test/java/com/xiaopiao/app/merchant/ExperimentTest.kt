package com.xiaopiao.app.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentTest {
    private val crowd = DemoCrowd.generate()
    private fun exp(t: ExperimentTemplate, game: List<String> = emptyList()) = Experiment(
        id = "e", templateId = t.id, status = ExperimentStatus.RUNNING, createdAt = 0,
        gameResponses = game.map { GameResponse(it, 0) },
    )

    @Test fun wilsonKnownValue() {
        val (lo, hi) = Stats.wilson(50, 100)
        assertEquals(0.4038, lo, 0.001)
        assertEquals(0.5962, hi, 0.001)
    }

    @Test fun wilsonEdgeCases() {
        assertEquals(0.0 to 1.0, Stats.wilson(0, 0))
        val (lo, hi) = Stats.wilson(10, 10)
        assertEquals(1.0, hi, 1e-9)
        assertTrue(lo in 0.6..0.8)
        val (lo0, _) = Stats.wilson(0, 10)
        assertEquals(0.0, lo0, 1e-9)
    }

    @Test fun wilsonNarrowsWithSample() {
        val small = Stats.wilson(5, 10).let { it.second - it.first }
        val big = Stats.wilson(50, 100).let { it.second - it.first }
        assertTrue(big < small)
    }

    @Test fun resultIsDeterministicAndConsistent() {
        ExperimentTemplates.all.forEach { t ->
            val r1 = ExperimentEngine.result(exp(t), crowd)
            val r2 = ExperimentEngine.result(exp(t), crowd)
            assertEquals(r1, r2)
            assertEquals(r1.sampleSize, r1.stats.sumOf { it.count })
            assertEquals(1.0, r1.stats.sumOf { it.share }, 1e-9)
            assertTrue("sample should be sizeable for ${t.id}: ${r1.sampleSize}", r1.sampleSize in 30..140)
            assertTrue(r1.stats.all { it.ciLow <= it.share && it.share <= it.ciHigh })
        }
    }

    @Test fun gameResponsesAreCountedSeparately() {
        val t = ExperimentTemplates.HALF_VS_TOPPING
        val base = ExperimentEngine.result(exp(t), crowd)
        val withGame = ExperimentEngine.result(exp(t, listOf("B", "B", "A")), crowd)
        assertEquals(3, withGame.gameCount)
        assertEquals(base.simulatedCount, withGame.simulatedCount)
        assertEquals(base.sampleSize + 3, withGame.sampleSize)
    }

    @Test fun confidenceNoteThresholds() {
        val t = ExperimentTemplates.HALF_VS_TOPPING
        fun stats(a: Int, b: Int): List<OptionStat> {
            val n = a + b
            return listOf(a to t.a, b to t.b).map { (c, o) ->
                val (lo, hi) = Stats.wilson(c, n)
                OptionStat(o, c, if (n == 0) 0.0 else c.toDouble() / n, lo, hi)
            }
        }
        assertEquals("样本偏少，仅供参考", ExperimentEngine.confidenceNote(10, stats(7, 3)).text)
        assertEquals(Confidence.THIN, ExperimentEngine.confidenceNote(10, stats(7, 3)).level)
        assertEquals(Confidence.EARLY, ExperimentEngine.confidenceNote(35, stats(25, 10)).level)
        assertEquals("样本 63 人，结论较稳定", ExperimentEngine.confidenceNote(63, stats(45, 18)).text)
        assertEquals(Confidence.EARLY, ExperimentEngine.confidenceNote(60, stats(31, 29)).level)
    }

    @Test fun leaderTieGoesToA() {
        val t = ExperimentTemplates.HALF_VS_TOPPING
        val tie = listOf(t.a, t.b).map { OptionStat(it, 5, 0.5, 0.3, 0.7) }
        val r = ExperimentResult(tie, 10, 10, 0, ConfidenceNote(Confidence.THIN, ""))
        assertEquals("A", r.leader.option.id)
    }
}
