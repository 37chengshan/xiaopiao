package com.xiaopiao.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design tokens. Apple-flavoured: warm paper background, ink text, one coral accent.
 * Screens must use these instead of raw colors so the design pass can retune them in one place.
 */
object XpColors {
    val Canvas = Color(0xFFF5F3EE)
    val Paper = Color(0xFFFFFDF8)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1C1C1E)
    val InkSecondary = Color(0xFF8E8E93)
    val InkTertiary = Color(0xFFC7C7CC)
    val Hairline = Color(0x14000000)
    val Accent = Color(0xFFFF6B4A)
    val AccentSoft = Color(0xFFFFE7E0)
    val Positive = Color(0xFF34C759)
    val Warning = Color(0xFFFF9F0A)
    val Stamp = Color(0xFFE5484D)

    // Added by the receipt lane (existing names above are untouched).
    /** Slightly darker paper, used for fibre texture and the fold shadow. */
    val PaperShade = Color(0xFFF1EBDD)
    /** Warm black used for printed receipt ink. */
    val PaperInk = Color(0xFF26231F)
    val PrinterBody = Color(0xFF2B2B30)
    val PrinterHighlight = Color(0xFF45454C)
    val PrinterSlit = Color(0xFF0B0B0D)
    val Scrim = Color(0x59000000)
    val ToastBackground = Color(0xF21C1C1E)
    val Shimmer = Color(0xFFE9E6DF)
    val ShimmerHighlight = Color(0xFFF7F5F0)
}

object XpSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object XpRadius {
    val s = 10.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val pill = 999.dp
}

/** Shared spring recipes so motion feels like one family. Nothing here is linear. */
object XpMotion {
    fun <T> snappy() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.8f, stiffness = 500f)
    fun <T> bouncy() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.62f, stiffness = 380f)
    fun <T> gentle() = androidx.compose.animation.core.spring<T>(dampingRatio = 0.9f, stiffness = 220f)
}
