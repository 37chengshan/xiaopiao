package com.xiaopiao.app.ui.insights

import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScrollNone
import androidx.compose.animation.core.tween
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
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.work.ReminderIntents
import com.xiaopiao.app.work.ReminderScheduler

private enum class InsightsRoute(val label: String) { AWARDS("周报"), SUBSCRIPTIONS("订阅"), REGRET("后悔") }

/** 洞察 tab: one compact bar, a segmented switch between 周报 / 订阅 / 后悔 (no hub, no large title). */
@Composable
fun InsightsScreen(container: AppContainer, open: OpenRequest? = null, onOpenConsumed: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ReminderScheduler.ensureScheduled(context) }

    val real by container.ledger.observeAll().collectAsState(emptyList())
    // Demo is a property of the data, not a switch (2026-10-28: tt shut down the 示例小票 toggle).
    val demo = real.any { it.note == DemoSeed.NOTE }
    var route by rememberSaveable { mutableStateOf(InsightsRoute.AWARDS) }
    val now = remember { System.currentTimeMillis() }
    val sample = remember(now) { SampleData.generate(now) }
    val input = InsightsInput(real, now, demo)

    LaunchedEffect(open) {
        val target = when (open?.target) {
            ReminderIntents.OPEN_WEEKLY -> InsightsRoute.AWARDS
            ReminderIntents.OPEN_SUBSCRIPTIONS -> InsightsRoute.SUBSCRIPTIONS
            else -> return@LaunchedEffect
        }
        route = target
        onOpenConsumed()
    }

    NavScaffold(
        "洞察", NavScrollNone, demo = demo, onBack = onBack,
    ) { top ->
        Column(Modifier.fillMaxSize().padding(top = top)) {
            Box(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s)) {
                Segmented(InsightsRoute.entries.map { it.label }, route.ordinal, { route = InsightsRoute.entries[it] })
            }
            AnimatedContent(
                route,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "insightsRoute", modifier = Modifier.weight(1f),
            ) { r ->
                val noop = {}
                when (r) {
                    InsightsRoute.AWARDS -> AwardsPage(input, noop)
                    InsightsRoute.SUBSCRIPTIONS -> SubscriptionPage(input, noop)
                    InsightsRoute.REGRET -> RegretPage(input, noop)
                }
            }
        }
    }
}
