package com.xiaopiao.app.ui.circle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The circles on this phone, shared by 圈子 and the receipt page's 「发到圈子」 (one source, so a post made from
 * the receipt is in the feed when the user switches tabs). Local demo state until the circle API is wired.
 */
class CircleStore(now: Long = System.currentTimeMillis()) {
    private val state = MutableStateFlow(CircleDemo.book(now))
    val book: StateFlow<CircleBook> = state.asStateFlow()

    /** Post ids the viewer liked. */
    private val likes = MutableStateFlow(emptySet<String>())
    val myLikes: StateFlow<Set<String>> = likes.asStateFlow()

    fun set(book: CircleBook) { state.value = book }

    fun post(circleId: String, post: CirclePost) = state.update { book ->
        book.copy(circles = book.circles.map { if (it.id == circleId) it.copy(posts = it.posts + post) else it })
    }

    fun comment(circleId: String, comment: CircleComment) = state.update { book ->
        book.copy(circles = book.circles.map { if (it.id == circleId) it.copy(comments = it.comments + comment) else it })
    }

    fun toggleLike(postId: String) = likes.update { if (postId in it) it - postId else it + postId }
}
