package com.xiaopiao.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn

/** `dedupeKey` is NULL (not "") when absent, so the unique index only constrains real keys. */
@Entity(
    tableName = "txns",
    indices = [Index(value = ["dedupeKey"], unique = true), Index(value = ["occurredAt"])],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val direction: String,
    val merchant: String,
    val category: String,
    val occurredAt: Long,
    val source: String,
    val note: String,
    @ColumnInfo(name = "cardLast4", defaultValue = "") val cardLast4: String,
    @ColumnInfo(name = "dedupeKey") val dedupeKey: String?,
)

fun Txn.toEntity() = TxnEntity(
    id = id, amountCents = amountCents, direction = direction.name, merchant = merchant,
    category = category.name, occurredAt = occurredAt, source = source.name, note = note,
    cardLast4 = cardLast4, dedupeKey = dedupeKey.ifEmpty { null },
)

fun TxnEntity.toDomain() = Txn(
    id = id, amountCents = amountCents,
    direction = runCatching { Direction.valueOf(direction) }.getOrDefault(Direction.OUT),
    merchant = merchant,
    category = runCatching { Category.valueOf(category) }.getOrDefault(Category.OTHER),
    occurredAt = occurredAt,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.MANUAL),
    note = note, cardLast4 = cardLast4, dedupeKey = dedupeKey.orEmpty(),
)

/** Raw captured notification, kept on device only. */
@Entity(tableName = "capture_log", indices = [Index(value = ["postedAt"])])
data class CaptureLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** "PARSED:<ruleId>", "IGNORED:<reason>", "UNPARSED", "DUPLICATE:<ruleId>" */
    val result: String,
    val txnId: Long?,
)

data class CaptureLogEntry(
    val id: Long = 0,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val result: String,
    val txnId: Long? = null,
)

fun CaptureLogEntity.toDomain() = CaptureLogEntry(id, packageName, title, text, postedAt, result, txnId)
fun CaptureLogEntry.toEntity() = CaptureLogEntity(id, packageName, title, text, postedAt, result, txnId)
