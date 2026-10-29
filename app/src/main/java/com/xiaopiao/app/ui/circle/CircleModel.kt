package com.xiaopiao.app.ui.circle

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar

/**
 * Local-only model for the 圈子 tab (docs/APP-DESIGN.md §5).
 *
 * One circle = one family group chat. Everything is demo data generated deterministically on device:
 * no accounts, no network, no ledger reads. Each member owns a [CircleShare] profile and every card in
 * the feed is masked through its author's profile, so revoking a switch instantly re-masks history.
 */

enum class CircleShare(val label: String, val hint: String, val defaultVisible: Boolean) {
    TodayTotal("今日总额", "只显示一天一共花了多少，不含店名", true),
    CategoryMix("分类构成", "按分类的占比，不写店名", true),
    WeeklyTitles("周报与头衔", "家庭周报里可以提到你的头衔", true),
    TxnDetail("单笔明细", "每一笔的金额、商户和时间", false),
    ReceiptFull("小票全文", "把小票原文一起发到群里", false);

    companion object {
        /** Conservative defaults: aggregates yes, anything that identifies a purchase no. */
        fun defaults(): Map<CircleShare, Boolean> = entries.associateWith { it.defaultVisible }
    }
}

enum class CircleTone { Accent, Positive, Info, Warning }

data class CircleMember(
    val id: String,
    val name: String,
    val initial: String,
    val tone: CircleTone,
    val isMe: Boolean = false,
    val shares: Map<CircleShare, Boolean> = CircleShare.defaults(),
    val role: CircleRole = CircleRole.Member,
    /** What this member calls themselves inside the circle (≤8 glyphs). */
    val nickname: String? = null,
    /** Owner-only annotation; never shown to other members. */
    val remark: String? = null,
) {
    fun shows(item: CircleShare): Boolean = shares[item] == true

    fun withShare(item: CircleShare, visible: Boolean): CircleMember =
        copy(shares = shares + (item to visible))
}

/** One line item on a member's day receipt. */
data class CircleItem(val merchant: String, val cents: Cents, val category: Category)

sealed interface CirclePost {
    val id: String
    val at: Long
}

data class ReceiptPost(
    override val id: String,
    override val at: Long,
    val authorId: String,
    val dayStart: Long,
    val items: List<CircleItem>,
) : CirclePost {
    val totalCents: Cents get() = items.sumOf { it.cents }
}

data class TextPost(
    override val id: String,
    override val at: Long,
    val authorId: String,
    val text: String,
    /** True for messages typed in this session: they never leave the phone. */
    val localOnly: Boolean = false,
) : CirclePost

data class SystemPost(
    override val id: String,
    override val at: Long,
    val title: String,
    val body: String,
) : CirclePost

data class CategorySlice(val category: Category, val cents: Cents, val percent: Int)

/** What a reader is allowed to see on one receipt card after masking. `null` means 未共享. */
data class ReceiptView(
    val totalCents: Cents?,
    val slices: List<CategorySlice>?,
    val detail: List<CircleItem>?,
    val full: List<CircleItem>?,
)

/** Category totals, biggest first. Percent is display-only; money stays in cents. */
fun ReceiptPost.categorySlices(): List<CategorySlice> {
    val total = totalCents
    if (total <= 0) return emptyList()
    return items.groupBy { it.category }
        .map { (category, group) ->
            val cents = group.sumOf { it.cents }
            CategorySlice(category, cents, ((cents * 200 + total) / (total * 2)).toInt())
        }
        .sortedByDescending { it.cents }
}

/**
 * Applies an author's sharing profile. Masking is hierarchical: without 今日总额 no detail or receipt
 * can be shown, because summing either one would leak the total; 小票全文 also needs 单笔明细 on.
 */
fun ReceiptPost.viewFor(shares: Map<CircleShare, Boolean>): ReceiptView {
    fun on(item: CircleShare) = shares[item] == true
    val totalVisible = on(CircleShare.TodayTotal)
    val detailVisible = totalVisible && on(CircleShare.TxnDetail)
    val fullVisible = detailVisible && on(CircleShare.ReceiptFull)
    return ReceiptView(
        totalCents = if (totalVisible) totalCents else null,
        slices = if (on(CircleShare.CategoryMix)) categorySlices() else null,
        detail = if (detailVisible) items else null,
        full = if (fullVisible) items else null,
    )
}

// --- reactions -------------------------------------------------------------------------------

val CircleReactions: List<String> = listOf("👍", "❤️", "😮")

/** Key used in the "my reactions" set: one per post and emoji. */
fun reactionKey(postId: String, emoji: String): String = "$postId|$emoji"

/** Deterministic starter counts so the demo feed does not look freshly installed. */
fun baseReactions(postId: String): Map<String, Int> {
    val seed = postId.fold(7) { acc, ch -> acc * 31 + ch.code } and 0x7fffffff
    return mapOf(
        CircleReactions[0] to 1 + seed % 3,
        CircleReactions[1] to seed / 3 % 3,
        CircleReactions[2] to seed / 7 % 2,
    )
}

data class ReactionView(val emoji: String, val count: Int, val mine: Boolean)

fun reactionsFor(postId: String, mine: Set<String>): List<ReactionView> {
    val base = baseReactions(postId)
    return CircleReactions.map { emoji ->
        val on = reactionKey(postId, emoji) in mine
        ReactionView(emoji, (base[emoji] ?: 0) + if (on) 1 else 0, on)
    }
}

fun toggleReaction(mine: Set<String>, postId: String, emoji: String): Set<String> {
    val key = reactionKey(postId, emoji)
    return if (key in mine) mine - key else mine + key
}

// --- feed rows -------------------------------------------------------------------------------

sealed interface CircleFeedRow {
    val key: String

    data class Day(val dayStart: Long) : CircleFeedRow {
        override val key: String get() = "day-$dayStart"
    }

    data class Post(val post: CirclePost) : CircleFeedRow {
        override val key: String get() = "post-${post.id}"
    }
}

/** Feed order plus one day separator before the first post of each day. Empty in, empty out. */
fun circleFeedRows(posts: List<CirclePost>): List<CircleFeedRow> {
    val out = mutableListOf<CircleFeedRow>()
    var currentDay: Long? = null
    posts.sortedBy { it.at }.forEach { post ->
        val day = startOfDay(post.at)
        if (day != currentDay) {
            out += CircleFeedRow.Day(day)
            currentDay = day
        }
        out += CircleFeedRow.Post(post)
    }
    return out
}

// --- formatting ------------------------------------------------------------------------------

fun dayLabel(dayStart: Long, now: Long): String {
    val today = startOfDay(now)
    return when (dayStart) {
        today -> "今天"
        today - DAY_MS -> "昨天"
        today - 2 * DAY_MS -> "前天"
        else -> {
            val calendar = Calendar.getInstance().apply { timeInMillis = dayStart }
            "${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日"
        }
    }
}

fun timeLabel(at: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = at }
    return "%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
}

// --- deterministic demo data -----------------------------------------------------------------

object CircleDemo {
    const val MY_ID = "me"
    const val CIRCLE_NAME = "我的一家"

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    fun members(): List<CircleMember> = listOf(
        CircleMember(MY_ID, "我", "我", CircleTone.Accent, isMe = true, role = CircleRole.Owner),
        CircleMember("mom", "妈妈", "妈", CircleTone.Positive, role = CircleRole.Admin, nickname = "老妈"),
        CircleMember("dad", "爸爸", "爸", CircleTone.Info),
        CircleMember("sister", "妹妹", "妹", CircleTone.Warning),
    )

    /** Three circles so switching, inviting and per-circle sharing can be exercised offline. */
    fun book(now: Long = System.currentTimeMillis()): CircleBook {
        val home = Circle(
            id = "home",
            name = CIRCLE_NAME,
            ownerId = MY_ID,
            members = members(),
            posts = feed(now),
            invite = newInvite(now - 3 * HOUR, seed = 42L),
        )
        val roomies = Circle(
            id = "roomies",
            name = "合租三人组",
            ownerId = "zhe",
            members = listOf(
                CircleMember(MY_ID, "我", "我", CircleTone.Accent, isMe = true),
                CircleMember("zhe", "阿哲", "哲", CircleTone.Info, role = CircleRole.Owner),
                CircleMember("yu", "小雨", "雨", CircleTone.Warning),
            ),
            posts = listOf(
                ReceiptPost("rr-1", now - 2 * HOUR, "zhe", startOfDay(now), listOf(
                    CircleItem("菜市场", 4200, Category.GROCERY),
                    CircleItem("地铁", 500, Category.TRANSPORT),
                )),
                TextPost("rt-1", now - 100 * MINUTE, "yu", "这周水电我先垫了，月底一起算"),
            ).sortedBy { it.at },
            invite = newInvite(now - 5 * HOUR, seed = 77L),
        )
        val partner = Circle(
            id = "partner",
            name = "两个人",
            ownerId = MY_ID,
            members = listOf(
                CircleMember(MY_ID, "我", "我", CircleTone.Accent, isMe = true, role = CircleRole.Owner),
                CircleMember("wei", "阿伟", "伟", CircleTone.Positive),
            ),
            posts = listOf(
                ReceiptPost("pr-1", now - 4 * HOUR, "wei", startOfDay(now), listOf(
                    CircleItem("盒马", 8900, Category.GROCERY),
                )),
            ),
            invite = newInvite(now - 20 * HOUR, seed = 9L),
        )
        return CircleBook(listOf(home, roomies, partner), currentId = home.id)
    }

    /**
     * Three days of family receipts, a few text bubbles and two system messages. Fixed content and
     * fixed clock times (today's posts are clamped to "a moment ago"), so every launch looks the same.
     */
    fun feed(now: Long = System.currentTimeMillis()): List<CirclePost> {
        val posts = mutableListOf<CirclePost>()
        fun day(offset: Int): Long = startOfDay(now + offset * DAY_MS)

        fun at(offset: Int, hour: Int, minute: Int): Long {
            val raw = day(offset) + hour * HOUR + minute * MINUTE
            if (offset < 0) return raw
            // Today's cards must not be in the future, whatever time the app is opened.
            val latest = maxOf(now - MINUTE - posts.size * 30_000L, day(0) + MINUTE)
            return minOf(raw, latest)
        }

        fun item(merchant: String, cents: Cents, category: Category) = CircleItem(merchant, cents, category)

        fun receipt(id: String, author: String, offset: Int, hour: Int, minute: Int, items: List<CircleItem>) {
            posts += ReceiptPost(id, at(offset, hour, minute), author, day(offset), items)
        }

        fun text(id: String, author: String, offset: Int, hour: Int, minute: Int, body: String) {
            posts += TextPost(id, at(offset, hour, minute), author, body)
        }

        fun system(id: String, offset: Int, hour: Int, minute: Int, title: String, body: String) {
            posts += SystemPost(id, at(offset, hour, minute), title, body)
        }

        receipt(
            "r-me-2", MY_ID, -2, 21, 20, listOf(
                item("地铁", 400, Category.TRANSPORT),
                item("瑞幸咖啡", 1250, Category.DRINK),
                item("美团外卖", 3800, Category.MEAL),
                item("便利店", 950, Category.SNACK),
            ),
        )
        receipt(
            "r-mom-2", "mom", -2, 19, 40, listOf(
                item("盒马鲜生", 12640, Category.GROCERY),
                item("菜市场", 3200, Category.GROCERY),
                item("鲜牛奶", 1990, Category.GROCERY),
            ),
        )
        text("t-mom-2", "mom", -2, 19, 46, "鱼新鲜，晚上回来吃")
        system("s-weekly", -1, 8, 5, "家庭周报出炉", "每人一份周报已生成，去「洞察」看分类构成和头衔。")
        receipt(
            "r-sister-1", "sister", -1, 12, 35, listOf(
                item("食堂", 1200, Category.MEAL),
                item("奶茶", 1600, Category.DRINK),
                item("电影票", 4500, Category.FUN),
            ),
        )
        text("t-sister-1", "sister", -1, 12, 40, "食堂今天的糖醋排骨居然不甜")
        receipt("r-dad-1", "dad", -1, 21, 10, emptyList())
        text("t-dad-1", "dad", -1, 21, 12, "今天在单位吃，钱包休息一天")
        system("s-title", -1, 21, 30, "妹妹 本周头衔「奶茶续命王」", "家庭头衔是娱乐标签，不构成建议。")
        receipt(
            "r-me-1", MY_ID, -1, 22, 5, listOf(
                item("地铁", 800, Category.TRANSPORT),
                item("公司食堂", 1600, Category.MEAL),
                item("喜茶", 2400, Category.DRINK),
                item("淘宝", 8900, Category.SHOPPING),
            ),
        )
        text("t-me-1", MY_ID, -1, 22, 8, "淘宝那单是给家里买的路由器")
        receipt(
            "r-me-0", MY_ID, 0, 12, 20, listOf(
                item("地铁", 400, Category.TRANSPORT),
                item("公司食堂", 1800, Category.MEAL),
                item("瑞幸咖啡", 1650, Category.DRINK),
            ),
        )
        receipt(
            "r-sister-0", "sister", 0, 12, 45, listOf(
                item("食堂", 1500, Category.MEAL),
                item("蜜雪冰城", 700, Category.DRINK),
            ),
        )
        return posts.sortedBy { it.at }
    }
}
