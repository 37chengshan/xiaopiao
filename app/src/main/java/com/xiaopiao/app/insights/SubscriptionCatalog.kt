package com.xiaopiao.app.insights

/** Kinds of subscriptions — used to spot "two video memberships" style duplicates. */
enum class SubscriptionKind(val label: String, val emoji: String) {
    VIDEO("视频会员", "🎬"),
    MUSIC("音乐会员", "🎵"),
    CLOUD("云存储", "☁️"),
    READING("阅读学习", "📖"),
    AI("AI 工具", "🤖"),
    FOOD("外卖会员", "🍔"),
}

object SubscriptionCatalog {
    private val kindKeywords: Map<SubscriptionKind, List<String>> = mapOf(
        SubscriptionKind.VIDEO to listOf("爱奇艺", "腾讯视频", "优酷", "芒果", "netflix", "disney", "youtube", "哔哩哔哩", "bilibili", "b站"),
        SubscriptionKind.MUSIC to listOf("网易云", "qq音乐", "spotify", "apple music", "酷狗", "酷我"),
        SubscriptionKind.CLOUD to listOf("icloud", "百度网盘", "阿里云盘", "夸克", "onedrive", "dropbox"),
        SubscriptionKind.READING to listOf("微信读书", "得到", "喜马拉雅", "知乎", "kindle", "多邻国", "duolingo"),
        SubscriptionKind.AI to listOf("chatgpt", "openai", "claude", "kimi", "copilot", "midjourney"),
        SubscriptionKind.FOOD to listOf("美团会员", "饿了么会员", "外卖会员", "神券"),
    )

    /** Words that make a merchant/note look like an auto-renewal even outside the 订阅 category. */
    val subscriptionHints: List<String> = listOf(
        "会员", "续费", "订阅", "自动", "vip", "包月", "包年", "连续", "plus", "premium", "月卡", "年卡", "apple",
    ) + kindKeywords.values.flatten()

    fun kindOf(merchant: String): SubscriptionKind? {
        val m = merchant.lowercase()
        return kindKeywords.entries.firstOrNull { (_, words) -> words.any { m.contains(it) } }?.key
    }

    fun looksLikeSubscription(text: String): Boolean {
        val t = text.lowercase()
        return subscriptionHints.any { t.contains(it) }
    }
}

/** How to turn off an auto-renewal. The app only guides — it never cancels anything. */
data class CancelGuide(val channel: String, val steps: List<String>)

object CancelGuides {
    val alipay = CancelGuide("支付宝", listOf("打开支付宝，点右下角「我的」", "设置 → 支付设置 → 免密支付/自动扣款", "找到这个商户，点进去选择「关闭服务」"))
    val wechat = CancelGuide("微信", listOf("打开微信，点「我」→「服务」→「钱包」", "右上角「支付设置」→「自动续费」", "找到这个商户，选择「关闭扣费服务」"))
    val apple = CancelGuide("Apple", listOf("打开「设置」，点最上面的 Apple ID", "点「订阅」", "选中这个订阅，点「取消订阅」"))

    private val appleHints = listOf("apple", "icloud", "app store", "itunes")

    /** All three channels, with the most likely one first (we can't see which wallet paid). */
    fun forMerchant(merchant: String): List<CancelGuide> {
        val m = merchant.lowercase()
        return if (appleHints.any { m.contains(it) }) listOf(apple, alipay, wechat) else listOf(alipay, wechat, apple)
    }

    /** One friendly line for in-app tips, when we know the app. */
    fun appTip(merchant: String): String? {
        val m = merchant.lowercase()
        return when {
            listOf("爱奇艺", "腾讯视频", "优酷", "芒果").any { m.contains(it) } -> "也可以在该 App「我的 → 会员中心 → 自动续费管理」里关闭。"
            listOf("网易云", "qq音乐").any { m.contains(it) } -> "也可以在该 App「我的 → 会员中心 → 管理自动续费」里关闭。"
            m.contains("icloud") || m.contains("apple") -> "Apple 系订阅统一在「设置 → Apple ID → 订阅」管理。"
            m.contains("百度网盘") -> "也可以在 App「我的 → 会员中心 → 自动续费管理」里关闭。"
            else -> null
        }
    }

    const val FOOTNOTE = "小票只负责告诉你怎么关，真正的开关永远在你手里 🔑"
}
