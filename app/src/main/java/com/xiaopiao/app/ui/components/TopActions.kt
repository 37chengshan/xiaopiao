package com.xiaopiao.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaopiao.app.ui.today.ReceiptStrip

/**
 * What the fixed top-bar slots do (docs/DESIGN.md §4.1). Provided once by the shell, so every root page
 * places the same [AppTopActions] / [LedgerSideLeading] instead of wiring its own gear.
 */
class TopBarHost(
    val openSettings: () -> Unit,
    val openWallet: () -> Unit = {},
    /** From 小票 / 统计 this jumps to 记账 and enters search there. */
    val openSearch: () -> Unit = {},
)

val LocalTopBarHost = staticCompositionLocalOf { TopBarHost(openSettings = {}) }

/**
 * Top-right of every root page: the resident receipt, then the gear. Same order, size and spacing everywhere.
 * [receipt] swaps the receipt slot for an edition's own button (小店版 passes its 经营小票); null = the resident strip.
 */
@Composable
fun RowScope.AppTopActions(receipt: (@Composable () -> Unit)? = null) {
    val host = LocalTopBarHost.current
    if (receipt != null) receipt() else ReceiptStrip()
    NavIconButton(Icons.Outlined.Settings, "设置", onClick = host.openSettings)
}

/** Top-left of the ledger-side root pages (记账 / 小票 / 统计): 卡包 + 搜索. */
@Composable
fun RowScope.LedgerSideLeading() {
    val host = LocalTopBarHost.current
    NavIconButton(Icons.Outlined.CreditCard, "卡包", onClick = host.openWallet)
    NavIconButton(Icons.Outlined.Search, "搜索", onClick = host.openSearch)
}
