package com.xiaopiao.app.ui.splash

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.motion.MotionMath
import com.xiaopiao.app.ui.motion.SplashTimeline
import com.xiaopiao.app.ui.motion.xpOut
import kotlinx.coroutines.delay

/** Mirrors `windowSplashScreenBackground` in `values/themes.xml`, so the system splash and this overlay are one surface. */
private val SplashBackground = Color(0xFFF5F3EE)

/** Paper and ink for the logo: the receipt stays a paper object, never UI chrome. */
private val SplashPaper = Color(0xFFFFFFFF)
private val SplashInk = Color(0xFF1D1D1F)
private val SplashWarning = Color(0xFFB25000)

/**
 * Cold-start overlay that continues the platform splash (docs/APP-DESIGN.md §2.5): the same printer and
 * paper stay on screen, then the paper shrinks into the persistent receipt control (300ms, dt-driven
 * spring) and the overlay fades (160ms). Whole sequence is capped at 1.2s; a tap skips it; reduced motion
 * drops the flight and only fades. Nothing here changes layout of the app underneath.
 */
@Composable
fun SplashHost(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    var destination by remember { mutableStateOf<Rect?>(null) }
    var target by remember { mutableStateOf<Rect?>(null) }
    var fly by remember { mutableFloatStateOf(0f) }
    var fade by remember { mutableFloatStateOf(0f) }
    var skip by remember { mutableStateOf(false) }

    // Map the receipt control's window bounds into this overlay's coordinate space.
    LaunchedEffect(target, originX, originY) {
        target?.let { destination = it.translate(-originX, -originY) }
    }

    LaunchedEffect(Unit) {
        val startedAt = SystemClock.elapsedRealtime()
        // 1) Wait for the receipt control to appear (it is composed with the app shell, one frame later).
        if (!reduced) {
            while (!skip && target == null &&
                SystemClock.elapsedRealtime() - startedAt < SplashTimeline.TARGET_WAIT_MS
            ) {
                target = ReceiptSlot.bounds()
                if (target == null) delay(16)
            }
        } else {
            delay(SplashTimeline.HOLD_MS.toLong())
        }
        val canFly = target != null && !reduced && !skip

        // 2) Paper travels to the control on a critically-damped-ish spring integrated by real dt.
        if (canFly) {
            var position = 0f
            var velocity = 0f
            var last = 0L
            while (!skip && !MotionMath.springSettled(position, velocity, 1f)) {
                val now = withFrameNanos { it }
                val dt = MotionMath.dtSeconds(last, now)
                last = now
                if (dt > 0f) {
                    val (p, v) = MotionMath.springStep(position, velocity, 1f, dt, stiffness = 420f, dampingRatio = 0.88f)
                    position = p
                    velocity = v
                    fly = position.coerceIn(0f, 1.04f)
                }
            }
            fly = 1f
            ReceiptSlot.landed()
        }

        // 3) Fade the overlay away; skipping jumps straight here.
        var elapsed = 0f
        var lastFrame = 0L
        while (elapsed < 1f) {
            val now = withFrameNanos { it }
            val dt = MotionMath.dtSeconds(lastFrame, now)
            lastFrame = now
            if (dt > 0f) {
                elapsed += dt / (SplashTimeline.FADE_MS / 1000f)
                fade = if (reduced) elapsed.coerceIn(0f, 1f) else xpOut(elapsed.coerceIn(0f, 1f))
            }
        }
        onFinished()
    }

    Box(
        modifier
            .fillMaxSize()
            // The overlay fades out as a layer: the app is already drawn underneath, so 1→0 alpha is the
            // whole exit (no veil colour, no flash at the end).
            .graphicsLayer { alpha = 1f - fade }
            .background(SplashBackground)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                originX = position.x
                originY = position.y
            }
            .pointerInput(Unit) { detectTapGestures { skip = true } }
            .clearAndSetSemantics { },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val paperWidth = 62.dp.toPx()
            val paperHeight = 58.dp.toPx()
            val start = Rect(
                left = centerX - paperWidth / 2f,
                top = centerY - paperHeight / 2f + 12.dp.toPx(),
                right = centerX + paperWidth / 2f,
                bottom = centerY + paperHeight / 2f + 12.dp.toPx(),
            )
            val end = destination ?: start
            val p = fly.coerceIn(0f, 1f)
            val rect = Rect(
                left = lerp(start.left, end.left, p),
                top = lerp(start.top, end.top, p),
                right = lerp(start.right, end.right, p),
                bottom = lerp(start.bottom, end.bottom, p),
            )

            // Printer: leaves as soon as the paper starts travelling.
            val printerAlpha = (1f - p * 2.2f).coerceIn(0f, 1f)
            if (printerAlpha > 0.01f) {
                // The printer stays put while the paper leaves it; it fades as the strip travels.
                drawPrinter(centerX, start.top - 2.dp.toPx(), printerAlpha)
            }
            drawPaper(rect, inkAlpha = (0.35f + 0.65f * p).coerceIn(0f, 1f))
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** 96x34dp ink capsule with a light slit and the warning dot, sitting right above the paper. */
private fun DrawScope.drawPrinter(centerX: Float, bottomY: Float, alpha: Float) {
    val w = 96.dp.toPx()
    val h = 34.dp.toPx()
    val top = bottomY - h
    drawRoundRect(
        color = SplashInk.copy(alpha = alpha),
        topLeft = Offset(centerX - w / 2f, top),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()),
    )
    // Slit
    val slitInset = 10.dp.toPx()
    drawRoundRect(
        color = SplashBackground.copy(alpha = alpha),
        topLeft = Offset(centerX - w / 2f + slitInset, bottomY - 7.dp.toPx()),
        size = Size(w - slitInset * 2f, 3.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
    )
    // Indicator light
    drawCircle(
        color = SplashWarning.copy(alpha = alpha),
        radius = 1.8.dp.toPx(),
        center = Offset(centerX - w / 2f + 10.dp.toPx(), top + 9.dp.toPx()),
    )
}

/** White strip with a torn bottom edge and three printed rules; scales with [rect]. */
private fun DrawScope.drawPaper(rect: Rect, inkAlpha: Float) {
    val tooth = (rect.height * 0.10f)
    val teeth = 5
    val step = rect.width / teeth
    val path = Path().apply {
        moveTo(rect.left, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right, rect.bottom - tooth)
        var x = rect.right
        var raised = true
        repeat(teeth) {
            x -= step
            lineTo(x, if (raised) rect.bottom else rect.bottom - tooth)
            raised = !raised
        }
        lineTo(rect.left, rect.top)
        close()
    }
    drawPath(path, SplashPaper)

    val inset = rect.width * 0.16f
    val lineHeight = Stroke(width = (rect.height * 0.045f).coerceAtLeast(1.5.dp.toPx()), cap = StrokeCap.Round)
    val rows = listOf(0.30f to 1.0f, 0.46f to 1.0f, 0.62f to 0.66f)
    rows.forEach { (fraction, widthFraction) ->
        val y = rect.top + rect.height * fraction
        drawLine(
            color = SplashInk.copy(alpha = 0.22f * inkAlpha),
            start = Offset(rect.left + inset, y),
            end = Offset(rect.left + inset + (rect.width - inset * 2f) * widthFraction, y),
            strokeWidth = lineHeight.width,
            cap = StrokeCap.Round,
        )
    }
    // Total rule
    val totalY = rect.top + rect.height * 0.78f
    drawLine(
        color = SplashInk.copy(alpha = 0.55f * inkAlpha),
        start = Offset(rect.left + inset, totalY),
        end = Offset(rect.left + inset + (rect.width - inset * 2f) * 0.72f, totalY),
        strokeWidth = lineHeight.width * 1.5f,
        cap = StrokeCap.Round,
    )
}
