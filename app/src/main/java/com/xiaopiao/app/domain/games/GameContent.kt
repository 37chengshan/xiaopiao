package com.xiaopiao.app.domain.games

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.yuan
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Data-driven mini games for the 圈子 tab (docs/APP-DESIGN.md §5.3).
 *
 * Structure follows the open-source quiz/personality engines we looked at
 * (e.g. github.com/KatherineGuoGuo/bigfive-open): questions + scoring live in plain Kotlin with no UI,
 * the player renders [GameQuestion] variants, and result bands are declared next to the content.
 * Every screen that shows one of these games must print [GAME_DISCLAIMER].
 */

const val GAME_DISCLAIMER = "娱乐，不构成建议"

/** 游戏页分组：性格测试 / 猜价格 / 小挑战。 */
enum class GameCategory { Personality, Guess, Challenge }

/** How a game turns answers into a result. */
enum class GameMode {
    /** Counts how often each [GameDefinition.profiles] key was picked; the top one wins. */
    Persona,
    /** Adds points and looks the total up in [GameDefinition.buckets]. */
    Score,
    /** Averages 1–5 scale answers per axis and shows a radar. */
    Radar,
}

/** How the big number on the result screen reads. */
enum class ScoreStyle { Correct, SavedCents, TasksDone, Points }

sealed interface GameQuestion {
    val id: String
    val prompt: String
}

/** One tappable answer. [profile] is used by persona games, [score] by score games. */
data class GameOption(
    val id: String,
    val label: String,
    val note: String = "",
    val emoji: String = "",
    val score: Int = 0,
    val profile: String? = null,
)

enum class ChoiceLayout { List, Duel }

data class ChoiceQuestion(
    override val id: String,
    override val prompt: String,
    val options: List<GameOption>,
    val hint: String = "",
    val layout: ChoiceLayout = ChoiceLayout.List,
) : GameQuestion

/** Guess the receipt total. [options] are the buttons; exactly one equals [actualCents]. */
data class PriceQuestion(
    override val id: String,
    override val prompt: String,
    val item: String,
    val emoji: String,
    val hint: String,
    val actualCents: Cents,
    val options: List<Cents>,
) : GameQuestion

/** A closed price band. [maxCents] is inclusive; the last band may use a large sentinel. */
data class PriceRange(val label: String, val minCents: Cents, val maxCents: Cents) {
    fun contains(cents: Cents): Boolean = cents in minCents..maxCents
}

/** Blind-box guess: pick the band the real price falls into. */
data class RangeQuestion(
    override val id: String,
    override val prompt: String,
    val item: String,
    val emoji: String,
    val hint: String,
    val actualCents: Cents,
    val ranges: List<PriceRange>,
) : GameQuestion

data class TrueFalseQuestion(
    override val id: String,
    override val prompt: String,
    val answer: Boolean,
    val explain: String,
) : GameQuestion

/** A one-day challenge. Answering「完成」 earns one point; the player samples [GameDefinition.sampleCount]. */
data class TaskQuestion(
    override val id: String,
    override val prompt: String,
    val emoji: String,
    val tip: String,
) : GameQuestion

/** A 1–5 answer that feeds [axis]. [reverse] 为反向题（同意越多，该维度越低）。 */
data class ScaleQuestion(
    override val id: String,
    override val prompt: String,
    val axis: String,
    val lowLabel: String,
    val highLabel: String,
    val reverse: Boolean = false,
) : GameQuestion

data class GameProfile(
    val key: String,
    val emoji: String,
    val label: String,
    val blurb: String,
    /** 结果页的三行小字：行为特征 / 容易卡住 / 可以试试。 */
    val insights: List<String> = emptyList(),
)

data class GameBucket(
    val minScore: Int,
    val maxScore: Int,
    val emoji: String,
    val label: String,
    val blurb: String,
    val insights: List<String> = emptyList(),
)

data class GameAxis(
    val key: String,
    val emoji: String,
    val label: String,
    val blurb: String,
    /** 低 / 中 / 高三档的一句话解释；空则不显示。 */
    val bands: List<String> = emptyList(),
) {
    /** 按 0..1 的得分取档位解释。 */
    fun band(value: Float): String? {
        if (bands.size < 3) return null
        return when {
            value < 0.34f -> bands[0]
            value < 0.67f -> bands[1]
            else -> bands[2]
        }
    }
}

data class GameDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val minutes: Int,
    val mode: GameMode,
    val category: GameCategory = GameCategory.Personality,
    val questions: List<GameQuestion>,
    val scoreStyle: ScoreStyle = ScoreStyle.Points,
    /** Draw this many questions per run (seeded). Null plays every question in order. */
    val sampleCount: Int? = null,
    val profiles: List<GameProfile> = emptyList(),
    val buckets: List<GameBucket> = emptyList(),
    val axes: List<GameAxis> = emptyList(),
    /** One extra line on the result screen (e.g.「游戏内金额」). */
    val note: String = "",
) {
    val rounds: Int get() = sampleCount ?: questions.size
}

/** A picked option or a scale value. Indexed by the question position in the round deck. */
sealed interface GameAnswer {
    data class Pick(val optionId: String) : GameAnswer
    data class Scale(val value: Int) : GameAnswer
}

data class RadarPoint(val label: String, val value: Float)

data class GameOutcome(
    val emoji: String,
    val title: String,
    val blurb: String,
    val scoreLine: String,
    val shareText: String,
    val radar: List<RadarPoint> = emptyList(),
    /** 结果页正文下面的三条分析（人格类来自 profile，分数类来自档位）。 */
    val insights: List<String> = emptyList(),
)

/** Pure scoring: no Compose, no I/O. Covered by GameContentTest. */
object GameScoring {
    fun deckFor(game: GameDefinition, seed: Long): List<GameQuestion> {
        val count = game.sampleCount ?: return game.questions
        if (count >= game.questions.size) return game.questions
        return game.questions.shuffled(Random(seed)).take(count)
    }

    /** Points this answer is worth. Correct guesses are 1, wrong ones 0. */
    fun scoreFor(question: GameQuestion, answer: GameAnswer): Int = when (question) {
        is ChoiceQuestion ->
            if (answer is GameAnswer.Pick) question.options.firstOrNull { it.id == answer.optionId }?.score ?: 0 else 0
        is PriceQuestion ->
            if (answer is GameAnswer.Pick && answer.optionId.toLongOrNull() == question.actualCents) 1 else 0
        is RangeQuestion ->
            if (answer is GameAnswer.Pick) {
                val picked = question.ranges.firstOrNull { it.label == answer.optionId }
                if (picked?.contains(question.actualCents) == true) 1 else 0
            } else 0
        is TrueFalseQuestion ->
            if (answer is GameAnswer.Pick && answer.optionId == question.answer.toString()) 1 else 0
        is TaskQuestion ->
            if (answer is GameAnswer.Pick && answer.optionId == TASK_DONE) 1 else 0
        is ScaleQuestion -> 0
    }

    const val TASK_DONE = "done"
    const val TASK_SKIP = "skip"

    /** Highest total a full round can reach; used by the result bucketing and its test. */
    fun maxScore(game: GameDefinition): Int =
        game.questions.map(::maxOfQuestion).sortedDescending().take(game.rounds).sum()

    fun minScore(game: GameDefinition): Int =
        game.questions.map(::minOfQuestion).sorted().take(game.rounds).sum()

    private fun maxOfQuestion(question: GameQuestion): Int = when (question) {
        is ChoiceQuestion -> question.options.maxOfOrNull { it.score } ?: 0
        is PriceQuestion, is RangeQuestion, is TrueFalseQuestion, is TaskQuestion -> 1
        is ScaleQuestion -> 0
    }

    private fun minOfQuestion(question: GameQuestion): Int = when (question) {
        is ChoiceQuestion -> question.options.minOfOrNull { it.score } ?: 0
        is PriceQuestion, is RangeQuestion, is TrueFalseQuestion, is TaskQuestion, is ScaleQuestion -> 0
    }

    /** The band containing [score], or null when the content has a hole (the test forbids that). */
    fun bucketFor(game: GameDefinition, score: Int): GameBucket? =
        game.buckets.firstOrNull { score in it.minScore..it.maxScore }

    fun totalScore(deck: List<GameQuestion>, answers: List<GameAnswer>): Int =
        deck.indices.sumOf { i -> answers.getOrNull(i)?.let { scoreFor(deck[i], it) } ?: 0 }

    fun outcome(game: GameDefinition, deck: List<GameQuestion>, answers: List<GameAnswer>): GameOutcome =
        when (game.mode) {
            GameMode.Persona -> personaOutcome(game, deck, answers)
            GameMode.Score -> scoreOutcome(game, deck, answers)
            GameMode.Radar -> radarOutcome(game, deck, answers)
        }

    private fun personaOutcome(
        game: GameDefinition,
        deck: List<GameQuestion>,
        answers: List<GameAnswer>,
    ): GameOutcome {
        val tally = mutableMapOf<String, Int>()
        deck.forEachIndexed { i, question ->
            val answer = answers.getOrNull(i) as? GameAnswer.Pick ?: return@forEachIndexed
            val option = (question as? ChoiceQuestion)?.options?.firstOrNull { it.id == answer.optionId }
            val key = option?.profile ?: return@forEachIndexed
            tally[key] = (tally[key] ?: 0) + 1
        }
        // maxByOrNull keeps the first profile on a tie, so the result is deterministic.
        val top = game.profiles.maxByOrNull { tally[it.key] ?: 0 } ?: game.profiles.first()
        val hits = tally[top.key] ?: 0
        return GameOutcome(
            emoji = top.emoji,
            title = top.label,
            blurb = top.blurb,
            insights = top.insights,
            scoreLine = "${answers.size} 次选择里，$hits 次指向它",
            shareText = "我在「${game.title}」里是 ${top.emoji}${top.label}：${top.blurb} $GAME_DISCLAIMER",
        )
    }

    private fun scoreOutcome(
        game: GameDefinition,
        deck: List<GameQuestion>,
        answers: List<GameAnswer>,
    ): GameOutcome {
        val total = totalScore(deck, answers)
        val bucket = bucketFor(game, total) ?: game.buckets.last()
        val line = when (game.scoreStyle) {
            ScoreStyle.Correct -> "答对 $total / ${deck.size}"
            ScoreStyle.SavedCents -> "本局省下 ¥${total.toLong().yuan()}"
            ScoreStyle.TasksDone -> "完成 $total / ${deck.size} 项"
            ScoreStyle.Points -> "$total 分"
        }
        return GameOutcome(
            emoji = bucket.emoji,
            title = bucket.label,
            blurb = bucket.blurb,
            insights = bucket.insights,
            scoreLine = line,
            shareText = "我在「${game.title}」里$line，拿到 ${bucket.emoji}${bucket.label}。$GAME_DISCLAIMER",
        )
    }

    private fun radarOutcome(
        game: GameDefinition,
        deck: List<GameQuestion>,
        answers: List<GameAnswer>,
    ): GameOutcome {
        val sums = game.axes.associate { it.key to mutableListOf<Int>() }
        deck.forEachIndexed { i, question ->
            if (question !is ScaleQuestion) return@forEachIndexed
            val raw = (answers.getOrNull(i) as? GameAnswer.Scale)?.value ?: return@forEachIndexed
            val value = if (question.reverse) 6 - raw else raw
            sums[question.axis]?.add(value)
        }
        val points = game.axes.map { axis ->
            val values = sums[axis.key].orEmpty()
            val average = if (values.isEmpty()) 3.0 else values.average()
            RadarPoint(axis.label, ((average - 1.0) / 4.0).toFloat().coerceIn(0f, 1f))
        }
        val topIndex = points.indices.maxByOrNull { points[it].value } ?: 0
        val lowIndex = points.indices.minByOrNull { points[it].value } ?: 0
        val topAxis = game.axes[topIndex]
        val lowAxis = game.axes[lowIndex]
        val top = points[topIndex]
        val percent = (top.value * 100).roundToInt()
        val lowPercent = (points[lowIndex].value * 100).roundToInt()
        return GameOutcome(
            emoji = topAxis.emoji,
            title = "「${topAxis.label}」最高",
            blurb = topAxis.blurb,
            scoreLine = "${game.axes.size} 维雷达 · ${topAxis.label} $percent%",
            shareText = "我的${game.title}：" +
                points.joinToString(" · ") { "${it.label} ${(it.value * 100).roundToInt()}%" } +
                "。$GAME_DISCLAIMER",
            radar = points,
            insights = buildList {
                add("最强：${topAxis.label} $percent% —— ${topAxis.blurb}")
                if (lowAxis.key == topAxis.key) {
                    add("各维打平：没有一面压倒其他，你在不同场景里切换得比较平均。")
                    add("均衡的好处是适应力强，代价是少了记忆点；想更清楚，可以挑一维刻意练一练。")
                } else {
                    add("最弱：${lowAxis.label} $lowPercent% —— ${lowAxis.blurb}")
                    add("两端差 ${(percent - lowPercent).coerceAtLeast(0)} 个百分点：这是你花钱时最容易被自己牵动的地方。")
                }
            },
        )
    }
}

/**
 * Duel option emojis. Declared at file scope so it initialises before [GameContent] builds its questions;
 * every key is exercised by GameContentTest (a missing one fails the test, not the player).
 */
private val DUEL_EMOJI = mapOf(
    "duel-1a" to "📊", "duel-1b" to "🎲",
    "duel-2a" to "🗓️", "duel-2b" to "🛌",
    "duel-3a" to "🧾", "duel-3b" to "🍜",
    "duel-4a" to "🗺️", "duel-4b" to "🚪",
    "duel-5a" to "📋", "duel-5b" to "🎫",
    "duel-6a" to "📉", "duel-6b" to "📦",
    "duel-7a" to "📝", "duel-7b" to "🛒",
    "duel-8a" to "🏦", "duel-8b" to "🍰",
    "duel-9a" to "🎁", "duel-9b" to "🛍️",
    "duel-10a" to "⭐", "duel-10b" to "🎬",
    "duel-11a" to "👕", "duel-11b" to "💖",
    "duel-12a" to "🧮", "duel-12b" to "🏃",
    "duel-13a" to "🚇", "duel-13b" to "🚕",
    "duel-14a" to "🏋️", "duel-14b" to "🧘",
    "duel-15a" to "🫱", "duel-15b" to "🤝",
    "duel-16a" to "📈", "duel-16b" to "🎢",
    "duel-17a" to "🍽️", "duel-17b" to "😋",
    "duel-18a" to "🔁", "duel-18b" to "📅",
    "duel-19a" to "🏨", "duel-19b" to "🎒",
    "duel-20a" to "🌦️", "duel-20b" to "🌞",
    "duel-21a" to "📚", "duel-21b" to "🚀",
    "duel-22a" to "🔧", "duel-22b" to "🆕",
    "duel-23a" to "🎀", "duel-23b" to "🎈",
    "duel-24a" to "📒", "duel-24b" to "🧠",
    "duel-25a" to "📷", "duel-25b" to "🤳",
    "duel-26a" to "🥗", "duel-26b" to "🌙",
    "duel-27a" to "🧴", "duel-27b" to "🥤",
    "duel-28a" to "🎯", "duel-28b" to "🪙",
    "duel-29a" to "↩️", "duel-29b" to "📦",
    "duel-30a" to "🌅", "duel-30b" to "🎇",
)

/** The eight playable games. All copy is local; nothing leaves the device. */
object GameContent {
    val games: List<GameDefinition> = listOf(
        walletPersona(),
        PersonalityGames.spendProfile(),
        PersonalityGames.savingStyle(),
        PersonalityGames.bigFive(),
        spendDuel(),
        receiptGuess(),
        blindBox(),
        dailyTasks(),
        saveChallenge(),
        ledgerQuiz(),
        spendRadar(),
    )

    fun byId(id: String): GameDefinition? = games.firstOrNull { it.id == id }

    // ---------------------------------------------------------------- 1. 钱包人格测试

    private fun walletPersona(): GameDefinition = GameDefinition(
        id = "wallet-persona",
        category = GameCategory.Personality,
        title = "钱包人格测试",
        subtitle = "10 个问题，看看你的钱包是什么性格",
        emoji = "👛",
        minutes = 3,
        mode = GameMode.Persona,
        profiles = listOf(
            GameProfile("planner", "🧮", "精算师", "每一分钱都有去处，账本比日历还整齐。").withInsights("账本清楚是你最稳的安全感", "为几块钱反复比价，时间成本更高", "给小额消费设一个「不用算」的上限"),
            GameProfile("vibe", "🌈", "氛围选手", "买的是心情，不是东西；开心就值。").withInsights("情绪到位就愿意花钱", "心情差的时候最容易超支", "下单前先放一晚，第二天还想要再买"),
            GameProfile("coupon", "🎟️", "囤券达人", "券包比钱包鼓，过期前一定用完。").withInsights("券让你觉得占了便宜", "为了用券买过本来不需要的东西", "先问：没有这张券我还会买吗"),
            GameProfile("stealth", "🕶️", "隐形富豪", "平时不声不响，花起来毫不手软。").withInsights("平时很省，偶尔一次大花", "大额决定容易跳过预算检查", "把大额支出提前写进当月计划"),
            GameProfile("moonlight", "🌙", "月光诗人", "月底的诗，都写在余额里。").withInsights("月初宽松，月底紧张", "中间不检查，问题总在最后爆发", "月中看一眼余额，比月底后悔有用"),
            GameProfile("saver", "🪙", "铁公鸡", "不是没钱，是舍不得。").withInsights("不花钱本身就让你安心", "该换的东西一直拖，最后更贵", "给必须项留一笔不用犹豫的额度"),
            GameProfile("adventurer", "🎢", "体验派", "钱要花在没做过的事上。").withInsights("为没做过的事花钱不心疼", "体验越多，账单越厚", "先定体验预算，超了就换下一次"),
            GameProfile("practical", "🔧", "实用主义", "能修就不换，能用就不买。").withInsights("能修就不换，能用就不买", "省了钱但用着不顺手，最后还是换", "把时间成本算进去再决定修还是换"),
            GameProfile("host", "🍻", "社交买单侠", "抢单速度比谁都快。").withInsights("抢单让你有面子", "事后回想会有点肉疼", "和朋友约好轮流请，比抢单轻松"),
            GameProfile("hunter", "🏹", "折扣猎人", "原价两个字，让你睡不着。").withInsights("原价两个字让你难受", "为折扣买过不需要的东西", "先列需求，再去找折扣"),
            GameProfile("collector", "🗃️", "收藏家", "喜欢的东西，要集齐一整套。").withInsights("成套才舒服", "补齐全套的成本常常超出预期", "先买一件试试，再决定要不要整组"),
            GameProfile("zen", "🍵", "佛系消费者", "买不买都行，随缘。").withInsights("买不买都行，随缘", "没被记录的支出最容易失控", "只做一件事：把它记下来"),
            GameProfile("ritual", "🕯️", "仪式感大师", "贵不贵不重要，氛围要对。").withInsights("氛围值得付溢价", "为氛围付的钱事后很难解释", "把仪式感放到低成本的场景里"),
            GameProfile("impulse", "⚡", "冲动星人", "下单的手指，比大脑快。").withInsights("下单的手指比大脑快", "退货和后悔是你的常客", "结算前关掉页面 10 分钟"),
        ),
        questions = listOf(
            choice(
                "wp-1", "发工资那天，你最先做什么？",
                o("wp-1a", "先把固定开销和存款分好", "planner", "🧮"),
                o("wp-1b", "翻一翻购物车里躺了很久的东西", "impulse", "⚡"),
                o("wp-1c", "约朋友吃一顿好的", "host", "🍻"),
            ),
            choice(
                "wp-2", "路过一家没去过的小店，你会？",
                o("wp-2a", "先看看价格，再决定进不进", "hunter", "🏹"),
                o("wp-2b", "直接进去逛逛，感受一下", "adventurer", "🎢"),
                o("wp-2c", "看橱窗就够了，不一定要买", "zen", "🍵"),
            ),
            choice(
                "wp-3", "购物车里放了三个月的东西，现在怎么样了？",
                o("wp-3a", "还在，说明当时没想清楚，删掉", "practical", "🔧"),
                o("wp-3b", "还在，等一个大促再下手", "coupon", "🎟️"),
                o("wp-3c", "早就买了，谁还记得", "impulse", "⚡"),
            ),
            choice(
                "wp-4", "朋友聚会，你更习惯怎么付钱？",
                o("wp-4a", "AA 最公平，当场算清", "planner", "🧮"),
                o("wp-4b", "我来吧，开心最重要", "host", "🍻"),
                o("wp-4c", "谁方便谁先付，回头再说", "zen", "🍵"),
            ),
            choice(
                "wp-5", "你怎么看待「贵一点但好看」？",
                o("wp-5a", "好看也是价值，愿意为它买单", "vibe", "🌈"),
                o("wp-5b", "先想想能不能用够久", "practical", "🔧"),
                o("wp-5c", "等打折，好看还便宜才完美", "hunter", "🏹"),
            ),
            choice(
                "wp-6", "月底看到账户余额，你的反应是？",
                o("wp-6a", "打开账本，核对每一笔", "planner", "🧮"),
                o("wp-6b", "下个月一定重新做人", "moonlight", "🌙"),
                o("wp-6c", "该花就花，反正还会再有", "stealth", "🕶️"),
            ),
            choice(
                "wp-7", "收到一个快递，最让你开心的是？",
                o("wp-7a", "拆开那一刻的期待感", "ritual", "🕯️"),
                o("wp-7b", "东西比想象中好用", "practical", "🔧"),
                o("wp-7c", "价格比平时便宜不少", "hunter", "🏹"),
            ),
            choice(
                "wp-8", "你更容易在哪种情况下掏钱？",
                o("wp-8a", "限量、联名、最后一件", "collector", "🗃️"),
                o("wp-8b", "朋友都在买，不买跟不上", "host", "🍻"),
                o("wp-8c", "今天心情特别好", "vibe", "🌈"),
            ),
            choice(
                "wp-9", "关于优惠券，你通常是？",
                o("wp-9a", "收藏一堆，过期前拼命用", "coupon", "🎟️"),
                o("wp-9b", "用得上的才领", "practical", "🔧"),
                o("wp-9c", "懒得研究规则", "zen", "🍵"),
            ),
            choice(
                "wp-10", "如果明天有一笔意外之财？",
                o("wp-10a", "存起来，或者还掉该还的", "saver", "🪙"),
                o("wp-10b", "去做一件一直想做的事", "adventurer", "🎢"),
                o("wp-10c", "买那个看了很久的东西", "collector", "🗃️"),
            ),
        ),
    )

    // ---------------------------------------------------------------- 2. 消费二选一

    private fun spendDuel(): GameDefinition = GameDefinition(
        id = "spend-duel",
        category = GameCategory.Personality,
        title = "消费二选一",
        subtitle = "30 道两难，看看你的花钱路数",
        emoji = "⚔️",
        minutes = 5,
        mode = GameMode.Persona,
        profiles = listOf(
            GameProfile("calc", "📊", "精算派", "先算再花，账目清楚才安心。").withInsights("先算再花，账目清楚才安心", "算太久会错过真正想要的东西", "给日常小额设一个「不算」区"),
            GameProfile("spontaneous", "🎈", "随性派", "感觉对了就出发，计划留给明天。").withInsights("感觉对了就出发", "惊喜多，超支也多", "出门前只定一个总上限"),
            GameProfile("planner", "🗓️", "计划派", "提前安排，按部就班最舒服。").withInsights("提前安排好一切最舒服", "计划被打乱时容易额外花钱", "给意外留出 10% 的余量"),
            GameProfile("hedonist", "🍰", "享乐派", "当下开心最重要，快乐不打折。").withInsights("当下开心最重要，快乐不打折", "月底容易和自己原本的计划打架", "把享乐的钱单独放在一边"),
        ),
        questions = listOf(
            duel("duel-1", "想买个大件，你会？", "先做参数对比", "看中就下单", "calc", "spontaneous"),
            duel("duel-2", "周末两天怎么过？", "提前排好行程", "睡到自然醒再说", "planner", "hedonist"),
            duel("duel-3", "点外卖时，你更像？", "凑满减算到分", "想吃啥点啥", "calc", "hedonist"),
            duel("duel-4", "临时被约出门，你会？", "先问清楚去哪", "换好衣服就走", "planner", "spontaneous"),
            duel("duel-5", "旅行前，你通常？", "做一张详细清单", "订张票就出发", "planner", "spontaneous"),
            duel("duel-6", "新手机发布，你会？", "等三个月看口碑", "首发就想要", "calc", "hedonist"),
            duel("duel-7", "逛超市时，你更像？", "按清单买", "逛到哪拿到哪", "planner", "spontaneous"),
            duel("duel-8", "发工资那天，你会？", "先转一笔去存", "先吃顿好的", "calc", "hedonist"),
            duel("duel-9", "朋友生日，你通常？", "提前挑好礼物", "当天顺手买", "planner", "spontaneous"),
            duel("duel-10", "看电影前，你会？", "先看评分", "到了影院再选", "calc", "hedonist"),
            duel("duel-11", "买衣服时，你更看重？", "基础款穿三年", "喜欢最重要", "calc", "hedonist"),
            duel("duel-12", "大促当天，你会？", "提前列好清单", "边逛边买", "planner", "spontaneous"),
            duel("duel-13", "出行时，你通常？", "算一下哪个划算", "累了就打车", "calc", "hedonist"),
            duel("duel-14", "想运动时，你会？", "先算单次成本", "想动就动", "calc", "spontaneous"),
            duel("duel-15", "交新朋友，你更像？", "慢慢了解", "聊得来就熟", "planner", "spontaneous"),
            duel("duel-16", "考虑换工作，你更看重？", "算清收入和通勤", "感觉对了就跳", "calc", "hedonist"),
            duel("duel-17", "挑餐厅时，你会？", "看人均和评分", "想吃就进", "calc", "hedonist"),
            duel("duel-18", "订会员时，你会？", "用完再续", "一次开一年省事", "calc", "planner"),
            duel("duel-19", "旅行住宿，你通常？", "提前比价订好", "到了再找", "planner", "spontaneous"),
            duel("duel-20", "周末出门前，你会？", "查好天气和路线", "出门再说", "planner", "hedonist"),
            duel("duel-21", "想学新技能，你会？", "先看课程大纲", "先报名再说", "planner", "hedonist"),
            duel("duel-22", "旧东西坏了，你会？", "能修就修", "直接买新的", "calc", "spontaneous"),
            duel("duel-23", "送礼时，你更像？", "想好对方缺什么", "看到好看就买", "planner", "spontaneous"),
            duel("duel-24", "记账这件事，你更像？", "每笔都记", "大额才记", "calc", "hedonist"),
            duel("duel-25", "拍照时，你通常？", "构好图再按", "随手抓拍", "planner", "spontaneous"),
            duel("duel-26", "深夜想吃夜宵，你会？", "忍一忍明天吃", "现在就点", "calc", "hedonist"),
            duel("duel-27", "出门时，你更可能？", "自己装一壶水", "路上买一瓶", "planner", "spontaneous"),
            duel("duel-28", "存钱这件事，你更像？", "定好每月目标", "剩多少算多少", "planner", "hedonist"),
            duel("duel-29", "买完东西后悔了，你会？", "退货止损", "留着说不定用得上", "calc", "spontaneous"),
            duel("duel-30", "假期最后一天，你会？", "收心准备明天", "玩到最后一刻", "planner", "hedonist"),
        ),
    )

    // ---------------------------------------------------------------- 3. 小票猜猜看

    private fun receiptGuess(): GameDefinition = GameDefinition(
        id = "receipt-guess",
        category = GameCategory.Guess,
        title = "小票猜猜看",
        subtitle = "10 张小票，猜中上面的价格",
        emoji = "🧾",
        minutes = 3,
        mode = GameMode.Score,
        scoreStyle = ScoreStyle.Correct,
        note = "价格取自常见门店，仅作猜谜，各城市有差异。",
        buckets = listOf(
            GameBucket(0, 2, "🫣", "价格盲盒", "你对价格不太敏感，猜价全靠手感。"),
            GameBucket(3, 5, "🙂", "大致靠谱", "心里有个大概，偶尔会被惊到。"),
            GameBucket(6, 8, "🧐", "价格雷达", "日常价格逃不过你的眼睛。"),
            GameBucket(9, 10, "🎯", "人形扫码枪", "小数点都逃不过你。"),
        ),
        questions = listOf(
            price("rg-1", "这三串关东煮多少钱？", "便利店 关东煮 3 串", "🍢", 1_250, 850, 1_250, 1_800, 2_400),
            price("rg-2", "这杯中杯生椰拿铁多少钱？", "生椰拿铁 中杯", "🥥", 1_600, 900, 1_600, 2_200, 2_800),
            price("rg-3", "这份巨无霸套餐多少钱？", "巨无霸套餐", "🍔", 3_900, 2_600, 3_200, 3_900, 4_800),
            price("rg-4", "这杯柠檬水多少钱？", "柠檬水", "🍋", 400, 400, 700, 1_000, 1_500),
            price("rg-5", "这杯大杯拿铁多少钱？", "大杯拿铁", "☕", 3_300, 2_200, 2_800, 3_300, 3_900),
            price("rg-6", "这盒草莓多少钱？", "草莓 500g", "🍓", 2_990, 1_590, 2_290, 2_990, 3_990),
            price("rg-7", "这份早餐组合多少钱？", "豆浆 + 油条 + 茶叶蛋", "🥟", 850, 550, 850, 1_200, 1_600),
            price("rg-8", "这份黄焖鸡米饭多少钱？", "黄焖鸡米饭", "🍗", 2_680, 1_680, 2_180, 2_680, 3_380),
            price("rg-9", "这张 IMAX 电影票多少钱？", "IMAX 电影票 1 张", "🎟️", 6_500, 4_500, 5_500, 6_500, 8_000),
            price("rg-10", "这瓶矿泉水多少钱？", "矿泉水 500ml", "💧", 200, 200, 300, 500, 800),
        ),
    )

    // ---------------------------------------------------------------- 4. 盲盒猜价

    private fun blindBox(): GameDefinition = GameDefinition(
        id = "blind-box",
        category = GameCategory.Guess,
        title = "盲盒猜价",
        subtitle = "8 个盲盒，把价格猜进正确的区间",
        emoji = "🎁",
        minutes = 3,
        mode = GameMode.Score,
        scoreStyle = ScoreStyle.Correct,
        note = "价格取自常见场景，仅作猜谜，各城市有差异。",
        buckets = listOf(
            GameBucket(0, 1, "🫥", "全靠缘分", "区间和你想的不太一样，但玩得开心。"),
            GameBucket(2, 4, "🌤️", "方向对了", "大方向没错，细节偶尔跑偏。"),
            GameBucket(5, 6, "🔍", "区间高手", "八次里中五次以上，感觉不错。"),
            GameBucket(7, 8, "🧿", "价格透视眼", "八九不离十，你是盲盒的天敌。"),
        ),
        questions = listOf(
            range(
                "bb-1", "这杯手冲咖啡多少钱？", "手冲咖啡", "☕", 3_800,
                band("¥0–15", 0, 1_499), band("¥15–30", 1_500, 2_999),
                band("¥30–45", 3_000, 4_499), band("¥45+", 4_500, 999_999),
            ),
            range(
                "bb-2", "这顿双人火锅多少钱？", "双人火锅", "🍲", 18_600,
                band("¥0–80", 0, 7_999), band("¥80–150", 8_000, 14_999),
                band("¥150–220", 15_000, 21_999), band("¥220+", 22_000, 999_999),
            ),
            range(
                "bb-3", "这瓶精酿啤酒多少钱？", "精酿啤酒 1 瓶", "🍺", 2_800,
                band("¥0–10", 0, 999), band("¥10–25", 1_000, 2_499),
                band("¥25–40", 2_500, 3_999), band("¥40+", 4_000, 999_999),
            ),
            range(
                "bb-4", "这份日式便当多少钱？", "日式便当", "🍱", 4_800,
                band("¥0–20", 0, 1_999), band("¥20–35", 2_000, 3_499),
                band("¥35–50", 3_500, 4_999), band("¥50+", 5_000, 999_999),
            ),
            range(
                "bb-5", "这束鲜花多少钱？", "鲜花 1 束", "💐", 6_800,
                band("¥0–30", 0, 2_999), band("¥30–60", 3_000, 5_999),
                band("¥60–100", 6_000, 9_999), band("¥100+", 10_000, 999_999),
            ),
            range(
                "bb-6", "这张话剧票多少钱？", "话剧票 1 张", "🎭", 28_000,
                band("¥0–100", 0, 9_999), band("¥100–200", 10_000, 19_999),
                band("¥200–300", 20_000, 29_999), band("¥300+", 30_000, 999_999),
            ),
            range(
                "bb-7", "这次洗剪吹多少钱？", "洗剪吹 1 次", "💇", 5_800,
                band("¥0–30", 0, 2_999), band("¥30–60", 3_000, 5_999),
                band("¥60–100", 6_000, 9_999), band("¥100+", 10_000, 999_999),
            ),
            range(
                "bb-8", "这盒 12 枚鸡蛋多少钱？", "鸡蛋 12 枚", "🥚", 1_580,
                band("¥0–10", 0, 999), band("¥10–20", 1_000, 1_999),
                band("¥20–30", 2_000, 2_999), band("¥30+", 3_000, 999_999),
            ),
        ),
    )

    // ---------------------------------------------------------------- 5. 今日小任务

    private fun dailyTasks(): GameDefinition = GameDefinition(
        id = "daily-tasks",
        category = GameCategory.Challenge,
        title = "今日小任务",
        subtitle = "随机抽 10 条小任务，做完几个算几个",
        emoji = "✅",
        minutes = 4,
        mode = GameMode.Score,
        scoreStyle = ScoreStyle.TasksDone,
        sampleCount = 10,
        buckets = listOf(
            GameBucket(0, 2, "🛋️", "今天先休息", "任务不急，明天还有 10 条。"),
            GameBucket(3, 5, "🌱", "小步前进", "做了几件，已经比昨天多一点。"),
            GameBucket(6, 8, "⚡", "行动派", "今天的你在状态里。"),
            GameBucket(9, 10, "🏅", "全勤选手", "十项全做，给自己鼓个掌。"),
        ),
        questions = listOf(
            task("dt-1", "把今天的一张纸质小票拍照存档", "🧾", "纸会褪色，记下的数字不会。"),
            task("dt-2", "记账时顺手写一句当天心情", "💧", "数字之外，还有那天的你。"),
            task("dt-3", "看看有没有忘了关的自动续费", "📱", "只是去看看，不一定要关。"),
            task("dt-4", "把口袋里的硬币放进一个罐子", "🪙", "看它慢慢变重，也是一种乐趣。"),
            task("dt-5", "清一个抽屉", "🧹", "顺便看看有多少「买了没用」。"),
            task("dt-6", "今天用一杯水代替一次饮料", "🥛", "只是体验一下，不用有压力。"),
            task("dt-7", "逛超市前列一张清单", "🛒", "看看能不能只买清单上的。"),
            task("dt-8", "睡前看一眼今天的支出", "🌙", "不做评价，只看看。"),
            task("dt-9", "走路时听完一首完整的歌", "🎧", "不刷手机的那种。"),
            task("dt-10", "把快递盒叠好再扔", "📦", "给收废品的人省点事。"),
            task("dt-11", "检查一笔不认识的扣款", "💳", "可能是忘记的订阅。"),
            task("dt-12", "自己煮一顿饭", "🍚", "哪怕只是泡面加个蛋。"),
            task("dt-13", "一站路用走的", "🚶", "顺便看看街边的小店。"),
            task("dt-14", "给下个月的自己写一句提醒", "📝", "不用是目标，一句话就好。"),
            task("dt-15", "把冰箱里快过期的先吃掉", "🧊", "减少一次浪费。"),
            task("dt-16", "记下一件想送人的礼物", "🎁", "提前想，不用现在买。"),
            task("dt-17", "算一次「这件东西等于几小时工作」", "🧮", "只是换算，没有结论。"),
            task("dt-18", "给窗台的植物浇一次水", "🌿", "也是给自己一点慢下来的时间。"),
            task("dt-19", "读完一篇存了很久的文章", "📖", "清一条收藏。"),
            task("dt-20", "提前十分钟上床", "🛏️", "今天的账明天再看也来得及。"),
        ),
    )

    // ---------------------------------------------------------------- 6. 省钱大挑战

    private fun saveChallenge(): GameDefinition = GameDefinition(
        id = "save-challenge",
        category = GameCategory.Challenge,
        title = "省钱大挑战",
        subtitle = "8 幕情景选择，看看这一局能省下多少",
        emoji = "🛡️",
        minutes = 4,
        mode = GameMode.Score,
        scoreStyle = ScoreStyle.SavedCents,
        note = "游戏内设定金额，仅作情景模拟，不是真实账单。",
        buckets = listOf(
            GameBucket(0, 19_999, "🌫️", "快乐至上", "这一局没怎么省，但玩得开心也算数。"),
            GameBucket(20_000, 59_999, "🙂", "有点东西", "有几幕你守住了。"),
            GameBucket(60_000, 99_999, "💪", "省钱熟练工", "大部分时候你都会先停一下。"),
            GameBucket(100_000, 138_000, "🏆", "守财高手", "八幕下来，钱包几乎没受委屈。"),
        ),
        questions = listOf(
            choice(
                "sc-1", "深夜刷到一件 599 元的卫衣，你会？",
                o("sc-1a", "关掉页面，明天再看", emoji = "🌙", note = "省下 ¥599", score = 59_900),
                o("sc-1b", "放进购物车，七天后还想要再说", emoji = "🛒", note = "省下 ¥599", score = 59_900),
                o("sc-1c", "现在就下单", emoji = "⚡", note = "没省下", score = 0),
            ),
            choice(
                "sc-2", "中午点外卖，你本来想吃 18 元的面，满 30 减 5。",
                o("sc-2a", "只点想吃的那份", emoji = "🍜", note = "省下 ¥12", score = 1_200),
                o("sc-2b", "为满减凑到 30 元", emoji = "🥡", note = "多吃了，没省下", score = 0),
                o("sc-2c", "关掉外卖，自己煮一碗", emoji = "🍳", note = "省下 ¥30", score = 3_000),
            ),
            choice(
                "sc-3", "朋友约看电影，你更可能？",
                o("sc-3a", "用已有的会员兑换", emoji = "🎟️", note = "省下 ¥45", score = 4_500),
                o("sc-3b", "买普通场", emoji = "🎬", note = "省下 ¥15", score = 1_500),
                o("sc-3c", "买 IMAX 场", emoji = "🍿", note = "没省下", score = 0),
            ),
            choice(
                "sc-4", "护肤品快用完了，柜子里还有两瓶没开封。",
                o("sc-4a", "先翻出囤货用完", emoji = "🧴", note = "省下 ¥199", score = 19_900),
                o("sc-4b", "用完再买一瓶", emoji = "🛍️", note = "没省下", score = 0),
                o("sc-4c", "大促再囤三瓶", emoji = "📦", note = "多买了，没省下", score = 0),
            ),
            choice(
                "sc-5", "下雨天要通勤，你会？",
                o("sc-5a", "提前十分钟坐地铁", emoji = "🚇", note = "省下 ¥28", score = 2_800),
                o("sc-5b", "打车", emoji = "🚕", note = "没省下", score = 0),
                o("sc-5c", "拼车", emoji = "🚗", note = "省下 ¥12", score = 1_200),
            ),
            choice(
                "sc-6", "朋友生日快到了，你会？",
                o("sc-6a", "手写一张卡片，加一件小礼物", emoji = "💌", note = "省下 ¥120", score = 12_000),
                o("sc-6b", "提前一个月慢慢挑", emoji = "🎁", note = "省下 ¥60", score = 6_000),
                o("sc-6c", "直接发个红包", emoji = "🧧", note = "没省下", score = 0),
            ),
            choice(
                "sc-7", "想学一门课，课程 299 元。",
                o("sc-7a", "先看完免费试听", emoji = "🎧", note = "省下 ¥299", score = 29_900),
                o("sc-7b", "去图书馆借相关的书", emoji = "📚", note = "省下 ¥199", score = 19_900),
                o("sc-7c", "直接买年卡", emoji = "💳", note = "没省下", score = 0),
            ),
            choice(
                "sc-8", "周末在家，你会怎么解决晚饭？",
                o("sc-8a", "自己煮一顿", emoji = "🍲", note = "省下 ¥60", score = 6_000),
                o("sc-8b", "约朋友来家里吃", emoji = "🥘", note = "省下 ¥30", score = 3_000),
                o("sc-8c", "点外卖", emoji = "🛵", note = "没省下", score = 0),
            ),
        ),
    )

    // ---------------------------------------------------------------- 7. 记账冷知识

    private fun ledgerQuiz(): GameDefinition = GameDefinition(
        id = "ledger-quiz",
        category = GameCategory.Challenge,
        title = "记账冷知识",
        subtitle = "15 道判断题，看看你有多懂账本",
        emoji = "🧠",
        minutes = 3,
        mode = GameMode.Score,
        scoreStyle = ScoreStyle.Correct,
        buckets = listOf(
            GameBucket(0, 5, "📖", "新手上路", "冷知识还热乎，翻两页就懂了。"),
            GameBucket(6, 9, "🧾", "账本学徒", "一半以上都答对，基础不错。"),
            GameBucket(10, 12, "📊", "记账老手", "你对账本的门道很清楚。"),
            GameBucket(13, 15, "🧠", "账本人形百科", "冷知识都被你答热了。"),
        ),
        questions = listOf(
            tf("lq-1", "同一笔支出在两本账里各记一次，统计会翻倍。", true, "重复记录会让总额虚高，对账时要留意。"),
            tf("lq-2", "1 元等于 100 分，用「分」记账可以避开小数误差。", true, "整数运算不会有 0.1 + 0.2 这类误差。"),
            tf("lq-3", "记账 App 必须联网才能记一笔。", false, "手动记账在本机就能完成，联网与否不影响。"),
            tf("lq-4", "借给朋友的钱和自己花掉的钱，在账本里是同一件事。", false, "借出去的钱还会回来，花掉的钱不会。"),
            tf("lq-5", "退款到账后，直接删掉原来那笔支出，和记一笔收入，报表结果完全一样。", false, "两者的分类与时间点不同，报表会对不上。"),
            tf("lq-6", "分类越多，账本一定越清楚。", false, "分类太细会难维护，几个大类往往更好用。"),
            tf("lq-7", "每月对一次账，能发现漏记或重复记的条目。", true, "对账是发现错漏最直接的办法。"),
            tf("lq-8", "记了账就一定会变有钱。", false, "记账只是记录，不会替你做决定。"),
            tf("lq-9", "固定支出单独标出来，更容易看出每月必须花的钱。", true, "房租、会员这类固定项值得单独看。"),
            tf("lq-10", "账单里的时间只用来排序，没有任何用处。", false, "时间能看出消费节奏与周期。"),
            tf("lq-11", "同一天记多笔小额支出，比只记一笔总额更容易看清钱花在哪。", true, "拆开记，分类才有意义。"),
            tf("lq-12", "记账的目的之一是了解自己的消费习惯，而不是给自己打分。", true, "记录不是审判。"),
            tf("lq-13", "现金和电子支付可以记在同一本账里。", true, "只要是同一段时间的收支，就能合并看。"),
            tf("lq-14", "记账只需要记金额，商户和备注都不重要。", false, "商户和备注是日后回忆这笔钱的线索。"),
            tf("lq-15", "每周固定时间回看一次账单，通常比月底一次看一个月更容易坚持。", true, "频率稳定，记忆也更清楚。"),
        ),
    )

    // ---------------------------------------------------------------- 8. 花式花钱雷达

    private fun spendRadar(): GameDefinition = GameDefinition(
        id = "spend-radar",
        title = "花式花钱雷达",
        subtitle = "6 道量表，画出你的花钱六维图",
        emoji = "📡",
        minutes = 2,
        mode = GameMode.Radar,
        axes = listOf(
            GameAxis("plan", "🧭", "计划力", "你花钱前会先想清楚，节奏感很强。"),
            GameAxis("impulse", "⚡", "冲动度", "心动就行动，你的钱包跟着情绪走。"),
            GameAxis("social", "🎉", "社交度", "你愿意为人和场合花钱，快乐常常是共享的。"),
            GameAxis("experience", "🎢", "体验度", "你更愿意把钱换成经历，回忆是你的收藏。"),
            GameAxis("minimal", "🪶", "极简度", "东西越少你越轻松，减法让你舒服。"),
            GameAxis("novelty", "🧪", "新鲜度", "你愿意为没试过的东西付一次学费。"),
        ),
        questions = listOf(
            scale(
                "sr-1", "出门前，你会把行程和预算想好吗？", "plan",
                lowLabel = "走到哪算哪", highLabel = "连备用方案都有",
            ),
            scale(
                "sr-2", "看到心动的东西，你通常多久下单？", "impulse",
                lowLabel = "先放很久", highLabel = "当场就买",
            ),
            scale(
                "sr-3", "和朋友聚会，你愿意为「一起开心」多花一点吗？", "social",
                lowLabel = "各付各的", highLabel = "开心最重要",
            ),
            scale(
                "sr-4", "同样的钱，你更愿意买「东西」还是「经历」？", "experience",
                lowLabel = "买东西", highLabel = "买经历",
            ),
            scale(
                "sr-5", "家里东西越少，你越舒服吗？", "minimal",
                lowLabel = "喜欢满满当当", highLabel = "越少越好",
            ),
            scale(
                "sr-6", "看到没试过的新东西，你会想试一次吗？", "novelty",
                lowLabel = "先观望", highLabel = "很想试",
            ),
        ),
    )

    // ---------------------------------------------------------------- builders

    private fun o(
        id: String,
        label: String,
        profile: String? = null,
        emoji: String = "",
        note: String = "",
        score: Int = 0,
    ) = GameOption(id = id, label = label, note = note, emoji = emoji, score = score, profile = profile)

    private fun choice(
        id: String,
        prompt: String,
        vararg options: GameOption,
        hint: String = "",
        layout: ChoiceLayout = ChoiceLayout.List,
    ) = ChoiceQuestion(id = id, prompt = prompt, options = options.toList(), hint = hint, layout = layout)

    private fun duel(
        id: String,
        prompt: String,
        left: String,
        right: String,
        leftProfile: String,
        rightProfile: String,
    ): ChoiceQuestion = ChoiceQuestion(
        id = id,
        prompt = prompt,
        layout = ChoiceLayout.Duel,
        options = listOf(
            GameOption(
                id = "${id}a", label = left, emoji = DUEL_EMOJI.getValue("${id}a"), profile = leftProfile,
            ),
            GameOption(
                id = "${id}b", label = right, emoji = DUEL_EMOJI.getValue("${id}b"), profile = rightProfile,
            ),
        ),
    )

    private fun price(
        id: String,
        prompt: String,
        item: String,
        emoji: String,
        actual: Long,
        vararg options: Long,
    ) = PriceQuestion(
        id = id,
        prompt = prompt,
        item = item,
        emoji = emoji,
        hint = "常见门店价格，仅作猜谜",
        actualCents = actual,
        options = options.toList(),
    )

    private fun band(label: String, min: Long, max: Long) = PriceRange(label, min, max)

    private fun range(
        id: String,
        prompt: String,
        item: String,
        emoji: String,
        actual: Long,
        vararg ranges: PriceRange,
    ) = RangeQuestion(
        id = id,
        prompt = prompt,
        item = item,
        emoji = emoji,
        hint = "选一个你觉得最接近的区间",
        actualCents = actual,
        ranges = ranges.toList(),
    )

    private fun tf(id: String, prompt: String, answer: Boolean, explain: String) =
        TrueFalseQuestion(id = id, prompt = prompt, answer = answer, explain = explain)

    private fun task(id: String, prompt: String, emoji: String, tip: String) =
        TaskQuestion(id = id, prompt = prompt, emoji = emoji, tip = tip)

    private fun scale(
        id: String,
        prompt: String,
        axis: String,
        lowLabel: String,
        highLabel: String,
    ) = ScaleQuestion(id = id, prompt = prompt, axis = axis, lowLabel = lowLabel, highLabel = highLabel)

}
