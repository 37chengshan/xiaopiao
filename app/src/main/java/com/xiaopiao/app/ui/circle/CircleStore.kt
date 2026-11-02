package com.xiaopiao.app.ui.circle

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.network.AccountClient
import com.xiaopiao.app.network.CircleApi
import com.xiaopiao.app.network.RemoteAvatar
import com.xiaopiao.app.network.RemoteCircle
import com.xiaopiao.app.network.RemoteMember
import com.xiaopiao.app.network.RemotePost
import com.xiaopiao.app.network.RemoteReceipt
import com.xiaopiao.app.network.RemoteReceiptLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Locale

/** The store for deep circle sheets, so every layer does not have to thread it through. */
val LocalCircleStore = androidx.compose.runtime.staticCompositionLocalOf<CircleStore?> { null }

/** Server error code → the circle UI's error line. */
fun circleErrorOf(e: Throwable): CircleError = when ((e as? com.xiaopiao.app.network.ApiException)?.code) {
    "CIRCLE_FULL" -> CircleError.CircleFull
    "INVITE_INVALID" -> CircleError.InvalidCode
    "INVALID_INPUT" -> CircleError.EmptyName
    else -> CircleError.NotAllowed
}

/** Where 圈子 stands with the server. The UI shows a state for each — never demo data in its place. */
enum class CircleSync { Loading, SignedOut, Ready, Failed }

/**
 * The circles of the signed-in account, shared by 圈子 and the receipt page's 「发到圈子」 (one source, so a post made
 * from the receipt is in the feed when the user switches tabs). Everything comes from the circle API
 * (server/src/circle.mjs); every write goes to the server first and the book is reloaded from its answer.
 *
 * The signed-in user is mapped to [CircleDemo.MY_ID] so the UI's "is this me" checks keep working; everyone else
 * keeps their real id, which is what member removal needs.
 */
class CircleStore(private val account: AccountClient) {
    private val api = CircleApi(account)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()

    private val state = MutableStateFlow(CircleBook(emptyList(), ""))
    val book: StateFlow<CircleBook> = state.asStateFlow()

    private val syncState = MutableStateFlow(if (account.user.value?.verified == true) CircleSync.Loading else CircleSync.SignedOut)
    val sync: StateFlow<CircleSync> = syncState.asStateFlow()

    /** Post ids the viewer liked (from the server's likedByMe, then optimistic). */
    private val likes = MutableStateFlow(emptySet<String>())
    val myLikes: StateFlow<Set<String>> = likes.asStateFlow()

    /** Like totals by post id, so the count moves with the heart. */
    private val likeCounts = MutableStateFlow(emptyMap<String, Int>())
    val likeCount: StateFlow<Map<String, Int>> = likeCounts.asStateFlow()

    /** Avatars by member id (the signed-in user under [CircleDemo.MY_ID]). */
    private val avatars = MutableStateFlow(emptyMap<String, RemoteAvatar>())
    val memberAvatars: StateFlow<Map<String, RemoteAvatar>> = avatars.asStateFlow()

    init {
        latest = this
        // Sign-in, sign-out and account switches reload the book.
        scope.launch {
            account.user.distinctUntilChangedBy { it?.id to it?.verified }.collect { refresh() }
        }
    }

    /** Fire-and-forget reload for screens that wrote through the API themselves (the receipt's 「发到圈子」). */
    fun refreshSoon() { scope.launch { refresh() } }

    /** Switching circles is local: it only changes which one is open. */
    fun set(book: CircleBook) { state.value = book }

    fun select(circleId: String) = state.update { switchCircle(it, circleId) }

    suspend fun refresh() = lock.withLock {
        val me = account.user.value
        if (me == null || !me.verified) {
            state.value = CircleBook(emptyList(), "")
            syncState.value = CircleSync.SignedOut
            return@withLock
        }
        if (state.value.circles.isEmpty()) syncState.value = CircleSync.Loading
        try {
            val remote = api.list()
            val loaded = coroutineScope {
                remote.map { c -> async { Triple(c, api.members(c.id), api.feed(c.id)) } }.awaitAll()
            }
            val circles = loaded.map { (c, members, posts) -> toCircle(c, members, posts, me.id) }
            val keep = state.value.currentId.takeIf { id -> circles.any { it.id == id } }
            state.value = CircleBook(circles, keep ?: circles.firstOrNull()?.id.orEmpty())
            val allPosts = loaded.flatMap { it.third }
            likes.value = allPosts.filter { it.likedByMe }.map { it.id }.toSet()
            likeCounts.value = allPosts.associate { it.id to it.likeCount }
            avatars.value = loaded.flatMap { it.second }.mapNotNull { m -> m.avatar?.let { mapId(m.id, me.id) to it } }.toMap()
            syncState.value = CircleSync.Ready
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            syncState.value = CircleSync.Failed
        }
    }

    suspend fun create(name: String): String {
        val created = api.create(name.trim())
        refresh()
        select(created.id)
        return created.name
    }

    suspend fun join(code: String): String {
        val joined = api.join(normalizeInviteCode(code))
        refresh()
        select(joined.id)
        return joined.name
    }

    suspend fun invite(circleId: String): CircleInvite {
        val invite = api.invite(circleId)
        val now = System.currentTimeMillis()
        val made = CircleInvite(code = invite.code, createdAt = now, maxUses = invite.maxUses, ttlMs = invite.expiresAt - now)
        state.update { b -> b.copy(circles = b.circles.map { if (it.id == circleId) it.copy(invite = made) else it }) }
        return made
    }

    suspend fun search(no: String) = api.search(no.trim())

    suspend fun requestJoin(no: String): String = api.requestJoin(no.trim())

    suspend fun requests(circleId: String) = api.requests(circleId)

    suspend fun decide(circleId: String, userId: String, approve: Boolean) { api.decide(circleId, userId, approve); refresh() }

    suspend fun leave(circleId: String) { api.leave(circleId); refresh() }

    suspend fun removeMember(circleId: String, memberId: String) { api.removeMember(circleId, memberId); refresh() }

    suspend fun postText(circleId: String, text: String) { api.post(circleId, text.trim(), null); refresh() }

    /** A day receipt goes up as category lines only — merchant names never leave the phone (server refuses them). */
    suspend fun postReceipt(circleIds: Collection<String>, post: ReceiptPost) {
        val receipt = toWire(post)
        circleIds.forEach { api.post(it, post.caption.trim(), receipt) }
        refresh()
    }

    fun toggleLike(postId: String) {
        val liked = postId !in likes.value
        likes.update { if (liked) it + postId else it - postId }
        likeCounts.update { it + (postId to ((it[postId] ?: 0) + if (liked) 1 else -1).coerceAtLeast(0)) }
        scope.launch {
            runCatching { api.like(postId, liked) }.onFailure {
                // Roll back: the heart must not claim a like the server never stored.
                likes.update { if (liked) it - postId else it + postId }
                likeCounts.update { m -> m + (postId to ((m[postId] ?: 0) + if (liked) -1 else 1).coerceAtLeast(0)) }
            }
        }
    }

    suspend fun comment(circleId: String, postId: String, text: String) {
        val me = account.user.value ?: return
        val c = api.comment(postId, text.trim())
        val comment = CircleComment(c.id, postId, mapId(c.author.id, me.id), c.text, c.createdAt)
        state.update { b -> b.copy(circles = b.circles.map { if (it.id == circleId) it.copy(comments = it.comments + comment) else it }) }
    }

    /** All comments of one post (the feed only carries the latest three). */
    suspend fun loadComments(circleId: String, postId: String) {
        val me = account.user.value ?: return
        val all = api.comments(postId).map { c -> CircleComment(c.id, postId, mapId(c.author.id, me.id), c.text, c.createdAt) }
        state.update { b ->
            b.copy(circles = b.circles.map { circle ->
                if (circle.id != circleId) circle
                else circle.copy(comments = circle.comments.filter { it.postId != postId } + all)
            })
        }
    }

    private fun mapId(id: String, myId: String) = if (id == myId) CircleDemo.MY_ID else id

    private fun toCircle(c: RemoteCircle, members: List<RemoteMember>, posts: List<RemotePost>, myId: String): Circle {
        val people = members.mapIndexed { i, m ->
            val mine = m.id == myId
            CircleMember(
                id = mapId(m.id, myId),
                name = m.username,
                initial = m.username.take(1).uppercase(),
                tone = CircleTone.entries[i % CircleTone.entries.size],
                isMe = mine,
                // What a member shares is decided when they post: the server only ever holds what they sent.
                shares = CircleShare.entries.associateWith { true },
                role = if (m.role == "owner") CircleRole.Owner else CircleRole.Member,
            )
        }
        val owner = members.firstOrNull { it.role == "owner" }?.id?.let { mapId(it, myId) } ?: people.firstOrNull()?.id.orEmpty()
        val feed = posts.map { p ->
            val author = mapId(p.author.id, myId)
            val r = p.receipt
            if (r != null) {
                ReceiptPost(
                    id = p.id, at = p.createdAt, authorId = author, dayStart = dayStartOf(r.date, p.createdAt),
                    items = r.lines.map { l -> CircleItem(l.label, l.amountCents, Category.entries.firstOrNull { it.label == l.label } ?: Category.OTHER) },
                    caption = p.text,
                    sharedTotal = r.totalCents,
                    sharedCount = r.count,
                    snapshot = r.snapshot,
                    remark = r.comment,
                )
            } else {
                TextPost(id = p.id, at = p.createdAt, authorId = author, text = p.text)
            }
        }
        val comments = posts.flatMap { p -> p.comments.map { c -> CircleComment(c.id, p.id, mapId(c.author.id, myId), c.text, c.createdAt) } }
        return Circle(
            id = c.id, name = c.name, ownerId = owner, members = people, posts = feed,
            // No invite until the user asks for one; the sheet creates it on the server.
            invite = CircleInvite(code = "", createdAt = 0L, uses = 0, maxUses = 0),
            comments = comments,
            no = c.no,
            pendingRequests = c.pendingRequests,
        )
    }

    private fun toWire(post: ReceiptPost): RemoteReceipt {
        val lines = post.items.groupBy { it.category }
            .map { (category, items) -> RemoteReceiptLine(category.label.take(16), items.sumOf { it.cents }) }
            .sortedByDescending { it.amountCents }
            .take(30)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(post.dayStart)
        val title = SimpleDateFormat("M月d日小票", Locale.CHINA).format(post.dayStart)
        return RemoteReceipt(date = date, totalCents = post.totalCents, title = title, count = post.entryCount, comment = "", lines = lines)
    }

    private fun dayStartOf(date: String, fallback: Long): Long =
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time }.getOrNull() ?: fallback

    companion object {
        /** The app's store, for readers outside composition (the AI's 「家里人这周花了多少」). */
        @Volatile var latest: CircleStore? = null
            private set
    }
}
