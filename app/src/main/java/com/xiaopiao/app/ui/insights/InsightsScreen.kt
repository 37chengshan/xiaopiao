package com.xiaopiao.app.ui.insights

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.XpSwitch
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.GroupedRow
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.Icons
import com.xiaopiao.app.OpenRequest
import com.xiaopiao.app.insights.SampleData
import com.xiaopiao.app.ui.components.LargeTitle
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.work.ReminderIntents
import com.xiaopiao.app.work.ReminderScheduler

private enum class InsightsRoute { HUB, AWARDS, SUBSCRIPTIONS, REGRET }

/** 洞察 tab: a hub of cards that navigate (internally) to awards, subscription detective and regret simulator. */
@Composable
fun InsightsScreen(container: AppContainer, open: OpenRequest? = null, onOpenConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ReminderScheduler.ensureScheduled(context) }

    val real by container.ledger.observeAll().collectAsState(emptyList())
    var demo by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(InsightsRoute.HUB) }
    val now = remember { System.currentTimeMillis() }
    val sample = remember(now) { SampleData.generate(now) }
    val input = InsightsInput(if (demo) sample else real, now, demo)

    LaunchedEffect(open) {
        val target = when (open?.target) {
            ReminderIntents.OPEN_WEEKLY -> InsightsRoute.AWARDS
            ReminderIntents.OPEN_SUBSCRIPTIONS -> InsightsRoute.SUBSCRIPTIONS
            else -> return@LaunchedEffect
        }
        route = target
        onOpenConsumed()
    }

    BackHandler(route != InsightsRoute.HUB) { route = InsightsRoute.HUB }
    AnimatedContent(
        route,
        transitionSpec = {
            val forward = targetState != InsightsRoute.HUB
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        label = "insightsRoute",
    ) { r ->
        val back = { route = InsightsRoute.HUB }
        when (r) {
            InsightsRoute.HUB -> Hub(input, onDemo = { demo = it }, onOpen = { route = it })
            InsightsRoute.AWARDS -> AwardsPage(input, back)
            InsightsRoute.SUBSCRIPTIONS -> SubscriptionPage(input, back)
            InsightsRoute.REGRET -> RegretPage(input, back)
        }
    }
}

@Composable
private fun Hub(input: InsightsInput, onDemo: (Boolean) -> Unit, onOpen: (InsightsRoute) -> Unit) {
    val scroll = rememberScrollState()
    NavScaffold("洞察", rememberNavScroll(scroll), demo = input.demo) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xxl)) {
            LargeTitle("洞察", "你的钱，悄悄在说什么")
            if (input.history.isEmpty()) {
                Column(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s)) {
                    XpCard {
                        Text("账本还空空的", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                        Text(
                            "记几笔后，头衔、订阅和模拟都会在这里长出来。想先看看效果？打开下面的演示数据开关。",
                            style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                            modifier = Modifier.padding(top = XpSpace.xs),
                        )
                    }
                }
            }
            GroupedSection(dividerInset = 62.dp) {
                item {
                    GroupedRow("周报颁奖典礼", subtitle = "领取本周头衔，看看花钱小报告", chevron = true,
                        leading = { IconTile(Icons.Outlined.EmojiEvents, size = 34.dp, selected = true) }) { onOpen(InsightsRoute.AWARDS) }
                }
                item {
                    GroupedRow("订阅侦探", subtitle = "揪出悄悄扣钱的自动续费，教你怎么关", chevron = true,
                        leading = { IconTile(Icons.Outlined.Search, size = 34.dp) }) { onOpen(InsightsRoute.SUBSCRIPTIONS) }
                }
                item {
                    GroupedRow("后悔模拟器", subtitle = "少花一点，能换多少顿火锅？", chevron = true,
                        leading = { IconTile(Icons.Outlined.AutoAwesome, size = 34.dp) }) { onOpen(InsightsRoute.REGRET) }
                }
            }
            GroupedSection(dividerInset = 62.dp, footer = "不会改动你的真实账本。") {
                item {
                    GroupedRow("小圈子", subtitle = "和 TA 一起对账、互颁头衔", value = "即将上线",
                        leading = { IconTile(Icons.Outlined.Group, size = 34.dp) })
                }
                item {
                    GroupedRow("用示例数据预览", value = null,
                        leading = { IconTile(Icons.Outlined.Science, size = 34.dp) },
                        trailing = { XpSwitch(input.demo, onDemo) }, onClick = { onDemo(!input.demo) })
                }
            }
        }
    }
}
