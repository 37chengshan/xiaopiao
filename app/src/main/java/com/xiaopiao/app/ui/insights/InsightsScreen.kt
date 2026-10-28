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
import com.xiaopiao.app.insights.SampleData
import com.xiaopiao.app.ui.components.LargeTitle
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.work.ReminderScheduler

private enum class InsightsRoute { HUB, AWARDS, SUBSCRIPTIONS, REGRET }

/** 洞察 tab: a hub of cards that navigate (internally) to awards, subscription detective and regret simulator. */
@Composable
fun InsightsScreen(container: AppContainer) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ReminderScheduler.ensureScheduled(context) }

    val real by container.ledger.observeAll().collectAsState(emptyList())
    var demo by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(InsightsRoute.HUB) }
    val now = remember { System.currentTimeMillis() }
    val sample = remember(now) { SampleData.generate(now) }
    val input = InsightsInput(if (demo) sample else real, now, demo)

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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = XpSpace.xxl)) {
        LargeTitle("洞察", "你的钱，悄悄在说什么")
        if (input.history.isEmpty()) {
            Column(Modifier.padding(horizontal = XpSpace.l)) {
                XpCard {
                    Text("🧾", style = MaterialTheme.typography.displayLarge)
                    Text("账本还空空的", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                    Text("记几笔后，头衔、订阅和模拟都会在这里长出来。想先看看效果？打开下面的演示数据开关。", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
                }
            }
        }
        Column(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
            HubCard("🏆", "周报颁奖典礼", "领取本周头衔，看看花钱小报告", XpColors.AccentSoft) { onOpen(InsightsRoute.AWARDS) }
            HubCard("🕵️", "订阅侦探", "揪出悄悄扣钱的自动续费，教你怎么关", Color3) { onOpen(InsightsRoute.SUBSCRIPTIONS) }
            HubCard("🔮", "后悔模拟器", "少花一点，能换多少顿火锅？", Color4) { onOpen(InsightsRoute.REGRET) }
            CircleCard()
            DemoToggle(input.demo, onDemo)
        }
    }
}

private val Color3 = androidx.compose.ui.graphics.Color(0xFFE6F2FF)
private val Color4 = androidx.compose.ui.graphics.Color(0xFFEFE8FF)

@Composable
private fun HubCard(emoji: String, title: String, desc: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).clip(RoundedCornerShape(24.dp)).background(XpColors.Card).padding(XpSpace.l),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(tint).padding(XpSpace.m)) {
            Text(emoji, style = MaterialTheme.typography.headlineLarge)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Text(desc, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = XpColors.InkTertiary)
    }
}

/** Placeholder only: nothing here is wired up yet. */
@Composable
private fun CircleCard() {
    XpCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            Text("👯 小圈子", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Pill("即将上线")
        }
        Text("和 TA 一起对账、互颁头衔", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(top = XpSpace.xs))
    }
}

@Composable
private fun DemoToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    XpCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
                    Text("用示例数据预览", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                    Pill(SampleData.LABEL)
                }
                Text("不会改动你的真实账本", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
            }
            Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = XpColors.Accent))
        }
    }
}
