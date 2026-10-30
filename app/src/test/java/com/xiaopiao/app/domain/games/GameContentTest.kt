package com.xiaopiao.app.domain.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content/engine contract (docs/APP-DESIGN.md §5.3): every game must be playable with the single
 * [GameScoring] engine, every answer shape must map to a result, and a game may never ship a question
 * the player cannot finish.
 */
class GameContentTest {
    private val games = GameContent.games

    @Test
    fun `every game has a unique id and a non-empty deck`() {
        assertEquals(11, games.size)
        assertEquals(games.size, games.map { it.id }.toSet().size)
        games.forEach { game ->
            assertTrue(game.id, game.questions.isNotEmpty())
            assertTrue(game.id, game.title.isNotBlank())
            assertTrue(game.id, game.minutes > 0)
            assertTrue(game.id, game.rounds in 1..game.questions.size)
        }
    }

    @Test
    fun `persona games only use options with a known profile`() {
        games.filter { it.mode == GameMode.Persona }.forEach { game ->
            val profiles = game.profiles.map { it.key }.toSet()
            assertTrue(game.id, profiles.isNotEmpty())
            game.questions.filterIsInstance<ChoiceQuestion>().forEach { question ->
                question.options.forEach { option ->
                    assertNotNull("${game.id}/${question.id}: ${option.id} has no profile", option.profile)
                    assertTrue("${game.id}/${question.id}: unknown profile ${option.profile}", option.profile in profiles)
                }
            }
        }
    }

    @Test
    fun `duel questions have exactly two options`() {
        games.flatMap { it.questions }.filterIsInstance<ChoiceQuestion>()
            .filter { it.layout == ChoiceLayout.Duel }
            .forEach { assertEquals(it.id, 2, it.options.size) }
    }

    @Test
    fun `price questions offer the real price`() {
        games.flatMap { it.questions }.filterIsInstance<PriceQuestion>().forEach { question ->
            assertTrue("${question.id} missing actual", question.options.contains(question.actualCents))
            assertEquals(question.id, question.options.size, question.options.toSet().size)
        }
    }

    @Test
    fun `range questions contain the real price in exactly one band`() {
        games.flatMap { it.questions }.filterIsInstance<RangeQuestion>().forEach { question ->
            assertEquals("${question.id}", 1, question.ranges.count { it.contains(question.actualCents) })
        }
    }

    @Test
    fun `scale questions point at declared axes`() {
        games.filter { it.mode == GameMode.Radar }.forEach { game ->
            val axes = game.axes.map { it.key }.toSet()
            assertTrue(game.id, axes.isNotEmpty())
            game.questions.filterIsInstance<ScaleQuestion>().forEach { question ->
                assertTrue("${game.id}: unknown axis ${question.axis}", question.axis in axes)
            }
            assertTrue(game.id, game.questions.all { it is ScaleQuestion })
        }
    }

    @Test
    fun `scoring maps price answers to one or zero`() {
        val question = games.flatMap { it.questions }.filterIsInstance<PriceQuestion>().first()
        val right = GameAnswer.Pick(question.actualCents.toString())
        val wrong = GameAnswer.Pick(question.options.first { it != question.actualCents }.toString())
        assertEquals(1, GameScoring.scoreFor(question, right))
        assertEquals(0, GameScoring.scoreFor(question, wrong))
    }

    @Test
    fun `range scoring accepts only the correct band`() {
        val question = games.flatMap { it.questions }.filterIsInstance<RangeQuestion>().first()
        val right = question.ranges.first { it.contains(question.actualCents) }
        val wrong = question.ranges.first { it != right }
        assertEquals(1, GameScoring.scoreFor(question, GameAnswer.Pick(right.label)))
        assertEquals(0, GameScoring.scoreFor(question, GameAnswer.Pick(wrong.label)))
    }

    @Test
    fun `every game produces an outcome for a full pass`() {
        games.forEach { game ->
            val deck = GameScoring.deckFor(game, seed = 7)
            val answers = deck.map { question ->
                when (question) {
                    is ChoiceQuestion -> GameAnswer.Pick(question.options.first().id)
                    is PriceQuestion -> GameAnswer.Pick(question.actualCents.toString())
                    is RangeQuestion -> GameAnswer.Pick(question.ranges.first { it.contains(question.actualCents) }.label)
                    is TrueFalseQuestion -> GameAnswer.Pick(question.answer.toString())
                    is TaskQuestion -> GameAnswer.Pick("done")
                    is ScaleQuestion -> GameAnswer.Scale(5)
                }
            }
            val outcome = GameScoring.outcome(game, deck, answers)
            assertTrue(game.id, outcome.title.isNotBlank())
            assertTrue(game.id, outcome.blurb.isNotBlank())
            assertTrue(game.id, outcome.shareText.isNotBlank())
            if (game.mode == GameMode.Radar) {
                assertEquals(game.id, game.axes.size, outcome.radar.size)
                outcome.radar.forEach { point -> assertTrue(game.id, point.value in 0f..100f) }
            }
        }
    }

    @Test
    fun `personality games come in both flavours and cover every category`() {
        val personality = games.filter { it.category == GameCategory.Personality }
        assertTrue("性格类太少", personality.size >= 4)
        assertTrue("缺人格类", personality.any { it.mode == GameMode.Persona })
        assertTrue("缺雷达类", personality.any { it.mode == GameMode.Radar })
        GameCategory.entries.forEach { category ->
            assertTrue("分组为空：$category", games.any { it.category == category })
        }
    }

    @Test
    fun `radar games keep five or six axes and print an analysis per run`() {
        games.filter { it.mode == GameMode.Radar }.forEach { game ->
            assertTrue("${game.id}: 轴太少", game.axes.size in 5..6)
            val deck = game.questions
            val answers = deck.map { question ->
                when (question) {
                    is ScaleQuestion -> GameAnswer.Scale(4)
                    else -> GameAnswer.Pick((question as? ChoiceQuestion)?.options?.firstOrNull()?.id ?: "x")
                }
            }
            val outcome = GameScoring.outcome(game, deck, answers)
            assertEquals("${game.id}: 需要三条分析", 3, outcome.insights.size)
            outcome.insights.forEach { assertTrue("${game.id}: 分析为空", it.isNotBlank()) }
        }
    }

    @Test
    fun `persona results carry three lines of analysis`() {
        games.filter { it.mode == GameMode.Persona }.forEach { game ->
            game.profiles.forEach { profile ->
                assertTrue("${game.id}/${profile.key}: 缺分析", profile.insights.size == 3)
            }
        }
    }

    @Test
    fun `persona outcome follows the most picked profile`() {
        val game = games.first { it.mode == GameMode.Persona }
        val question = game.questions.filterIsInstance<ChoiceQuestion>().first()
        val option = question.options.first()
        val deck = listOf(question)
        val outcome = GameScoring.outcome(game, deck, listOf(GameAnswer.Pick(option.id)))
        val profile = game.profiles.first { it.key == option.profile }
        assertEquals(profile.label, outcome.title)
    }

    @Test
    fun `wrong answers map to the lowest bucket - never crash`() {
        games.filter { it.mode == GameMode.Score }.forEach { game ->
            val deck = GameScoring.deckFor(game, seed = 3)
            val answers = deck.map { question ->
                when (question) {
                    is ChoiceQuestion -> GameAnswer.Pick(question.options.last().id)
                    is PriceQuestion -> GameAnswer.Pick("-1")
                    is RangeQuestion -> GameAnswer.Pick("__none__")
                    is TrueFalseQuestion -> GameAnswer.Pick((!question.answer).toString())
                    is TaskQuestion -> GameAnswer.Pick("skip")
                    is ScaleQuestion -> GameAnswer.Scale(1)
                }
            }
            val outcome = GameScoring.outcome(game, deck, answers)
            assertFalse(game.id, outcome.title.isBlank())
        }
    }
}
