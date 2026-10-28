package com.xiaopiao.app.ui.today

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * Vertical drag that only claims the gesture when it starts in the wanted direction ([up] or down),
 * so the parent scroll still works for the opposite direction. [onEnd] gets the total travel and whether it completed.
 */
fun Modifier.directionalDrag(
    up: Boolean,
    enabled: Boolean,
    onDrag: (total: Float) -> Unit,
    onEnd: (total: Float, completed: Boolean) -> Unit,
): Modifier = if (!enabled) this else pointerInput(up) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = 0f
        val first = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            if ((over < 0f) == up) { change.consume(); total += over }
        } ?: return@awaitEachGesture
        onDrag(total)
        val completed = verticalDrag(first.id) { change ->
            total += change.positionChange().y
            change.consume()
            onDrag(total)
        }
        onEnd(total, completed)
    }
}
