package com.xiaopiao.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.capture.PaymentListenerService
import com.xiaopiao.app.data.CaptureLogEntry
import com.xiaopiao.app.importer.ImportSummary
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.motion.XpPushHost
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.ThemeMode
import com.xiaopiao.app.ui.LaunchSide
import com.xiaopiao.app.ui.NavPrefs
import com.xiaopiao.app.ui.navStore
import com.xiaopiao.app.ui.theme.ColorTheme
import com.xiaopiao.app.ui.theme.ThemePrefs
import com.xiaopiao.app.ui.today.ReceiptSoundPrefs
import kotlinx.coroutines.launch

/** The three settings sub-pages that get a real push transition (docs/APP-DESIGN.md §7.5). */
private enum class SettingsSub(val title: String) {
    Appearance("外观"),
    Data("数据与隐私"),
    About("关于"),
}

@Composable
fun SettingsScreen(
    container: AppContainer,
    /** Switches 个人版 ⇄ 小店版; the row reads 「切换到个人版」 when [inMerchant]. */
    onSwitchEdition: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    inMerchant: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listenerOn by remember { mutableStateOf(NotificationAccess.listenerEnabled(context)) }
    var postGranted by remember { mutableStateOf(NotificationAccess.postNotificationsGranted(context)) }
    var importing by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }
    var privacyOpen by remember { mutableStateOf(false) }
    var demoMessage by remember { mutableStateOf<String?>(null) }
    var sub by rememberSaveable { mutableStateOf<SettingsSub?>(null) }
    val logFlow = remember(container) { container.captureLog.observeRecent() }
    val log: List<CaptureLogEntry> by logFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val listenerConnected by PaymentListenerService.isConnected.collectAsStateWithLifecycle()

    val themePrefs = remember(context) { ThemePrefs(context.applicationContext) }
    val mode by themePrefs.observeMode().collectAsState(initial = ThemeMode.System)
    val colorTheme by themePrefs.observeTheme().collectAsState(initial = ColorTheme.Default)
    val navPrefs = remember(context) { NavPrefs(context.applicationContext.navStore) }
    val launchSide by navPrefs.observeLaunch().collectAsState(initial = LaunchSide.Ledger)
    val soundPrefs = remember(context) { ReceiptSoundPrefs(context.applicationContext) }
    val soundOn by soundPrefs.observeEnabled().collectAsState(initial = true)

    // The user comes back from system settings: re-check both permissions.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        listenerOn = NotificationAccess.listenerEnabled(context)
        postGranted = NotificationAccess.postNotificationsGranted(context)
    }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { postGranted = it }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                summary = container.csvImporter.import(uri)
                importing = false
            }
        }
    }

    val scroll = rememberScrollState()
    if (privacyOpen) PrivacySheet { privacyOpen = false }

    // Sub-page push: the incoming page slides in over this list (220ms), this list drifts 1/8 left.
    XpPushHost(
        page = sub?.let { target ->
            {
                when (target) {
                    SettingsSub.Appearance -> AppearancePage(
                        mode = mode,
                        onMode = { scope.launch { themePrefs.setMode(it) } },
                        theme = colorTheme,
                        onTheme = { scope.launch { themePrefs.setTheme(it) } },
                        onBack = { sub = null },
                    )

                    SettingsSub.Data -> DataPage(
                        importing = importing,
                        summary = summary,
                        log = log,
                        demoMessage = demoMessage,
                        onPickCsv = {
                            csvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                        },
                        onClearLog = { scope.launch { container.captureLog.clear() } },
                        onLoadDemo = {
                            scope.launch {
                                val n = container.loadDemoData()
                                demoMessage = if (n > 0) "已载入 $n 笔，去记账页看看吧～" else "账本里已经有数据了"
                            }
                        },
                        onClearAll = {
                            scope.launch {
                                container.clearAllData()
                                summary = null
                                demoMessage = "已清空，从零开始～"
                            }
                        },
                        onBack = { sub = null },
                    )

                    SettingsSub.About -> AboutPage(onPrivacy = { privacyOpen = true }, onBack = { sub = null })
                }
            }
        },
        onBack = { sub = null },
        modifier = Modifier.fillMaxSize(),
    ) {
        NavScaffold("设置", rememberNavScroll(scroll), onBack = onBack, glassGround = true) { top ->
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = 120.dp)) {
                Box(Modifier.xpRevealLayer(index = 0)) {
                    AppearanceNavSection(
                        value = "${mode.label} · ${colorTheme.label}",
                        onOpen = { sub = SettingsSub.Appearance },
                    )
                }
                Box(Modifier.xpRevealLayer(index = 1)) {
                    LaunchSideSection(launchSide) { scope.launch { navPrefs.setLaunch(it) } }
                }
                Box(Modifier.xpRevealLayer(index = 2)) {
                    ReceiptSection(soundOn) { scope.launch { soundPrefs.setEnabled(it) } }
                }
                Box(Modifier.xpRevealLayer(index = 3)) {
                    CaptureSection(
                        listenerEnabled = listenerOn,
                        listenerConnected = listenerConnected,
                        postGranted = postGranted,
                        onOpenListenerSettings = { NotificationAccess.openListenerSettings(context) },
                        onRequestPost = { postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onReconnect = { PaymentListenerService.reconnect(context) },
                    )
                }
                Box(Modifier.xpRevealLayer(index = 4)) {
                    DataNavSection(onOpen = { sub = SettingsSub.Data })
                }
                Box(Modifier.xpRevealLayer(index = 5)) {
                    EditionSwitchSection(inMerchant, onSwitchEdition)
                }
                Box(Modifier.xpRevealLayer(index = 6)) {
                    ReplayOnboardingSection { scope.launch { com.xiaopiao.app.ui.onboarding.OnboardingPrefs(context.applicationContext).reset() } }
                }
                Box(Modifier.xpRevealLayer(index = 7)) {
                    AboutNavSection(onOpen = { sub = SettingsSub.About })
                }
            }
        }
    }
}
