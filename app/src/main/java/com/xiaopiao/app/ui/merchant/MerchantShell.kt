package com.xiaopiao.app.ui.merchant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.merchant.ExperimentEngine
import com.xiaopiao.app.merchant.MerchantStore
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.ui.components.NavBarItem
import com.xiaopiao.app.ui.components.NavBarSpec
import com.xiaopiao.app.ui.components.XpNavBar
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpPaletteOverride
import com.xiaopiao.app.ui.theme.merchantPalette
import com.xiaopiao.app.ui.theme.paletteFor

/** The four destinations of 小店版. The middle nav slot is the record action, not a tab. */
enum class MerchantTab(val label: String, val icon: ImageVector) {
    Today("今日", Icons.Outlined.Today),
    Revenue("营收", Icons.Outlined.PointOfSale),
    Analysis("分析", Icons.Outlined.Insights),
    Mine("我的", Icons.Outlined.Person),
}

/** Pages above the tab bar: the receipt, and the existing customer-experiment flow. */
enum class MerchantSub(val order: Int, val title: String) {
    Receipt(-1, "经营小票"),
    Crowd(0, "人群洞察"),
    Setup(1, "发起实验"),
    Results(2, "看结果"),
    Decide(3, "你来决定"),
    Game(4, "二选一大作战"),
}

/** Which half of the 小店 bar is open and which icon is lit — pure, so the mapping is unit-tested. */
object MerchantNav {
    /** 我的 and every experiment step sit on the social half; 今日 / 营收 / 分析 on the ledger half. */
    fun isSocial(tab: MerchantTab, sub: MerchantSub?): Boolean = tab == MerchantTab.Mine || sub != null

    fun selectedId(tab: MerchantTab, sub: MerchantSub?): String = when (sub) {
        MerchantSub.Crowd -> "crowd"
        MerchantSub.Setup, MerchantSub.Results, MerchantSub.Decide -> "lab"
        else -> tab.name.lowercase()
    }

    /** 实验 opens the results once there is an experiment, the setup before that. */
    fun labStep(hasExperiment: Boolean): MerchantSub = if (hasExperiment) MerchantSub.Results else MerchantSub.Setup

    fun tabFor(id: String): MerchantTab? = MerchantTab.entries.firstOrNull { it.name.lowercase() == id }
}

/**
 * 小店版 bar, same component and the same five-icon rule as the user edition: `今日 · 营收 · ⊕ · 分析 | 我的`, and once
 * 我的 is open `经营 · ⊕ | 人群 · 我的 · 实验` (the experiment flow's steps stay inside 人群 / 实验).
 */
private val MerchantNavSpec = NavBarSpec(
    a = NavBarItem("today", "今日", Icons.Outlined.Today),
    b = NavBarItem("revenue", "营收", Icons.Outlined.PointOfSale),
    c = NavBarItem("analysis", "分析", Icons.Outlined.Insights),
    fold = NavBarItem("fold", "经营", Icons.Outlined.Storefront),
    entry = NavBarItem("mine", "我的", Icons.Outlined.Person),
    sa = NavBarItem("crowd", "人群", Icons.Outlined.Groups),
    sb = NavBarItem("lab", "实验", Icons.Outlined.Science),
    plusLabel = "记营收", plusDescription = "记营收",
)

/**
 * 小店版 is a whole-product mode (docs/APP-DESIGN.md §6): its own palette, its own navigation,
 * its own home. Entered from 我的 in the user edition, left through 我的 → 切换到用户版.
 *
 * Jobs, in nav order: 今日 (how did today go) · 营收 (the book + CSV import) · 分析 (local sentences)
 * · 我的 (experiments, data notes, way back). 小票 opens from the 今日/营收 header.
 */
@Composable
fun MerchantShell(container: AppContainer, onExit: () -> Unit, onOpenSettings: () -> Unit = {}) {
    val palette = remember(XpColors.style, XpColors.isDark) { merchantPalette(paletteFor(XpColors.style, XpColors.isDark)) }
    XpPaletteOverride(palette) {
        val store = remember { MerchantStore.get(container.appContext) }
        val state by store.state.collectAsState()
        val today = remember { RevenueBook.startOfDay(System.currentTimeMillis()) }
        var tab by rememberSaveable { mutableStateOf(MerchantTab.Today) }
        var lastLedgerTab by rememberSaveable { mutableStateOf(MerchantTab.Today) }
        var sub by rememberSaveable { mutableStateOf<MerchantSub?>(null) }
        var gameReturn by rememberSaveable { mutableStateOf(MerchantSub.Results) }
        var receiptDay by rememberSaveable { mutableStateOf(today) }
        var recordOpen by remember { mutableStateOf(false) }
        var recordDay by remember { mutableStateOf(today) }
        var csvOpen by remember { mutableStateOf(false) }
        var privacyOpen by remember { mutableStateOf(false) }

        val experiment = state.experiment
        androidx.compose.runtime.SideEffect { if (tab != MerchantTab.Mine) lastLedgerTab = tab }
        val result = remember(experiment, store.crowd) { experiment?.let { ExperimentEngine.result(it, store.crowd) } }

        fun openReceipt(day: Long) { receiptDay = day; sub = MerchantSub.Receipt }
        fun openRecord(day: Long) { recordDay = day; recordOpen = true }
        fun openSub(target: MerchantSub) {
            if (target == MerchantSub.Game) {
                gameReturn = if (sub == MerchantSub.Game || sub == null) MerchantSub.Results else sub!!
                sub = MerchantSub.Game
            } else {
                sub = target
            }
        }

        BackHandler {
            when {
                sub == MerchantSub.Game -> sub = gameReturn
                sub != null -> sub = null
                tab != MerchantTab.Today -> tab = MerchantTab.Today
                else -> onExit()
            }
        }

        Column(Modifier.fillMaxSize().background(XpColors.Canvas)) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .then(if (sub == MerchantSub.Receipt) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier),
            ) {
                if (sub == null) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                        label = "merchant-tab",
                    ) { current ->
                        when (current) {
                            MerchantTab.Today -> MerchantTodayScreen(
                                entries = state.revenue,
                                today = today,
                                onRecordToday = { openRecord(today) },
                                onEditDay = ::openRecord,
                                onOpenReceipt = ::openReceipt,
                                onOpenRevenue = { tab = MerchantTab.Revenue },
                                onOpenSettings = onOpenSettings,
                            )
                            MerchantTab.Revenue -> MerchantRevenueScreen(
                                entries = state.revenue,
                                today = today,
                                onImportCsv = { csvOpen = true },
                                onRecord = { openRecord(today) },
                                onEditDay = ::openRecord,
                                onOpenReceipt = ::openReceipt,
                                onOpenSettings = onOpenSettings,
                            )
                            MerchantTab.Analysis -> MerchantAnalysisScreen(
                                entries = state.revenue,
                                today = today,
                                onRecord = { openRecord(today) },
                                onOpenCrowd = { sub = MerchantSub.Crowd },
                                onOpenRevenue = { tab = MerchantTab.Revenue },
                                onOpenSettings = onOpenSettings,
                            )
                            MerchantTab.Mine -> MerchantMineScreen(
                                state = state,
                                result = result,
                                onOpenSub = ::openSub,
                                onPrivacy = { privacyOpen = true },
                                onExit = onExit,
                                onOpenSettings = onOpenSettings,
                            )
                        }
                    }
                } else {
                    when (sub) {
                        MerchantSub.Receipt -> MerchantReceiptScreen(
                            entries = state.revenue,
                            today = today,
                            initialDay = receiptDay,
                            onBack = { sub = null },
                            onRecordDay = ::openRecord,
                        )
                        else -> MerchantExperimentScreen(
                            sub = sub!!,
                            store = store,
                            state = state,
                            result = result,
                            onNav = ::openSub,
                            onClose = { sub = null },
                            onPrivacy = { privacyOpen = true },
                        )
                    }
                }
            }
            // Receipt and the game own the screen; everywhere else the bar stays, with the flow's step lighting 人群 / 实验.
            if (sub != MerchantSub.Receipt && sub != MerchantSub.Game) {
                XpNavBar(
                    spec = MerchantNavSpec,
                    social = MerchantNav.isSocial(tab, sub),
                    selectedId = MerchantNav.selectedId(tab, sub),
                    onSelect = { id ->
                        when (id) {
                            "crowd" -> sub = MerchantSub.Crowd
                            "lab" -> sub = MerchantNav.labStep(experiment != null)
                            else -> MerchantNav.tabFor(id)?.let { tab = it; sub = null }
                        }
                    },
                    onFold = { tab = lastLedgerTab; sub = null },
                    onPlus = { openRecord(today) },
                )
            }
        }

        if (recordOpen) {
            val existing = RevenueBook.entryFor(state.revenue, recordDay)
            RevenueEntrySheet(
                dayStart = recordDay,
                existing = existing,
                onSave = { entry ->
                    store.update { it.recordRevenue(entry) }
                    recordOpen = false
                },
                onDelete = {
                    store.update { it.removeRevenue(recordDay) }
                    recordOpen = false
                },
                onDismiss = { recordOpen = false },
            )
        }
        if (csvOpen) {
            RevenueCsvSheet(
                entries = state.revenue,
                today = today,
                onImport = { rows ->
                    store.update { it.importRevenue(rows) }
                    csvOpen = false
                },
                onDismiss = { csvOpen = false },
            )
        }
        if (privacyOpen) PrivacySheet { privacyOpen = false }
    }
}
