package com.xiaopiao.app.merchant

import kotlin.random.Random

/**
 * SYNTHETIC demo population. Nothing here is real user data: it is generated from a fixed seed so the
 * merchant lane can show how aggregated, opt-in insights would look. Every screen that renders it must
 * carry the 「演示数据」 label.
 */
const val DEMO_SHOP_NAME = "楼下的芋泥奶茶铺"
const val DEMO_LABEL = "演示数据"

enum class AgeBand(val label: String) { A18_24("18-24"), A25_34("25-34"), A35_44("35-44"), A45_PLUS("45+") }

enum class DrinkKind(val label: String, val emoji: String) {
    MILK_TEA("奶茶", "🧋"), FRUIT_TEA("果茶", "🍑"), COFFEE("咖啡", "☕"), OTHER("其他", "🥤"),
}

enum class Topping(val label: String, val emoji: String) {
    TARO("芋泥", "🍠"), PEARL("珍珠", "⚫"), COCONUT("椰果", "🥥"), PUDDING("布丁", "🍮"), NONE("不加料", "🙅"),
}

/** Time-of-day buckets used by the heatmap. Hours outside 8..23 fall into [LATE]. */
enum class DayPart(val label: String, val startHour: Int) {
    MORNING("早 8-11", 8), NOON("午 11-14", 11), AFTERNOON("下午 14-17", 14),
    EVENING("傍晚 17-20", 17), NIGHT("晚 20-23", 20), LATE("深夜", 23);

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 8..10 -> MORNING
            in 11..13 -> NOON
            in 14..16 -> AFTERNOON
            in 17..19 -> EVENING
            in 20..22 -> NIGHT
            else -> LATE
        }
    }
}

data class DrinkPurchase(
    /** 0 = Monday … 6 = Sunday. */
    val dayOfWeek: Int,
    val hour: Int,
    val priceCents: Long,
    val kind: DrinkKind,
)

/**
 * One synthetic opt-in neighbour. The three `*Lean` fields are latent traits (0..1) used only to
 * simulate how this person would answer a preference experiment.
 */
data class CrowdMember(
    val id: Int,
    val ageBand: AgeBand,
    val favoriteTopping: Topping,
    val purchases: List<DrinkPurchase>,
    val toppingLean: Double,
    val loyaltyLean: Double,
    val priceSensitivity: Double,
) {
    val averagePriceCents: Long get() = if (purchases.isEmpty()) 0 else purchases.sumOf { it.priceCents } / purchases.size
    val mainKind: DrinkKind
        get() = purchases.groupingBy { it.kind }.eachCount().maxByOrNull { it.value }?.key ?: DrinkKind.OTHER
}

object DemoCrowd {
    const val DEFAULT_SEED = 20261028L
    const val DEFAULT_SIZE = 300

    private val ageWeights = listOf(AgeBand.A18_24 to 38, AgeBand.A25_34 to 36, AgeBand.A35_44 to 18, AgeBand.A45_PLUS to 8)
    private val toppingWeights = listOf(Topping.TARO to 30, Topping.PEARL to 26, Topping.COCONUT to 12, Topping.PUDDING to 14, Topping.NONE to 18)
    private val kindWeights = listOf(DrinkKind.MILK_TEA to 52, DrinkKind.FRUIT_TEA to 24, DrinkKind.COFFEE to 16, DrinkKind.OTHER to 8)
    private val priceBases = longArrayOf(900, 1300, 1600, 1900, 2300, 2800)

    fun generate(seed: Long = DEFAULT_SEED, size: Int = DEFAULT_SIZE): List<CrowdMember> {
        val rnd = Random(seed)
        return List(size) { id -> member(id, rnd) }
    }

    private fun member(id: Int, rnd: Random): CrowdMember {
        val age = pick(ageWeights, rnd)
        val topping = pick(toppingWeights, rnd)
        val base = priceBases[rnd.nextInt(priceBases.size)] + age.ordinal * 50L
        val count = 3 + rnd.nextInt(14)
        val purchases = List(count) { purchase(rnd, base) }
        val toppingLean = (if (topping == Topping.NONE) 0.25 else 0.7) + (rnd.nextDouble() - 0.5) * 0.3
        return CrowdMember(
            id = id,
            ageBand = age,
            favoriteTopping = topping,
            purchases = purchases,
            toppingLean = toppingLean.coerceIn(0.02, 0.98),
            loyaltyLean = (0.3 + age.ordinal * 0.08 + (rnd.nextDouble() - 0.5) * 0.4).coerceIn(0.02, 0.98),
            priceSensitivity = (0.75 - age.ordinal * 0.08 + (rnd.nextDouble() - 0.5) * 0.4).coerceIn(0.02, 0.98),
        )
    }

    private fun purchase(rnd: Random, base: Long): DrinkPurchase {
        val weekend = rnd.nextInt(100) < 34
        val day = if (weekend) 5 + rnd.nextInt(2) else rnd.nextInt(5)
        // Afternoon (14-17) and evening peaks; mornings and late nights are thin on purpose.
        val hour = pickHour(rnd)
        val price = (base + (rnd.nextInt(7) - 3) * 100L).coerceAtLeast(600)
        return DrinkPurchase(day, hour, price, pick(kindWeights, rnd))
    }

    private fun pickHour(rnd: Random): Int {
        val w = listOf(8 to 3, 9 to 3, 10 to 4, 11 to 7, 12 to 9, 13 to 8, 14 to 14, 15 to 18, 16 to 15, 17 to 11, 18 to 10, 19 to 9, 20 to 6, 21 to 4, 22 to 2, 23 to 1)
        return pick(w, rnd)
    }

    private fun <T> pick(weights: List<Pair<T, Int>>, rnd: Random): T {
        var roll = rnd.nextInt(weights.sumOf { it.second })
        for ((v, w) in weights) {
            if (roll < w) return v
            roll -= w
        }
        return weights.last().first
    }
}
