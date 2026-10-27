package com.xiaopiao.app.merchant

/** k-anonymity threshold: a cell describing fewer than this many people is never shown. */
const val K_ANONYMITY = 20

/**
 * One chart cell. When [people] is null the cell is suppressed (fewer than [K_ANONYMITY] people) and
 * even the true count is withheld so it cannot be back-computed from neighbours' rendering.
 */
data class Cell(val label: String, val people: Int?, val share: Float?) {
    val suppressed: Boolean get() = people == null
}

data class CohortStats(
    val totalPeople: Int,
    val avgPriceCents: Long?,
    val repeatRate: Float?,
    val kindShare: List<Cell>,
    /** 7 rows (Mon..Sun) × 6 day parts. */
    val heatmap: List<List<Cell>>,
    val priceBands: List<Cell>,
    val toppings: List<Cell>,
) {
    val heatmapMax: Int get() = heatmap.flatten().maxOfOrNull { it.people ?: 0 } ?: 0
    val suppressedCells: Int
        get() = (kindShare + heatmap.flatten() + priceBands + toppings).count { it.suppressed }
}

object Aggregator {
    val priceBandLabels = listOf("<10 元", "10-15 元", "15-20 元", "20-25 元", "25 元+")
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    /** People with at least this many purchases in the window count as 「常客」. */
    const val REPEAT_MIN_PURCHASES = 8

    fun priceBand(cents: Long): Int = when {
        cents < 1000 -> 0
        cents < 1500 -> 1
        cents < 2000 -> 2
        cents < 2500 -> 3
        else -> 4
    }

    fun cell(label: String, people: Int, total: Int, k: Int = K_ANONYMITY): Cell =
        if (people < k) Cell(label, null, null) else Cell(label, people, if (total == 0) 0f else people.toFloat() / total)

    fun compute(crowd: List<CrowdMember>, ageFilter: AgeBand? = null, k: Int = K_ANONYMITY): CohortStats {
        val members = if (ageFilter == null) crowd else crowd.filter { it.ageBand == ageFilter }
        val total = members.size
        if (total < k) return suppressedAll(total)

        val kindShare = DrinkKind.entries.map { kind ->
            cell("${kind.emoji}${kind.label}", members.count { m -> m.purchases.any { it.kind == kind } }, total, k)
        }
        val heat = (0..6).map { day ->
            DayPart.entries.map { part ->
                val people = members.count { m -> m.purchases.any { it.dayOfWeek == day && DayPart.of(it.hour) == part } }
                cell("周${weekdayLabels[day]} ${part.label}", people, total, k)
            }
        }
        val bands = priceBandLabels.mapIndexed { i, label ->
            cell(label, members.count { priceBand(it.averagePriceCents) == i }, total, k)
        }
        val toppings = Topping.entries.map { t ->
            cell("${t.emoji}${t.label}", members.count { it.favoriteTopping == t }, total, k)
        }
        val repeaters = members.count { it.purchases.size >= REPEAT_MIN_PURCHASES }
        return CohortStats(
            totalPeople = total,
            avgPriceCents = members.sumOf { it.averagePriceCents } / total,
            repeatRate = repeaters.toFloat() / total,
            kindShare = kindShare,
            heatmap = heat,
            priceBands = bands,
            toppings = toppings,
        )
    }

    private fun suppressedAll(total: Int): CohortStats = CohortStats(
        totalPeople = total,
        avgPriceCents = null,
        repeatRate = null,
        kindShare = DrinkKind.entries.map { Cell("${it.emoji}${it.label}", null, null) },
        heatmap = (0..6).map { d -> DayPart.entries.map { Cell("周${weekdayLabels[d]} ${it.label}", null, null) } },
        priceBands = priceBandLabels.map { Cell(it, null, null) },
        toppings = Topping.entries.map { Cell("${it.emoji}${it.label}", null, null) },
    )
}
