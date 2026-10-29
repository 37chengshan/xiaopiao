package com.xiaopiao.app.domain

import kotlin.math.abs

/**
 * Categories are data (tt#70/71): every built-in ships a default look, and the user can add, rename,
 * recolor, reorder or hide them. [Category] stays as the legacy enum for code that has not migrated
 * yet (capture rules, insights); a transaction carries a [Txn.categoryId] pointing at one of these.
 *
 * The 13 legacy enum values map 1:1 onto the built-in ids below ([CategoryDefaults.idFor]).
 */
data class CategoryDef(
    /** Stable id: the legacy enum name in lower case for built-ins, `custom:<epoch>` for user rows. */
    val id: String,
    val label: String,
    /** Key into the icon catalogue (`ui/category/CategoryIconCatalog`). */
    val iconKey: String,
    /** Key into [CategoryHue]. */
    val hue: String,
    /** Restrict to one direction; null = offered for both 支出 and 收入. */
    val direction: Direction? = null,
    val builtIn: Boolean = false,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
) {
    fun fits(direction: Direction): Boolean = this.direction == null || this.direction == direction
    val isCustom: Boolean get() = !builtIn
}

/** Twelve hues (plus grey reserved for 其他) — light and dark values both clear 3:1 against their 12% tint. */
enum class CategoryHue(val key: String, val light: Long, val dark: Long) {
    ORANGE("orange", 0xFFC4681DL, 0xFFF0A164L),
    BROWN("brown", 0xFF8F6446L, 0xFFC0997AL),
    YELLOW("yellow", 0xFFA8790BL, 0xFFE5C05EL),
    GREEN("green", 0xFF3F8C4EL, 0xFF74C084L),
    BLUE("blue", 0xFF3A6FD0L, 0xFF82A9EEL),
    PINK("pink", 0xFFC45080L, 0xFFEE92B6L),
    PURPLE("purple", 0xFF7C54C4L, 0xFFB393ECL),
    INDIGO("indigo", 0xFF4F5EBEL, 0xFF909DE9L),
    TEAL("teal", 0xFF2A8C8CL, 0xFF68C6C6L),
    RED("red", 0xFFC2443CL, 0xFFF28880L),
    VERMILION("vermilion", 0xFFCE5030L, 0xFFF59478L),
    BLUEGREEN("bluegreen", 0xFF348D78L, 0xFF76CBB6L),
    GREY("grey", 0xFF76767CL, 0xFFA2A2A8L),
    ;

    companion object {
        /** The 12 the custom-category picker offers (grey included: it is a colour like any other). */
        val pickable: List<CategoryHue> = entries.toList()

        fun of(key: String): CategoryHue = entries.firstOrNull { it.key == key } ?: GREY
    }
}

/** Built-in categories, their default icon/hue, and the enum ↔ id bridge. */
object CategoryDefaults {
    const val OTHER_ID = "other"
    const val MAX_LABEL = 8
    /** Custom rows live in the same table; their ids must never collide with a built-in id. */
    const val CUSTOM_PREFIX = "custom:"

    private fun builtIn(id: String, label: String, icon: String, hue: CategoryHue, order: Int) =
        CategoryDef(id = id, label = label, iconKey = icon, hue = hue.key, builtIn = true, sortOrder = order)

    /** Same order and labels as the legacy enum, so an old row resolves to the same thing it always did. */
    val all: List<CategoryDef> = listOf(
        builtIn("meal", "吃饭", "restaurant", CategoryHue.ORANGE, 0),
        builtIn("drink", "奶茶咖啡", "local_cafe", CategoryHue.BROWN, 1),
        builtIn("snack", "零食", "cookie", CategoryHue.YELLOW, 2),
        builtIn("grocery", "买菜日用", "shopping_basket", CategoryHue.GREEN, 3),
        builtIn("transport", "出行", "directions_subway", CategoryHue.BLUE, 4),
        builtIn("shopping", "购物", "shopping_bag", CategoryHue.PINK, 5),
        builtIn("fun", "娱乐", "sports_esports", CategoryHue.PURPLE, 6),
        builtIn("subscription", "会员订阅", "autorenew", CategoryHue.INDIGO, 7),
        builtIn("housing", "住房水电", "home", CategoryHue.TEAL, 8),
        builtIn("health", "医疗健康", "medical_services", CategoryHue.RED, 9),
        builtIn("social", "人情红包", "card_giftcard", CategoryHue.VERMILION, 10),
        builtIn("learning", "学习", "menu_book", CategoryHue.BLUEGREEN, 11),
        builtIn(OTHER_ID, "其他", "receipt", CategoryHue.GREY, 12),
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String): CategoryDef? = byId[id]

    /** 「吃饭」 for meal; unknown ids fall back to 其他 so a deleted category never renders blank. */
    fun labelOf(id: String): String = byId[id]?.label ?: "其他"

    /** The legacy enum value → built-in id. */
    fun idFor(category: Category): String = category.name.lowercase()

    /** Inverse, for writers that still speak the enum (capture, quick add). */
    fun enumFor(id: String): Category =
        Category.entries.firstOrNull { it.name.lowercase() == id } ?: Category.OTHER

    /** A new user category: order lands after everything visible, hue picked round-robin-free by the caller. */
    fun custom(
        label: String,
        iconKey: String,
        hue: CategoryHue,
        direction: Direction?,
        now: Long,
        sortOrder: Int,
    ) = CategoryDef(
        id = CUSTOM_PREFIX + now,
        label = label.trim().take(MAX_LABEL),
        iconKey = iconKey,
        hue = hue.key,
        direction = direction,
        builtIn = false,
        hidden = false,
        sortOrder = sortOrder,
    )
}

/**
 * Resolves one transaction to the category it should draw: its own id first, then the legacy enum,
 * then 其他 (a row whose custom category was deleted without a merge).
 */
fun List<CategoryDef>.resolve(txn: Txn): CategoryDef {
    val byId = firstOrNull { it.id == txn.categoryId }
    if (byId != null) return byId
    val legacy = CategoryDefaults.idFor(txn.category)
    return firstOrNull { it.id == legacy } ?: firstOrNull { it.id == CategoryDefaults.OTHER_ID } ?: CategoryDefaults.all.last()
}

/** Same, for a bare id (filter chips, pickers); null when the row is gone (deleted custom category). */
fun List<CategoryDef>.resolveId(id: String): CategoryDef? = firstOrNull { it.id == id }

/** Chip text for the payment channel of a row (tt#70: 支付宝 / 微信 / 手动). */
fun sourceChipLabel(source: Source, channel: String): String? = when (source) {
    Source.MANUAL -> "手动记账"
    Source.QUICK_ADD -> "手动记账"
    Source.CSV -> "账单导入"
    Source.NOTIFICATION -> when (channel) {
        CHANNEL_WECHAT -> "微信自动记账"
        CHANNEL_ALIPAY -> "支付宝自动记账"
        // Capture hooks the channel in one line (CaptureProcessor); until then stay honest about it.
        else -> "自动记账"
    }
}

const val CHANNEL_ALIPAY = "alipay"
const val CHANNEL_WECHAT = "wechat"
const val CHANNEL_MANUAL = "manual"

/**
 * Ledger money text: always two decimals and thousands separators ("1,280.50", "0.00") — the
 * reference prints 支 18.28 / 收 0.00 on every card, never "18.3" or a bare "0".
 */
fun Cents.yuanGrouped(): String {
    val value = abs(this)
    val whole = (value / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val cents = (value % 100).toString().padStart(2, '0')
    return (if (this < 0) "-" else "") + whole + "." + cents
}
