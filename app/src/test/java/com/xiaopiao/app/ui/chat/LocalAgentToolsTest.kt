package com.xiaopiao.app.ui.chat

import com.xiaopiao.app.domain.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class LocalAgentToolsTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val now = LocalDate.of(2026, 11, 1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun txn(at: Long, cents: Long = 100, direction: Direction = Direction.OUT) = Txn(id = at, amountCents = cents, merchant = "咖啡店", category = Category.DRINK, occurredAt = at, direction = direction, source = Source.MANUAL, note = "SECRET ORIGINAL RECEIPT")
    @Test fun todayUsesCalendarDayAcrossDstAndSeparatesIncome() {
        val start = LocalDate.of(2026,11,1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026,11,2).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(25 * 3600_000L, end - start)
        val history = listOf(txn(start - 1, 999), txn(start, 1234), txn(end - 1, 300), txn(now, 500, Direction.IN), txn(end, 888))
        val result = LocalAgentTools.execute("summarize_period", buildJsonObject { put("period", "today") }, history, now, zone)
        assertEquals(1534L, result.getValue("total_out").jsonPrimitive.long)
        assertEquals(500L, result.getValue("total_in").jsonPrimitive.long)
        assertEquals(3, result.getValue("count").jsonPrimitive.int)
    }
    @Test fun queriesAreBoundedAndNeverExposeNotes() {
        val rows = (1L..50L).map { txn(now - it, it) }
        val result = LocalAgentTools.execute("query_transactions", buildJsonObject { put("limit", 1000) }, rows, now, zone)
        assertEquals(20, result.getValue("rows").jsonArray.size)
        assertEquals(50, result.getValue("matched_count").jsonPrimitive.int)
        assertTrue(result.getValue("truncated").jsonPrimitive.boolean)
        assertFalse(result.toString().contains("SECRET"))
        assertFalse(result.toString().contains("note"))
    }
    @Test fun circleDeniesMissingSharingAndModelCannotWrite() {
        val circle = LocalAgentTools.execute("read_circle", buildJsonObject {}, emptyList(), now, zone)
        assertFalse(circle.getValue("shared").jsonPrimitive.boolean)
        assertEquals("TOOL_NOT_ALLOWED", LocalAgentTools.execute("commit_transactions", buildJsonObject {}, emptyList(), now, zone).getValue("error").jsonPrimitive.content)
    }
    @Test fun savedConfirmationAndMarkdownSurviveHistorySerialization() {
        val item = ChatItem.Confirm(12, ExpenseDraft("午饭", Category.MEAL, 1234L, "午饭12.34"), ConfirmState.Saved, transactionId = 42, autoRequested = true)
        val sessions = listOf(ChatSession(1, "我的会话", listOf(ChatItem.User(1, "午饭12.34"), item, ChatItem.Assistant(13, "**已记下**")), now))
        assertEquals(sessions, Json.decodeFromString<List<ChatSession>>(Json.encodeToString(sessions)))
    }
}
