package com.xiaopiao.app.ui

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Debug-only frame logger. The vivo test phone does not populate `dumpsys gfxinfo` (Total frames = 0) and
 * `SurfaceFlinger --latency` is empty, so the only way to get real numbers there is to measure Choreographer
 * callbacks inside the process. Every frame slower than 24ms is logged, plus a rolling summary, and a watchdog
 * dumps the main thread's stack while it is stuck. Never active in release builds.
 */
@Composable
fun DebugFrameMonitor() {
    if (!com.xiaopiao.app.BuildConfig.DEBUG) return
    LaunchedEffect(Unit) {
        StallWatchdog.start()
        val choreographer = Choreographer.getInstance()
        var last = 0L
        var slow = 0
        var total = 0
        var windowStart = 0
        while (true) {
            val now = suspendCancellableCoroutine<Long> { cont -> choreographer.postFrameCallback { cont.resume(it) } }
            StallWatchdog.beat()
            if (last != 0L) {
                val dtMs = (now - last) / 1_000_000f
                total++
                if (dtMs > 24f) {
                    slow++
                    Log.w("FrameMon", "slow ${dtMs.toInt()}ms  @${now / 1_000_000 % 100000}")
                }
                val window = total - windowStart
                if (window >= 120) {
                    Log.i("FrameMon", "window: slow=$slow / $window  (${100 * slow / window}%)")
                    slow = 0
                    windowStart = total
                }
            }
            last = now
        }
    }
}

/** Notices a stalled main thread and dumps its stack — a profiler-free way to name the blocker on a locked-down ROM. */
private object StallWatchdog {
    @Volatile private var beat = SystemClock.uptimeMillis()
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        val main = Looper.getMainLooper().thread
        Thread({
            while (true) {
                try {
                    Thread.sleep(40)
                } catch (_: InterruptedException) {
                }
                val gap = SystemClock.uptimeMillis() - beat
                if (gap > 120) {
                    Log.w("FrameMon", "STALL ${gap}ms on main:")
                    main.stackTrace.take(24).forEach { Log.w("FrameMon", "    at $it") }
                    while (SystemClock.uptimeMillis() - beat > 120) {
                        try {
                            Thread.sleep(40)
                        } catch (_: InterruptedException) {
                        }
                    }
                }
            }
        }, "stall-watchdog").apply { isDaemon = true }.start()
    }

    fun beat() {
        beat = SystemClock.uptimeMillis()
    }
}
