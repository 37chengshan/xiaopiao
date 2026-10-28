package com.xiaopiao.app.domain.ai

/**
 * The agent's capability surface (docs/AGENT.md §3). The cloud model will receive exactly these
 * names and JSON schemas; the local engine calls the same code paths, so swapping the brain in
 * later does not change what the UI shows.
 */
data class ToolSpec(
    val name: String,
    /** What the user-facing step says while the tool runs. */
    val label: String,
    val description: String,
    val params: String,
    /** Tools that change data must be confirmed by the user before they run. */
    val mutating: Boolean = false,
)

object Tools {
    val queryTransactions = ToolSpec(
        name = "query_transactions",
        label = "读取账本",
        description = "查本机流水。何时用：用户问某段时间/某分类的具体记录。何时别用：只要汇总时用 summarize_period。",
        params = """{"from":"epochMs","to":"epochMs","category?":"Category","direction?":"OUT|IN","limit?":50}""",
    )
    val summarizePeriod = ToolSpec(
        name = "summarize_period",
        label = "汇总",
        description = "按天/分类汇总一段时间。何时用：今天/本周/本月花了多少、分类占比。",
        params = """{"period":"today|week|month","group_by?":"day|category"}""",
    )
    val findPeak = ToolSpec(
        name = "find_peak",
        label = "找出极值",
        description = "找某维度最大的一项（哪天/哪个分类/哪个商户）。何时用：哪天花得最多、最大一笔。",
        params = """{"period":"today|week|month","dimension":"day|category|merchant"}""",
    )
    val readCircle = ToolSpec(
        name = "read_circle",
        label = "读取圈子共享",
        description = "读家庭成员已授权的数据。未共享返回 null，禁止推断。何时用：家人/圈子相关问题。",
        params = """{"scope":"me|family","period":"today|week|month"}""",
    )
    val draftTransactions = ToolSpec(
        name = "draft_transactions",
        label = "解析一句话",
        description = "把口语句子解析成账目草稿，不入账。何时用：用户描述一笔消费。",
        params = """{"text":"午饭 32，骑车 2"}""",
    )
    val commitTransactions = ToolSpec(
        name = "commit_transactions",
        label = "写入账本",
        description = "把用户确认过的草稿落账。只有用户点了确认卡才能调用。",
        params = """{"drafts":[...],"confirm_token":"string"}""",
        mutating = true,
    )

    val all = listOf(queryTransactions, summarizePeriod, findPeak, readCircle, draftTransactions, commitTransactions)
}

/** One executed step, as the UI renders it. */
data class ToolRun(val spec: ToolSpec, val detail: String)
