package com.xiaopiao.app.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import com.xiaopiao.app.data.CaptureLogEntry
import com.xiaopiao.app.importer.ImportSummary
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.motion.XpReveal
import com.xiaopiao.app.ui.theme.ColorTheme
import com.xiaopiao.app.ui.theme.ThemeMode
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr

private val IconInset = 58.dp

/**
 * Rows that open a sub-page. The sub-page is where the actual controls live, so the root list stays a
 * one-screen overview (Apple settings rhythm) and the push has something to reveal.
 */
@Composable
fun AppearanceNavSection(value: String, onOpen: () -> Unit) {
    GroupedSection(header = com.xiaopiao.app.ui.UiLabels.title("外观"), dividerInset = IconInset) {
        item {
            GroupedRow(
                title = com.xiaopiao.app.ui.UiLabels.title("主题与配色"),
                subtitle = com.xiaopiao.app.ui.UiLabels.title("跟随系统、浅色、深色；五套主题配色"),
                value = value,
                leading = { IconTile(Icons.Outlined.Palette, tint = XpColors.Info, background = XpColors.InfoSoft) },
                chevron = true,
                onClick = onOpen,
            )
        }
    }
}

@Composable
fun DataNavSection(onOpen: () -> Unit) {
    GroupedSection(header = com.xiaopiao.app.ui.UiLabels.title("数据"), dividerInset = IconInset) {
        item {
            GroupedRow(
                title = com.xiaopiao.app.ui.UiLabels.title("数据与隐私"),
                subtitle = com.xiaopiao.app.ui.UiLabels.title("导入账单、通知记录、清空数据"),
                leading = { IconTile(Icons.Outlined.Storage, tint = XpColors.Positive, background = XpColors.PositiveSoft) },
                chevron = true,
                onClick = onOpen,
            )
        }
    }
}

@Composable
fun AboutNavSection(onOpen: () -> Unit) {
    val account = com.xiaopiao.app.ui.account.LocalAccount.current
    var feedback by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    GroupedSection(dividerInset = IconInset) {
        item {
            GroupedRow(
                title = tr("反馈问题", "Report a problem"),
                subtitle = tr("一键发给开发者，发送前可以看到全部内容", "Sent to the developer; you see everything before it goes"),
                leading = { IconTile(Icons.Outlined.Feedback) },
                chevron = true,
                onClick = { feedback = true },
            )
        }
        item {
            GroupedRow(
                title = com.xiaopiao.app.ui.UiLabels.title("关于"),
                value = com.xiaopiao.app.ui.UiLabels.title("隐私说明 · 版本"),
                leading = { IconTile(Icons.Outlined.Info) },
                chevron = true,
                onClick = onOpen,
            )
        }
    }
    if (feedback && account != null) FeedbackSheet(account, attached = null) { feedback = false }
}

/** 外观 sub-page: one thing to do, reached with a real push instead of a scroll. */
@Composable
fun AppearancePage(
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    theme: ColorTheme,
    onTheme: (ColorTheme) -> Unit,
    onBack: () -> Unit,
) {
    val scroll = rememberScrollState()
    NavScaffold(
        com.xiaopiao.app.ui.UiLabels.title("外观"), rememberNavScroll(scroll), onBack = onBack,
        backLabel = tr("设置", "Settings"),
    ) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = 120.dp)) {
            XpReveal(index = 0) { AppearanceSection(mode, onMode) }
            XpReveal(index = 1) { ColorThemeSection(theme, onTheme) }
            XpReveal(index = 2) {
                Text(
                    tr(
                        "主题只换颜色，不改布局。深色跟随系统时可以手动覆盖。",
                        "A theme only changes colour, never layout. With dark set to system you can still override it.",
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(horizontal = XpSpace.xl, vertical = XpSpace.xs),
                )
            }
        }
    }
}

/** 数据与隐私 sub-page: import, capture log and the destructive controls, all behind one push. */
@Composable
fun DataPage(
    importing: Boolean,
    summary: ImportSummary?,
    log: List<CaptureLogEntry>,
    demoMessage: String?,
    onPickCsv: () -> Unit,
    onClearLog: () -> Unit,
    onLoadDemo: () -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    val scroll = rememberScrollState()
    NavScaffold(
        com.xiaopiao.app.ui.UiLabels.title("数据与隐私"), rememberNavScroll(scroll), onBack = onBack,
        backLabel = tr("设置", "Settings"),
    ) { top ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(XpSpace.xs),
        ) {
            XpReveal(index = 0) {
                DataSection(
                    importing = importing,
                    summary = summary,
                    log = log,
                    demoMessage = demoMessage,
                    onPickCsv = onPickCsv,
                    onClearLog = onClearLog,
                    onLoadDemo = onLoadDemo,
                    onClearAll = onClearAll,
                )
            }
            XpReveal(index = 1) {
                Text(
                    tr(
                        "账单原文只在这台手机上解析；清空后不留副本。",
                        "Bill text is parsed on this phone only; clearing leaves no copy behind.",
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(horizontal = XpSpace.xl, vertical = XpSpace.s),
                )
            }
        }
    }
}

/** 关于 sub-page. */
@Composable
fun AboutPage(onPrivacy: () -> Unit, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    NavScaffold(
        com.xiaopiao.app.ui.UiLabels.title("关于"), rememberNavScroll(scroll), onBack = onBack,
        backLabel = tr("设置", "Settings"),
    ) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = 120.dp)) {
            XpReveal(index = 0) { AboutSection(onPrivacy) }
            XpReveal(index = 1) {
                Text(
                    tr(
                        "数字由这台手机算出来；AI 只负责把事实写成一句话。",
                        "Every number is computed on this phone; the AI only puts the facts into a sentence.",
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(horizontal = XpSpace.xl, vertical = XpSpace.s),
                )
            }
        }
    }
}
