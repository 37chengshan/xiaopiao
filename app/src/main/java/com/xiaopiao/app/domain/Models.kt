package com.xiaopiao.app.domain

/** Money is always stored in cents (分) to avoid floating-point drift. */
typealias Cents = Long

enum class Direction { OUT, IN }

enum class Source(val label: String) {
    NOTIFICATION("通知自动"),
    QUICK_ADD("一句话"),
    MANUAL("手动"),
    CSV("账单导入"),
}

enum class Category(val label: String, val emoji: String) {
    MEAL("吃饭", "🍚"),
    DRINK("奶茶咖啡", "🧋"),
    SNACK("零食", "🍪"),
    GROCERY("买菜日用", "🛒"),
    TRANSPORT("出行", "🚇"),
    SHOPPING("购物", "🛍️"),
    FUN("娱乐", "🎮"),
    SUBSCRIPTION("会员订阅", "🔁"),
    HOUSING("住房水电", "🏠"),
    HEALTH("医疗健康", "💊"),
    SOCIAL("人情红包", "🧧"),
    LEARNING("学习", "📚"),
    OTHER("其他", "🧾"),
}

data class Txn(
    val id: Long = 0,
    val amountCents: Cents,
    val direction: Direction = Direction.OUT,
    val merchant: String,
    val category: Category,
    val occurredAt: Long,
    val source: Source,
    val note: String = "",
    /** Last four digits of the card the payment settled on, when the notification named it (§5.5). */
    val cardLast4: String = "",
    /** Stable key used to drop duplicates from repeated notifications / imports. */
    val dedupeKey: String = "",
)

data class Badge(
    val code: String,
    val title: String,
    val emoji: String,
    val reason: String,
)

data class Subscription(
    val merchant: String,
    val amountCents: Cents,
    val cycleDays: Int,
    val lastChargedAt: Long,
    val nextChargeAt: Long,
    val occurrences: Int,
) {
    val yearlyCents: Cents get() = amountCents * 365 / cycleDays
}

/** One printed daily receipt. */
data class DailyReceipt(
    val dayStart: Long,
    val items: List<Txn>,
    val totalOutCents: Cents,
    val aiComment: String,
    val badge: Badge?,
)
