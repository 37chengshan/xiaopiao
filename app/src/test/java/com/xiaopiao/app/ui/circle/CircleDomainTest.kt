package com.xiaopiao.app.ui.circle

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleDomainTest {
    /** Wednesday 21:00, so "this week" has room for today, yesterday and a broken streak. */
    private val now = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.OCTOBER, 28, 21, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun member(id: String = "me", role: CircleRole = CircleRole.Owner, shares: Map<CircleShare, Boolean> = CircleShare.defaults()) =
        CircleMember(id, id, id.take(1), CircleTone.Accent, isMe = id == "me", role = role, shares = shares)

    private fun circle(
        members: List<CircleMember> = listOf(member("me"), member("a"), member("b")),
        posts: List<CirclePost> = emptyList(),
        invite: CircleInvite = newInvite(now - 1000, 7),
    ) = Circle("c", "家", "me", members, posts, invite)

    private fun book(vararg circles: Circle) = CircleBook(circles.toList(), circles.first().id)

    private fun receipt(id: String, author: String, daysAgo: Int, cents: Long, items: Int = 1): ReceiptPost {
        val at = startOfDay(now) - daysAgo * DAY_MS + 20 * 60 * 60 * 1000
        return ReceiptPost(id, at, author, startOfDay(at), List(items) { CircleItem("店", cents / items, Category.MEAL) })
    }

    // --- invite codes --------------------------------------------------------------------------

    @Test
    fun `invite codes are six unambiguous characters`() {
        repeat(50) { seed ->
            val code = inviteCode(seed.toLong())
            assertEquals(6, code.length)
            assertTrue(code.all { it in "ABCDEFGHJKMNPQRSTUVWXYZ23456789" })
        }
    }

    @Test
    fun `redeeming is case insensitive and spends one use`() {
        val inv = newInvite(now - 60_000, 11)
        val b = book(circle(invite = inv))
        val result = redeemInvite(b, inv.code.lowercase(), now, member("newbie"))
        assertNull(result.error)
        val after = result.book.circles.first()
        assertEquals(4, after.members.size)
        assertEquals(1, after.invite.uses)
        assertEquals(CircleRole.Member, after.members.last().role)
    }

    @Test
    fun `expired and exhausted codes are rejected`() {
        val expired = newInvite(now - INVITE_TTL_MS - 1, 12)
        assertEquals(CircleError.ExpiredCode, redeemInvite(book(circle(invite = expired)), expired.code, now, member("x")).error)

        val exhausted = newInvite(now - 1000, 13).copy(uses = INVITE_MAX_USES)
        assertEquals(CircleError.CodeUsedUp, redeemInvite(book(circle(invite = exhausted)), exhausted.code, now, member("x")).error)
        assertEquals(CircleError.InvalidCode, redeemInvite(book(circle()), "ZZZZZZ", now, member("x")).error)
    }

    @Test
    fun `seventh member is refused`() {
        val six = (1..6).map { member("m$it") }
        val result = redeemInvite(book(circle(members = six)), circle(members = six).invite.code, now, member("late"))
        assertEquals(CircleError.CircleFull, result.error)
    }

    @Test
    fun `circle names are capped at twelve glyphs`() {
        val b = book(circle())
        assertEquals(CircleError.NameTooLong, renameCircle(b, "c", "十二个字的名字确实放不下吧").error)
        assertEquals(CircleError.EmptyName, renameCircle(b, "c", "  ").error)
        assertEquals("新家", renameCircle(b, "c", " 新家 ").book.circles.first().name)
    }

    // --- roles ---------------------------------------------------------------------------------

    @Test
    fun `owner cannot be demoted or removed`() {
        assertEquals(CircleError.NotAllowed, setRole(book(circle()), "c", "me", CircleRole.Member).error)
        assertEquals(CircleError.NotAllowed, removeMember(book(circle()), "c", "me").error)
    }

    @Test
    fun `at most three admins`() {
        val members = listOf(member("me"), member("a"), member("b"), member("d"), member("e"))
        var b = book(circle(members = members))
        b = setRole(b, "c", "a", CircleRole.Admin).book
        b = setRole(b, "c", "b", CircleRole.Admin).book
        b = setRole(b, "c", "d", CircleRole.Admin).book
        assertEquals(CircleError.NotAllowed, setRole(b, "c", "e", CircleRole.Admin).error)
    }

    @Test
    fun `only the member can edit their sharing switches`() {
        assertTrue(canEditShares("a", "a"))
        assertFalse(canEditShares("me", "a"))
        assertFalse(canEditShares("admin", "a"))
    }

    @Test
    fun `removing a member keeps history but re-masks it`() {
        val c = circle(posts = listOf(receipt("r1", "a", 0, 3_000)))
        val before = (c.posts.first() as ReceiptPost).viewFor(c.sharesOf("a"))
        assertEquals(3_000L, before.totalCents)
        val afterCircle = removeMember(book(c), "c", "a").book.circles.first()
        val orphanShares = afterCircle.sharesOf("a")
        assertTrue(orphanShares.values.none { it })
        val after = (afterCircle.posts.first() as ReceiptPost).viewFor(orphanShares)
        assertEquals(null, after.totalCents)
    }

    // --- leaderboards ---------------------------------------------------------------------------

    private fun boardBook(): CircleBook {
        val a = member("a")
        val b = member("b")
        val c = member("c")
        return book(
            circle(
                members = listOf(member("me"), a, b, c),
                posts = listOf(
                    receipt("1", "me", 0, 1_000),
                    receipt("2", "a", 0, 5_000, items = 2),
                    receipt("3", "b", 1, 2_000),
                    receipt("4", "c", 0, 0),
                ),
            ),
        )
    }

    @Test
    fun `least and most spent rank opposite ways`() {
        val c = boardBook().current()
        val least = leaderboard(c, LeaderboardType.LeastSpent, now, "me").rows
        assertEquals(listOf("c", "me", "b", "a"), least.map { it.memberId })
        val most = leaderboard(c, LeaderboardType.MostSpent, now, "me").rows
        assertEquals(listOf("a", "b", "me", "c"), most.map { it.memberId })
        assertTrue(least.first { it.memberId == "c" }.dormant)
    }

    @Test
    fun `boards need three eligible members`() {
        val two = book(circle(members = listOf(member("me"), member("a"))))
        assertFalse(leaderboard(two.current(), LeaderboardType.MostSpent, now, "me").visible)
        assertTrue(leaderboard(boardBook().current(), LeaderboardType.MostSpent, now, "me").visible)
    }

    @Test
    fun `members who hide totals are left out of spend boards`() {
        val hidden = member("b", shares = CircleShare.defaults() + (CircleShare.TodayTotal to false))
        val c = circle(members = listOf(member("me"), member("a"), hidden, member("d")), posts = listOf(receipt("1", "b", 0, 9_999)))
        val board = leaderboard(c, LeaderboardType.MostSpent, now, "me")
        assertEquals(1, board.unshared)
        assertFalse(board.rows.any { it.memberId == "b" })
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val c = circle(posts = listOf(receipt("1", "me", 0, 100), receipt("2", "me", 1, 100), receipt("3", "me", 2, 100), receipt("4", "me", 4, 100)))
        assertEquals(3, streakDays(c, "me", now))
        assertEquals(0, streakDays(c, "a", now))
    }

    @Test
    fun `week window label names the monday range`() {
        val label = weekWindowLabel(now)
        assertTrue(label.contains("–"))
        assertTrue(label.contains("周"))
    }

    // --- sharing / awards -----------------------------------------------------------------------

    @Test
    fun `share targets summarize each circle`() {
        val targets = shareTargets(CircleDemo.book(now), CircleDemo.MY_ID)
        assertEquals(3, targets.size)
        assertTrue(targets.all { it.memberCount >= 2 })
        assertTrue(targets.all { it.visibleItems in 1..CircleShare.entries.size })
    }

    @Test
    fun `sharing a receipt lands in each selected circle`() {
        val b = CircleDemo.book(now)
        val receipt = ReceiptPost("share", now, CircleDemo.MY_ID, startOfDay(now), listOf(CircleItem("店", 100, Category.MEAL)))
        val after = shareReceiptTo(b, setOf("home", "roomies"), receipt, now)
        assertTrue(after.byId("home")!!.posts.any { it.id == "share-home" })
        assertTrue(after.byId("roomies")!!.posts.any { it.id == "share-roomies" })
        assertFalse(after.byId("partner")!!.posts.any { it.id.startsWith("share") })
    }

    @Test
    fun `award card names every member and stays deterministic per week`() {
        val c = CircleDemo.book(now).current()
        val first = awardSystemPost(c, now)
        val second = awardSystemPost(c, now + 60_000)
        assertEquals(first.title, second.title)
        assertEquals(first.body, second.body)
        assertTrue(first.body.contains("老妈"))
        assertEquals(4, first.body.split(" · ").size)
    }
}
