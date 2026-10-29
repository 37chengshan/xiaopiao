package com.xiaopiao.app.ui.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.xiaopiao.app.ui.components.Grabber
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.NavTextAction
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardStore by preferencesDataStore(name = "onboarding_prefs")

/** First-run tour flag (docs/APP-DESIGN.md §8). */
class OnboardingPrefs(private val context: Context) {
    private val doneKey = booleanPreferencesKey("done")

    fun observeDone(): Flow<Boolean> = context.onboardStore.data.map { it[doneKey] ?: false }

    suspend fun setDone() { context.onboardStore.edit { it[doneKey] = true } }

    suspend fun reset() { context.onboardStore.edit { it[doneKey] = false } }
}

/** Three pages: what it is, the two gestures, the data promise. Skippable, re-openable from 我的. */
@Composable
fun OnboardingHost(onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = 3
    // Same top bar as every other page: nothing to name (the title belongs to the story), 跳过 in the right capsule.
    NavScaffold(
        title = "",
        scroll = NavScrollNone,
        trailing = { NavTextAction("跳过", onDone, tint = XpColors.InkSecondary) },
    ) { top ->
        Column(
            Modifier.fillMaxSize().padding(top = top).navigationBarsPadding()
                .padding(horizontal = XpSpace.xl),
        ) {
            val reduced = com.xiaopiao.app.ui.components.rememberReducedMotion()
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    // Reduced motion: dissolve only, no travel.
                    if (reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                    else (fadeIn(tween(180)) + slideInHorizontally { it / 8 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally { -it / 8 })
                },
                label = "onboarding",
            ) { p ->
                when (p) {
                    0 -> PagePaper()
                    1 -> PageGestures()
                    else -> PagePrivacy()
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = XpSpace.m), horizontalArrangement = Arrangement.Center) {
                repeat(pages) { i ->
                    val selectedDot = i == page
                    val size by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (selectedDot) 8.dp else 6.dp,
                        animationSpec = com.xiaopiao.app.ui.theme.XpMotion.snappy(),
                        label = "dotSize$i",
                    )
                    val dotColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selectedDot) XpColors.Ink else XpColors.InkTertiary.copy(alpha = 0.4f),
                        animationSpec = tween(180, easing = com.xiaopiao.app.ui.theme.XpEase.out),
                        label = "dotColor$i",
                    )
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(size)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
            XpButton(
                if (page == pages - 1) "开始记账" else "继续",
                { if (page == pages - 1) onDone() else page++ },
                Modifier.fillMaxWidth().padding(bottom = XpSpace.l),
            )
        }
    }
}

@Composable
private fun PageHead(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = XpSpace.xxl)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = XpColors.Ink)
        Spacer(Modifier.height(XpSpace.s))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = XpColors.InkSecondary)
    }
}

@Composable
private fun PagePaper() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PageHead("每天一张\n会说话的小票", "记账只是顺手，晚上撕下这张纸才是正事。")
        Spacer(Modifier.height(XpSpace.xl))
        // The receipt itself is the illustration: paper, lines, a stamp.
        Column(
            Modifier.width(188.dp).clip(RoundedCornerShape(6.dp)).background(XpColors.Paper).padding(XpSpace.l),
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            Text("今日小票", style = MaterialTheme.typography.titleSmall, color = XpColors.PaperInk, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            repeat(3) { i ->
                Box(Modifier.fillMaxWidth(if (i == 2) 0.7f else 1f).height(2.dp).background(XpColors.PaperInk.copy(alpha = 0.18f)))
            }
            Spacer(Modifier.height(XpSpace.s))
            Box(
                Modifier.size(56.dp).align(Alignment.CenterHorizontally).clip(CircleShape)
                    .background(XpColors.Stamp.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text("已记", style = MaterialTheme.typography.titleMedium, color = XpColors.Stamp) }
        }
    }
}

@Composable
private fun PageGestures() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        PageHead("手势比按钮快", "不用找按钮。按住不放，比点来点去快。")
        Spacer(Modifier.height(XpSpace.xl))
        GestureRow(Icons.Outlined.ReceiptLong, "长按右上角的小票", "约 1.5 秒，震一下就开始打印", index = 0)
        GestureRow(Icons.Outlined.CallMade, "长按中间的「＋」", "向左展开说话，松手记账，上滑取消", index = 1)
        GestureRow(Icons.Outlined.TouchApp, "轻点「＋」", "打开手动表单，键盘输入一样快", index = 2)
    }
}

@Composable
private fun GestureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    index: Int = 0,
) {
    com.xiaopiao.app.ui.motion.XpReveal(index = index + 1) {
    Row(Modifier.fillMaxWidth().padding(vertical = XpSpace.m), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Fill),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = XpColors.Ink, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(XpSpace.m))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = XpColors.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        }
    }
    }
}

@Composable
private fun PagePrivacy() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        PageHead("账是私事", "账单原文、截图、语音都只在这台手机上处理。")
        Spacer(Modifier.height(XpSpace.xl))
        GestureRow(Icons.Outlined.Lock, "原文不出手机", "只保存结构化字段，云端拿不到截图", index = 0)
        GestureRow(Icons.Outlined.Lock, "贡献默认关闭", "要分享聚合数据，得你自己打开", index = 1)
        GestureRow(Icons.Outlined.Lock, "随时撤回", "关掉开关，历史贡献一并失效", index = 2)
    }
}
