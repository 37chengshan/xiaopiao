package com.xiaopiao.app.network

import android.content.Context
import androidx.room.withTransaction
import com.xiaopiao.app.data.AppDatabase
import com.xiaopiao.app.data.SyncCodec
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Account sync for the ledger (docs/API-SYNC.md §5): push dirty rows and tombstones, then pull every
 * change after the stored cursor. Runs only for a signed-in, verified account; without one the ledger
 * simply stays on this device.
 */
class LedgerSync(context: Context, private val account: AccountClient, private val db: AppDatabase) {
    data class Status(val running: Boolean = false, val lastOkAt: Long = 0, val error: String? = null)

    private val prefs = context.getSharedPreferences("sync-v1", Context.MODE_PRIVATE)
    private val dao = db.syncDao()
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow(Status(lastOkAt = prefs.getLong("lastOk", 0)))
    val status = mutableStatus.asStateFlow()

    private val deviceId: String = prefs.getString("device", null)
        ?: SyncCodec.newSyncId().also { prefs.edit().putString("device", it).apply() }

    val enabled: Boolean get() = account.configured && account.user.value?.verified == true

    /** While the app is in the foreground: sync on sign-in, within seconds of a write, and every minute. */
    @OptIn(FlowPreview::class)
    suspend fun keepInSync() = coroutineScope {
        launch {
            merge(
                account.user.map { it?.id to it?.verified }.distinctUntilChanged().map { },
                dao.observePending().filter { it > 0 }.debounce(2_000).map { },
            ).collect { syncNow() }
        }
        while (true) {
            delay(60_000)
            syncNow()
        }
    }

    suspend fun syncNow(): Boolean {
        val user = account.user.value?.takeIf { enabled } ?: return false
        return mutex.withLock {
            mutableStatus.update { it.copy(running = true) }
            try {
                adopt(user.id)
                push()
                pull()
                val now = System.currentTimeMillis()
                prefs.edit().putLong("lastOk", now).apply()
                mutableStatus.value = Status(lastOkAt = now)
                true
            } catch (e: CancellationException) {
                mutableStatus.update { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                val message = (e as? ApiException)?.message ?: "同步没成功，稍后会自动再试"
                mutableStatus.update { it.copy(running = false, error = message) }
                false
            }
        }
    }

    /** First sync for this account on this device: everything here gets uploaded, cursor starts over. */
    private suspend fun adopt(userId: String) {
        if (prefs.getString("user", null) == userId) return
        db.withTransaction {
            dao.markAllTxnsDirty()
            dao.markTouchedCategoriesDirty()
        }
        prefs.edit().putString("user", userId).putLong("since", 0).apply()
    }

    private class Item(val kind: String, val id: String, val json: JsonObject, val pushed: suspend () -> Unit)

    private suspend fun push() {
        val items = dao.dirtyTxns().map { row ->
            Item(SyncCodec.KIND_TXN, row.syncId, SyncCodec.txnJson(row)) { dao.txnPushed(row.syncId, row.updatedAt) }
        } + dao.dirtyCategories().map { row ->
            Item(SyncCodec.KIND_CATEGORY, row.id, SyncCodec.categoryJson(row)) { dao.categoryPushed(row.id, row.updatedAt) }
        } + dao.tombstones().map { row ->
            Item(row.kind, row.syncId, Json.parseToJsonElement(row.body).jsonObject) {
                dao.tombstonePushed(row.kind, row.syncId, row.updatedAt)
            }
        }
        // The API takes ≤ 500 rows per call; a rejected row stays queued.
        items.chunked(400).forEach { batch ->
            val body = buildJsonObject {
                put("deviceId", deviceId)
                putJsonArray("txns") { batch.filter { it.kind == SyncCodec.KIND_TXN }.forEach { add(it.json) } }
                putJsonArray("categories") { batch.filter { it.kind == SyncCodec.KIND_CATEGORY }.forEach { add(it.json) } }
            }
            val result = account.request("/v1/ledger/push", body)
            val rejected = result["rejected"]?.jsonArray.orEmpty().map {
                val o = it.jsonObject
                o["kind"]?.jsonPrimitive?.contentOrNull to o["id"]?.jsonPrimitive?.contentOrNull
            }.toSet()
            db.withTransaction { batch.filterNot { (it.kind to it.id) in rejected }.forEach { it.pushed() } }
            // The server kept a newer version we may already be past; read everything again to take it.
            if (result["stale"]?.jsonArray.orEmpty().isNotEmpty()) prefs.edit().putLong("since", 0).apply()
        }
    }

    private suspend fun pull() {
        var since = prefs.getLong("since", 0)
        do {
            val page = account.request("/v1/ledger/changes?since=$since&limit=200")
            db.withTransaction {
                page["categories"]?.jsonArray.orEmpty().forEach { applyCategory(it.jsonObject) }
                page["txns"]?.jsonArray.orEmpty().forEach { applyTxn(it.jsonObject) }
            }
            since = page.getValue("nextSince").jsonPrimitive.long
            prefs.edit().putLong("since", since).apply()
        } while (page["hasMore"]?.jsonPrimitive?.boolean == true)
    }

    private suspend fun applyTxn(o: JsonObject) {
        val id = o.getValue("id").jsonPrimitive.content
        val remoteAt = o.getValue("updatedAt").jsonPrimitive.long
        val local = dao.txnBySyncId(id)
        if (local != null && (local.dirty && local.updatedAt > remoteAt || !local.dirty && local.updatedAt == remoteAt)) return
        if (o["deleted"]?.jsonPrimitive?.boolean == true) {
            if (local != null) dao.deleteTxn(id)
            return
        }
        val row = SyncCodec.txnFrom(o, local)
        if (local == null) dao.insertTxn(row) else dao.updateTxn(row)
    }

    private suspend fun applyCategory(o: JsonObject) {
        val id = o.getValue("id").jsonPrimitive.content
        val remoteAt = o.getValue("updatedAt").jsonPrimitive.long
        val local = dao.category(id)
        if (local != null && (local.dirty && local.updatedAt > remoteAt || !local.dirty && local.updatedAt == remoteAt)) return
        if (o["deleted"]?.jsonPrimitive?.boolean == true) dao.deleteCategory(id) else dao.putCategory(SyncCodec.categoryFrom(o))
    }
}
