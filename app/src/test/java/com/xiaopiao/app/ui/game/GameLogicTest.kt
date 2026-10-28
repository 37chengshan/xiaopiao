package com.xiaopiao.app.ui.game

import com.xiaopiao.app.merchant.Experiment
import com.xiaopiao.app.merchant.ExperimentStatus
import com.xiaopiao.app.merchant.ExperimentTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLogicTest {
    private val exp = Experiment("e", ExperimentTemplates.HALF_VS_TOPPING.id, ExperimentStatus.RUNNING, 0)

    @Test fun roundsContainExactlyOneExperimentRoundAtSlotTwo() {
        val rounds = GameLogic.buildRounds(exp, 42L)
        assertEquals(GameLogic.ROUNDS, rounds.size)
        assertEquals(listOf(2), rounds.indices.filter { rounds[it].isExperiment })
        assertEquals("A", rounds[2].left.id)
        assertEquals("B", rounds[2].right.id)
    }

    @Test fun withoutExperimentOnlyFillersAndDeterministic() {
        val a = GameLogic.buildRounds(null, 7L)
        assertEquals(GameLogic.ROUNDS, a.size)
        assertTrue(a.none { it.isExperiment })
        assertEquals(a, GameLogic.buildRounds(null, 7L))
        assertEquals(a.size, a.map { it.id }.toSet().size)
    }

    @Test fun outcomePicksMostFrequentPersonaTiesToEarliest() {
        val t = ExperimentTemplates.HALF_VS_TOPPING
        val topping = GameChoice("B", t.b.title, t.b.emoji, t.b.persona)
        val ice = GameChoice("ice", "冰饮", "🧊", "冰饮战士")
        val out = GameLogic.outcome(listOf(ice, topping, topping, ice, topping))
        assertEquals("小料党", out.persona.name)
        val tie = GameLogic.outcome(listOf(ice, topping))
        assertEquals("冰饮战士", tie.persona.name)
        assertTrue(out.badge.code.startsWith("game_"))
    }

    @Test fun everyPersonaUsedByTemplatesAndFillersHasInfo() {
        val names = ExperimentTemplates.all.flatMap { listOf(it.a.persona, it.b.persona) } +
            GameLogic.fillerPool.flatMap { listOf(it.left.persona, it.right.persona) }
        names.forEach { assertTrue("missing $it", GameLogic.personaInfo(it).blurb != "独一无二的你。") }
    }
}
