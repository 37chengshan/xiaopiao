package com.xiaopiao.app.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.xiaopiao.app.XiaopiaoApp
import com.xiaopiao.app.work.CaptureWatchScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads Alipay / WeChat payment notifications. The raw text is parsed on this device only;
 * only the structured fields reach the ledger.
 *
 * Chinese ROMs drop the listener binding silently (after a reinstall, a battery freeze, or just
 * because), so this class also feeds [CaptureStatus] and asks for a repair when it loses the binding.
 */
class PaymentListenerService : NotificationListenerService() {
    /** Only the rule id / amount reaches logcat — never the notification text (docs/APP-DESIGN.md §4.4). */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w(TAG, "capture failed: ${e.javaClass.simpleName}")
        },
    )

    override fun onCreate() {
        super.onCreate()
        CaptureStatus.attach(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        CaptureStatus.markConnected(true)
        Log.i(TAG, "connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        CaptureStatus.markConnected(false)
        Log.w(TAG, "disconnected")
        // The system may rebind on its own; a nudge costs nothing and saves a day of silence.
        CaptureWatchScheduler.repairSoon(this, DEFAULT_REPAIR_DELAY_MS)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PaymentApps.all) return
        val n = sbn.notification ?: return
        CaptureStatus.attach(this)
        // Record before any filtering: this is what proves the lane can still read.
        CaptureStatus.recordNotification(sbn.packageName, sbn.postTime)
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "XpCapture"

        /** Give the OS a moment to rebind by itself before we step in. */
        private const val DEFAULT_REPAIR_DELAY_MS = 20_000L

        /** Asks the system to rebuild the binding (no permission change, safe to call from the UI). */
        fun reconnect(context: Context) {
            requestRebind(ComponentName(context.applicationContext, PaymentListenerService::class.java))
            Log.i(TAG, "rebind requested")
        }
    }
}
