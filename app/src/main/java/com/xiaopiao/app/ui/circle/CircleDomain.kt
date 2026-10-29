package com.xiaopiao.app.ui.circle

import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar

/**
 * Circle management + leaderboards (docs/APP-DESIGN.md §5.2, §5.4).
 *
 * Pure functions over immutable state so the sheet UI stays dumb and the rules are unit-testable:
 * names are capped at 12 glyphs, invite codes are 6 characters (case-insensitive, 24 h, 10 uses),
 * six members per circle, admins can never flip another member's sharing switches, and a board only
 * appears once three eligible members are in it.
 */

enum class CircleRole { Owner, Admin, Member }

/** 6 characters from an alphabet without look-alikes (0/O, 1/I/L). */
private const val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
const val INVITE_LENGTH = 6
const val INVITE_TTL_MS = 24 * 60 * 60 * 1000L
const val INVITE_MAX_USES = 10
const val CIRCLE_MAX_MEMBERS = 6
const val CIRCLE_MAX_NAME = 12
const val MEMBER_MAX_NICKNAME = 8
const val CIRCLE_MAX_ADMINS = 3

data class CircleInvite(
    val code: String,
    val createdAt: Long,
    val uses: Int = 0,
    val maxUses: Int = INVITE_MAX_USES,
    val ttlMs: Long = INVITE_TTL_MS,
) {
    val remainingUses: Int get() = (maxUses - uses).coerceAtLeast(0)
    fun isValid(now: Long): Boolean = uses < maxUses && now - createdAt <= ttlMs
    fun expiresAt(): Long = createdAt + ttlMs
}

data class Circle(
    val id: String,
    val name: String,
    val ownerId: String,
    val members: List<CircleMember>,
    val posts: List<CirclePost>,
    val invite: CircleInvite,
) {
    fun member(id: String): CircleMember? = members.firstOrNull { it.id == id }
    fun owner(): CircleMember? = member(ownerId)
    val adminCount: Int get() = members.count { it.role == CircleRole.Admin }
    fun isFull(): Boolean = members.size >= CIRCLE_MAX_MEMBERS
    /** A member who left keeps their history, but every item is masked on read. */
    fun sharesOf(memberId: String): Map<CircleShare, Boolean> =
        member(memberId)?.shares ?: CircleShare.entries.associateWith { false }
}

/** All circles on this phone plus which one is open. Local demo state: never leaves the device. */
data class CircleBook(val circles: List<Circle>, val currentId: String) {
    fun current(): Circle = circles.firstOrNull { it.id == currentId } ?: circles.first()
    fun byId(id: String): Circle? = circles.firstOrNull { it.id == id }
}

enum class CircleError { EmptyName, NameTooLong, CircleFull, InvalidCode, ExpiredCode, CodeUsedUp, NotAllowed, AlreadyMember }

data class CircleResult(val book: CircleBook, val error: CircleError? = null)

fun normalizeInviteCode(raw: String): String = raw.trim().uppercase().replace(" ", "")

/** Deterministic code so the demo looks the same on every launch; real invites would use SecureRandom. */
fun inviteCode(seed: Long): String {
    var value = seed
    val out = StringBuilder(INVITE_LENGTH)
    repeat(INVITE_LENGTH) {
        value = value * 6364136223846793005L + 1442695040888963407L
        val index = ((value ushr 33).toInt() and 0x7fffffff) % INVITE_ALPHABET.length
        out.append(INVITE_ALPHABET[index])
    }
    return out.toString()
}

fun newInvite(now: Long, seed: Long): CircleInvite = CircleInvite(code = inviteCode(seed), createdAt = now)

/** Creates an empty circle with the viewer as owner; ids stay local and deterministic. */
fun createCircle(book: CircleBook, rawName: String, owner: CircleMember, now: Long): CircleResult {
    val name = rawName.trim()
    if (name.isEmpty()) return CircleResult(book, CircleError.EmptyName)
    if (name.length > CIRCLE_MAX_NAME) return CircleResult(book, CircleError.NameTooLong)
    val id = "c-${now}-${book.circles.size}"
    val circle = Circle(
        id = id,
        name = name,
        ownerId = owner.id,
        members = listOf(owner.copy(role = CircleRole.Owner)),
        posts = emptyList(),
        invite = newInvite(now, now / 1000),
    )
    return CircleResult(book.copy(circles = book.circles + circle, currentId = id))
}

fun renameCircle(book: CircleBook, circleId: String, rawName: String): CircleResult {
    val name = rawName.trim()
    if (name.isEmpty()) return CircleResult(book, CircleError.EmptyName)
    if (name.length > CIRCLE_MAX_NAME) return CircleResult(book, CircleError.NameTooLong)
    return CircleResult(book.copy(circles = book.circles.map { if (it.id == circleId) it.copy(name = name) else it }))
}

fun switchCircle(book: CircleBook, circleId: String): CircleBook =
    if (book.byId(circleId) == null) book else book.copy(currentId = circleId)

fun regenerateInvite(book: CircleBook, circleId: String, now: Long): CircleBook =
    book.copy(circles = book.circles.map { circle ->
        if (circle.id != circleId) circle
        else circle.copy(invite = newInvite(now, circle.invite.createdAt + circle.members.size + now / 1000))
    })

fun redeemInvite(book: CircleBook, rawCode: String, now: Long, joiner: CircleMember): CircleResult {
    val code = normalizeInviteCode(rawCode)
    if (code.length != INVITE_LENGTH) return CircleResult(book, CircleError.InvalidCode)
    val circle = book.circles.firstOrNull { it.invite.code == code } ?: return CircleResult(book, CircleError.InvalidCode)
    if (!circle.invite.isValid(now)) {
        return CircleResult(book, if (circle.invite.uses >= circle.invite.maxUses) CircleError.CodeUsedUp else CircleError.ExpiredCode)
    }
    if (circle.members.any { it.id == joiner.id }) return CircleResult(book, CircleError.AlreadyMember)
    if (circle.isFull()) return CircleResult(book, CircleError.CircleFull)
    val next = circle.copy(
        members = circle.members + joiner.copy(role = CircleRole.Member),
        invite = circle.invite.copy(uses = circle.invite.uses + 1),
    )
    return CircleResult(book.copy(circles = book.circles.map { if (it.id == circle.id) next else it }))
}

/** Self-service fields: nobody else can change these, including the owner. */
fun setNickname(book: CircleBook, circleId: String, memberId: String, raw: String): CircleResult {
    val nickname = raw.trim()
    if (nickname.length > MEMBER_MAX_NICKNAME) return CircleResult(book, CircleError.NameTooLong)
    return CircleResult(book.mapMember(circleId, memberId) { it.copy(nickname = nickname.ifEmpty { null }) })
}

/** Owner-only, private annotation; members never see it. */
fun setRemark(book: CircleBook, circleId: String, memberId: String, raw: String): CircleResult {
    val circle = book.byId(circleId) ?: return CircleResult(book, CircleError.NotAllowed)
    val remark = raw.trim()
    if (remark.length > MEMBER_MAX_NICKNAME) return CircleResult(book, CircleError.NameTooLong)
    val updated = circle.copy(members = circle.members.map {
        if (it.id == memberId) it.copy(remark = remark.ifEmpty { null }) else it
    })
    return CircleResult(book.copy(circles = book.circles.map { if (it.id == circleId) updated else it }))
}

fun setRole(book: CircleBook, circleId: String, memberId: String, role: CircleRole): CircleResult {
    val circle = book.byId(circleId) ?: return CircleResult(book, CircleError.NotAllowed)
    if (memberId == circle.ownerId) return CircleResult(book, CircleError.NotAllowed)
    if (role == CircleRole.Admin && circle.adminCount >= CIRCLE_MAX_ADMINS) return CircleResult(book, CircleError.NotAllowed)
    return CircleResult(book.mapMember(circleId, memberId) { it.copy(role = role) })
}

fun removeMember(book: CircleBook, circleId: String, memberId: String): CircleResult {
    val circle = book.byId(circleId) ?: return CircleResult(book, CircleError.NotAllowed)
    if (memberId == circle.ownerId) return CircleResult(book, CircleError.NotAllowed)
    // History stays, but every post by the removed member is re-masked on read.
    val updated = circle.copy(members = circle.members.filterNot { it.id == memberId })
    return CircleResult(book.copy(circles = book.circles.map { if (it.id == circleId) updated else it }))
}

/** Only the member may flip their own switches; the owner and admins see them read-only. */
fun canEditShares(viewerId: String, memberId: String): Boolean = viewerId == memberId

/** Sharing switches belong to the member, so an admin's attempt is a no-op. */
fun setShare(book: CircleBook, circleId: String, memberId: String, item: CircleShare, visible: Boolean): CircleResult =
    CircleResult(book.mapMember(circleId, memberId) { it.withShare(item, visible) })

private fun CircleBook.mapMember(circleId: String, memberId: String, block: (CircleMember) -> CircleMember): CircleBook =
    copy(circles = circles.map { circle ->
        if (circle.id != circleId) circle
        else circle.copy(members = circle.members.map { if (it.id == memberId) block(it) else it })
    })

/** What a viewer calls a member: their own nickname, the owner's private remark, then the joined name. */
fun CircleMember.displayName(viewerId: String, ownerId: String): String =
    remark?.takeIf { viewerId == ownerId } ?: nickname ?: name

// --- leaderboards ------------------------------------------------------------------------------

enum class LeaderboardType { LeastSpent, MostSpent, Streak, Badges }

data class LeaderboardRow(
    val memberId: String,
    val name: String,
    val value: Long,
    val count: Int,
    val rank: Int,
    val isMe: Boolean,
    val dormant: Boolean = false,
    val shared: Boolean = true,
    val badges: List<CircleBadge> = emptyList(),
)

data class CircleLeaderboard(
    val type: LeaderboardType,
    val rows: List<LeaderboardRow>,
    val eligible: Int,
    val unshared: Int,
) {
    /** Board only exists once three people are in it (docs §5.2 sample threshold). */
    val visible: Boolean get() = eligible >= 3
}

data class CircleBadge(val emoji: String, val title: String)

/** Monday 00:00 of the week that contains [now]. */
fun weekStart(now: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val dow = calendar.get(Calendar.DAY_OF_WEEK)
    val back = (dow + 5) % 7 // Monday = 0
    return startOfDay(now) - back * DAY_MS
}

/** "10/26–11/1 · 第 43 周" */
fun weekWindowLabel(now: Long): String {
    val start = weekStart(now)
    val end = start + 6 * DAY_MS
    val c = Calendar.getInstance()
    c.timeInMillis = start
    val from = "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
    c.timeInMillis = end
    val to = "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
    val week = c.get(Calendar.WEEK_OF_YEAR)
    return "$from–$to · 第 $week 周"
}

fun weekSpendOf(circle: Circle, memberId: String, now: Long): Long {
    val start = weekStart(now)
    return circle.posts.asSequence()
        .filterIsInstance<ReceiptPost>()
        .filter { it.authorId == memberId && it.at in start until now + 1 }
        .sumOf { it.totalCents }
}

fun weekTxnCount(circle: Circle, memberId: String, now: Long): Int {
    val start = weekStart(now)
    return circle.posts.asSequence()
        .filterIsInstance<ReceiptPost>()
        .filter { it.authorId == memberId && it.at in start until now + 1 }
        .sumOf { it.items.size }
}

/** Consecutive days with at least one receipt, counting back from today. */
fun streakDays(circle: Circle, memberId: String, now: Long): Int {
    val days = circle.posts.asSequence()
        .filterIsInstance<ReceiptPost>()
        .filter { it.authorId == memberId }
        .map { startOfDay(it.at) }
        .toSet()
    var day = startOfDay(now)
    var streak = 0
    while (day in days) {
        streak += 1
        day -= DAY_MS
    }
    return streak
}

private val BADGE_POOL = listOf(
    CircleBadge("🌱", "第一天"),
    CircleBadge("🧾", "三张连打"),
    CircleBadge("🍜", "吃饭规律"),
    CircleBadge("☕", "咖啡续命"),
    CircleBadge("🛒", "囤货达人"),
    CircleBadge("🚇", "通勤战士"),
    CircleBadge("🌙", "早睡省钱"),
    CircleBadge("🎯", "预算守门"),
    CircleBadge("🧊", "冷静选手"),
    CircleBadge("💛", "最会过日子"),
)

fun badgesFor(circle: Circle, memberId: String, now: Long): List<CircleBadge> {
    val seed = (memberId.hashCode() * 31 + weekStart(now).toInt()) and 0x7fffffff
    val picks = (0 until 3).map { BADGE_POOL[(seed / (it + 1)) % BADGE_POOL.size] }
    return picks.distinctBy { it.title }
}

fun leaderboard(circle: Circle, type: LeaderboardType, now: Long, viewerId: String): CircleLeaderboard {
    val ownerId = circle.ownerId
    fun row(member: CircleMember, value: Long, count: Int, dormant: Boolean = false, shared: Boolean = true) =
        LeaderboardRow(
            memberId = member.id,
            name = member.displayName(viewerId, ownerId),
            value = value,
            count = count,
            rank = 0,
            isMe = member.id == viewerId,
            dormant = dormant,
            shared = shared,
            badges = if (type == LeaderboardType.Badges) badgesFor(circle, member.id, now) else emptyList(),
        )

    val ordered: List<LeaderboardRow>
    val eligible: Int
    val unshared: Int
    when (type) {
        LeaderboardType.LeastSpent, LeaderboardType.MostSpent -> {
            val sharers = circle.members.filter { it.shows(CircleShare.TodayTotal) }
            unshared = circle.members.size - sharers.size
            eligible = sharers.size
            ordered = sharers
                .map { m ->
                    val spend = weekSpendOf(circle, m.id, now)
                    row(m, spend, weekTxnCount(circle, m.id, now), dormant = spend == 0L)
                }
                .sortedWith(if (type == LeaderboardType.LeastSpent) compareBy({ it.value }, { it.name }) else compareByDescending<LeaderboardRow> { it.value }.thenBy { it.name })
        }
        LeaderboardType.Streak -> {
            unshared = 0
            eligible = circle.members.size
            ordered = circle.members
                .map { m -> val s = streakDays(circle, m.id, now); row(m, s.toLong(), s) }
                .sortedWith(compareByDescending<LeaderboardRow> { it.value }.thenBy { it.name })
        }
        LeaderboardType.Badges -> {
            unshared = 0
            eligible = circle.members.size
            ordered = circle.members
                .map { m -> val badges = badgesFor(circle, m.id, now); row(m, badges.size.toLong(), badges.size).copy(badges = badges) }
                .sortedWith(compareByDescending<LeaderboardRow> { it.value }.thenBy { it.name })
        }
    }
    val ranked = ordered.mapIndexed { index, r -> r.copy(rank = index + 1) }
    return CircleLeaderboard(type, ranked, eligible, unshared)
}

/** Per-member weekly titles used by the award card; deterministic per week. */
fun weeklyTitle(circle: Circle, member: CircleMember, now: Long): CircleBadge {
    val seed = (member.id.hashCode() * 131 + weekStart(now).toInt()) and 0x7fffffff
    return BADGE_POOL[seed % BADGE_POOL.size]
}

fun awardSystemPost(circle: Circle, now: Long): SystemPost {
    val titles = circle.members.joinToString(" · ") { m ->
        val title = weeklyTitle(circle, m, now)
        "${m.displayName(m.id, circle.ownerId)} ${title.emoji}${title.title}"
    }
    return SystemPost(
        id = "award-${weekStart(now)}",
        at = now,
        title = "本周家庭颁奖",
        body = titles,
    )
}

// --- sharing to a circle -----------------------------------------------------------------------

data class ShareTarget(
    val circleId: String,
    val name: String,
    val memberCount: Int,
    val visibleItems: Int,
    val sharedTotalVisible: Boolean,
    val selected: Boolean = false,
)

/** One line per circle: how much the viewer would reveal if a receipt is posted there. */
fun shareTargets(book: CircleBook, viewerId: String): List<ShareTarget> = book.circles.map { circle ->
    val shares = circle.sharesOf(viewerId)
    ShareTarget(
        circleId = circle.id,
        name = circle.name,
        memberCount = circle.members.size,
        visibleItems = CircleShare.entries.count { shares[it] == true },
        sharedTotalVisible = shares[CircleShare.TodayTotal] == true,
    )
}

/** Posts today's receipt as the viewer into the given circles; the card is masked on read. */
fun shareReceiptTo(book: CircleBook, circleIds: Set<String>, post: ReceiptPost, now: Long): CircleBook =
    book.copy(circles = book.circles.map { circle ->
        if (circle.id !in circleIds) circle
        else circle.copy(posts = circle.posts + post.copy(id = "${post.id}-${circle.id}", at = now))
    })
