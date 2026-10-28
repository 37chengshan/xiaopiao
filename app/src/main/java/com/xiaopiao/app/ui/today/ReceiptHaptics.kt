package com.xiaopiao.app.ui.today

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * The printer's touch, played straight on the [Vibrator] (not through the view's haptic-feedback flag, which many
 * OEM ROMs silence). Needs the VIBRATE permission. [enabled] mirrors [ReceiptSoundPrefs.observeVibration].
 * Names match XpHaptics so [PrintSession] reads the same: tick per line, thud on the stamp, confirm when torn.
 */
class ReceiptHaptics(context: Context) {
    @Volatile var enabled: Boolean = true

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    /** True when this device can vibrate at all (settings can show or hide the switch). */
    val available: Boolean get() = vibrator != null

    /** One printed line: the lightest tick. */
    fun tick() = play(VibrationEffect.EFFECT_TICK, fallbackMs = 12, amplitude = 70)

    /** Stamp lands: heavy click. */
    fun thud() = play(VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 40, amplitude = 255)

    /** Paper passes the perforation. */
    fun threshold() = play(VibrationEffect.EFFECT_CLICK, fallbackMs = 20, amplitude = 160)

    /** Torn off / saved: a short double pulse that reads as "done". */
    fun confirm() {
        if (!enabled) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 28, 70, 42), intArrayOf(0, 180, 0, 255), -1)
        vibrate(effect)
    }

    private fun play(predefined: Int, fallbackMs: Long, amplitude: Int) {
        if (!enabled) return
        val effect = runCatching { VibrationEffect.createPredefined(predefined) }
            .getOrElse { VibrationEffect.createOneShot(fallbackMs, amplitude) }
        vibrate(effect)
    }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // MEDIA: feedback that goes with the sound, so the system's "touch feedback" switch does not mute the printer.
                v.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build())
            } else {
                @Suppress("DEPRECATION") v.vibrate(effect)
            }
        }.onFailure { Log.w(TAG, "vibrate failed", it) }
    }

    private companion object { const val TAG = "ReceiptHaptics" }
}
