package com.xiaopiao.app.ui.chat

import android.content.Context
import android.util.AtomicFile
import com.xiaopiao.app.wallet.AndroidCardCipher
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Durable device-only history; payload (including pending CSV) is encrypted by Android Keystore. */
class ChatSessionStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "chat-sessions.v1"))
    private val cipher = AndroidCardCipher("xp_chat_history_v1")
    private val json = Json { ignoreUnknownKeys = true }
    fun load(): List<ChatSession> = runCatching {
        val raw = file.openRead().bufferedReader().use { it.readText() }
        json.decodeFromString<List<ChatSession>>(cipher.open(raw) ?: error("Cannot decrypt"))
    }.getOrDefault(emptyList())
    @Synchronized fun save(sessions: List<ChatSession>) {
        val body = cipher.seal(json.encodeToString(sessions.take(50)))
        val out = file.startWrite()
        try { out.write(body.toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
}
