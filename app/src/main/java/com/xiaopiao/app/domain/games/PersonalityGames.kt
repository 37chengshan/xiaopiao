package com.xiaopiao.app.domain.games

/**
 * 性格类小游戏（docs/APP-DESIGN.md §5.3）：六维雷达 + 人格标签。
 *
 * 放在 [com.xiaopiao.app.domain.games.GameContent] 之外，只为一个原因——内容太长，
 * 一个文件塞八套题目已经很难翻；这里再放两套独立成品，游戏列表仍然只在 GameContent 里登记。
 *
 * 规则：题库与评语都是本地纯数据，不联网、不引用真实账目；结果只描述行为倾向，
 * 不给理财建议、不做收益承诺。
 */
object PersonalityGames {

    // ------------------------------------------------------------ 消费人格六维（Radar）

    /** 六轴：计划、冲动、享乐、社交、节俭、新鲜。每轴两题，取平均。 */
    fun spendProfile(): GameDefinition = GameDefinition(
        id = "spend-profile",
        title = "消费人格六维",
        subtitle = "12 道量表，画出你的六维消费人格",
        emoji = "",
        minutes = 3,
        mode = GameMode.Radar,
        category = GameCategory.Personality,
        axes = listOf(
            GameAxis("plan", "", "计划力", "花钱前会先排好顺序：先做计划、再掏钱，临时起意少。",
                bands = listOf(
                    "常常临时决定，预算只是参考。",
                    "大额会算，小额随手。",
                    "先算再花，顺序感很强。",
                ),
            ),
            GameAxis("impulse", "", "冲动度", "看到喜欢的很难等到明天；决定和下单几乎同时发生。",
                bands = listOf(
                    "想清楚才动手，很难被倒计时催。",
                    "会心动，但通常能等一等。",
                    "看到就想买，决定和下单几乎同时。",
                ),
            ),
            GameAxis("enjoy", "", "享乐度", "愿意为「此刻开心」付钱，情绪价值在你的账本里占比不低。",
                bands = listOf(
                    "更愿意把钱留着，当下享受排后面。",
                    "偶尔犒劳自己，比例可控。",
                    "很舍得为此刻的开心付钱。",
                ),
            ),
            GameAxis("social", "", "社交度", "和人一起的花费更舍得：请客、聚会、礼物都算值得。",
                bands = listOf(
                    "更习惯各付各的，社交消费克制。",
                    "看关系远近决定花多少。",
                    "为人和场合花钱最不心疼。",
                ),
            ),
            GameAxis("thrift", "", "节俭度", "能省就省，把「不花」当默认选项，花出去要有个理由。",
                bands = listOf(
                    "省事优先，不太为差价折腾。",
                    "该省的时候会省。",
                    "能省一定省，不花是默认选项。",
                ),
            ),
            GameAxis("novelty", "", "新鲜度", "对没试过的东西好奇，愿意为体验和探索买单。",
                bands = listOf(
                    "熟悉和确定更让你安心。",
                    "有兴趣，但会先观望。",
                    "很想试新的，愿意为探索付费。",
                ),
            ),
        ),
        questions = listOf(
            scale("sp-1", "买东西之前，你会先列清单或算一下预算吗？", "plan", "直接买", "先算清楚"),
            scale("sp-2", "月底看到账单时，你通常清楚钱花去哪了吗？", "plan", "完全没印象", "每一笔都知道"),
            scale("sp-3", "看到心动的东西，你多久会下单？", "impulse", "放很久再说", "当场就买"),
            scale("sp-4", "促销倒计时会让你更快按下付款吗？", "impulse", "基本没影响", "很难忍住"),
            scale("sp-5", "你愿意为「今天心情不好」买点开心吗？", "enjoy", "不太会", "很愿意"),
            scale("sp-6", "同样的预算，你更愿意花在当下享受上吗？", "enjoy", "更愿意留着", "先享受再说"),
            scale("sp-7", "和朋友出去，你愿意多花一点让大家更开心吗？", "social", "各花各的", "开心最重要"),
            scale("sp-8", "送礼和请客，你会比给自己花钱更大方吗？", "social", "不会", "明显更舍得"),
            scale("sp-9", "能用更便宜的方式解决时，你会换吗？", "thrift", "懒得折腾", "一定会换"),
            scale("sp-10", "「这东西还能用」会让你打消买新的念头吗？", "thrift", "还是会买", "就不买了"),
            scale("sp-11", "看到没见过的新品，你会想试试吗？", "novelty", "不太感冒", "很想试"),
            scale("sp-12", "你愿意为一次没体验过的事情花钱吗？", "novelty", "不如买实物", "愿意试新的"),
        ),
    )


    // ------------------------------------------------------------ 人格五维（Mini-IPIP）

    /**
     * 20 题五维人格：题目结构直接取自 IPIP 的 Mini-IPIP 短表（Donnellan et al., 2006；ipip.ori.org 公共领域），
     * 每个维度 4 题、正反各两题；中文按原意改写，不是逐字翻译。
     * 第五维用「情绪稳定」而不是「神经质」，分数越高越稳定，和其余四维同向。
     */
    fun bigFive(): GameDefinition = GameDefinition(
        id = "big-five",
        title = "人格五维",
        subtitle = "20 道量表，Mini-IPIP 短表的五维轮廓",
        emoji = "",
        minutes = 4,
        mode = GameMode.Radar,
        category = GameCategory.Personality,
        axes = listOf(
            GameAxis(
                "extraversion", "", "外向性", "你从人和热闹里获得能量，独处太久会闷。",
                bands = listOf(
                    "安静、内敛，社交后需要独处充电。",
                    "社交和独处都能接受，看场合切换。",
                    "主动、爱表达，人群里自然站在前面。",
                ),
            ),
            GameAxis(
                "agreeableness", "", "宜人性", "你容易站在别人角度想，冲突让你不舒服。",
                bands = listOf(
                    "直接、就事论事，不太照顾气氛。",
                    "能合作也能坚持自己的立场。",
                    "体贴、愿意让步，常做关系里的润滑剂。",
                ),
            ),
            GameAxis(
                "conscientiousness", "", "尽责性", "计划、秩序和「做完」本身让你安心。",
                bands = listOf(
                    "随性、灵活，计划常被临场改掉。",
                    "该做的事会做，但不追求完美。",
                    "有条理、守承诺，事情交给你就放心。",
                ),
            ),
            GameAxis(
                "stability", "", "情绪稳定", "情绪起伏小，压力下也能维持节奏。",
                bands = listOf(
                    "情绪来得快，压力容易写在脸上。",
                    "大体平稳，遇到大事会有波动。",
                    "情绪很稳，很少被外界带走。",
                ),
            ),
            GameAxis(
                "openness", "", "开放性", "你对新观点、新体验好奇，愿意想抽象的东西。",
                bands = listOf(
                    "偏好熟悉和确定，实用优先。",
                    "既接受新东西，也需要熟悉的落脚点。",
                    "好奇、爱想象，愿意为没试过的事冒险。",
                ),
            ),
        ),
        questions = listOf(
            scale("bf-e1", "我是聚会上的活跃分子。", "extraversion", "完全不像我", "非常像我"),
            scale("bf-e2", "我愿意在聚会上和很多不同的人聊天。", "extraversion", "完全不像我", "非常像我"),
            scale("bf-e3", "我不太爱说话。", "extraversion", "完全不像我", "非常像我", reverse = true),
            scale("bf-e4", "我更喜欢待在人群后面。", "extraversion", "完全不像我", "非常像我", reverse = true),
            scale("bf-a1", "我会为别人的感受而难过。", "agreeableness", "完全不像我", "非常像我"),
            scale("bf-a2", "我能感受到别人的情绪。", "agreeableness", "完全不像我", "非常像我"),
            scale("bf-a3", "我对别人其实不太感兴趣。", "agreeableness", "完全不像我", "非常像我", reverse = true),
            scale("bf-a4", "我不太关心别人的问题。", "agreeableness", "完全不像我", "非常像我", reverse = true),
            scale("bf-c1", "事情一到手，我马上就做完。", "conscientiousness", "完全不像我", "非常像我"),
            scale("bf-c2", "我喜欢井井有条。", "conscientiousness", "完全不像我", "非常像我"),
            scale("bf-c3", "我常忘了把东西放回原处。", "conscientiousness", "完全不像我", "非常像我", reverse = true),
            scale("bf-c4", "我常把事情弄得一团糟。", "conscientiousness", "完全不像我", "非常像我", reverse = true),
            scale("bf-s1", "大多数时候我很放松。", "stability", "完全不像我", "非常像我"),
            scale("bf-s2", "我很少感到低落。", "stability", "完全不像我", "非常像我"),
            scale("bf-s3", "我情绪起伏很大。", "stability", "完全不像我", "非常像我", reverse = true),
            scale("bf-s4", "我很容易被惹恼。", "stability", "完全不像我", "非常像我", reverse = true),
            scale("bf-o1", "我的想象力很丰富。", "openness", "完全不像我", "非常像我"),
            scale("bf-o2", "我对抽象的话题有兴趣。", "openness", "完全不像我", "非常像我"),
            scale("bf-o3", "我很难理解抽象的概念。", "openness", "完全不像我", "非常像我", reverse = true),
            scale("bf-o4", "我的想象力其实不太好。", "openness", "完全不像我", "非常像我", reverse = true),
        ),
    )

    // ------------------------------------------------------------ 存钱人格（Persona）

    /** 六种人格：自动、目标、应急、随手、极简、享受。10 题，四选一。 */
    fun savingStyle(): GameDefinition = GameDefinition(
        id = "saving-style",
        title = "存钱人格",
        subtitle = "10 个问题，看看你是哪种存钱的人",
        emoji = "",
        minutes = 3,
        mode = GameMode.Persona,
        category = GameCategory.Personality,
        profiles = listOf(
            GameProfile(
                "auto", "", "自动派",
                "钱要先离开视线才存得下；手动转账永远会被你忘掉，自动划走反而毫无痛感。",
            ).withInsights(
                "把决定交给流程，比交给意志力可靠",
                "自动扣款后不看账单，容易对余额失去实感",
                "每月只看一次转出记录，既省心又不脱节",
            ),
            GameProfile(
                "goal", "", "目标派",
                "为一件具体的东西存钱你最来劲：有了目标，忍耐就变成了进度条。",
            ).withInsights(
                "看得见的目标让你更愿意忍",
                "目标达成后容易一口气花掉，再回到零",
                "试试把下一个目标提前写好，别留空窗期",
            ),
            GameProfile(
                "buffer", "", "应急派",
                "你存钱是为了「万一」；账户里那笔不动的钱让你睡得踏实。",
            ).withInsights(
                "安全感来自账户里的余量，而不是收益",
                "过度保守时，该换的东西也会一直拖着",
                "给「可以用」和「不能动」各留一个口袋",
            ),
            GameProfile(
                "casual", "", "随手派",
                "你不做计划，但零碎的钱常常能留下来；存钱对你是顺手，不是任务。",
            ).withInsights(
                "小额的持续比一次大额更符合你的节奏",
                "没有目标时，攒下的钱容易被一次冲动带走",
                "给随手存的钱一个明确的去处",
            ),
            GameProfile(
                "minimal", "", "极简派",
                "你的存钱方式是「少买」：东西越少，账户越稳，空间和心情一起变轻。",
            ).withInsights(
                "减法对你比加法有效",
                "有些必要支出被你一起砍掉，事后会补回来",
                "先划出「必须花」的清单，再对剩下的做减法",
            ),
            GameProfile(
                "enjoy", "", "享受派",
                "你更相信「钱是拿来用的」；存钱要靠外部约束，而不是自我感动。",
            ).withInsights(
                "你为体验花钱时最不后悔",
                "单纯靠意志存钱，通常撑不过一个月",
                "把存钱变成固定动作，而不是每次重新决定",
            ),
        ),
        questions = listOf(
            choice(
                "ss-1", "发工资那天，你通常先做什么？",
                o("ss-1a", "先转一笔到另一个账户", profile = "auto"),
                o("ss-1b", "先补上想买的东西", profile = "enjoy"),
                o("ss-1c", "看这个月有没有目标要攒", profile = "goal"),
                o("ss-1d", "没特别安排，先放着", profile = "casual"),
            ),
            choice(
                "ss-2", "看到账户余额时，你最先想到的是？",
                o("ss-2a", "够不够应付意外", profile = "buffer"),
                o("ss-2b", "离目标还差多少", profile = "goal"),
                o("ss-2c", "这个月还能花多少", profile = "enjoy"),
                o("ss-2d", "剩多少都行，反正没计划", profile = "casual"),
            ),
            choice(
                "ss-3", "想买的东西超出预算时，你会？",
                o("ss-3a", "算了，不买了", profile = "minimal"),
                o("ss-3b", "定个目标，攒够再买", profile = "goal"),
                o("ss-3c", "先买了，之后省回来", profile = "enjoy"),
                o("ss-3d", "看看能不能用更便宜的方式解决", profile = "casual"),
            ),
            choice(
                "ss-4", "哪种情况最让你安心？",
                o("ss-4a", "账户里有一笔长期不动的钱", profile = "buffer"),
                o("ss-4b", "每个月自动扣走一笔", profile = "auto"),
                o("ss-4c", "家里东西不多、开销很小", profile = "minimal"),
                o("ss-4d", "想花的时候花得起", profile = "enjoy"),
            ),
            choice(
                "ss-5", "朋友约你出门，但你这周已经花超了，你会？",
                o("ss-5a", "去，但只点最便宜的", profile = "minimal"),
                o("ss-5b", "去，钱的事之后再说", profile = "enjoy"),
                o("ss-5c", "不去，把这次的钱留起来", profile = "goal"),
                o("ss-5d", "去，反正平时也没怎么花", profile = "casual"),
            ),
            choice(
                "ss-6", "你更相信哪种存钱方式？",
                o("ss-6a", "自动划走，眼不见心不烦", profile = "auto"),
                o("ss-6b", "给一个具体目标攒", profile = "goal"),
                o("ss-6c", "少买就是存", profile = "minimal"),
                o("ss-6d", "有富余就存一点", profile = "casual"),
            ),
            choice(
                "ss-7", "突然多出一笔钱，你会？",
                o("ss-7a", "直接存起来不动", profile = "buffer"),
                o("ss-7b", "补上之前想买没买的", profile = "enjoy"),
                o("ss-7c", "存一半，花一半", profile = "casual"),
                o("ss-7d", "拿去推进目标", profile = "goal"),
            ),
            choice(
                "ss-8", "让你放弃一次想买的东西，最有效的理由是？",
                o("ss-8a", "家里已经有一个了", profile = "minimal"),
                o("ss-8b", "攒够钱能买更好的", profile = "goal"),
                o("ss-8c", "这个月的额度已经自动转走了", profile = "auto"),
                o("ss-8d", "留着应急更踏实", profile = "buffer"),
            ),
            choice(
                "ss-9", "回想上一次存下钱，是因为？",
                o("ss-9a", "自动转账，我几乎没感觉", profile = "auto"),
                o("ss-9b", "为了一个明确的目标", profile = "goal"),
                o("ss-9c", "那个月几乎没买东西", profile = "minimal"),
                o("ss-9d", "刚好没遇到想花的", profile = "casual"),
            ),
            choice(
                "ss-10", "如果只能保留一种习惯，你选？",
                o("ss-10a", "每月固定自动存一笔", profile = "auto"),
                o("ss-10b", "有一个明确的目标在推进", profile = "goal"),
                o("ss-10c", "账户里始终有一笔应急钱", profile = "buffer"),
                o("ss-10d", "想花的时候不用犹豫", profile = "enjoy"),
            ),
        ),
    )

    // ------------------------------------------------------------ builders

    private fun o(
        id: String,
        label: String,
        profile: String,
    ) = GameOption(id = id, label = label, profile = profile)

    private fun choice(
        id: String,
        prompt: String,
        vararg options: GameOption,
        hint: String = "",
    ) = ChoiceQuestion(id = id, prompt = prompt, options = options.toList(), hint = hint)

    private fun scale(
        id: String,
        prompt: String,
        axis: String,
        lowLabel: String,
        highLabel: String,
        reverse: Boolean = false,
    ) = ScaleQuestion(
        id = id, prompt = prompt, axis = axis, lowLabel = lowLabel, highLabel = highLabel, reverse = reverse,
    )
}

/** 结果页的三行小字：行为特征 / 容易卡住的地方 / 可以试试。 */
fun GameProfile.withInsights(vararg lines: String): GameProfile = copy(insights = lines.toList())
