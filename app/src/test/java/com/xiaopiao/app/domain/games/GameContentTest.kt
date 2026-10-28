package com.xiaopiao.app.domain.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants for the data-driven games: enough rounds to play, unique ids, no blank copy, and no score
 * that falls outside a result band (the content is the "schema", so it is tested like one).
 */
class GameContentTest {
    private val games = GameContent.games

    @Test
    fun eightOrMoreGamesEachWithFivePlusQuestions() {
        assertTrue("expected at least 8 games", games.size >= 8)
        assertEquals("game ids must be unique", games.size, games.map { it.id }.toSet().size)
        games.forEach { game ->
            assertTrue("${game.id}: needs at least 5 questions", game.questions.size >= 5)
            assertTrue("${game.id}: needs at least 5 rounds", game.rounds >= 5)
            assertTrue("${game.id}: minutes", game.minutes >= 1)
        }
    }

    @Test
    fun requiredGamesArePresent() {
        val ids = games.map { it.id }.toSet()
        listOf(
            "wallet-persona", "spend-duel", "receipt-guess", "blind-box",
            "daily-tasks", "save-challenge", "ledger-quiz", "spend-radar",
        ).forEach { id -> assertTrue("missing game $id", id in ids) }

        assertTrue("钱包人格测试 needs 12+ results", GameContent.byId("wallet-persona")!!.profiles.size >= 12)
        assertEquals("消费二选一 needs 30 duels", 30, GameContent.byId("spend-duel")!!.questions.size)
        assertEquals("小票猜猜看 needs 10 receipts", 10, GameContent.byId("receipt-guess")!!.questions.size)
        assertEquals("今日小任务 draws 10", 10, GameContent.byId("daily-tasks")!!.rounds)
        assertEquals("省钱大挑战 needs 8 scenes", 8, GameContent.byId("save-challenge")!!.questions.size)
        assertEquals("记账冷知识 needs 15 statements", 15, GameContent.byId("ledger-quiz")!!.questions.size)
        assertEquals("花式花钱雷达 needs 5 scales", 5, GameContent.byId("spend-radar")!!.questions.size)
    }

    @Test
    fun questionIdsAreUniqueAcrossAllGames() {
        val ids = games.flatMap { game -> game.questions.map { it.id } }
        assertEquals("question ids must be globally unique", ids.size, ids.toSet().size)
    }

    @Test
    fun optionIdsAreUniqueInsideEachQuestion() {
        games.forEach { game ->
            game.questions.forEach { question ->
                val ids = when (question) {
                    is ChoiceQuestion -> question.options.map { it.id }
                    is PriceQuestion -> question.options.map { it.toString() }
                    is RangeQuestion -> question.ranges.map { it.label }
                    is TrueFalseQuestion -> listOf("true", "false")
                    is TaskQuestion -> listOf(GameScoring.TASK_DONE, GameScoring.TASK_SKIP)
                    is ScaleQuestion -> emptyList()
                }
                assertEquals("${question.id}: option ids must be unique", ids.size, ids.toSet().size)
            }
        }
    }

    @Test
    fun noBlankCopy() {
        fun text(value: String, where: String) = assertTrue("$where is blank", value.isNotBlank())
        games.forEach { game ->
            text(game.id, "${game.id}.id")
            text(game.title, "${game.id}.title")
            text(game.subtitle, "${game.id}.subtitle")
            text(game.emoji, "${game.id}.emoji")
            game.profiles.forEach { profile ->
                text(profile.key, "${game.id}.profile.key")
                text(profile.emoji, "${game.id}.profile.emoji")
                text(profile.label, "${game.id}.profile.label")
                text(profile.blurb, "${game.id}.profile.blurb")
            }
            game.buckets.forEach { bucket ->
                text(bucket.emoji, "${game.id}.bucket.emoji")
                text(bucket.label, "${game.id}.bucket.label")
                text(bucket.blurb, "${game.id}.bucket.blurb")
                assertTrue("${game.id}: bucket range", bucket.minScore <= bucket.maxScore)
            }
            game.axes.forEach { axis ->
                text(axis.key, "${game.id}.axis.key")
                text(axis.emoji, "${game.id}.axis.emoji")
                text(axis.label, "${game.id}.axis.label")
                text(axis.blurb, "${game.id}.axis.blurb")
            }
            game.questions.forEach { question ->
                text(question.id, "${game.id}.question.id")
                text(question.prompt, "${question.id}.prompt")
                when (question) {
                    is ChoiceQuestion -> {
                        assertTrue("${question.id}: needs 2+ options", question.options.size >= 2)
                        question.options.forEach { option ->
                            text(option.id, "${question.id}.option.id")
                            text(option.label, "${question.id}.option.label")
                            if (question.layout == ChoiceLayout.Duel) {
                                text(option.emoji, "${question.id}.duel option emoji")
                            }
                        }
                    }
                    is PriceQuestion -> {
                        text(question.item, "${question.id}.item")
                        text(question.emoji, "${question.id}.emoji")
                        text(question.hint, "${question.id}.hint")
                        assertTrue("${question.id}: price must be positive", question.actualCents > 0)
                    }
                    is RangeQuestion -> {
                        text(question.item, "${question.id}.item")
                        text(question.emoji, "${question.id}.emoji")
                        text(question.hint, "${question.id}.hint")
                        assertTrue("${question.id}: price must be positive", question.actualCents > 0)
                    }
                    is TrueFalseQuestion -> text(question.explain, "${question.id}.explain")
                    is TaskQuestion -> {
                        text(question.emoji, "${question.id}.emoji")
                        text(question.tip, "${question.id}.tip")
                    }
                    is ScaleQuestion -> {
                        text(question.axis, "${question.id}.axis")
                        text(question.lowLabel, "${question.id}.lowLabel")
                        text(question.highLabel, "${question.id}.highLabel")
                    }
                }
            }
        }
    }

    @Test
    fun personaGamesUseEveryProfileAndNoStrangers() {
        games.filter { it.mode == GameMode.Persona }.forEach { game ->
            val keys = game.profiles.map { it.key }.toSet()
            val options = game.questions.filterIsInstance<ChoiceQuestion>().flatMap { it.options }
            assertTrue("${game.id}: no profiles", keys.isNotEmpty())
            assertEquals(
                "${game.id}: option points at an unknown profile",
                emptySet<String>(),
                options.mapNotNull { it.profile }.toSet() - keys,
            )
            assertEquals(
                "${game.id}: every option needs a profile",
                emptySet<String>(),
                options.filter { it.profile == null }.map { it.id }.toSet(),
            )
            assertEquals(
                "${game.id}: every profile must be reachable",
                keys,
                options.mapNotNull { it.profile }.toSet(),
            )
        }
    }

    @Test
    fun everyPossibleScoreHasABucket() {
        games.filter { it.mode == GameMode.Score }.forEach { game ->
            assertTrue("${game.id}: score games need buckets", game.buckets.isNotEmpty())
            val max = GameScoring.maxScore(game)
            assertTrue("${game.id}: max score must be positive", max > 0)
            assertTrue("${game.id}: min score must be >= 0", GameScoring.minScore(game) >= 0)
            for (score in 0..max) {
                assertNotNull("${game.id}: no bucket for $score", GameScoring.bucketFor(game, score))
            }
        }
    }

    @Test
    fun bucketsAreOrderedAndContiguous() {
        games.filter { it.mode == GameMode.Score }.forEach { game ->
            val sorted = game.buckets.sortedBy { it.minScore }
            assertEquals("${game.id}: buckets must start at 0", 0, sorted.first().minScore)
            sorted.zipWithNext().forEach { (a, b) ->
                assertEquals("${game.id}: bucket gap after ${a.maxScore}", a.maxScore + 1, b.minScore)
            }
            assertEquals(
                "${game.id}: last bucket must reach the max",
                GameScoring.maxScore(game),
                sorted.last().maxScore,
            )
        }
    }

    @Test
    fun priceQuestionsOfferExactlyOneCorrectTotal() {
        games.flatMap { it.questions }.filterIsInstance<PriceQuestion>().forEach { question ->
            assertEquals("${question.id}: prices must be distinct", question.options.size, question.options.toSet().size)
            assertEquals(
                "${question.id}: exactly one option equals the real price",
                1,
                question.options.count { it == question.actualCents },
            )
        }
    }

    @Test
    fun rangeQuestionsCoverTheRealPriceInExactlyOneBand() {
        games.flatMap { it.questions }.filterIsInstance<RangeQuestion>().forEach { question ->
            val sorted = question.ranges.sortedBy { it.minCents }
            assertEquals("${question.id}: bands must start at 0", 0L, sorted.first().minCents)
            sorted.zipWithNext().forEach { (a, b) ->
                assertEquals("${question.id}: band gap after ${a.maxCents}", a.maxCents + 1, b.minCents)
            }
            assertEquals(
                "${question.id}: the real price must sit in exactly one band",
                1,
                question.ranges.count { it.contains(question.actualCents) },
            )
            assertEquals(
                "${question.id}: band labels must be unique",
                question.ranges.size,
                question.ranges.map { it.label }.toSet().size,
            )
        }
    }

    @Test
    fun radarGamesAskEveryAxisOnce() {
        games.filter { it.mode == GameMode.Radar }.forEach { game ->
            assertTrue("${game.id}: needs 5+ axes", game.axes.size >= 5)
            val axes = game.axes.map { it.key }.toSet()
            val scales = game.questions.filterIsInstance<ScaleQuestion>()
            assertEquals("${game.id}: every question is a scale", game.questions.size, scales.size)
            scales.forEach { question ->
                assertTrue("${question.id}: unknown axis", question.axis in axes)
            }
            assertEquals("${game.id}: each axis must be asked", axes, scales.map { it.axis }.toSet())
        }
    }

    @Test
    fun sampledGamesDrawExactlyTheirRoundCount() {
        games.forEach { game ->
            val deck = GameScoring.deckFor(game, 42L)
            assertEquals("${game.id}: deck size", game.rounds, deck.size)
            assertEquals("${game.id}: deck ids", deck.size, deck.map { it.id }.toSet().size)
        }
    }

    @Test
    fun everyGameProducesAFilledOutcomeWithTheDisclaimer() {
        games.forEach { game ->
            val deck = GameScoring.deckFor(game, 7L)
            val answers = deck.map { question ->
                when (question) {
                    is ChoiceQuestion -> GameAnswer.Pick(question.options.last().id)
                    is PriceQuestion -> GameAnswer.Pick(question.options.last().toString())
                    is RangeQuestion -> GameAnswer.Pick(question.ranges.last().label)
                    is TrueFalseQuestion -> GameAnswer.Pick((!question.answer).toString())
                    is TaskQuestion -> GameAnswer.Pick(GameScoring.TASK_DONE)
                    is ScaleQuestion -> GameAnswer.Scale(5)
                }
            }
            val outcome = GameScoring.outcome(game, deck, answers)
            assertTrue("${game.id}: outcome emoji", outcome.emoji.isNotBlank())
            assertTrue("${game.id}: outcome title", outcome.title.isNotBlank())
            assertTrue("${game.id}: outcome blurb", outcome.blurb.isNotBlank())
            assertTrue("${game.id}: outcome score line", outcome.scoreLine.isNotBlank())
            assertTrue("${game.id}: share text needs the disclaimer", outcome.shareText.contains(GAME_DISCLAIMER))
            if (game.mode == GameMode.Radar) {
                assertEquals("${game.id}: radar points", game.axes.size, outcome.radar.size)
                outcome.radar.forEach { point ->
                    assertTrue("${game.id}: radar value", point.value in 0f..1f)
                }
            }
        }
    }

    @Test
    fun personaOutcomeIsStableOnTies() {
        val game = GameContent.byId("wallet-persona")!!
        val first = game.questions.first() as ChoiceQuestion
        // A single answer always resolves to that answer's profile.
        val only = GameScoring.outcome(game, listOf(first), listOf(GameAnswer.Pick(first.options.first().id)))
        val profile = game.profiles.first { it.key == first.options.first().profile }
        assertEquals(profile.label, only.title)
        // No answers at all falls back to the first profile instead of crashing.
        val empty = GameScoring.outcome(game, emptyList(), emptyList())
        assertEquals(game.profiles.first().label, empty.title)
    }
}
