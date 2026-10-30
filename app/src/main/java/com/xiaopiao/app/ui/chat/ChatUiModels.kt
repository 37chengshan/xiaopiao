package com.xiaopiao.app.ui.chat

import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay

/**
 * Pure UI state for the AI page (v7): the two top segments, the composer's derived mode, attachment
 * staging, session grouping and the receipt-text parser feed. Kept out of the composables so every rule
 * can be unit-tested without a device.
 */

/** The pill in the top bar. [Ledger] is a read-only digest of this week; questions still go to [Chat]. */

/** What the user attached before sending. Bytes stay local; only the uri is held in memory. */
enum class ChatAttachmentKind { Image, File }

/** A staged attachment chip. [uriString] is a SAF/content uri that never leaves the device. */
data class ChatAttachment(
    val uriString: String,
    val kind: ChatAttachmentKind,
    val name: String? = null,
    val mime: String? = null,
) {
    val isImage: Boolean get() = kind == ChatAttachmentKind.Image
    val isCsv: Boolean
        get() = !isImage && (
            mime?.contains("csv") == true ||
                mime?.contains("comma-separated") == true ||
                mime?.contains("excel") == true ||
                name?.endsWith(".csv", ignoreCase = true) == true
            )
}

/** Composer state, derived only from what the user typed, staged attachments and the stream. */
enum class ChatInputMode { Idle, Composing, Streaming }

/** What the single round button does in each mode: same place, same size, different job. */
enum class ChatPrimaryAction { Voice, Send, Stop }

/** Everything the composer needs to render itself, computed once from text + attachments + stream. */
data class ComposerPlan(
    val mode: ChatInputMode,
    val primary: ChatPrimaryAction,
    val expanded: Boolean,
    /** 1 line when idle, up to [ChatInput.MAX_LINES] while typing; beyond that the field scrolls. */
    val maxLines: Int,
)

object ChatInput {
    /** ChatGPT-style cap: the card grows to six lines, then the text scrolls inside it. */
    const val MAX_LINES = 6

    /** Maximum images the composer accepts at once (chips would not fit otherwise). */
    const val MAX_IMAGES = 4

    fun mode(text: String, streaming: Boolean, attachments: Int = 0): ChatInputMode = when {
        streaming -> ChatInputMode.Streaming
        text.isBlank() && attachments <= 0 -> ChatInputMode.Idle
        else -> ChatInputMode.Composing
    }

    fun primary(mode: ChatInputMode): ChatPrimaryAction = when (mode) {
        ChatInputMode.Idle -> ChatPrimaryAction.Voice
        ChatInputMode.Composing -> ChatPrimaryAction.Send
        ChatInputMode.Streaming -> ChatPrimaryAction.Stop
    }

    fun canSend(text: String, streaming: Boolean, attachments: Int = 0): Boolean =
        !streaming && (text.isNotBlank() || attachments > 0)

    /** One line while idle; the moment there is text or a chip, the card opens to the two-row shape. */
    fun isExpanded(mode: ChatInputMode): Boolean = mode != ChatInputMode.Idle

    fun plan(text: String, streaming: Boolean, attachments: Int = 0): ComposerPlan {
        val mode = mode(text, streaming, attachments)
        return ComposerPlan(mode, primary(mode), isExpanded(mode), MAX_LINES)
    }

    /** Chips keep the last four only; a fifth pick replaces the oldest instead of growing the row. */
    fun stage(existing: List<ChatAttachment>, incoming: List<ChatAttachment>): List<ChatAttachment> {
        if (incoming.isEmpty()) return existing
        val kept = existing + incoming
        return if (kept.size <= MAX_IMAGES) kept else kept.takeLast(MAX_IMAGES)
    }
}

/** One saved conversation. [updatedAt] drives the "今天 / 昨天 / 7 天内" groups in the drawer. */
@kotlinx.serialization.Serializable
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
