package com.xiaopiao.app.ui.chat

import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay

/**
 * Pure UI state for the AI page (v6): the two top segments, the composer's derived mode and the session
 * grouping. Kept out of the composables so the rules can be unit-tested without a device.
 */

/** The pill in the top bar. [Ledger] is a read-only digest of this week; questions still go to [Chat]. */
enum class ChatSegment(val label: String) { Chat("聊天"), Ledger("账本") }

/** Composer state, derived only from what the user typed and whether a reply is streaming. */
enum class ChatInputMode { Idle, Composing, Streaming }

/** What the one round button does in each mode: same size, different job. */
enum class ChatPrimaryAction { Voice, Send, Stop }

object ChatInput {
    /** A streaming reply owns the button, so typing during a stream keeps the stop affordance. */
    fun mode(text: String, streaming: Boolean): ChatInputMode = when {
        streaming -> ChatInputMode.Streaming
        text.isBlank() -> ChatInputMode.Idle
        else -> ChatInputMode.Composing
    }

    fun primary(mode: ChatInputMode): ChatPrimaryAction = when (mode) {
        ChatInputMode.Idle -> ChatPrimaryAction.Voice
        ChatInputMode.Composing -> ChatPrimaryAction.Send
        ChatInputMode.Streaming -> ChatPrimaryAction.Stop
    }

    fun canSend(text: String, streaming: Boolean): Boolean = text.isNotBlank() && !streaming

    /** The card grows to two rows as soon as there is text; it never grows while empty. */
    fun isExpanded(mode: ChatInputMode): Boolean = mode != ChatInputMode.Idle
}

/** One saved conversation. [updatedAt] drives the "今天 / 昨天 / 7 天内" groups in the drawer. */
data class ChatSession(
    val id: Int,
    val title: String,
    val items: List<ChatItem>,
    val updatedAt: Long,
) {
    val rounds: Int get() = items.count { it is ChatItem.User }
}

enum class SessionBucket(val label: String) {
    Today("今天"),
    Yesterday("昨天"),
    LastWeek("7 天内"),
    Earlier("更早"),
}

object SessionGroups {
    /** 7 天内 = the five days before yesterday; anything older is 更早. */
    fun bucketOf(updatedAt: Long, now: Long): SessionBucket {
        val today = startOfDay(now)
        val day = startOfDay(updatedAt)
        return when {
            day >= today -> SessionBucket.Today
            day >= today - DAY_MS -> SessionBucket.Yesterday
            day >= today - 6 * DAY_MS -> SessionBucket.LastWeek
            else -> SessionBucket.Earlier
        }
    }

    /** Buckets in display order, newest first inside each bucket, empty buckets dropped. */
    fun group(sessions: List<ChatSession>, now: Long): List<Pair<SessionBucket, List<ChatSession>>> =
        SessionBucket.entries
            .map { bucket ->
                bucket to sessions
                    .filter { bucketOf(it.updatedAt, now) == bucket }
                    .sortedByDescending { it.updatedAt }
            }
            .filter { it.second.isNotEmpty() }

    /** Title for a saved conversation: the first thing the user asked, trimmed for the drawer row. */
    fun titleOf(items: List<ChatItem>, maxChars: Int = 18): String {
        val first = items.filterIsInstance<ChatItem.User>().firstOrNull()?.text?.trim().orEmpty()
        if (first.isEmpty()) return "新对话"
        return if (first.length <= maxChars) first else first.take(maxChars - 1) + "…"
    }
}
