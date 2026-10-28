package com.xiaopiao.app.ui.splash

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * The landing slot for the splash paper (docs/APP-DESIGN.md §2.5 / §3.1).
 *
 * Whoever renders the persistent mini-receipt control in the root top bar only has to add
 * `Modifier.receiptControlSlot()` to it. Until that control exists the splash simply fades out; once it
 * reports bounds, the paper shrinks into it and [onLanded] fires so the control can play its little
 * "spit" nudge. Nothing else in the app needs to know about the splash.
 */
object ReceiptSlot {
    private var provider: (() -> Rect?)? = null
    private var landedCallback: (() -> Unit)? = null

    /** Bounds of the receipt control in window coordinates, or null when it is not on screen. */
    fun bounds(): Rect? = provider?.invoke()

    /** True when a receipt control is currently composed. */
    val isRegistered: Boolean get() = provider != null

    internal fun register(bounds: () -> Rect?, onLanded: () -> Unit) {
        provider = bounds
        landedCallback = onLanded
    }

    internal fun unregister() {
        provider = null
        landedCallback = null
    }

    /** Called once the splash paper has landed on the control. */
    fun landed() {
        landedCallback?.invoke()
    }
}

/**
 * Reports this element's window bounds as the splash landing slot. Apply it to the mini receipt control
 * in the root top bar; pass [onLanded] to react to the paper arriving (2dp nudge + spring).
 */
fun Modifier.receiptControlSlot(onLanded: () -> Unit = {}): Modifier = composed {
    var rect by remember { mutableStateOf<Rect?>(null) }
    DisposableEffect(Unit) {
        ReceiptSlot.register({ rect }, onLanded)
        onDispose { ReceiptSlot.unregister() }
    }
    onGloballyPositioned { coordinates ->
        val next = coordinates.boundsInWindow()
        if (next.width > 0f && next.height > 0f) rect = next
    }
}
