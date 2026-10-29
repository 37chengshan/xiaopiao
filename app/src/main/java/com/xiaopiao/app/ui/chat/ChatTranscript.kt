package com.xiaopiao.app.ui.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The chat keeps its last conversation on the phone (tt 2026-10-29): opening the tab restores it
 * instead of starting blank. Only text turns are stored — cards and tool steps are rebuilt from the
 * ledger when needed, and nothing here ever leaves the device.
 */
object ChatTranscript {
    private const val FILE = "chat_transcript.json"

    fun load(context: Context): List<ChatItem> {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    val text = row.optString("text")
                    if (text.isBlank()) continue
                    val id = row.optLong("id", i.toLong())
                    when (row.optString("role")) {
                        "user" -> add(ChatItem.User(id, text))
                        else -> add(ChatItem.Assistant(id, text, streaming = false))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, items: List<ChatItem>) {
        val rows = items.mapNotNull { item ->
            when (item) {
                is ChatItem.User -> JSONObject().put("role", "user").put("id", item.id).put("text", item.text)
                is ChatItem.Assistant -> JSONObject().put("role", "assistant").put("id", item.id).put("text", item.text)
                else -> null
            }
        }
        if (rows.isEmpty()) return
        runCatching {
            val file = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(JSONArray(rows).toString())
            tmp.renameTo(file)
        }
    }
}
