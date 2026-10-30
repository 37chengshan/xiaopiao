package com.xiaopiao.app.ui.capture

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.capture.CaptureStatus
import com.xiaopiao.app.R
import com.xiaopiao.app.capture.PaymentApps
import com.xiaopiao.app.capture.PaymentListenerService
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.settings.NotificationAccess
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The three things automatic capture needs, in the order they fail on Chinese ROMs: the listener permission,
 * the background switches that keep it bound, and the payment apps' own notifications. Android only lets us
 * check the first one for sure, so the other two say how to do it instead of pretending to know.
 * Used as an onboarding page and as the sheet behind the ledger's nudge.
 */
@Composable
fun CaptureSetupSteps(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val en = LocalConfiguration.current.locales[0].language == "en"
    var listenerOn by remember { mutableStateOf(NotificationAccess.listenerEnabled(context)) }
    var batteryFree by remember { mutableStateOf(ignoresBatteryOptimizations(context)) }
    val lastSeenAt by CaptureStatus.lastSeenAt.collectAsStateWithLifecycle()
    val lastPackage by CaptureStatus.lastPackage.collectAsStateWithLifecycle()
    // The user comes back from system settings: re-check.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        listenerOn = NotificationAccess.listenerEnabled(context)
        batteryFree = ignoresBatteryOptimizations(context)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(XpSpace.l)) {
        SetupStep(
            number = 1,
            done = listenerOn,
            title = if (en) "Allow notification access" else "打开「通知使用权」",
            body = if (listenerOn) (if (en) "On. Payment notifications are read on this phone only." else "已打开。通知只在这台手机上解析。")
            else if (en) "Find Daily Slip in the list and switch it on." else "在列表里找到「今日小票」，打开开关。",
            action = if (listenerOn) null else (if (en) "Open" else "去开启"),
            onAction = { NotificationAccess.openListenerSettings(context) },
        )
        SetupStep(
            number = 2,
            done = batteryFree,
            title = if (en) "Let it stay in the background" else "允许它在后台常驻",
            body = backgroundHint(en),
            action = if (batteryFree) null else (if (en) "Settings" else "去设置"),
            onAction = { NotificationAccess.openAppSettings(context) },
        )
        SetupStep(
            number = 3,
            done = lastSeenAt > 0L,
            title = if (en) "Keep WeChat / Alipay notifications on" else "微信、支付宝的通知别关",
            body = if (lastSeenAt > 0L) {
                val app = PaymentApps.label(lastPackage.orEmpty())
                if (en) "Working: already read a notification from $app." else "读取正常：已经收到过$app 的通知。"
            } else if (en) {
                "WeChat: Me → Settings → Notifications, turn on new-message alerts and message details. Alipay: Me → Settings → Notifications."
            } else {
                "微信：我 → 设置 → 消息通知，打开「新消息通知」和「通知显示消息详情」。支付宝：我的 → 设置 → 消息通知。"
            },
        )
    }
}

@Composable
private fun SetupStep(
    number: Int,
    done: Boolean,
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) XpColors.PositiveSoft else XpColors.Fill),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Outlined.Check, contentDescription = null, tint = XpColors.Positive, modifier = Modifier.size(16.dp))
            else Text("$number", style = MaterialTheme.typography.labelLarge, color = XpColors.Ink)
        }
        Spacer(Modifier.width(XpSpace.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = XpColors.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        }
        if (action != null) {
            Spacer(Modifier.width(XpSpace.s))
            XpButton(action, onAction, style = XpButtonStyle.Secondary, size = XpButtonSize.Small)
        }
    }
}

/**
 * A slim row above the ledger while capture is off or unbound; tapping it opens [CaptureSetupSteps].
 * Waits a moment before judging "unbound": the system binds the listener a beat after the app starts.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CaptureNudge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val en = LocalConfiguration.current.locales[0].language == "en"
    var listenerOn by remember { mutableStateOf(NotificationAccess.listenerEnabled(context)) }
    val connected by CaptureStatus.connected.collectAsStateWithLifecycle()
    var settled by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    val reconnect = rememberReconnect()
    LaunchedEffect(Unit) { delay(2_500); settled = true }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { listenerOn = NotificationAccess.listenerEnabled(context) }

    AnimatedVisibility(
        visible = !listenerOn || (settled && !connected),
        modifier = modifier,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = XpSpace.m)
                .clip(RoundedCornerShape(XpRadius.m))
                .background(XpColors.WarningSoft)
                .pressableClick { sheetOpen = true }
                .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = XpColors.Warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(XpSpace.s))
            Text(
                when {
                    !listenerOn -> if (en) "Auto-capture is off · 3 steps to turn on" else "自动记账还没开 · 3 步开启"
                    else -> if (en) "Auto-capture lost its connection · fix it" else "自动记账断开了 · 去修复"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = XpColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = XpColors.InkSecondary, modifier = Modifier.size(18.dp))
        }
    }

    if (sheetOpen) {
        XpSheet(onDismiss = { sheetOpen = false }) {
            Column(Modifier.padding(horizontal = XpSpace.xl).padding(bottom = XpSpace.xl)) {
                Text(if (en) "Turn on auto-capture" else "开启自动记账", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
                Spacer(Modifier.size(XpSpace.s))
                Text(
                    if (en) "Pay with WeChat or Alipay and the bill writes itself." else "用微信、支付宝付完款，账自己就记上了。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XpColors.InkSecondary,
                )
                Spacer(Modifier.size(XpSpace.xl))
                CaptureSetupSteps()
                if (listenerOn && !connected) {
                    Spacer(Modifier.size(XpSpace.l))
                    if (reconnect.phase == ReconnectPhase.Failed) {
                        Text(
                            stringResource(R.string.capture_reconnect_failed_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = XpColors.Negative,
                            modifier = Modifier.padding(bottom = XpSpace.s),
                        )
                    }
                    XpButton(
                        stringResource(if (reconnect.phase == ReconnectPhase.Trying) R.string.capture_reconnect_trying else R.string.capture_reconnect),
                        reconnect::run,
                        Modifier.fillMaxWidth(),
                        enabled = reconnect.phase != ReconnectPhase.Trying,
                    )
                }
            }
        }
    }
}

enum class ReconnectPhase { Idle, Trying, Connected, Failed }

/**
 * 「重新连接」 with a result (docs/APP-DESIGN.md §7.7.1): asks the system to rebind, then waits up to
 * [ReconnectTimeoutMs] for [CaptureStatus.connected]. The caller shows Trying / Failed; Connected shows itself
 * because the status flips to 已连接.
 */
@Stable
class ReconnectController internal constructor(private val context: Context, private val scope: CoroutineScope) {
    var phase by mutableStateOf(ReconnectPhase.Idle)
        private set

    fun run() {
        if (phase == ReconnectPhase.Trying) return
        phase = ReconnectPhase.Trying
        PaymentListenerService.reconnect(context)
        scope.launch {
            val ok = withTimeoutOrNull(ReconnectTimeoutMs) { CaptureStatus.connected.first { it } } != null
            phase = if (ok) ReconnectPhase.Connected else ReconnectPhase.Failed
        }
    }
}

private const val ReconnectTimeoutMs = 6_000L

@Composable
fun rememberReconnect(): ReconnectController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { ReconnectController(context.applicationContext, scope) }
}

private fun ignoresBatteryOptimizations(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

/** Each ROM hides the background switches somewhere else; name the path for the common ones. */
private fun backgroundHint(en: Boolean): String {
    if (en) return "Allow auto-start and unrestricted battery for Daily Slip, or the system may stop reading."
    return when (Build.MANUFACTURER.lowercase()) {
        "vivo", "iqoo" -> "i 管家 → 应用管理 → 今日小票，打开「自启动」和「允许后台高耗电」。"
        "xiaomi", "redmi" -> "应用信息里打开「自启动」，省电策略选「无限制」。"
        "oppo", "oneplus", "realme" -> "应用信息 → 耗电管理，允许「自启动」和「后台运行」。"
        "huawei", "honor" -> "设置 → 应用启动管理 → 今日小票，改为手动管理并全部打开。"
        else -> "应用信息 → 电池，选「不限制」，否则系统可能停掉读取。"
    }
}
