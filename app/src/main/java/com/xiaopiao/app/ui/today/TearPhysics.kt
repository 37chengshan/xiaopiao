package com.xiaopiao.app.ui.today

/**
 * Pure maths for tearing the receipt off (docs/DESIGN.md §7, apple-design §5-6, §9):
 * 1:1 tracking downward, rubber-band resistance upward, and a decision that looks where the flick is *going*.
 */
object TearPhysics {
    const val DECELERATION = 0.998f

    /** Where a release at [velocity] (px/s) would coast to: Apple's exponential-decay projection. */
    fun project(velocity: Float, rate: Float = DECELERATION): Float = (velocity / 1000f) * rate / (1f - rate)

    /** Soft boundary: the further past the edge, the less the content follows. Result in 0 until [dimension]. */
    fun rubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float {
        if (overshoot <= 0f) return 0f
        return (overshoot * dimension * constant) / (dimension + constant * overshoot)
    }

    /** Paper offset for a finger [travel] (px): 1:1 down, rubber-banded up. */
    fun offsetFor(travel: Float, dimension: Float): Float =
        if (travel >= 0f) travel else -rubberBand(-travel, dimension)

    /** Tear when the projected landing point passes [threshold], but never from a tiny nudge. */
    fun shouldTear(offset: Float, velocity: Float, threshold: Float): Boolean {
        if (offset >= threshold) return true
        if (offset < threshold * MIN_TRAVEL_FRACTION) return false
        return offset + project(velocity) >= threshold * FLING_FACTOR
    }

    /** Paper tilt (degrees) while it is being pulled: a hint of the rip, growing with travel, capped. */
    fun dragTilt(offset: Float, threshold: Float): Float = (offset / threshold).coerceIn(0f, 1.2f) * 2.2f

    private const val MIN_TRAVEL_FRACTION = 0.25f
    private const val FLING_FACTOR = 1.6f
}
