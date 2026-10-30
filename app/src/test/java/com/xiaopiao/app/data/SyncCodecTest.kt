package com.xiaopiao.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The push JSON is a whitelist on the server (docs/API-SYNC.md §2): extra keys get the row rejected. */
class SyncCodecTest {

    private val row = TxnEntity(
        id = 7, amountCents = 2500, direction = "OUT", merchant = "老乡鸡", category = "MEAL",
        occurredAt = 1_760_000_000_000, source = "NOTIFICATION", note = "", cardLast4 = "",
        dedupeKey = "wx|123", categoryId = "", channel = "wechat",
        syncId = "abc123", updatedAt = 1_760_000_000_500, dirty = true,
    )

    @Test
    fun `txn json carries exactly the API fields and nothing local`() {
        val json = SyncCodec.txnJson(row)
        assertEquals(
            setOf("id", "amountCents", "direction", "merchant", "category", "occurredAt", "source", "note",
                "cardLast4", "updatedAt", "deleted"),
            json.keys,
        )
        assertEquals("abc123", json["id"]!!.jsonPrimitive.content)
        // A legacy row without categoryId goes up as the built-in id of its enum.
        assertEquals("meal", json["category"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, json["cardLast4"])
        assertEquals(false, json["deleted"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `long text is clipped to the server limits`() {
        val json = SyncCodec.txnJson(row.copy(merchant = "店".repeat(100), note = "字".repeat(300)))
        assertEquals(64, json["merchant"]!!.jsonPrimitive.content.length)
        assertEquals(200, json["note"]!!.jsonPrimitive.content.length)
    }

    @Test
    fun `a pulled row keeps this device's dedupe key, channel and row id`() {
        val pulled = SyncCodec.txnFrom(Json.parseToJsonElement(SyncCodec.txnJson(row.copy(categoryId = "custom:9")).toString()).jsonObject, row)
        assertEquals(7L, pulled.id)
        assertEquals("wx|123", pulled.dedupeKey)
        assertEquals("wechat", pulled.channel)
        assertEquals("custom:9", pulled.categoryId)
        assertEquals("OTHER", pulled.category)
        assertEquals(false, pulled.dirty)
    }

    @Test
    fun `a row new to this device has no dedupe key and an unknown source falls back to manual`() {
        val o = Json.parseToJsonElement(SyncCodec.txnJson(row.copy(source = "VOICE")).toString()).jsonObject
        val pulled = SyncCodec.txnFrom(o, existing = null)
        assertEquals(0L, pulled.id)
        assertNull(pulled.dedupeKey)
        assertEquals("MANUAL", pulled.source)
    }

    @Test
    fun `a tombstone is a full row marked deleted at the delete time`() {
        val stone = SyncCodec.tombstone(row, now = 1_760_000_009_000)
        val body = Json.parseToJsonElement(stone.body).jsonObject
        assertEquals(SyncCodec.KIND_TXN, stone.kind)
        assertEquals(true, body["deleted"]!!.jsonPrimitive.boolean)
        assertEquals(1_760_000_009_000, body["updatedAt"]!!.jsonPrimitive.long)
        assertEquals(2500, body["amountCents"]!!.jsonPrimitive.long)
    }

    @Test
    fun `v4 migration is additive and queues existing rows for the first push`() {
        val upper = SyncSchema.statements.map { it.uppercase() }
        upper.forEach { sql -> listOf("DROP ", "DELETE FROM").forEach { assertTrue(sql, !sql.contains(it)) } }
        assertTrue(SyncSchema.statements.any { it.contains("UNIQUE INDEX") && it.contains("syncId") })
        // The unique index must come after every row has been given its own id.
        val fill = SyncSchema.statements.indexOfFirst { it.startsWith("UPDATE txns SET syncId") }
        val index = SyncSchema.statements.indexOfFirst { it.contains("UNIQUE INDEX") }
        assertTrue(fill in 0 until index)
    }
}
