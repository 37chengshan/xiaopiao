package com.xiaopiao.app.network

import android.content.Context
import com.xiaopiao.app.BuildConfig
import com.xiaopiao.app.wallet.AndroidCardCipher
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class AccountUser(val id: String, val email: String, val username: String, val verified: Boolean)
class ApiException(val code: String, message: String) : Exception(message)

/** Application-owned session only; never stores the model key or a QMuse identity. */
class AccountClient(context: Context) {
    private val prefs = context.getSharedPreferences("account-v1", Context.MODE_PRIVATE)
    private val cipher = AndroidCardCipher("xp_account_v1")
    private var token: String? = prefs.getString("session", null)?.let(cipher::open)
    private val mutableUser = MutableStateFlow(prefs.getString("user", null)?.let { runCatching { parseUser(Json.parseToJsonElement(it).jsonObject) }.getOrNull() })
    val user = mutableUser.asStateFlow()
    val configured: Boolean get() = BuildConfig.API_BASE_URL.isNotBlank()
    private fun parseUser(o: JsonObject) = AccountUser(o.getValue("id").jsonPrimitive.content, o.getValue("email").jsonPrimitive.content, o.getValue("username").jsonPrimitive.content, o.getValue("verified").jsonPrimitive.boolean)
    private fun connection(path: String): HttpURLConnection {
        if (!configured) throw ApiException("NOT_CONFIGURED", "云端连接尚未配置，可以继续本机记账")
        val uri = URI(BuildConfig.API_BASE_URL)
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.host in listOf("127.0.0.1", "localhost", "10.0.2.2")))
        return (uri.resolve(path).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 25_000
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }
    suspend fun request(path: String, data: JsonObject? = null): JsonObject = withContext(Dispatchers.IO) {
        val c = connection(path)
        try {
            c.requestMethod = if (data == null) "GET" else "POST"
            if (data != null) { c.doOutput = true; c.outputStream.use { it.write(data.toString().toByteArray()) } }
            val ok = c.responseCode in 200..299
            val result = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val obj = runCatching { Json.parseToJsonElement(result).jsonObject }.getOrElse { throw ApiException("NETWORK", "连接失败，请稍后重试") }
            if (!ok) {
                if (c.responseCode == 401) clear()
                throw ApiException(obj["error"]?.jsonPrimitive?.content ?: "NETWORK", obj["message"]?.jsonPrimitive?.content ?: "连接失败，请重试")
            }
            obj
        } finally { c.disconnect() }
    }
    suspend fun auth(action: String, fields: Map<String, String>) {
        val obj = request("/v1/auth/$action", buildJsonObject { fields.forEach { (k,v) -> put(k,v) }; put("client", "native") })
        withContext(Dispatchers.IO) {
            obj["token"]?.jsonPrimitive?.content?.let { token = it; prefs.edit().putString("session", cipher.seal(it)).commit() }
            obj["user"]?.jsonObject?.let { mutableUser.value = parseUser(it); prefs.edit().putString("user", it.toString()).commit() }
        }
    }
    suspend fun refresh() {
        if (token == null || !configured) return
        val obj = request("/v1/auth/me")
        obj["user"]?.jsonObject?.let { mutableUser.value = parseUser(it); prefs.edit().putString("user", it.toString()).apply() }
    }
    suspend fun logout() {
        try { request("/v1/auth/logout", buildJsonObject {}) } finally { clear() }
    }
    private fun clear() { token = null; mutableUser.value = null; prefs.edit().clear().apply() }

    /** Closing collection disconnects the socket immediately, including while blocked in readLine. */
    fun stream(data: JsonObject) = callbackFlow<JsonObject> {
        val ref = AtomicReference<HttpURLConnection?>()
        val worker = launch(Dispatchers.IO) {
            try {
                val c = connection("/v1/chat"); ref.set(c)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                c.requestMethod = "POST"; c.doOutput = true
                c.outputStream.use { it.write(data.toString().toByteArray()) }
                if (c.responseCode !in 200..299) {
                    if (c.responseCode == 401) clear()
                    val obj = c.errorStream?.bufferedReader()?.use { Json.parseToJsonElement(it.readText()).jsonObject }
                    throw ApiException(obj?.get("error")?.jsonPrimitive?.content ?: "NETWORK", obj?.get("message")?.jsonPrimitive?.content ?: "AI 连接失败")
                }
                c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val frame = StringBuilder()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) {
                            if (frame.isNotEmpty()) { send(Json.parseToJsonElement(frame.toString()).jsonObject); frame.clear() }
                        } else if (line.startsWith("data:")) {
                            if (frame.isNotEmpty()) frame.append('\n')
                            frame.append(line.removePrefix("data:").trimStart())
                            if (frame.length > 256_000) throw ApiException("FRAME_LIMIT", "AI 回复过长")
                        }
                    }
                }
                close()
            } catch (e: Exception) { close(e) }
            finally { ref.getAndSet(null)?.disconnect() }
        }
        awaitClose { ref.getAndSet(null)?.disconnect(); worker.cancel() }
    }
}
