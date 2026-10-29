package com.xiaopiao.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider

/** One receipt on its way out of the printer. */
class PrintJob(val day: Long, val lines: List<ReceiptLine>, val timeline: PrintTimeline, val stampText: String, val share: ShareContent)

/**
 * The print ceremony's owner, one per app shell: it holds the settings, today's number for the resident strip
 * and the [PrintSession]. Pages only reach it through [LocalReceiptHost] ([ReceiptStrip]); nothing here is page layout.
 */
@Stable
class ReceiptHost internal constructor(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    val session: PrintSession,
    val prefs: ReceiptPrefs,
) {
    var settings by mutableStateOf(ReceiptSettings()); internal set
    var todayCents: Cents by mutableLongStateOf(0L); internal set
    var today: Long by mutableLongStateOf(startOfDay(System.currentTimeMillis())); internal set
    var job by mutableStateOf<PrintJob?>(null); private set
    var share by mutableStateOf<ShareContent?>(null); internal set
    internal var reduced = false
    internal var openPage: () -> Unit = {}

    val printing: Boolean get() = session.phase != PrintPhase.Closed

    /** Prints [day] with the saved settings. [from] is where the printer leaves from (the strip or the printer key). */
    fun print(day: Long = today, from: Offset? = null) {
        if (printing) return
        scope.launch {
            val all = container.ledger.observeAll().first()
            val now = System.currentTimeMillis()
            val content = settings.content
            val excluded = settings.excluded
            val defs = container.categories.defs()
            val lines = ReceiptSource.linesFor(all, day, today, content, now, excluded = excluded, defs = defs)
            val receipt = ReceiptSource.receiptFor(all, day, excluded, defs)
            val stamp = ReceiptLayout.stampText(receipt)
            val meta = ReceiptSource.metaFor(all, day, now, excluded, defs)
            val share = ShareContent(
                full = ReceiptLayout.build(receipt, day == today, false, expanded = true, meta = meta, content = ReceiptPreset.Full.content),
                stampText = stamp,
                fileKey = ReceiptLayout.isoDay(day).substringBefore(' '),
                briefLines = ReceiptLayout.build(receipt, day == today, false, meta = meta, content = ReceiptPreset.Brief.content),
                colors = settings.colors(),
                font = settings.font,
            )
            val timeline = PrintTimeline.build(lines, reduced)
            job = PrintJob(day, lines, timeline, stamp, share)
            session.open(timeline, reduced, from)
        }
    }

    fun openReceiptPage() = openPage()

    internal fun closeToSettings() {
        session.close()
        openPage()
    }
}

val LocalReceiptHost = staticCompositionLocalOf<ReceiptHost?> { null }

@Composable
fun rememberReceiptHost(container: AppContainer, onOpenPage: () -> Unit): ReceiptHost {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = remember { ReceiptHaptics(context) }
    val sound = remember { ReceiptSound(context) }
    DisposableEffect(sound) { onDispose { sound.release() } }
    val soundPrefs = remember { ReceiptSoundPrefs(context) }
    val soundOn by soundPrefs.observeEnabled().collectAsState(initial = true)
    val vibrationOn by soundPrefs.observeVibration().collectAsState(initial = true)
    SideEffect { sound.enabled = soundOn; haptics.enabled = vibrationOn }

    val host = remember(container) {
        ReceiptHost(container, scope, PrintSession(scope, haptics, sound), ReceiptPrefs(context.applicationContext.receiptSettingsStore))
    }
    val reduced = rememberReducedMotion()
    val latestOpen by rememberUpdatedState(onOpenPage)
    SideEffect { host.reduced = reduced; host.openPage = latestOpen }

    LaunchedEffect(host) { host.prefs.observe().collect { host.settings = it } }
    // Keep "today" honest across midnight while the app stays open.
    LaunchedEffect(host) {
        while (true) { host.today = startOfDay(System.currentTimeMillis()); delay(60_000) }
    }
    LaunchedEffect(host, host.today) {
        container.ledger.observeRange(host.today, host.today + DAY_MS).collect { host.todayCents = ReceiptSource.todayOutCents(it, host.today) }
    }
    return host
}

/** The overlay + share sheet for [host]; place once, above the navigation, so any page's strip can trigger it. */
@Composable
fun ReceiptHostOverlay(host: ReceiptHost) {
    val job = host.job ?: return
    val density = LocalDensity.current
    val session = host.session
    val colors = host.settings.colors()
    CompositionLocalProvider(LocalPaperColors provides colors, LocalReceiptFont provides host.settings.font) {
        ReceiptOverlay(
            session, job.lines, job.timeline, job.stampText,
            onTear = { v -> session.tear(v, with(density) { 900.dp.toPx() }, { host.share = job.share }, host.reduced) },
            onTorn = { host.share = job.share },
            onSkip = { session.skip(job.timeline) },
            onClose = { session.close() },
            onSettings = host::closeToSettings,
        )
    }
    host.share?.let { ReceiptShareSheet(it) { host.share = null } }
}
