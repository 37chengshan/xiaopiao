package com.xiaopiao.app.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.xiaopiao.app.capture.CaptureStatus
import com.xiaopiao.app.capture.PaymentListenerService
import java.util.concurrent.TimeUnit

/**
 * When should the watchdog force a rebind? Pure so it can be tested without Android: the rule is
 * "the listener is enabled, we are not connected, and the last connect was not just now".
 */
object CaptureWatchRule {
    /** A binding younger than this is the system doing its job — the watchdog stays out of the way. */
    const val GRACE_MS = 60_000L

    fun shouldRepair(enabled: Boolean, connected: Boolean, connectedSince: Long, now: Long): Boolean {
        if (!enabled || connected) return false
        val age = now - connectedSince
        return connectedSince <= 0L || age > GRACE_MS || age < 0L
    }
}

/**
 * Self-heals the notification lane. Reading payments depends on a system-held binding that Chinese
 * ROMs drop silently after a reinstall, a battery freeze, or a force-stop; without this the app goes
 * quiet with no visible error.
 *
 * Two triggers, both cheap:
 * - immediately after `onListenerDisconnected` (a rebind usually restores it within seconds);
 * - a periodic sweep as a backstop when the process was killed and nobody noticed the disconnect.
 *
 * Rebind is a no-op when the listener is disabled or already connected, so this never fights the OS.
 */
class CaptureRepairWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        CaptureStatus.attach(context)
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val repair = CaptureWatchRule.shouldRepair(
            enabled = enabled,
            connected = CaptureStatus.connected.value,
            connectedSince = CaptureStatus.connectedSince.value,
            now = System.currentTimeMillis(),
        )
        if (repair) PaymentListenerService.reconnect(context)
        return Result.success()
    }
}

object CaptureWatchScheduler {
    private const val UNIQUE_PERIODIC = "capture_watch"
    private const val UNIQUE_REPAIR = "capture_repair"
    private const val PERIOD_MINUTES = 30L

    /** Idempotent: safe to call on every app start. */
    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<CaptureRepairWorker>(PERIOD_MINUTES, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    /** One-shot nudge, coalesced: a burst of disconnects still results in a single rebind. */
    fun repairSoon(context: Context, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<CaptureRepairWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_REPAIR, ExistingWorkPolicy.KEEP, request,
        )
    }
}
