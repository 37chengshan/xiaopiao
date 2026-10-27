package com.xiaopiao.app.ui.game

import com.xiaopiao.app.domain.Badge
import com.xiaopiao.app.merchant.Experiment
import kotlin.random.Random

/** One side of a swipe card. Left swipe picks [left], right swipe picks [right]. */
data class GameChoice(val id: String, val label: String, val emoji: String, val persona: String)

data class GameRound(
    val id: String,
    val question: String,
    val left: GameChoice,
    val right: GameChoice,
    /** True for the round that comes from the merchant's experiment; only this answer is a 「游戏表态」. */
    val isExperiment: Boolean,
)

data class PersonaInfo(val name: String, val emoji: String, val blurb: String)

data class GameOutcome(val persona: PersonaInfo, val badge: Badge, val tally: List<Pair<String, Int>>)

object GameLogic {
    const val ROUNDS = 6
    private const val EXPERIMENT_SLOT = 2

    private fun filler(id: String, q: String, l: GameChoice, r: GameChoice) = GameRound(id, q, l, r, false)
    private fun c(id: String, label: String, emoji: String, persona: String) = GameChoice(id, label, emoji, persona)

    val fillerPool = listOf(
        filler("ice", "冰的还是热的？", c("hot", "热饮", "☕", "暖心派"), c("ice", "冰饮", "🧊", "冰饮战士")),
        filler("sweet", "甜度你站哪边？", c("full", "全糖快乐", "🍬", "甜党"), c("less", "少糖清爽", "🍃", "清爽派")),
        filler("fresh", "遇到新品时…", c("new", "先尝为敬", "🆕", "尝鲜派"), c("old", "老味道最稳", "🫖", "老味道派")),
        filler("time", "你更常在什么时候来一杯？", c("pm", "下午续命", "🌤️", "下午茶人"), c("night", "夜宵时刻", "🌙", "夜猫子")),
        filler("fruit", "果茶 vs 奶茶", c("fruit", "果茶", "🍑", "清爽派"), c("milk", "奶茶", "🧋", "甜党")),
        filler("size", "杯子大小？", c("big", "越大越好", "🥤", "大杯控"), c("small", "刚刚好", "🫗", "克制小可爱")),
        filler("shop", "买奶茶的方式？", c("walk", "走到店里买", "🚶", "到店派"), c("app", "提前下单自取", "📱", "计划派")),
        filler("friend", "喝奶茶的搭子？", c("solo", "自己一个人", "🎧", "独享派"), c("duo", "约朋友一起", "👯", "划算搭子")),
    )

    fun buildRounds(experiment: Experiment?, seed: Long): List<GameRound> {
        val rnd = Random(seed)
        val fillers = fillerPool.shuffled(rnd)
        val exp = experiment?.template?.let { t ->
            GameRound(
                id = "exp-${t.id}",
                question = t.question,
                left = GameChoice(t.a.id, t.a.title, t.a.emoji, t.a.persona),
                right = GameChoice(t.b.id, t.b.title, t.b.emoji, t.b.persona),
                isExperiment = true,
            )
        } ?: return fillers.take(ROUNDS)
        val rest = fillers.take(ROUNDS - 1).toMutableList()
        rest.add(EXPERIMENT_SLOT, exp)
        return rest
    }

    private val personas = mapOf(
        "小料党" to PersonaInfo("小料党", "🍠", "没有小料的奶茶是不完整的，你的杯底永远满满当当。"),
        "划算搭子" to PersonaInfo("划算搭子", "🥂", "好东西要分享，约上朋友一起喝才最开心。"),
        "积分收藏家" to PersonaInfo("积分收藏家", "🎟️", "每一分积分都被你好好收着，长期主义本主义。"),
        "打卡常客" to PersonaInfo("打卡常客", "📅", "固定的日子固定的一杯，仪式感拉满。"),
        "大杯控" to PersonaInfo("大杯控", "🥤", "要喝就喝痛快，杯子越大越安心。"),
        "克制小可爱" to PersonaInfo("克制小可爱", "🧋", "刚刚好就是最好，你很懂得适可而止。"),
        "冰饮战士" to PersonaInfo("冰饮战士", "🧊", "冬天也要加冰，你是行走的制冷机。"),
        "暖心派" to PersonaInfo("暖心派", "☕", "一杯热饮捧在手里，整个人都被治愈。"),
        "甜党" to PersonaInfo("甜党", "🍬", "人生已经够苦了，奶茶必须是甜的。"),
        "清爽派" to PersonaInfo("清爽派", "🍃", "清清爽爽才是你的节奏。"),
        "尝鲜派" to PersonaInfo("尝鲜派", "🆕", "菜单一上新你就第一个到。"),
        "老味道派" to PersonaInfo("老味道派", "🫖", "认定的味道就不会变，专一又可靠。"),
        "下午茶人" to PersonaInfo("下午茶人", "🌤️", "下午三点，一杯续命是必须的。"),
        "夜猫子" to PersonaInfo("夜猫子", "🌙", "夜深了，来一杯陪你的人。"),
        "到店派" to PersonaInfo("到店派", "🚶", "喜欢走进店里闻到香味的感觉。"),
        "计划派" to PersonaInfo("计划派", "📱", "提前安排好，到店即取。"),
        "独享派" to PersonaInfo("独享派", "🎧", "一杯奶茶，一首歌，自己的小世界。"),
    )

    fun personaInfo(name: String): PersonaInfo = personas[name] ?: PersonaInfo(name, "✨", "独一无二的你。")

    /** [answers] are the chosen [GameChoice]s in play order; ties go to the persona answered first. */
    fun outcome(answers: List<GameChoice>): GameOutcome {
        val tally = answers.groupingBy { it.persona }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key to it.value }
        val top = tally.firstOrNull()?.first ?: "尝鲜派"
        val info = personaInfo(top)
        val badge = Badge(
            code = "game_${info.name}",
            title = "『${info.name}』徽章",
            emoji = info.emoji,
            reason = "在「二选一大作战」里获得",
        )
        return GameOutcome(info, badge, tally)
    }
}
