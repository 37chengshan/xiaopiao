package com.xiaopiao.app.merchant

import kotlinx.serialization.Serializable

const val NO_GUARANTEE = "效果因店而异，不保证营收提升，最终由你决定。"
const val REVIEW_AFTER_DAYS = 14

data class Suggestion(
    val id: String,
    val headline: String,
    val detail: String,
    val tradeoff: String,
    val emoji: String,
)

/** The owner's choice, stored locally with a 「两周后复盘」 reminder note. */
@Serializable
data class Decision(
    val experimentId: String,
    val suggestionId: String,
    val headline: String,
    val chosenAt: Long,
    val reviewAt: Long,
    val reviewNote: String,
)

/** Rule/template based, deterministic. Always returns exactly three cards. */
object SuggestionGenerator {
    fun generate(template: ExperimentTemplate, result: ExperimentResult, quietSlot: String? = null): List<Suggestion> {
        val lead = result.leader.option
        val sharePct = (result.leader.share * 100).toInt()
        val slot = quietSlot ?: "客流较少的时段"
        val adopt = Suggestion(
            id = "adopt_${lead.id}",
            headline = lead.adoptHeadline,
            detail = "「${lead.title}」在这轮里领先，约 $sharePct% 的参与者更心动（${result.note.text}）。",
            tradeoff = lead.tradeoff,
            emoji = lead.emoji,
        )
        val pilot = Suggestion(
            id = "pilot_${lead.id}",
            headline = "先在${slot}小范围试点「${lead.title}」",
            detail = "不改全天菜单，只在${slot}试两周，成本和风险都小一些。",
            tradeoff = "试错成本低，但样本和声量也小，效果要更久才看得出来。",
            emoji = "🧪",
        )
        val third = if (result.marginPoints < 10 || result.sampleSize < 30 || result.intervalsOverlap) {
            Suggestion(
                id = "retest",
                headline = "再邀请顾客玩一轮，等数据更扎实",
                detail = "两个选项差距不大或样本偏少，多问一轮再决定也不迟。",
                tradeoff = "决策慢一点，但不容易被偶然结果带偏。",
                emoji = "🔁",
            )
        } else {
            val other = result.runnerUp.option
            Suggestion(
                id = "keep_both",
                headline = "两个都留着：把「${other.title}」当偶尔的小彩蛋",
                detail = "领先的当主推，「${other.title}」留给喜欢它的那部分顾客。",
                tradeoff = "照顾到不同口味，但菜单和话术会稍微复杂一点。",
                emoji = other.emoji,
            )
        }
        return listOf(adopt, pilot, third).map { it.copy(tradeoff = "${it.tradeoff} $NO_GUARANTEE") }
    }

    fun decide(experimentId: String, suggestion: Suggestion, now: Long): Decision = Decision(
        experimentId = experimentId,
        suggestionId = suggestion.id,
        headline = suggestion.headline,
        chosenAt = now,
        reviewAt = now + REVIEW_AFTER_DAYS * 24L * 60 * 60 * 1000,
        reviewNote = "两周后复盘：试了「${suggestion.headline}」之后，常客和客单有没有变化？（仅本机备忘）",
    )
}
