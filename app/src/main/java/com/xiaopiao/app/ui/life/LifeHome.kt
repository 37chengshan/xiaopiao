package com.xiaopiao.app.ui.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.tr

/**
 * 生活 tab (导航 v8, docs/LIFE-SPEC.md). Holds the slot in the bar and the swipe order until the page itself is
 * built; its look waits for tt to pick one of two visual directions.
 */
@Composable
fun LifeHome() {
    NavScaffold(tr("生活", "Life"), NavScrollNone, trailing = { AppTopActions() }) { top ->
        Box(Modifier.fillMaxSize().padding(top = top, start = 32.dp, end = 32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("吃 · 练 · 动", "Eat · Train · Move"), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                Text(
                    tr("饮食和运动记录正在做，很快上线", "Meal and workout logs are on the way"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = XpColors.InkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
