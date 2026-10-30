package com.xiaopiao.app.capture

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live state of the notification lane, shared by [PaymentListenerService] and the settings screen.
 *
 * Two different questions, two different answers:
 * - [connected] — does the system currently hold a live binding? Process-local, dropped on death.
 * - [lastSeenAt] — when did we last see *any* notification from Alipay / WeChat? Persisted, so the
 *   user can tell "读取正常但今天没花钱" from "读取已经断了".
 */
object CaptureStatus {
    private const val PREFS = "capture_status"
    private const val KEY_LAST_AT = "last_seen_at"
    private const val KEY_LAST_PKG = "last_seen_pkg"

    private val connectedState = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = connectedState.asStateFlow()

    private val connectedSinceState = MutableStateFlow(0L)

    /** When the current binding was established; 0 while disconnected. Guards the watchdog from racing a fresh connect. */
    val connectedSince: StateFlow<Long> = connectedSinceState.asStateFlow()

    private val lastSeenState = MutableStateFlow(0L)
    val lastSeenAt: StateFlow<Long> = lastSeenState.asStateFlow()

    private val lastPackageState = MutableStateFlow<String?>(null)
    val lastPackage: StateFlow<String?> = lastPackageState.asStateFlow()

    private var prefs: SharedPreferences? = null

    /** Idempotent; called from `Application.onCreate` and from the listener service. */
    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        lastSeenState.value = p.getLong(KEY_LAST_AT, 0L)
        lastPackageState.value = p.getString(KEY_LAST_PKG, null)
    }

    fun markConnected(connected: Boolean) {
        connectedState.value = connected
        connectedSinceState.value = if (connected) System.currentTimeMillis() else 0L
    }

    /**
     * Called for every notification from a payment app, parsed or not: this is the proof that the
     * lane is reading. [postedAt] is wall-clock (StatusBarNotification.postTime).
     */
    fun recordNotification(pkg: String, postedAt: Long) {
        val at = if (postedAt > 0) postedAt else System.currentTimeMillis()
        lastSeenState.value = at
        lastPackageState.value = pkg
        prefs?.edit()?.putLong(KEY_LAST_AT, at)?.putString(KEY_LAST_PKG, pkg)?.apply()
    }
}
