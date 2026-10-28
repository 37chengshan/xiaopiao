package com.xiaopiao.app.merchant

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the merchant lane persists. Pure, immutable; every mutation returns a new copy. */
@Serializable
data class MerchantState(
    val experiment: Experiment? = null,
    val decisions: List<Decision> = emptyList(),
    val badgeCodes: List<String> = emptyList(),
    val gamesPlayed: Int = 0,
) {
    fun publish(template: ExperimentTemplate, now: Long): MerchantState = copy(
        experiment = Experiment(
            id = "exp-$now", templateId = template.id, status = ExperimentStatus.RUNNING, createdAt = now,
        ),
    )

    /** A game answer only counts toward a running experiment. */
    fun addGameResponse(optionId: String, now: Long): MerchantState {
        val exp = experiment ?: return this
        if (exp.status != ExperimentStatus.RUNNING) return this
        return copy(experiment = exp.copy(gameResponses = exp.gameResponses + GameResponse(optionId, now)))
    }

    fun finishGame(badgeCode: String?): MerchantState = copy(
        gamesPlayed = gamesPlayed + 1,
        badgeCodes = if (badgeCode == null || badgeCode in badgeCodes) badgeCodes else badgeCodes + badgeCode,
    )

    fun adopt(suggestion: Suggestion, now: Long): MerchantState {
        val exp = experiment ?: return this
        return copy(
            experiment = exp.copy(status = ExperimentStatus.ENDED),
            decisions = decisions + SuggestionGenerator.decide(exp.id, suggestion, now),
        )
    }

    fun decisionFor(experimentId: String): Decision? = decisions.lastOrNull { it.experimentId == experimentId }

    fun clearExperiment(): MerchantState = copy(experiment = null)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun encode(state: MerchantState): String = json.encodeToString(serializer(), state)
        fun decode(text: String): MerchantState =
            runCatching { json.decodeFromString(serializer(), text) }.getOrDefault(MerchantState())
    }
}
