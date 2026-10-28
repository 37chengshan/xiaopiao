package com.xiaopiao.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.data.CaptureLogEntry
import com.xiaopiao.app.importer.ImportSummary
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.theme.ThemeMode
import com.xiaopiao.app.ui.theme.DesignStyle
import com.xiaopiao.app.ui.theme.ThemePrefs
import com.xiaopiao.app.ui.today.ReceiptSoundPrefs
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, onOpenMerchant: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listenerOn by remember { mutableStateOf(NotificationAccess.listenerEnabled(context)) }
    var postGranted by remember { mutableStateOf(NotificationAccess.postNotificationsGranted(context)) }
    var importing by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }
    var privacyOpen by remember { mutableStateOf(false) }
    var demoMessage by remember { mutableStateOf<String?>(null) }
    val logFlow = remember(container) { container.captureLog.observeRecent() }
    val log: List<CaptureLogEntry> by logFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val themePrefs = remember(context) { ThemePrefs(context.applicationContext) }
    val mode by themePrefs.observeMode().collectAsState(initial = ThemeMode.System)
    val designStyle by themePrefs.observeStyle().collectAsState(initial = DesignStyle.Apple)
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
    NavScaffold("设置", rememberNavScroll(scroll), onBack = onBack) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = 120.dp)) {
            AppearanceSection(mode, designStyle, { scope.launch { themePrefs.setMode(it) } }, { scope.launch { themePrefs.setStyle(it) } })
            ReceiptSection(soundOn) { scope.launch { soundPrefs.setEnabled(it) } }
            CaptureSection(
                listenerEnabled = listenerOn, postGranted = postGranted,
                onOpenListenerSettings = { NotificationAccess.openListenerSettings(context) },
                onRequestPost = { postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
            DataSection(
                importing = importing, summary = summary, log = log, demoMessage = demoMessage,
                onPickCsv = {
                    csvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                },
                onClearLog = { scope.launch { container.captureLog.clear() } },
                onLoadDemo = {
                    scope.launch {
                        val n = container.loadDemoData()
                        demoMessage = if (n > 0) "已载入 $n 笔，去记账页看看吧～" else "示例账本已经在里面了"
                    }
                },
                onClearAll = {
                    scope.launch {
                        container.clearAllData()
                        summary = null
                        demoMessage = "已清空，从零开始～"
                    }
                },
            )
            MerchantSwitchSection(onOpenMerchant)
            ReplayOnboardingSection { scope.launch { com.xiaopiao.app.ui.onboarding.OnboardingPrefs(context.applicationContext).reset() } }
            AboutSection { privacyOpen = true }
        }
    }
}
