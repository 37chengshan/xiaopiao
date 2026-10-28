package com.xiaopiao.app.ui.circle

import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleModelTest {
    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 28, 15, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val members = CircleDemo.members()
    private val me = members.first { it.isMe }

    private fun myReceipts() = CircleDemo.feed(now)
        .filterIsInstance<ReceiptPost>()
        .filter { it.authorId == CircleDemo.MY_ID }

    @Test
    fun feedIsDeterministic() {
        assertEquals(CircleDemo.feed(now), CircleDemo.feed(now))
    }

    @Test
    fun feedIdsAreUnique() {
        val ids = CircleDemo.feed(now).map { it.id }
        assertEquals(ids.distinct().size, ids.size)
    }

    @Test
    fun feedIsChronological() {
        val times = CircleDemo.feed(now).map { it.at }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun everyAuthorIsAMember() {
        val ids = members.map { it.id }.toSet()
        CircleDemo.feed(now).forEach { post ->
            when (post) {
                is ReceiptPost -> assertTrue("unknown author ${post.authorId}", post.authorId in ids)
                is TextPost -> assertTrue("unknown author ${post.authorId}", post.authorId in ids)
                is SystemPost -> Unit
            }
        }
    }

    @Test
    fun feedCoversReceiptsTextAndSystemMessages() {
        val posts = CircleDemo.feed(now)
        assertTrue(posts.any { it is ReceiptPost })
        assertTrue(posts.any { it is TextPost })
        assertTrue(posts.any { it is SystemPost })
    }

    @Test
    fun defaultsAreConservative() {
        assertEquals(5, me.shares.size)
        assertTrue(me.shows(CircleShare.TodayTotal))
        assertTrue(me.shows(CircleShare.CategoryMix))
        assertTrue(me.shows(CircleShare.WeeklyTitles))
        assertFalse(me.shows(CircleShare.TxnDetail))
        assertFalse(me.shows(CircleShare.ReceiptFull))
    }

    @Test
    fun defaultViewMasksDetailButKeepsTotalAndCategories() {
        val post = myReceipts().first()
        val view = post.viewFor(me.shares)
        assertEquals(post.totalCents, view.totalCents)
        assertTrue(view.slices != null)
        assertNull(view.detail)
        assertNull(view.full)
    }

    @Test
    fun hidingTotalAlsoHidesDetailAndFullReceipt() {
        val post = myReceipts().first()
        val closed = me
            .withShare(CircleShare.TodayTotal, false)
            .withShare(CircleShare.TxnDetail, true)
            .withShare(CircleShare.ReceiptFull, true)
        val view = post.viewFor(closed.shares)
        assertNull(view.totalCents)
        assertNull(view.detail)
        assertNull(view.full)
    }

    @Test
    fun detailSwitchRevealsItemsAndFullNeedsDetail() {
        val post = myReceipts().first()
        val detailOnly = post.viewFor(me.withShare(CircleShare.TxnDetail, true).shares)
        assertEquals(post.items, detailOnly.detail)
        assertNull("小票全文 still off", detailOnly.full)

        val withFull = me.withShare(CircleShare.TxnDetail, true).withShare(CircleShare.ReceiptFull, true)
        assertEquals(post.items, post.viewFor(withFull.shares).full)

        val fullWithoutDetail = me.withShare(CircleShare.ReceiptFull, true)
        assertNull(post.viewFor(fullWithoutDetail.shares).full)
    }

    @Test
    fun categoryMixSwitchIsIndependentFromTotal() {
        val post = myReceipts().first()
        val noCategories = post.viewFor(me.withShare(CircleShare.CategoryMix, false).shares)
        assertNull(noCategories.slices)
        assertEquals(post.totalCents, noCategories.totalCents)
    }

    @Test
    fun categorySlicesAddUpAndRoundToWholePercent() {
        val post = myReceipts().first()
        val slices = post.categorySlices()
        assertTrue(slices.isNotEmpty())
        assertEquals(post.totalCents, slices.sumOf { it.cents })
        assertEquals(slices.sortedByDescending { it.cents }, slices)
        val percent = slices.sumOf { it.percent }
        assertTrue("percent sum was $percent", percent in 98..102)
    }

    @Test
    fun zeroSpendReceiptKeepsAnEmptyCard() {
        val empty = CircleDemo.feed(now)
            .filterIsInstance<ReceiptPost>()
            .first { it.authorId == "dad" && it.items.isEmpty() }
        assertTrue(empty.categorySlices().isEmpty())
        val view = empty.viewFor(me.shares)
        assertEquals(0L, view.totalCents!!)
        assertTrue(view.slices!!.isEmpty())
        assertTrue(view.detail == null)
    }

    @Test
    fun sharingOnlyTouchesOneMember() {
        val updated = me.withShare(CircleShare.TodayTotal, false)
        assertFalse(updated.shows(CircleShare.TodayTotal))
        assertTrue(me.shows(CircleShare.TodayTotal))
        assertTrue(updated.shares.keys.containsAll(CircleShare.entries))
    }

    @Test
    fun reactionsToggleCountAndMineFlag() {
        val postId = myReceipts().first().id
        val base = baseReactions(postId)
        val before = reactionsFor(postId, emptySet())

        val mine = toggleReaction(emptySet(), postId, "👍")
        val after = reactionsFor(postId, mine)
        val thumbBefore = before.first { it.emoji == "👍" }
        val thumbAfter = after.first { it.emoji == "👍" }
        assertFalse(thumbBefore.mine)
        assertTrue(thumbAfter.mine)
        assertEquals((base["👍"] ?: 0) + 1, thumbAfter.count)

        val undone = toggleReaction(mine, postId, "👍")
        assertTrue(undone.isEmpty())
        assertEquals(before, reactionsFor(postId, undone))
    }

    @Test
    fun feedRowsInsertExactlyOneHeaderPerDay() {
        val posts = CircleDemo.feed(now)
        val rows = circleFeedRows(posts)
        assertEquals(posts.size, rows.filterIsInstance<CircleFeedRow.Post>().size)
        val days = rows.filterIsInstance<CircleFeedRow.Day>().map { it.dayStart }
        assertEquals(days.distinct(), days)
        assertEquals(posts.map { startOfDay(it.at) }.distinct().size, days.size)
    }

    @Test
    fun emptyFeedHasNoRows() {
        assertTrue(circleFeedRows(emptyList()).isEmpty())
    }

    @Test
    fun onePostStillGetsADayHeader() {
        val post = CircleDemo.feed(now).first()
        val rows = circleFeedRows(listOf(post))
        assertEquals(2, rows.size)
        assertTrue(rows[0] is CircleFeedRow.Day)
        assertEquals(CircleFeedRow.Post(post), rows[1])
    }

    @Test
    fun dayLabelsUseRelativeNames() {
        val today = startOfDay(now)
        assertEquals("今天", dayLabel(today, now))
        assertEquals("昨天", dayLabel(today - DAY_MS, now))
        assertEquals("前天", dayLabel(today - 2 * DAY_MS, now))
    }

    @Test
    fun timeLabelIsZeroPadded() {
        val at = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 3, 8, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("08:05", timeLabel(at))
    }
}
