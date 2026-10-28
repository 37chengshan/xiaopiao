package com.xiaopiao.app.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val crowd = DemoCrowd.generate()
    private val exp = Experiment("e", ExperimentTemplates.LARGE_VS_MEDIUM.id, ExperimentStatus.RUNNING, 0)

    @Test fun alwaysExactlyThreeAndDeterministic() {
        ExperimentTemplates.all.forEach { t ->
            val r = ExperimentEngine.result(exp.copy(templateId = t.id), crowd)
            val a = SuggestionGenerator.generate(t, r)
            assertEquals(3, a.size)
            assertEquals(a, SuggestionGenerator.generate(t, r))
            assertEquals(3, a.map { it.id }.toSet().size)
        }
    }

    @Test fun noRevenueGuaranteeAndDisclaimerPresent() {
        val t = exp.template
        val cards = SuggestionGenerator.generate(t, ExperimentEngine.result(exp, crowd))
        assertTrue(cards.all { it.tradeoff.contains("不保证") })
        assertTrue(cards.none { it.detail.contains("一定") || it.detail.contains("保证提升") })
    }

    @Test fun thinSampleSuggestsRetest() {
        val t = ExperimentTemplates.HALF_VS_TOPPING
        val stats = listOf(
            OptionStat(t.a, 3, 0.6, 0.2, 0.9), OptionStat(t.b, 2, 0.4, 0.1, 0.8),
        )
        val r = ExperimentResult(stats, 5, 5, 0, ConfidenceNote(Confidence.THIN, "样本偏少，仅供参考"))
        val cards = SuggestionGenerator.generate(t, r, quietSlot = "周二下午")
        assertEquals("retest", cards[2].id)
        assertTrue(cards[1].headline.contains("周二下午"))
        assertEquals("adopt_A", cards[0].id)
    }

    @Test fun decisionHasTwoWeekReview() {
        val t = exp.template
        val s = SuggestionGenerator.generate(t, ExperimentEngine.result(exp, crowd)).first()
        val d = SuggestionGenerator.decide("e", s, 1_000L)
        assertEquals(1_000L + 14 * 86_400_000L, d.reviewAt)
        assertTrue(d.reviewNote.startsWith("两周后复盘"))
    }

    @Test fun stateAdoptEndsExperimentAndRecordsDecision() {
        val state = MerchantState().publish(exp.template, 5L)
        val result = ExperimentEngine.result(state.experiment!!, crowd)
        val s = SuggestionGenerator.generate(exp.template, result).first()
        val next = state.adopt(s, 10L)
        assertEquals(ExperimentStatus.ENDED, next.experiment!!.status)
        assertEquals(1, next.decisions.size)
        assertEquals(s.id, next.decisionFor(next.experiment.id)!!.suggestionId)
    }

    @Test fun stateRoundTripsThroughJson() {
        val state = MerchantState().publish(exp.template, 5L).addGameResponse("A", 6L).finishGame("game_x")
        assertEquals(state, MerchantState.decode(MerchantState.encode(state)))
        assertEquals(MerchantState(), MerchantState.decode("not json"))
    }

    @Test fun gameResponsesIgnoredWhenNoRunningExperiment() {
        assertEquals(MerchantState(), MerchantState().addGameResponse("A", 1L))
    }
}
