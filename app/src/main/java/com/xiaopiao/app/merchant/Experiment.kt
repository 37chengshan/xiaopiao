package com.xiaopiao.app.merchant

import kotlinx.serialization.Serializable
import kotlin.math.sqrt
import kotlin.random.Random

/** One side of a two-way preference question. [persona] feeds the consumer game's result screen. */
data class ExperimentOption(
    val id: String,
    val title: String,
    val blurb: String,
    val emoji: String,
    val persona: String,
    val adoptHeadline: String,
    val tradeoff: String,
)

data class ExperimentTemplate(
    val id: String,
    val title: String,
    val question: String,
    val a: ExperimentOption,
    val b: ExperimentOption,
) {
    val options: List<ExperimentOption> get() = listOf(a, b)
    fun option(id: String): ExperimentOption = if (id == b.id) b else a
}

object ExperimentTemplates {
    val HALF_VS_TOPPING = ExperimentTemplate(
        id = "half_vs_topping",
        title = "第二杯半价 vs 免费加小料",
        question = "二选一：这两个小福利，你更心动哪个？",
        a = ExperimentOption(
            "A", "第二杯半价", "约朋友一起来，第二杯 5 折", "🥂", "划算搭子",
            "把「第二杯半价」放进两周活动", "拉动结伴下单，但每单毛利会被摊薄；适合客流偏少的时段。",
        ),
        b = ExperimentOption(
            "B", "免费加小料", "任意一杯送一份小料", "🍠", "小料党",
            "把「免费加小料」放进两周活动", "更容易让顾客觉得被宠到，成本可控；小料备货要多留一点。",
        ),
    )
    val POINTS_VS_WEEKLY = ExperimentTemplate(
        id = "points_vs_weekly",
        title = "会员积分 vs 每周一杯折扣",
        question = "二选一：你更愿意拿哪种常客福利？",
        a = ExperimentOption(
            "A", "会员积分", "每杯累计积分，攒够换饮品", "🎟️", "积分收藏家",
            "试试把「会员积分」做成两周内双倍积分", "积分有沉淀感，但兑换前需要顾客记得回来；要留意积分成本。",
        ),
        b = ExperimentOption(
            "B", "每周一杯折扣", "每周固定一天，一杯 8 折", "📅", "打卡常客",
            "试试「每周一杯折扣」的固定日", "让到店更有节奏，容易养成习惯；折扣日的客流可能被集中。",
        ),
    )
    val LARGE_VS_MEDIUM = ExperimentTemplate(
        id = "large_vs_medium",
        title = "大杯加 2 元 vs 中杯原价",
        question = "二选一：同一杯，你怎么选？",
        a = ExperimentOption(
            "A", "大杯加 2 元", "多 100ml，加 2 元", "🥤", "大杯控",
            "把「大杯加 2 元」摆到菜单显眼位置", "客单价有机会提高一点，但价格敏感的顾客可能犹豫。",
        ),
        b = ExperimentOption(
            "B", "中杯原价", "标准杯量，价格不变", "🧋", "克制小可爱",
            "保持「中杯原价」，把它设为主推", "价格门槛低、不吓跑新客；客单价提升空间有限。",
        ),
    )
    val all = listOf(HALF_VS_TOPPING, POINTS_VS_WEEKLY, LARGE_VS_MEDIUM)
    fun byId(id: String): ExperimentTemplate = all.firstOrNull { it.id == id } ?: all.first()
}

@Serializable
enum class ExperimentStatus(val label: String) { DRAFT("草稿"), RUNNING("进行中"), ENDED("已结束") }

/** A response the real user gave while playing the game. Kept apart from transactions. */
@Serializable
data class GameResponse(val optionId: String, val at: Long)

@Serializable
data class Experiment(
    val id: String,
    val templateId: String,
    val status: ExperimentStatus = ExperimentStatus.DRAFT,
    val createdAt: Long,
    val gameResponses: List<GameResponse> = emptyList(),
) {
    val template: ExperimentTemplate get() = ExperimentTemplates.byId(templateId)
}

enum class Confidence { STABLE, EARLY, THIN }

data class ConfidenceNote(val level: Confidence, val text: String)

data class OptionStat(
    val option: ExperimentOption,
    val count: Int,
    val share: Double,
    val ciLow: Double,
    val ciHigh: Double,
)

data class ExperimentResult(
    val stats: List<OptionStat>,
    val sampleSize: Int,
    val simulatedCount: Int,
    val gameCount: Int,
    val note: ConfidenceNote,
) {
    /** Option with the larger share; ties go to A so output is deterministic. */
    val leader: OptionStat get() = stats.maxWith(compareBy<OptionStat> { it.count }.thenByDescending { it.option.id })
    val runnerUp: OptionStat get() = stats.first { it !== leader }
    val marginPoints: Double get() = (leader.share - runnerUp.share) * 100
    val intervalsOverlap: Boolean get() = leader.ciLow <= runnerUp.ciHigh
}

object Stats {
    /** Wilson score interval for a binomial proportion. Returns (0,1) when n == 0. */
    fun wilson(successes: Int, n: Int, z: Double = 1.96): Pair<Double, Double> {
        if (n <= 0) return 0.0 to 1.0
        val p = successes.toDouble() / n
        val z2 = z * z
        val denom = 1 + z2 / n
        val centre = (p + z2 / (2 * n)) / denom
        val half = z * sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / denom
        return (centre - half).coerceAtLeast(0.0) to (centre + half).coerceAtMost(1.0)
    }
}

object ExperimentEngine {
    private const val STABLE_MIN = 50
    private const val EARLY_MIN = 30

    /** Deterministic simulated answer for one crowd member, or null when they did not take part. */
    fun simulatedAnswer(member: CrowdMember, template: ExperimentTemplate): String? {
        val rnd = Random(member.id * 7919L + template.id.hashCode())
        if (rnd.nextDouble() >= 0.12 + 0.2 * member.loyaltyLean) return null
        val pA = when (template.id) {
            ExperimentTemplates.HALF_VS_TOPPING.id -> 0.2 + 0.6 * member.priceSensitivity * (1 - member.toppingLean) + 0.1
            ExperimentTemplates.POINTS_VS_WEEKLY.id -> 0.15 + 0.7 * member.loyaltyLean
            else -> 0.2 + 0.6 * (1 - member.priceSensitivity)
        }
        return if (rnd.nextDouble() < pA) template.a.id else template.b.id
    }

    fun result(experiment: Experiment, crowd: List<CrowdMember>): ExperimentResult {
        val template = experiment.template
        val simulated = crowd.mapNotNull { simulatedAnswer(it, template) }
        val game = experiment.gameResponses.map { it.optionId }
        val all = simulated + game
        val n = all.size
        val stats = template.options.map { opt ->
            val c = all.count { it == opt.id }
            val (lo, hi) = Stats.wilson(c, n)
            OptionStat(opt, c, if (n == 0) 0.0 else c.toDouble() / n, lo, hi)
        }
        return ExperimentResult(stats, n, simulated.size, game.size, confidenceNote(n, stats))
    }

    fun confidenceNote(n: Int, stats: List<OptionStat>): ConfidenceNote {
        val close = stats.size == 2 && stats[0].let { a -> stats[1].let { b -> a.ciLow <= b.ciHigh && b.ciLow <= a.ciHigh } }
        return when {
            n >= STABLE_MIN && !close -> ConfidenceNote(Confidence.STABLE, "样本 $n 人，结论较稳定")
            n >= STABLE_MIN -> ConfidenceNote(Confidence.EARLY, "样本 $n 人，两边比较接近，难分高下")
            n >= EARLY_MIN -> ConfidenceNote(Confidence.EARLY, "样本 $n 人，初步有倾向，建议再多邀请一些")
            else -> ConfidenceNote(Confidence.THIN, "样本偏少，仅供参考")
        }
    }
}
