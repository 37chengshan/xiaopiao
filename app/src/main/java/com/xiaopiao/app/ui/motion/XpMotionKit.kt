package com.xiaopiao.app.ui.motion

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.XpEase
import kotlinx.coroutines.delay

/**
 * Shared motion vocabulary (docs/APP-DESIGN.md §7.5).
 *
 * Every value here is time-based and frame-clock driven: [rememberDtProgress] (and the dt loops in the
 * splash hand-off) advance by the real delta between frames, so a 90Hz or 120Hz device finishes in the same
 * milliseconds as a 60Hz one. Nothing may count frames, and nothing may change layout — all offsets go
 * through `graphicsLayer`.
 */
object XpTiming {
    /** Sparse login light drifting behind glass, in milliseconds. */
    const val AccountAmbient = 32_000

    /** Page / block entrance. */
    const val Enter = MotionMath.ENTER_MS

    /** Sibling stagger. */
    const val Stagger = MotionMath.STAGGER_STEP_MS

    /** Settings sub-page push in. */
    const val PushIn = 220

    /** Settings sub-page pop back. */
    const val PopOut = 180

    /** How far the page underneath drifts while a sub-page covers it (fraction of the width). */
    const val PushParallax = 1f / 8f

    /** How far the sub-page travels while entering (fraction of the width). */
    const val PushTravel = 1f / 4f

    /** Pressed feedback: must be on screen in the very first frame. */
    const val Press = MotionMath.PRESS_MS

    /** Skeletons must stay visible at least this long, or they read as a flicker. */
    const val SkeletonMin = 200
}

/**
 * Motion templates (tt 2026-11-01: 动画模板化). Pick a spec by *intent*, never by numbers at the call site:
 *
 * - Movement and scale use springs — they can be interrupted mid-flight and keep their velocity, which is what
 *   makes a control feel physical instead of "played back".
 * - Opacity and colour use short strong-ease-out tweens; a spring adds nothing to a fade.
 * - Exits are quicker than entrances and accelerate away ([XpEase.exit]).
 */
object XpMotion {
    /** Default for anything that moves: thumbs, placements, slides. Settles fast with a hint of life. */
    fun <T> snappy(visibility: T? = null) = spring(dampingRatio = 0.82f, stiffness = 520f, visibilityThreshold = visibility)

    /** Small confirmations that should land with a visible overshoot: a pop, a chip, a tab glyph. */
    fun <T> bouncy(visibility: T? = null) = spring(dampingRatio = 0.58f, stiffness = 480f, visibilityThreshold = visibility)

    /** Large surfaces (sheets, whole pages): no overshoot, unhurried. */
    fun <T> gentle(visibility: T? = null) = spring(dampingRatio = 1f, stiffness = 340f, visibilityThreshold = visibility)

    /** Finger down: must be visible in the first frame. */
    fun <T> pressIn() = tween<T>(XpTiming.Press, easing = XpEase.out)

    /** Finger up: springs back with a small overshoot. */
    fun <T> pressOut(visibility: T? = null) = spring(dampingRatio = 0.6f, stiffness = 620f, visibilityThreshold = visibility)

    /** Opacity / colour in. */
    fun <T> fade(durationMs: Int = 180) = tween<T>(durationMs, easing = XpEase.out)

    /** Opacity / colour out. */
    fun <T> fadeAway(durationMs: Int = 120) = tween<T>(durationMs, easing = XpEase.exit)

    // ── Ready-made transitions ────────────────────────────────────────────────────────────────────

    private val offsetSnappy get() = snappy(IntOffset.VisibilityThreshold)

    /** Appear in place. */
    fun enterFade(): EnterTransition = fadeIn(fade())

    /** Disappear in place. */
    fun exitFade(): ExitTransition = fadeOut(fadeAway())

    /** Rise into place from [fraction] of its own height below (floating pills, toasts). */
    fun enterRise(fraction: Float = 0.5f): EnterTransition =
        fadeIn(fade()) + slideInVertically(offsetSnappy) { (it * fraction).toInt() }

    /** Sink back out. */
    fun exitSink(fraction: Float = 0.33f): ExitTransition =
        fadeOut(fadeAway()) + slideOutVertically(fadeAway()) { (it * fraction).toInt() }

    /** Grow out of [from] scale — buttons and controls that appear on demand. */
    fun enterPop(from: Float = 0.9f): EnterTransition = fadeIn(fade()) + scaleIn(bouncy(), initialScale = from)

    /** Shrink away. */
    fun exitPop(to: Float = 0.94f): ExitTransition = fadeOut(fadeAway()) + scaleOut(fadeAway(), targetScale = to)

    /** Swap two states in the same slot (labels, top-bar modes). */
    fun crossfade(): ContentTransform = fadeIn(fade()) togetherWith fadeOut(fadeAway(90))

    /** Swap with a small pop on the incoming state. */
    fun crossfadePop(from: Float = 0.94f): ContentTransform = enterPop(from) togetherWith fadeOut(fadeAway(90))

    /** A secondary page slides in from the trailing edge over the current one. */
    fun pushForward(): ContentTransform =
        (fadeIn(fade()) + slideInHorizontally(offsetSnappy) { it / 10 }) togetherWith fadeOut(fadeAway())

    /** …and slides back out. */
    fun pushBack(): ContentTransform =
        fadeIn(fade()) togetherWith (fadeOut(fadeAway()) + slideOutHorizontally(fadeAway(180)) { it / 10 })
}

/** The app's strong ease-out, usable as a plain function (for `withFrameNanos` loops). */
val xpOut: (Float) -> Float = { MotionMath.easeOutStrong(it) }

/**
 * True for the first time a page is shown in this process, false when the same page is entered again (a tab
 * switch back). Pages use it to skip replaying their entrance: switching tabs re-composes the destination, and
 * replaying 57 reveals every time read as “the whole page refreshed”.
 */
val LocalPageEntrance = androidx.compose.runtime.compositionLocalOf { true }

/** Process-wide record of the pages whose entrance already played. Survives an activity recreate. */
object PageEntranceSeen {
    val routes: MutableSet<String> = mutableSetOf()
}

/**
 * One-shot 0..1 progress driven by `withFrameNanos`. Re-runs when [key] changes, so a screen can replay
 * its entrance by bumping the key. Under reduced motion it returns 1 immediately (content just appears).
 */
@Composable
fun rememberDtProgress(
    key: Any? = Unit,
    durationMs: Int = XpTiming.Enter,
    delayMs: Int = 0,
    easing: (Float) -> Float = xpOut,
    reduced: Boolean = rememberReducedMotion(),
): Float {
    // The single choke point for every entrance animation in the app: `xpRevealLayer` and `XpReveal` both end up
    // here. On a re-entered page the run length collapses to 0, which makes the initial value 1f — the content is
    // already in its final state on the first frame instead of fading in again.
    val enter = LocalPageEntrance.current
    val runMs = if (enter) durationMs else 0
    var progress by remember(key, reduced, runMs) { mutableFloatStateOf(if (reduced || runMs <= 0) 1f else 0f) }
    LaunchedEffect(key, reduced, runMs, delayMs) {
        if (reduced || runMs <= 0) {
            progress = 1f
            return@LaunchedEffect
        }
        progress = 0f
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsedMs = (now - start) / 1_000_000f
            val raw = MotionMath.progress(elapsedMs, runMs, delayMs)
            progress = if (raw <= 0f) 0f else easing(raw)
            if (raw >= 1f) break
        }
    }
    return progress
}

/**
 * Same entrance as [XpReveal], but as a modifier, for containers whose scope must stay intact
 * (`ColumnScope` sheets, `LazyColumn` item roots). Never changes layout — `graphicsLayer` only.
 */
fun Modifier.xpRevealLayer(
    index: Int = 0,
    animate: Boolean = true,
    durationMs: Int = XpTiming.Enter,
    delayStepMs: Int = XpTiming.Stagger,
    riseDp: Dp = 10.dp,
    scaleFrom: Float = 1f,
): Modifier = composed {
    val delay = if (animate) MotionMath.staggerDelayMs(index, delayStepMs) else 0
    val p = rememberDtProgress(
        key = Triple(animate, durationMs, delay),
        durationMs = if (animate) durationMs else 0,
        delayMs = delay,
    )
    graphicsLayer {
        alpha = p
        translationY = (1f - p) * riseDp.toPx()
        if (scaleFrom != 1f) {
            val s = scaleFrom + (1f - scaleFrom) * p
            scaleX = s
            scaleY = s
        }
    }
}

/**
 * Fade + rise entrance with an optional stagger [index]. Purely `graphicsLayer`, so it never re-lays-out
 * the parent (no content shift, no scroll jump). Pass [animate] = false for rows that appear later than the
 * first screen — they should arrive already visible instead of animating on every scroll.
 */
@Composable
fun XpReveal(
    index: Int = 0,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    durationMs: Int = XpTiming.Enter,
    delayStepMs: Int = XpTiming.Stagger,
    riseDp: Dp = 10.dp,
    scaleFrom: Float = 1f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.xpRevealLayer(
            index = index,
            animate = animate,
            durationMs = durationMs,
            delayStepMs = delayStepMs,
            riseDp = riseDp,
            scaleFrom = scaleFrom,
        ),
    ) { content() }
}

/**
 * Two-layer push used by settings → sub-page (docs/APP-DESIGN.md §7.5): the incoming page slides in from
 * 1/4 of the width with a fade (220ms), the page underneath drifts 1/8 to the left; popping reverses in
 * 180ms. The last pushed page is remembered so the exit has something to draw.
 */
@OptIn(androidx.activity.ExperimentalActivityApi::class)
@Composable
fun XpPushHost(
    page: (@Composable () -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enterMs: Int = XpTiming.PushIn,
    exitMs: Int = XpTiming.PopOut,
    base: @Composable BoxScope.() -> Unit,
) {
    val reduced = rememberReducedMotion()
    var last by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    SideEffect { if (page != null) last = page }
    val active = page ?: last
    var progress by remember { mutableFloatStateOf(0f) }
    var backGesture by remember { mutableStateOf(false) }

    LaunchedEffect(page != null, reduced, backGesture) {
        if (backGesture) return@LaunchedEffect
        val target = if (page != null) 1f else 0f
        if (reduced) {
            progress = target
            return@LaunchedEffect
        }
        if (progress == target) return@LaunchedEffect
        val from = progress
        val duration = if (target > from) enterMs else exitMs
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val f = MotionMath.progress((now - start) / 1_000_000f, duration)
            progress = from + (target - from) * xpOut(f)
            if (f >= 1f) break
        }
        progress = target
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = -progress * widthPx * XpTiming.PushParallax
            },
        ) { base() }
        if (active != null && progress > 0f) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = progress
                    translationX = (1f - progress) * widthPx * XpTiming.PushTravel
                },
            ) { active() }
        }
        androidx.activity.compose.PredictiveBackHandler(enabled = page != null) { events ->
            backGesture = true
            try {
                events.collect { event -> if (!reduced) progress = 1f - event.progress }
                onBack()
            } catch (_: kotlinx.coroutines.CancellationException) {
                // The existing transition restores the live page when the gesture is cancelled.
            } finally { backGesture = false }
        }
        BackHandler(enabled = page == null && progress > 0f) { /* consume repeated back during exit */ }
    }
}

/**
 * Keeps a skeleton on screen for at least [minVisibleMs]. Without this, a cache hit makes the placeholder
 * flash for two frames and reads as a glitch. Returns the value the UI should render.
 */
@Composable
fun rememberSkeletonVisible(loading: Boolean, minVisibleMs: Int = XpTiming.SkeletonMin): Boolean {
    var show by remember { mutableStateOf(loading) }
    var since by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loading) {
        if (loading) {
            if (!show) since = SystemClock.elapsedRealtime()
            show = true
        } else if (show) {
            val waited = SystemClock.elapsedRealtime() - since
            if (waited < minVisibleMs) delay(minVisibleMs - waited)
            show = false
        }
    }
    return show
}
