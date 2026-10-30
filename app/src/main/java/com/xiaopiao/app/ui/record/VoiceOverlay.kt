package com.xiaopiao.app.ui.record

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HearingDisabled
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.asr.AsrModel
import com.xiaopiao.app.domain.asr.AsrModelManager
import com.xiaopiao.app.domain.parse.ParsedEntry
import com.xiaopiao.app.domain.parse.QuickParser
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far (dp) the finger must travel up from where the hold began before release means 「取消」. */
private val CancelLift = 72.dp

/**
 * What the bar says when the finger lifts without a usable sentence (docs/APP-DESIGN.md §7.7.1). A usable one
 * never stops here: it goes straight into 记一笔, pre-filled.
 */
sealed interface VoiceOutcome {
    /** Nothing usable came out of the mic. */
    data object NotHeard : VoiceOutcome
    data class Problem(val message: String) : VoiceOutcome
}

/**
 * The single voice surface (docs/APP-DESIGN.md §4.2). Any entry point — the centre key, the record
 * ball — asks this host to listen; the bar lives above the tab bar so it survives tab switches.
 *
 * Hold-to-talk: [hold] follows the finger while the key is held, [release] ends the sentence — or
 * cancels it when the finger has slid up past [CancelLift] (the bar turns red and says 松开取消 first).
 */
class VoiceHostState(
    val controller: VoiceController,
    val models: AsrModelManager,
    private val scope: CoroutineScope,
) {
    var open by mutableStateOf(false)
        private set
    var requested by mutableStateOf(false)
        private set

    /** Finger travel above the hold point, in px; 0 while not holding. */
    var liftPx by mutableFloatStateOf(0f)
        private set
    var cancelArmed by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<VoiceOutcome?>(null)

    /** Non-null when the mic needs a model first; the prompt offers the one-tap download. */
    var promptModel by mutableStateOf<AsrModel?>(null)
        private set
    var downloading by mutableStateOf(false)
        private set
    var downloadError by mutableStateOf<String?>(null)
        private set

    fun request() {
        outcome = null
        liftPx = 0f
        cancelArmed = false
        requested = true
    }

    fun permissionResult(granted: Boolean) {
        requested = false
        if (!granted) {
            outcome = VoiceOutcome.Problem("没有麦克风权限，去系统设置里允许")
            return
        }
        open = true
        controller.start()
        // No model for this language yet: close the bar and ask for the download instead.
        controller.pendingModel?.let { model ->
            controller.clearPending()
            open = false
            promptModel = model
        }
    }

    /** Finger moved while holding; [dyPx] is negative upwards, measured from the press point. */
    fun hold(dyPx: Float, thresholdPx: Float) {
        if (!open) return
        liftPx = (-dyPx).coerceAtLeast(0f)
        cancelArmed = liftPx > thresholdPx
    }

    /** Finger lifted: finish the sentence, or drop it if the cancel line was crossed. */
    fun release() {
        val cancel = cancelArmed
        liftPx = 0f
        cancelArmed = false
        if (!open) return
        if (cancel) cancel() else controller.stop()
    }

    /** Download, then listen without another tap (tt: 「下载完直接开始听」). */
    fun downloadAndListen() {
        val model = promptModel ?: return
        if (downloading) return
        downloading = true
        downloadError = null
        scope.launch {
            try {
                models.ensure(model)
                downloading = false
                promptModel = null
                open = true
                controller.startAfterDownload()
            } catch (e: kotlinx.coroutines.CancellationException) {
                downloading = false
                throw e
            } catch (e: Exception) {
                downloading = false
                downloadError = e.message ?: "下载失败"
            }
        }
    }

    fun dismissPrompt() {
        promptModel = null
        downloading = false
        downloadError = null
        controller.clearPending()
    }

    fun stop() { controller.stop() }

    fun cancel() {
        controller.cancel()
        open = false
        liftPx = 0f
        cancelArmed = false
    }

    fun dismiss() { open = false }
}

@Composable
fun rememberVoiceHost(container: AppContainer): VoiceHostState {
    val scope = rememberCoroutineScope()
    val controller = remember(container) {
        VoiceController(
            context = container.appContext,
            models = container.asrModels,
            prefs = container.asrPrefs,
            hotwordSeed = { container.asrHotwordSeed() },
        )
    }
    val host = remember(controller) { VoiceHostState(controller, container.asrModels, scope) }
    DisposableEffect(host) { onDispose { controller.release() } }
    return host
}

/** The cancel line in px, for whoever feeds [VoiceHostState.hold]. */
@Composable
fun rememberVoiceCancelThresholdPx(): Float = with(LocalDensity.current) { CancelLift.toPx() }

/**
 * The listening bar. On release the sentence is parsed by local rules only (QuickParser, no model call —
 * tt 2026-10-29: 规则做好就行) and handed to [onRecord] as a pre-filled 记一笔 row: amount, category, words and
 * day filled in, the user checks or changes the category and saves (tt: 说完直接进记一笔). Without an amount
 * the words still go in, so nothing said is lost. Only 没听清 / errors stay on the bar.
 */
@Composable
fun VoiceOverlay(
    host: VoiceHostState,
    onRecord: (Txn) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val controller = host.controller
    val haptics = rememberXpHaptics()
    val en = LocalConfiguration.current.locales[0].language == "en"
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        host.permissionResult(granted)
    }

    // Ask for the mic the first time it is actually needed, then start listening.
    LaunchedEffect(host.requested) {
        if (!host.requested) return@LaunchedEffect
        if (controller.hasPermission) host.permissionResult(true) else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(controller.transcript) {
        val text = controller.consume() ?: return@LaunchedEffect
        host.dismiss()
        val entries = QuickParser.parse(text, System.currentTimeMillis())
        // A lone syllable (「嗯」「被。」) is the model hearing noise, not a sentence it failed to read.
        if (entries.isEmpty() && text.count { it.isLetterOrDigit() } < 2) {
            haptics.reject()
            host.outcome = VoiceOutcome.NotHeard
        } else {
            haptics.confirm()
            // Several amounts in one breath: the first goes into the pane; the rest are rare enough to re-say.
            onRecord(
                entries.firstOrNull()?.toLedgerTxn()
                    ?: Txn(amountCents = 0, merchant = text.take(32), category = com.xiaopiao.app.domain.Category.OTHER, occurredAt = System.currentTimeMillis(), source = com.xiaopiao.app.domain.Source.QUICK_ADD),
            )
        }
    }
    LaunchedEffect(controller.error) {
        val err = controller.error ?: return@LaunchedEffect
        if (controller.listening) return@LaunchedEffect
        host.cancel()
        haptics.reject()
        host.outcome = if (err.startsWith("没听清")) VoiceOutcome.NotHeard else VoiceOutcome.Problem(err)
    }
    LaunchedEffect(host.outcome) {
        val shown = host.outcome ?: return@LaunchedEffect
        delay(2_600L)
        if (host.outcome == shown) host.outcome = null
    }

    host.promptModel?.let { model ->
        VoiceModelPrompt(model = model, host = host)
    }

    val outcome = host.outcome
    val listening = host.open
    AnimatedVisibility(
        visible = listening || outcome != null,
        modifier = modifier,
        enter = expandVertically(spring(0.85f, 340f), expandFrom = Alignment.Bottom) + fadeIn(tween(120)),
        exit = shrinkVertically(tween(140)) + fadeOut(tween(110)),
    ) {
        val shape = RoundedCornerShape(XpRadius.pill)
        val tint by animateColorAsState(
            when {
                listening && host.cancelArmed -> XpColors.Negative
                listening -> XpColors.Primary
                else -> XpColors.Negative
            },
            tween(160),
            label = "voiceTint",
        )
        val lift = if (listening) (host.liftPx / 400f).coerceIn(0f, 1f) else 0f
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = XpSpace.l)
                .graphicsLayer { translationY = -lift * 16.dp.toPx() }
                .heightIn(min = 56.dp)
                .voiceGlass(backdrop, shape, tint, armed = listening && host.cancelArmed),
        ) {
            AnimatedContent(
                targetState = if (listening) null else outcome,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "voiceBar",
            ) { shown ->
                when (shown) {
                    null -> ListeningRow(host, tint, en)
                    VoiceOutcome.NotHeard -> ResultRow(
                        icon = Icons.Outlined.HearingDisabled,
                        tint = tint,
                        text = if (en) "Didn't catch that — hold and say it again" else "没听清，按住再说一次",
                    )
                    is VoiceOutcome.Problem -> ResultRow(icon = Icons.Outlined.Close, tint = tint, text = shown.message)
                }
            }
        }
    }
}

/** Liquid glass when the page is being recorded behind us; a solid card otherwise (dialogs, previews). */
@Composable
private fun Modifier.voiceGlass(backdrop: Backdrop?, shape: RoundedCornerShape, tint: Color, armed: Boolean): Modifier {
    val reduced = rememberReducedMotion()
    val film = if (armed) XpColors.Negative.copy(alpha = 0.22f) else XpColors.Card.copy(alpha = if (XpColors.isDark) 0.40f else 0.55f)
    val rim = tint.copy(alpha = 0.22f)
    return if (backdrop == null || reduced) {
        this.clip(shape).background(if (armed) XpColors.Negative.copy(alpha = 0.12f).compositeOver(XpColors.Card) else XpColors.Card)
    } else {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(12.dp.toPx())
                lens(10.dp.toPx(), 16.dp.toPx())
            },
            shadow = { Shadow(radius = 18.dp, color = rim) },
            onDrawSurface = { drawRect(film) },
        )
    }
}

@Composable
private fun ListeningRow(host: VoiceHostState, tint: Color, en: Boolean) {
    val controller = host.controller
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = XpSpace.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(XpSpace.s))
        VoiceLevel(active = controller.listening || controller.partial.isNotEmpty(), level = controller.rms, color = tint)
        Spacer(Modifier.width(XpSpace.m))
        Text(
            when {
                host.cancelArmed -> if (en) "Release to cancel" else "松开取消"
                controller.partial.isNotBlank() -> controller.partial
                controller.listening -> if (en) "Listening…" else "听你说…"
                else -> if (en) "Getting ready…" else "准备中…"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (host.cancelArmed) XpColors.Negative else XpColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!host.cancelArmed) {
            Text(
                if (en) "Slide up to cancel" else "上滑取消",
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
            )
        }
    }
}

@Composable
private fun ResultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = XpSpace.l, end = XpSpace.s, top = XpSpace.s, bottom = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(XpSpace.s))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = XpColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun ParsedEntry.toLedgerTxn() = com.xiaopiao.app.domain.Txn(
    amountCents = amountCents,
    direction = direction,
    merchant = merchant,
    category = category,
    occurredAt = occurredAt,
    source = com.xiaopiao.app.domain.Source.QUICK_ADD,
    note = note,
)

/** Bars driven by the live mic level: the user must be able to see that the phone hears them. */
@Composable
private fun VoiceLevel(active: Boolean, level: Float, color: Color) {
    val factors = remember { floatArrayOf(0.55f, 1f, 0.75f, 0.9f, 0.6f) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        factors.forEach { f ->
            val h = if (active) (8f + 20f * f * level.coerceIn(0.08f, 1f)) else 8f
            Box(
                Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (active) 0.95f else 0.45f)),
            )
        }
    }
}
