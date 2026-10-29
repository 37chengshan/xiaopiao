package com.xiaopiao.app.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.service.notification.StatusBarNotification
import com.xiaopiao.app.XiaopiaoApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads Alipay / WeChat payment notifications. The raw text is parsed on this device only;
 * only the structured fields reach the ledger.
 */
class PaymentListenerService : NotificationListenerService() {
    /** Only the rule id reaches logcat — never the notification text (docs/APP-DESIGN.md §4.4). */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w(TAG, "capture failed: ${e.javaClass.simpleName}")
        },
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PaymentApps.all) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        ).maxByOrNull { it.length }.orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val container = (applicationContext as XiaopiaoApp).container
        val pkg = sbn.packageName
        val postedAt = sbn.postTime
        scope.launch {
            when (val result = container.captureProcessor.process(pkg, title, text, postedAt)) {
                is ParseResult.Parsed -> Log.i(TAG, "PARSED:${result.payment.ruleId} ${result.payment.amountCents}")
                ParseResult.Unparsed -> Log.i(TAG, "UNPARSED")
                is ParseResult.Ignored -> Log.i(TAG, "IGNORED")
                ParseResult.Irrelevant -> Unit
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.value = true
        Log.i(TAG, "connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected.value = false
        Log.w(TAG, "disconnected")
    }

    companion object {
        const val TAG = "XpCapture"

        /** True while the system holds a live binding — Chinese ROMs drop it silently after a reinstall. */
        private val connected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = connected.asStateFlow()

        /** Asks the system to rebuild the binding (no permission change, safe to call from the UI). */
        fun reconnect(context: Context) {
            requestRebind(ComponentName(context.applicationContext, PaymentListenerService::class.java))
            Log.i(TAG, "rebind requested")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
