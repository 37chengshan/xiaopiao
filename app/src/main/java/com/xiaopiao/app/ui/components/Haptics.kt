package com.xiaopiao.app.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/** Named haptic moments so every screen "feels" the same: tick (light), thud (stamp), confirm, reject. */
@Stable
class XpHaptics(private val feedback: HapticFeedback) {
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    fun select() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    fun thud() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
    fun threshold() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
}

/** Remembers an [XpHaptics] bound to the current view. */
@Composable
fun rememberXpHaptics(): XpHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { XpHaptics(feedback) }
}

/** True when the user turned system animations off (animator duration scale == 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}
