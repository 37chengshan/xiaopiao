package com.xiaopiao.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.ui.theme.XpColors

@Composable
fun ChatScreen(
    @Suppress("UNUSED_PARAMETER") container: AppContainer,
    @Suppress("UNUSED_PARAMETER") onRecordVoice: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onRecordManual: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("对话页施工中", style = MaterialTheme.typography.titleMedium, color = XpColors.InkSecondary)
    }
}
