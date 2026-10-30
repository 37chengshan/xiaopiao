package com.xiaopiao.app.verification

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import androidx.room.Room
import com.xiaopiao.app.data.AppDatabase
import com.xiaopiao.app.data.toEntity
import com.xiaopiao.app.domain.*
import com.xiaopiao.app.network.AccountClient
import com.xiaopiao.app.network.LedgerSync
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.concurrent.thread

/** Run explicitly on a QA emulator. Real Room + AccountClient + LedgerSync; no external account/mail. */
class LedgerSyncCheck : Instrumentation() {
    private var live = false
    override fun onCreate(arguments: Bundle?) { live = arguments?.getString("live") == "true"; super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking { if (live) checkLiveSync() else checkSync() }
            result.putString("stream", if (live) "PASS: real production pre-login upload, cloud merge, deduplication and cleanup\n" else "PASS: pre-login upload, cloud merge, no duplicates, demo exclusion, account-switch isolation\n")
            finish(-1, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: " + e.javaClass.simpleName + "\n")
            finish(0, result)
        }
    }

    private suspend fun checkLiveSync() {
        val configFile = targetContext.filesDir.resolve("qa-sync.json")
        val config = Json.parseToJsonElement(configFile.readText()).jsonObject
        val prefix = "live-sync-qa-${System.nanoTime()}-"
        fun context(tag: String) = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences(prefix + tag + name, mode)
        }
        val base = "https://xiaopiao.citygenius.top"
        val seed = AccountClient(context("seed"), base)
        val account = AccountClient(context("local"), base)
        val credentials = mapOf("identifier" to config.getValue("email").jsonPrimitive.content,
            "password" to config.getValue("password").jsonPrimitive.content)
        val db = Room.inMemoryDatabaseBuilder(targetContext, AppDatabase::class.java).build()
        val stamp = System.currentTimeMillis()
        val cloudId = "qa-cloud-$stamp"; val localId = "qa-local-$stamp"
        fun row(id: String, cents: Long, deleted: Boolean = false) = buildJsonObject {
            put("id", id); put("amountCents", cents); put("direction", "OUT"); put("merchant", "QA sync")
            put("category", "meal"); put("occurredAt", stamp); put("source", "MANUAL")
            put("updatedAt", if (deleted) System.currentTimeMillis()+1000 else stamp); put("deleted", deleted)
        }
        fun push(vararg rows: JsonObject) = buildJsonObject {
            put("deviceId", "qa-sync-check"); putJsonArray("txns") { rows.forEach { add(it) } }; putJsonArray("categories") {}
        }
        try {
            seed.auth("login", credentials)
            check(seed.user.value?.verified == true)
            seed.request("/v1/ledger/push", push(row(cloudId,321)))
            val local = Txn(amountCents = 123, merchant = "QA sync", category = Category.MEAL, occurredAt = stamp, source = Source.MANUAL)
            db.txnDao().insert(local.toEntity().copy(syncId = localId, updatedAt = stamp, dirty = false))
            val sync = LedgerSync(context("local"), account, db)
            check(!sync.syncNow())
            account.auth("login", credentials)
            check(sync.syncNow())
            check(db.syncDao().txnBySyncId(localId)?.amountCents == 123L)
            check(db.syncDao().txnBySyncId(cloudId)?.amountCents == 321L)
            check(sync.syncNow())
            check(db.txnDao().syncable().count { it.syncId == localId } == 1)
            val page = account.request("/v1/ledger/changes?since=0&limit=200")
            check(page.getValue("txns").jsonArray.any { it.jsonObject["id"]?.jsonPrimitive?.content == localId && it.jsonObject["deleted"]?.jsonPrimitive?.boolean != true })
        } finally {
            try { seed.request("/v1/ledger/push", push(row(cloudId,321,true),row(localId,123,true))) } finally {
                runCatching { seed.logout() }; runCatching { account.logout() }; db.close(); configFile.delete()
                for (tag in listOf("seed","local")) for (name in listOf("account-v1","sync-v1")) targetContext.deleteSharedPreferences(prefix+tag+name)
            }
        }
    }

    private suspend fun checkSync() {
        val prefix = "sync-qa-${System.nanoTime()}-"
        val context = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences(prefix + name, mode)
        }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val now = System.currentTimeMillis()
        val cloud = linkedMapOf<String, JsonObject>()
        cloud["cloud-before-login"] = buildJsonObject {
            put("id", "cloud-before-login"); put("amountCents", 321); put("direction", "OUT")
            put("merchant", "Cloud QA"); put("category", "meal"); put("occurredAt", now)
            put("source", "MANUAL"); put("updatedAt", now); put("deleted", false)
        }
        var pushedRows = 0
        val socket = ServerSocket(0)
        val server = thread(isDaemon = true, name = "qa-ledger-api") {
            try {
                while (!socket.isClosed) socket.accept().use { peer ->
                    val reader = peer.getInputStream().bufferedReader()
                    val request = reader.readLine().split(' ')
                    var length = 0
                    while (true) {
                        val line = reader.readLine()
                        if (line.isNullOrEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    val chars = CharArray(length); var read = 0
                    while (read < length) { val n = reader.read(chars, read, length-read); if (n < 0) break; read += n }
                    val input = if (length > 0) Json.parseToJsonElement(String(chars)).jsonObject else buildJsonObject {}
                    val body = when {
                        request[1] == "/v1/auth/login" -> buildJsonObject {
                            val other = input["identifier"]?.jsonPrimitive?.content == "other"
                            put("token", (if (other) "b" else "a").repeat(43))
                            putJsonObject("user") { put("id", if (other) "other" else "owner"); put("email", "qa@example.test"); put("username", "owner"); put("verified", true) }
                        }
                        request[1] == "/v1/ledger/push" -> buildJsonObject {
                            val rows = input.getValue("txns").jsonArray
                            rows.forEach { val row = it.jsonObject; cloud[row.getValue("id").jsonPrimitive.content] = row; pushedRows++ }
                            put("applied", rows.size); putJsonArray("rejected") {}; putJsonArray("stale") {}; put("seq", cloud.size)
                        }
                        request[1].startsWith("/v1/ledger/changes") -> buildJsonObject {
                            putJsonArray("txns") { cloud.values.forEach { add(it) } }; putJsonArray("categories") {}
                            put("nextSince", cloud.size); put("hasMore", false)
                        }
                        else -> buildJsonObject { put("ok", true) }
                    }.toString().toByteArray()
                    peer.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    peer.getOutputStream().write(body)
                }
            } catch (_: java.net.SocketException) { /* normal close */ }
        }
        try {
            val local = Txn(amountCents = 123, merchant = "Local QA", category = Category.MEAL, occurredAt = now, source = Source.MANUAL)
            // A clean row from before sign-in must still be adopted and uploaded.
            db.txnDao().insert(local.toEntity().copy(syncId = "local-before-login", updatedAt = now, dirty = false))
            db.txnDao().insert(local.copy(dedupeKey = "demo|qa").toEntity().copy(syncId = "demo-row", updatedAt = now, dirty = true))
            val account = AccountClient(context, "http://127.0.0.1:${socket.localPort}")
            val sync = LedgerSync(context, account, db)
            check(!sync.syncNow()); check(!cloud.containsKey("local-before-login"))
            account.auth("login", mapOf("identifier" to "owner", "password" to "test-only"))
            check(sync.syncNow()); check(cloud.containsKey("local-before-login")); check(!cloud.containsKey("demo-row"))
            check(db.txnDao().syncable().size == 2); check(pushedRows == 1)
            check(sync.syncNow()); check(db.txnDao().syncable().size == 2); check(pushedRows == 1)
            account.auth("login", mapOf("identifier" to "other", "password" to "test-only"))
            check(!sync.syncNow()); check(pushedRows == 1)
        } finally {
            socket.close(); server.join(1000); db.close()
            targetContext.deleteSharedPreferences(prefix + "account-v1")
            targetContext.deleteSharedPreferences(prefix + "sync-v1")
        }
    }
}
