package com.xiaopiao.app.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.ui.components.LargeTitle

@Composable
fun TodayScreen(container: AppContainer) {
    Column { LargeTitle("今日小票") }
}
