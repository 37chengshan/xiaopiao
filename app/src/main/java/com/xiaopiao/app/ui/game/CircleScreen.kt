package com.xiaopiao.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.merchant.MerchantStore
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** 圈子 tab: small circles (coming soon) and the warm-up 二选一 game / persona results. */
@Composable
fun CircleScreen(container: AppContainer) {
    val store = remember { MerchantStore.get(container.appContext) }
    val state by store.state.collectAsState()
    var playing by rememberSaveable { mutableStateOf(false) }
    var seed by rememberSaveable { mutableStateOf(0L) }
    BackHandler(playing) { playing = false }
    if (playing) {
        DuelGameScreen(
            experiment = null, seed = seed,
            onExperimentAnswer = {}, onFinished = { code -> store.update { it.finishGame(code) } },
            onExit = { playing = false },
        )
        return
    }
    NavScaffold("圈子", NavScrollNone) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = top, bottom = 120.dp)) {
            Column(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s)) {
                XpCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("已玩", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                            Text("${state.gamesPlayed} 局", style = MaterialTheme.typography.displaySmall, color = XpColors.Ink)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("徽章", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                            Text("${state.badgeCodes.size} 枚", style = MaterialTheme.typography.displaySmall, color = XpColors.Ink)
                        }
                        IconTile(Icons.Outlined.EmojiEvents, size = 44.dp, tint = XpColors.Warning, background = XpColors.WarningSoft)
                    }
                }
            }
            GroupedSection(dividerInset = 62.dp) {
                item {
                    GroupedRow(
                        "二选一大作战", subtitle = "来一局，看看你是哪种人", chevron = true,
                        leading = { IconTile(Icons.Outlined.SportsEsports, size = 34.dp, tint = XpColors.Info, background = XpColors.InfoSoft) },
                    ) { seed = System.currentTimeMillis(); playing = true }
                }
                item {
                    GroupedRow(
                        "小圈子", value = "即将上线",
                        leading = { IconTile(Icons.Outlined.Group, size = 34.dp, tint = XpColors.Positive, background = XpColors.PositiveSoft) },
                    )
                }
            }
        }
    }
}
