package com.xiaopiao.app.ui.circle

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
fun CircleHome(@Suppress("UNUSED_PARAMETER") container: AppContainer) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("圈子施工中", style = MaterialTheme.typography.titleMedium, color = XpColors.InkSecondary)
    }
}
