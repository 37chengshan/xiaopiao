package com.xiaopiao.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.settings.NotificationAccess
import com.xiaopiao.app.ui.theme.ThemeMode
import com.xiaopiao.app.ui.theme.ThemePrefs
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.today.ReceiptSoundPrefs
import kotlinx.coroutines.launch

private const val PAGES = 4

/**
 * First-run guide: 4 pages, one title and at most one line each, skippable. Full-screen dialog so it also
 * covers the bottom bar. Permission asks come after the value pitch, and 「先手动记」 is always allowed.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(XpColors.Canvas).statusBarsPadding().navigationBarsPadding().padding(horizontal = XpSpace.l)) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (page < PAGES - 1) Box(Modifier.height(48.dp).pressableClick(onClick = onFinish).padding(horizontal = XpSpace.m), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.onb_skip), style = MaterialTheme.typography.labelLarge, color = XpColors.InkSecondary)
                }
            }
            AnimatedContent(
                targetState = page, modifier = Modifier.weight(1f).fillMaxWidth(), label = "onbPage",
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(280)) { it / 4 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(120)))
                },
            ) { p -> Page(p) }
            Dots(page)
            Spacer(Modifier.height(XpSpace.l))
            XpButton(
                stringResource(if (page == PAGES - 1) R.string.onb_start else R.string.onb_next),
                { if (page == PAGES - 1) onFinish() else page++ }, Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(XpSpace.l))
        }
    }
}

@Composable
private fun Page(p: Int) {
    val (icon, title, body) = when (p) {
        0 -> Triple(Icons.Outlined.ReceiptLong, R.string.onb_1_title, R.string.onb_1_body)
        1 -> Triple(Icons.Outlined.NotificationsActive, R.string.onb_2_title, R.string.onb_2_body)
        2 -> Triple(Icons.Outlined.Lock, R.string.onb_3_title, R.string.onb_3_body)
        else -> Triple(Icons.Outlined.Tune, R.string.onb_4_title, R.string.onb_4_body)
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        IconBadge(icon)
        Spacer(Modifier.height(XpSpace.xl))
        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, color = XpColors.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(XpSpace.s))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(XpSpace.xl))
        when (p) {
            1 -> PermissionActions()
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                Chip(R.string.onb_3_chip_local); Chip(R.string.onb_3_chip_opt); Chip(R.string.onb_3_chip_revoke)
            }
            3 -> LookAndFeel()
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)).background(XpColors.AccentSoft), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(44.dp), tint = XpColors.Accent)
    }
}

@Composable
private fun Chip(res: Int) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(XpColors.Fill).padding(horizontal = XpSpace.m, vertical = XpSpace.s)) {
        Text(stringResource(res), style = MaterialTheme.typography.labelLarge, color = XpColors.Ink)
    }
}

@Composable
private fun PermissionActions() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
        XpButton(stringResource(R.string.onb_2_allow), { NotificationAccess.openListenerSettings(context) }, Modifier.fillMaxWidth(), style = XpButtonStyle.Secondary)
        Text(
            stringResource(R.string.onb_2_manual), style = MaterialTheme.typography.labelLarge, color = XpColors.InkSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = XpSpace.s),
        )
    }
}

@Composable
private fun LookAndFeel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePrefs = remember { ThemePrefs(context) }
    val feedback = remember { ReceiptSoundPrefs(context) }
    val mode by themePrefs.observeMode().collectAsState(initial = ThemeMode.System)
    val sound by feedback.observeEnabled().collectAsState(initial = true)
    val vibration by feedback.observeVibration().collectAsState(initial = true)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
        Segmented(
            listOf(stringResource(R.string.onb_theme_system), stringResource(R.string.onb_theme_light), stringResource(R.string.onb_theme_dark)),
            mode.ordinal, { scope.launch { themePrefs.setMode(ThemeMode.entries[it]) } },
        )
        ToggleRow(stringResource(R.string.onb_sound), sound) { scope.launch { feedback.setEnabled(it) } }
        ToggleRow(stringResource(R.string.onb_vibration), vibration) { scope.launch { feedback.setVibration(it) } }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink)
        Switch(checked, onChange)
    }
}

@Composable
private fun Dots(page: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(PAGES) { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(if (i == page) 20.dp else 8.dp, 8.dp).clip(CircleShape)
                    .background(if (i == page) XpColors.Ink else XpColors.Hairline),
            )
        }
    }
}
