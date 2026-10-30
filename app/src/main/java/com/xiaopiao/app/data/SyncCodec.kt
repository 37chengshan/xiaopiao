package com.xiaopiao.app.data

import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.Source
import java.util.UUID
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Rows ↔ the sync API's JSON (docs/API-SYNC.md). Only structured fields cross: no capture text, no
 * dedupe keys, no channel. Lengths are clipped to the server's limits so a long note never gets a
 * whole row rejected.
 */
internal object SyncCodec {
    const val KIND_TXN = "txn"
    const val KIND_CATEGORY = "category"

    fun newSyncId(): String = UUID.randomUUID().toString().replace("-", "")

    fun txnJson(e: TxnEntity, deleted: Boolean = false): JsonObject = buildJsonObject {
        put("id", e.syncId)
        put("amountCents", e.amountCents)
        put("direction", e.direction)
        put("merchant", e.merchant.trim().take(64))
        put("category", e.categoryId.ifEmpty { e.category.lowercase() }.take(32))
        put("occurredAt", e.occurredAt)
        put("source", e.source)
        put("note", e.note.take(200))
        if (CARD.matches(e.cardLast4)) put("cardLast4", e.cardLast4) else put("cardLast4", JsonNull)
        put("updatedAt", e.updatedAt.coerceAtLeast(1))
        put("deleted", deleted)
    }

    fun categoryJson(e: CategoryEntity, deleted: Boolean = false): JsonObject = buildJsonObject {
        put("id", e.id)
        put("label", e.label.take(32))
        put("iconKey", e.iconKey)
        put("hue", e.hue)
        if (e.direction == null) put("direction", JsonNull) else put("direction", e.direction)
        put("hidden", e.hidden)
        put("sortOrder", e.sortOrder.coerceIn(0, 100_000))
        put("updatedAt", e.updatedAt.coerceAtLeast(1))
        put("deleted", deleted)
    }

    /** A pulled row; local-only fields (dedupe key, channel, row id) survive from [existing]. */
    fun txnFrom(o: JsonObject, existing: TxnEntity?): TxnEntity {
        val categoryId = o.text("category").ifEmpty { CategoryDefaults.OTHER_ID }
        val source = o.text("source")
        return TxnEntity(
            id = existing?.id ?: 0,
            amountCents = o.getValue("amountCents").jsonPrimitive.long,
            direction = o.text("direction"),
            merchant = o.text("merchant"),
            category = CategoryDefaults.enumFor(categoryId).name,
            occurredAt = o.getValue("occurredAt").jsonPrimitive.long,
            source = if (Source.entries.any { it.name == source }) source else Source.MANUAL.name,
            note = o.text("note"),
            cardLast4 = o.text("cardLast4"),
            dedupeKey = existing?.dedupeKey,
            categoryId = categoryId,
            channel = existing?.channel.orEmpty(),
            syncId = o.text("id"),
            updatedAt = o.getValue("updatedAt").jsonPrimitive.long,
            dirty = false,
        )
    }

    fun categoryFrom(o: JsonObject): CategoryEntity {
        val id = o.text("id")
        return CategoryEntity(
            id = id,
            label = o.text("label"),
            iconKey = o.text("iconKey"),
            hue = o.text("hue"),
            direction = o["direction"]?.jsonPrimitive?.contentOrNull,
            builtIn = CategoryDefaults.find(id) != null,
            hidden = o["hidden"]?.jsonPrimitive?.boolean ?: false,
            sortOrder = o["sortOrder"]?.jsonPrimitive?.int ?: 0,
            updatedAt = o.getValue("updatedAt").jsonPrimitive.long,
            dirty = false,
        )
    }

    fun tombstone(e: TxnEntity, now: Long) =
        TombstoneEntity(KIND_TXN, e.syncId, now, txnJson(e.copy(updatedAt = now), deleted = true).toString())

    fun tombstone(e: CategoryEntity, now: Long) =
        TombstoneEntity(KIND_CATEGORY, e.id, now, categoryJson(e.copy(updatedAt = now), deleted = true).toString())

    private val CARD = Regex("\\d{4}")

    private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}
