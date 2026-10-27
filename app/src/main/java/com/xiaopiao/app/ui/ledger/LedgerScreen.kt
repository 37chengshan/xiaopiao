package com.xiaopiao.app.ui.ledger

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.ui.components.LargeTitle

@Composable
fun LedgerScreen(container: AppContainer) {
    Column { LargeTitle("账本") }
}
