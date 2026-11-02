package com.xiaopiao.app.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Wire shapes of the circle API (server/src/circle.mjs). Plain data; the UI model lives in ui/circle. */
data class RemoteCircle(val id: String, val name: String, val role: String, val memberCount: Int, val createdAt: Long, val no: String = "", val pendingRequests: Int = 0)
/** A circle found by its number: no id, members or posts (docs/API-CIRCLE.md §9). */
data class FoundCircle(val no: String, val name: String, val memberCount: Int, val status: String)
data class JoinRequest(val userId: String, val username: String, val createdAt: Long, val avatar: RemoteAvatar?)
data class RemoteMember(val id: String, val username: String, val role: String, val joinedAt: Long, val avatar: RemoteAvatar?)
data class RemoteAuthor(val id: String, val username: String)
data class RemoteComment(val id: String, val text: String, val createdAt: Long, val author: RemoteAuthor)
data class RemoteReceiptLine(val label: String, val amountCents: Long, val count: Int? = null)
data class RemoteReceipt(
    val date: String, val totalCents: Long, val title: String, val count: Int, val comment: String, val lines: List<RemoteReceiptLine>,
    /** The author's print settings at share time; null for cards from before snapshots existed. */
    val snapshot: com.xiaopiao.app.domain.ReceiptSnapshot? = null,
)
data class RemotePost(
    val id: String,
    val text: String,
    val receipt: RemoteReceipt?,
    val createdAt: Long,
    val author: RemoteAuthor,
    val likeCount: Int,
    val likedByMe: Boolean,
    val commentCount: Int,
    val comments: List<RemoteComment>,
)
data class RemoteInvite(val code: String, val expiresAt: Long, val maxUses: Int)

/** `{ "preset": "p3" }` or `{ "url": "/v1/avatars/<id>?v=…" }` (docs/API-ACCOUNT.md, Avatar). */
data class RemoteAvatar(val preset: String?, val url: String?)

/** Thin client over the account session: every call is Bearer-authenticated by [AccountClient]. */
class CircleApi(private val client: AccountClient) {
    suspend fun list(): List<RemoteCircle> =
        client.request("/v1/circles")["circles"]?.jsonArray.orEmpty().map { circle(it.jsonObject) }

    suspend fun create(name: String): RemoteCircle =
        circle(client.request("/v1/circles", buildJsonObject { put("name", name) }).obj("circle"))

    suspend fun join(code: String): RemoteCircle =
        circle(client.request("/v1/circles/join", buildJsonObject { put("code", code) }).obj("circle"))

    suspend fun members(circleId: String): List<RemoteMember> =
        client.request("/v1/circles/${seg(circleId)}/members")["members"]?.jsonArray.orEmpty().map {
            val o = it.jsonObject
            RemoteMember(o.str("id"), o.str("username"), o.str("role"), o.long("joinedAt"), avatarOf(o["avatar"]))
        }

    suspend fun invite(circleId: String): RemoteInvite {
        val o = client.request("/v1/circles/${seg(circleId)}/invites", buildJsonObject { })
        return RemoteInvite(o.str("code"), o.long("expiresAt"), o["maxUses"]?.jsonPrimitive?.intOrNull ?: 0)
    }

    suspend fun leave(circleId: String) { client.request("/v1/circles/${seg(circleId)}/leave", buildJsonObject { }) }

    suspend fun removeMember(circleId: String, userId: String) {
        client.request("/v1/circles/${seg(circleId)}/members/${seg(userId)}", method = "DELETE")
    }

    suspend fun feed(circleId: String): List<RemotePost> =
        client.request("/v1/circles/${seg(circleId)}/feed?limit=50")["posts"]?.jsonArray.orEmpty().map { post(it.jsonObject) }

    suspend fun post(circleId: String, text: String, receipt: RemoteReceipt?): RemotePost =
        post(client.request("/v1/circles/${seg(circleId)}/posts", buildJsonObject {
            put("text", text)
            if (receipt != null) put("receipt", buildJsonObject {
                put("date", receipt.date)
                put("totalCents", receipt.totalCents)
                put("title", receipt.title)
                put("count", receipt.count)
                put("comment", receipt.comment)
                put("lines", buildJsonArray {
                    receipt.lines.forEach { l -> add(buildJsonObject { put("label", l.label); put("amountCents", l.amountCents) }) }
                })
            })
        }).obj("post"))

    suspend fun like(postId: String, liked: Boolean) {
        client.request("/v1/posts/${seg(postId)}/like", method = if (liked) "PUT" else "DELETE")
    }

    suspend fun comment(postId: String, text: String): RemoteComment =
        comment(client.request("/v1/posts/${seg(postId)}/comments", buildJsonObject { put("text", text) }).obj("comment"))

    suspend fun comments(postId: String): List<RemoteComment> =
        client.request("/v1/posts/${seg(postId)}/comments?limit=50")["comments"]?.jsonArray.orEmpty().map { comment(it.jsonObject) }

    private fun circle(o: JsonObject) = RemoteCircle(
        o.str("id"), o.str("name"), o.str("role"), o["memberCount"]?.jsonPrimitive?.intOrNull ?: 0, o.long("createdAt"),
        no = o.str("no"), pendingRequests = o["pendingRequests"]?.jsonPrimitive?.intOrNull ?: 0,
    )

    suspend fun search(no: String): FoundCircle {
        val o = client.request("/v1/circles/search?no=${seg(no)}").obj("circle")
        return FoundCircle(o.str("no"), o.str("name"), o["memberCount"]?.jsonPrimitive?.intOrNull ?: 0, o.str("status"))
    }

    /** Asks to join by number; the owner decides. Returns "pending" or "member". */
    suspend fun requestJoin(no: String): String =
        client.request("/v1/circles/requests", buildJsonObject { put("no", no) }).str("status")

    suspend fun requests(circleId: String): List<JoinRequest> =
        client.request("/v1/circles/${seg(circleId)}/requests")["requests"]?.jsonArray.orEmpty().map {
            val o = it.jsonObject
            JoinRequest(o.str("userId"), o.str("username"), o.long("createdAt"), avatarOf(o["avatar"]))
        }

    suspend fun decide(circleId: String, userId: String, approve: Boolean) {
        client.request("/v1/circles/${seg(circleId)}/requests/${seg(userId)}/${if (approve) "approve" else "reject"}", buildJsonObject { })
    }

    private fun post(o: JsonObject) = RemotePost(
        id = o.str("id"),
        text = o["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        receipt = (o["receipt"] as? JsonObject)?.let { r ->
            RemoteReceipt(
                date = r.str("date"),
                totalCents = r.long("totalCents"),
                title = r.str("title"),
                count = r["count"]?.jsonPrimitive?.intOrNull ?: 0,
                comment = r["comment"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                lines = (r["lines"] as? JsonArray).orEmpty().map { l ->
                    RemoteReceiptLine(l.jsonObject.str("label"), l.jsonObject.long("amountCents"), l.jsonObject["count"]?.jsonPrimitive?.intOrNull)
                },
                snapshot = (r["style"] as? JsonObject)?.let { st ->
                    com.xiaopiao.app.domain.ReceiptSnapshot(
                        paper = st.str("paper"),
                        ink = st["ink"]?.jsonPrimitive?.longOrNull?.toInt(),
                        font = st.str("font"),
                        content = st["content"]?.jsonPrimitive?.intOrNull ?: 0,
                        stamp = r["stamp"]?.jsonPrimitive?.contentOrNull,
                        printedAt = r.long("printedAt"),
                        prevCents = r["prevCents"]?.jsonPrimitive?.longOrNull,
                    )
                },
            )
        },
        createdAt = o.long("createdAt"),
        author = author(o.obj("author")),
        likeCount = o["likeCount"]?.jsonPrimitive?.intOrNull ?: 0,
        likedByMe = o["likedByMe"]?.jsonPrimitive?.booleanOrNull == true,
        commentCount = o["commentCount"]?.jsonPrimitive?.intOrNull ?: 0,
        comments = (o["comments"] as? JsonArray).orEmpty().map { comment(it.jsonObject) },
    )

    private fun comment(o: JsonObject) = RemoteComment(o.str("id"), o.str("text"), o.long("createdAt"), author(o.obj("author")))
    private fun author(o: JsonObject) = RemoteAuthor(o.str("id"), o.str("username"))
    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.long(k: String) = this[k]?.jsonPrimitive?.longOrNull ?: 0L
    private fun JsonObject.obj(k: String) = this[k]?.jsonObject ?: JsonObject(emptyMap())
    private fun seg(id: String) = java.net.URLEncoder.encode(id, "UTF-8")

    companion object {
        fun avatarOf(e: kotlinx.serialization.json.JsonElement?): RemoteAvatar? {
            val o = e as? JsonObject ?: return null
            val preset = o["preset"]?.jsonPrimitive?.contentOrNull
            val url = o["url"]?.jsonPrimitive?.contentOrNull
            return if (preset == null && url == null) null else RemoteAvatar(preset, url)
        }
    }
}
