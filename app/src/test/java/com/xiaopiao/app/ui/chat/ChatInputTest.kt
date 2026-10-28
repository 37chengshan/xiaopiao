package com.xiaopiao.app.ui.chat

import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputTest {

    @Test
    fun modeFollowsTextAndStreaming() {
        assertEquals(ChatInputMode.Idle, ChatInput.mode("", streaming = false))
        assertEquals(ChatInputMode.Idle, ChatInput.mode("   ", streaming = false))
        assertEquals(ChatInputMode.Composing, ChatInput.mode("午饭", streaming = false))
        // A streaming reply owns the button, even if the user already typed the next question.
        assertEquals(ChatInputMode.Streaming, ChatInput.mode("午饭", streaming = true))
        assertEquals(ChatInputMode.Streaming, ChatInput.mode("", streaming = true))
    }

    @Test
    fun oneRoundButtonHasOneJobPerMode() {
        assertEquals(ChatPrimaryAction.Voice, ChatInput.primary(ChatInputMode.Idle))
        assertEquals(ChatPrimaryAction.Send, ChatInput.primary(ChatInputMode.Composing))
        assertEquals(ChatPrimaryAction.Stop, ChatInput.primary(ChatInputMode.Streaming))
    }

    @Test
    fun sendIsBlockedWhileEmptyOrStreaming() {
        assertFalse(ChatInput.canSend("", streaming = false))
        assertFalse(ChatInput.canSend("  \n ", streaming = false))
        assertTrue(ChatInput.canSend("记一笔 28", streaming = false))
        assertFalse(ChatInput.canSend("记一笔 28", streaming = true))
    }

    @Test
    fun cardOnlyExpandsOnceThereIsText() {
        assertFalse(ChatInput.isExpanded(ChatInputMode.Idle))
        assertTrue(ChatInput.isExpanded(ChatInputMode.Composing))
        assertTrue(ChatInput.isExpanded(ChatInputMode.Streaming))
    }
}

class SessionGroupsTest {

    private val now = startOfDay(1_800_000_000_000L) + 15 * 60 * 60 * 1000L // today at 15:00

    private fun session(id: Int, at: Long) = ChatSession(
        id = id,
        title = "会话 $id",
        items = listOf(ChatItem.User(id.toLong(), "问题 $id")),
        updatedAt = at,
    )

    @Test
    fun bucketsArePinnedToDayBoundaries() {
        assertEquals(SessionBucket.Today, SessionGroups.bucketOf(now - 1000L, now))
        assertEquals(SessionBucket.Today, SessionGroups.bucketOf(now + 60_000L, now))
        assertEquals(SessionBucket.Yesterday, SessionGroups.bucketOf(now - DAY_MS, now))
        assertEquals(SessionBucket.Yesterday, SessionGroups.bucketOf(now - DAY_MS + 1000L, now))
        assertEquals(SessionBucket.LastWeek, SessionGroups.bucketOf(now - 2 * DAY_MS, now))
        assertEquals(SessionBucket.LastWeek, SessionGroups.bucketOf(now - 6 * DAY_MS, now))
        assertEquals(SessionBucket.Earlier, SessionGroups.bucketOf(now - 7 * DAY_MS, now))
        assertEquals(SessionBucket.Earlier, SessionGroups.bucketOf(now - 40 * DAY_MS, now))
    }

    @Test
    fun groupsKeepDisplayOrderAndDropEmptyBuckets() {
        val sessions = listOf(
            session(1, now - 8 * 3600_000L),           // today morning
            session(2, now - 2 * DAY_MS),              // within 7 days
            session(3, now - DAY_MS),                  // yesterday
            session(4, now - 30 * DAY_MS),             // earlier
            session(5, now - 60_000L),                 // today, newest
        )
        val groups = SessionGroups.group(sessions, now)
        assertEquals(
            listOf(SessionBucket.Today, SessionBucket.Yesterday, SessionBucket.LastWeek, SessionBucket.Earlier),
            groups.map { it.first },
        )
        // Newest first inside a bucket.
        assertEquals(listOf(5, 1), groups.first().second.map { it.id })
        assertEquals(listOf(3), groups[1].second.map { it.id })
        assertEquals(listOf(2), groups[2].second.map { it.id })
        assertEquals(listOf(4), groups[3].second.map { it.id })
    }

    @Test
    fun emptyBucketIsNotRendered() {
        val groups = SessionGroups.group(listOf(session(1, now)), now)
        assertEquals(1, groups.size)
        assertEquals(SessionBucket.Today, groups.single().first)
    }

    @Test
    fun titleComesFromTheFirstUserQuestion() {
        val items = listOf(
            ChatItem.Tool(1, "读取账本"),
            ChatItem.User(2, "这个星期我花在吃饭上的钱有多少"),
            ChatItem.User(3, "那上周呢"),
        )
        val title = SessionGroups.titleOf(items, maxChars = 12)
        assertTrue(title.startsWith("这个星期我花在"))
        assertTrue(title.endsWith("…"))
        assertEquals(12, title.length)
        assertEquals("新对话", SessionGroups.titleOf(emptyList()))
        assertEquals("早饭 8", SessionGroups.titleOf(listOf(ChatItem.User(1, "早饭 8"))))
    }

    @Test
    fun roundsCountOnlyUserTurns() {
        val s = ChatSession(1, "t", listOf(ChatItem.User(1, "a"), ChatItem.Tool(2, "x"), ChatItem.User(3, "b")), now)
        assertEquals(2, s.rounds)
    }
}
