package com.xiaopiao.app.ui.games

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.games.ChoiceLayout
import com.xiaopiao.app.domain.games.ChoiceQuestion
import com.xiaopiao.app.domain.games.GAME_DISCLAIMER
import com.xiaopiao.app.domain.games.GameAnswer
import com.xiaopiao.app.domain.games.GameDefinition
import com.xiaopiao.app.domain.games.GameOption
import com.xiaopiao.app.domain.games.GameOutcome
import com.xiaopiao.app.domain.games.GameQuestion
import com.xiaopiao.app.domain.games.GameScoring
import com.xiaopiao.app.domain.games.PriceQuestion
import com.xiaopiao.app.domain.games.RadarPoint
import com.xiaopiao.app.domain.games.RangeQuestion
import com.xiaopiao.app.domain.games.ScaleQuestion
import com.xiaopiao.app.domain.games.TaskQuestion
import com.xiaopiao.app.domain.games.TrueFalseQuestion
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One player for every [GameQuestion] variant (docs/APP-DESIGN.md §5.3). Progress is a hairline bar under
 * the compact header; questions cross-fade with a ≤220ms spring unless the system asked for reduced motion.
 * The result screen owns the only big label + the share text; both stay on-device.
 */
@Composable
fun GamePlayer(game: GameDefinition, onExit: () -> Unit) {
    val haptics = rememberXpHaptics()
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    var run by remember { mutableIntStateOf(0) }
    val deck = remember(game.id, run) { GameScoring.deckFor(game, System.nanoTime()) }
    var index by remember(game.id, run) { mutableIntStateOf(0) }
    val answers = remember(game.id, run) { mutableStateListOf<GameAnswer>() }
    var finished by remember(game.id, run) { mutableStateOf(false) }
    var feedback by remember(game.id, run, index) { mutableStateOf<Boolean?>(null) }

    fun advance() {
        if (index + 1 >= deck.size) {
            finished = true
            haptics.thud()
        } else {
            index += 1
        }
    }

    fun answer(question: GameQuestion, value: GameAnswer) {
        if (answers.size != index) return
        answers.add(value)
        when (question) {
            is PriceQuestion, is RangeQuestion, is TrueFalseQuestion -> {
                val correct = GameScoring.scoreFor(question, value) == 1
                feedback = correct
                if (correct) haptics.confirm() else haptics.reject()
            }
            is ScaleQuestion -> {
                haptics.select()
                // Leave the chosen circle visible for a beat before the next statement slides in.
                scope.launch { delay(180); advance() }
            }
            is TaskQuestion -> {
                if (GameScoring.scoreFor(question, value) == 1) haptics.confirm() else haptics.select()
                advance()
            }
            is ChoiceQuestion -> {
                haptics.select()
                advance()
            }
        }
    }

    BackHandler { onExit() }

    NavScaffold(title = game.title, scroll = NavScrollNone, onBack = onExit) { top ->
        Column(
            Modifier.fillMaxSize().padding(top = top),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProgressBar(
                fraction = if (finished) 1f else index.toFloat() / deck.size.coerceAtLeast(1),
                reduced = reduced,
            )
            if (finished) {
                ResultView(
                    game = game,
                    outcome = GameScoring.outcome(game, deck, answers),
                    reduced = reduced,
                    onReplay = { run += 1 },
                    onExit = onExit,
                    modifier = Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth(),
                )
            } else {
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { questionTransition(reduced) },
                    label = "gameQuestion",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { i ->
                    QuestionView(
                        question = deck[i],
                        index = i,
                        total = deck.size,
                        feedback = feedback,
                        onAnswer = { answer(deck[i], it) },
                        onContinue = {
                            feedback = null
                            advance()
                        },
                    )
                }
            }
        }
    }
}

private fun questionTransition(reduced: Boolean): ContentTransform {
    if (reduced) return EnterTransition.None togetherWith ExitTransition.None
    val enter = slideInHorizontally(spring(dampingRatio = 1f, stiffness = 700f)) { it / 8 } +
        fadeIn(tween(160))
    val leave = slideOutHorizontally(tween(140)) { -it / 10 } + fadeOut(tween(120))
    return enter togetherWith leave
}

@Composable
private fun ProgressBar(fraction: Float, reduced: Boolean) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = 1f, stiffness = 700f),
        label = "gameProgress",
    )
    Box(Modifier.fillMaxWidth().height(3.dp).background(XpColors.Hairline)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).background(XpColors.Accent))
    }
}

@Composable
private fun QuestionView(
    question: GameQuestion,
    index: Int,
    total: Int,
    feedback: Boolean?,
    onAnswer: (GameAnswer) -> Unit,
    onContinue: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
        ) {
            Text(
                "第 ${index + 1} / $total 题",
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
            )
            Text(
                question.prompt,
                style = MaterialTheme.typography.titleLarge,
                color = XpColors.Ink,
                modifier = Modifier.padding(top = XpSpace.s),
            )
            Spacer(Modifier.height(XpSpace.l))

            when (question) {
                is ChoiceQuestion -> ChoiceBlock(question, onAnswer)
                is PriceQuestion -> PriceBlock(question, locked = feedback != null, onAnswer)
                is RangeQuestion -> RangeBlock(question, locked = feedback != null, onAnswer)
                is TrueFalseQuestion -> TrueFalseBlock(question, locked = feedback != null, onAnswer)
                is TaskQuestion -> TaskBlock(question, onAnswer)
                is ScaleQuestion -> ScaleBlock(question, onAnswer)
            }

            if (feedback != null) {
                Spacer(Modifier.height(XpSpace.l))
                FeedbackPanel(question, feedback)
                Spacer(Modifier.height(XpSpace.l))
                XpButton(
                    text = if (index + 1 >= total) "看结果" else "下一题",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(XpSpace.xl))
            Text(
                GAME_DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(XpSpace.s))
        }
    }
}

@Composable
private fun ChoiceBlock(question: ChoiceQuestion, onAnswer: (GameAnswer) -> Unit) {
    if (question.hint.isNotBlank()) {
        Text(
            question.hint,
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(bottom = XpSpace.s),
        )
    }
    when (question.layout) {
        ChoiceLayout.Duel -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            question.options.forEach { option ->
                DuelCard(option, Modifier.weight(1f)) { onAnswer(GameAnswer.Pick(option.id)) }
            }
        }
        ChoiceLayout.List -> Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            question.options.forEach { option ->
                OptionRow(option) { onAnswer(GameAnswer.Pick(option.id)) }
            }
        }
    }
}

@Composable
private fun DuelCard(option: GameOption, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(172.dp)
            .clip(RoundedCornerShape(XpRadius.m))
            .background(XpColors.Surface2)
            .pressableClick(scaleDown = 0.96f, onClick = onClick)
            .padding(XpSpace.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(option.emoji, style = MaterialTheme.typography.displayMedium)
        Text(
            option.label,
            style = MaterialTheme.typography.titleSmall,
            color = XpColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = XpSpace.s),
        )
    }
}

@Composable
private fun OptionRow(option: GameOption, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.m))
            .background(XpColors.Fill)
            .pressableClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = XpSpace.l, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.emoji.isNotBlank()) {
            Text(
                option.emoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = XpSpace.m),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.titleSmall, color = XpColors.Ink)
            if (option.note.isNotBlank()) {
                Text(
                    option.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = XpColors.InkTertiary)
    }
}

@Composable
private fun ItemCard(emoji: String, item: String, hint: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.l))
            .background(XpColors.Card)
            .padding(XpSpace.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.displayLarge)
        Text(
            item,
            style = MaterialTheme.typography.titleMedium,
            color = XpColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = XpSpace.s),
        )
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PriceBlock(question: PriceQuestion, locked: Boolean, onAnswer: (GameAnswer) -> Unit) {
    ItemCard(question.emoji, question.item, question.hint)
    Spacer(Modifier.height(XpSpace.l))
    ButtonGrid(
        labels = question.options.map { "¥${it.yuan()}" to it.toString() },
        enabled = !locked,
        onPick = { onAnswer(GameAnswer.Pick(it)) },
    )
}

@Composable
private fun RangeBlock(question: RangeQuestion, locked: Boolean, onAnswer: (GameAnswer) -> Unit) {
    ItemCard(question.emoji, question.item, question.hint)
    Spacer(Modifier.height(XpSpace.l))
    ButtonGrid(
        labels = question.ranges.map { it.label to it.label },
        enabled = !locked,
        onPick = { onAnswer(GameAnswer.Pick(it)) },
    )
}

@Composable
private fun ButtonGrid(labels: List<Pair<String, String>>, enabled: Boolean, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        labels.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                row.forEach { (label, id) ->
                    XpButton(
                        text = label,
                        onClick = { onPick(id) },
                        modifier = Modifier.weight(1f),
                        style = XpButtonStyle.Secondary,
                        enabled = enabled,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrueFalseBlock(question: TrueFalseQuestion, locked: Boolean, onAnswer: (GameAnswer) -> Unit) {
    Text(
        "这句话对吗？",
        style = MaterialTheme.typography.bodySmall,
        color = XpColors.InkSecondary,
        modifier = Modifier.padding(bottom = XpSpace.s),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        XpButton(
            text = "✅ 对",
            onClick = { onAnswer(GameAnswer.Pick("true")) },
            modifier = Modifier.weight(1f),
            style = XpButtonStyle.Secondary,
            enabled = !locked,
        )
        XpButton(
            text = "❌ 错",
            onClick = { onAnswer(GameAnswer.Pick("false")) },
            modifier = Modifier.weight(1f),
            style = XpButtonStyle.Secondary,
            enabled = !locked,
        )
    }
}

@Composable
private fun TaskBlock(question: TaskQuestion, onAnswer: (GameAnswer) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(question.emoji, style = MaterialTheme.typography.displayMedium)
        Text(
            question.tip,
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = XpSpace.s),
        )
        Spacer(Modifier.height(XpSpace.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            XpButton(
                text = "完成",
                onClick = { onAnswer(GameAnswer.Pick(GameScoring.TASK_DONE)) },
                modifier = Modifier.weight(1f),
            )
            XpButton(
                text = "下次一定",
                onClick = { onAnswer(GameAnswer.Pick(GameScoring.TASK_SKIP)) },
                modifier = Modifier.weight(1f),
                style = XpButtonStyle.Secondary,
            )
        }
    }
}

@Composable
private fun ScaleBlock(question: ScaleQuestion, onAnswer: (GameAnswer) -> Unit) {
    var chosen by remember(question.id) { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        (1..5).forEach { value ->
            val selected = chosen == value
            Box(
                Modifier.weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(XpRadius.m))
                    .background(if (selected) XpColors.Primary else XpColors.Fill)
                    .pressableClick(scaleDown = 0.94f) {
                        if (chosen == null) {
                            chosen = value
                            onAnswer(GameAnswer.Scale(value))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$value",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) XpColors.OnPrimary else XpColors.Ink,
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.s),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            question.lowLabel,
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            question.highLabel,
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun FeedbackPanel(question: GameQuestion, correct: Boolean) {
    val tint = if (correct) XpColors.PositiveSoft else XpColors.WarningSoft
    val accent = if (correct) XpColors.Positive else XpColors.Warning
    val reveal = when (question) {
        is PriceQuestion -> "实际价格 ¥${question.actualCents.yuan()}"
        is RangeQuestion -> {
            val band = question.ranges.firstOrNull { it.contains(question.actualCents) }?.label ?: "—"
            "实际价格 ¥${question.actualCents.yuan()}，落在「$band」"
        }
        is TrueFalseQuestion ->
            (if (question.answer) "这句话是对的。" else "这句话是错的。") + " " + question.explain
        else -> ""
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.m))
            .background(tint)
            .padding(XpSpace.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (correct) "✅" else "🙂", style = MaterialTheme.typography.titleMedium)
            Text(
                if (correct) "答对了" else "差一点",
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                modifier = Modifier.padding(start = XpSpace.s),
            )
        }
        if (reveal.isNotBlank()) {
            Text(
                reveal,
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.Ink,
                modifier = Modifier.padding(top = XpSpace.s),
            )
        }
    }
}

@Composable
private fun ResultView(
    game: GameDefinition,
    outcome: GameOutcome,
    reduced: Boolean,
    onReplay: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberXpHaptics()
    var copied by remember(outcome) { mutableStateOf(false) }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(84.dp)
                    .clip(RoundedCornerShape(XpRadius.l))
                    .background(XpColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(outcome.emoji, style = MaterialTheme.typography.displayMedium)
            }
            Column(Modifier.padding(start = XpSpace.l)) {
                Text("你的结果", style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
                Text(
                    outcome.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = XpColors.Ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            outcome.scoreLine,
            style = MaterialTheme.typography.titleLarge,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(top = XpSpace.m),
        )

        if (outcome.radar.isNotEmpty()) {
            Spacer(Modifier.height(XpSpace.m))
            RadarCard(outcome.radar, reduced)
        }

        Spacer(Modifier.height(XpSpace.m))
        XpCard { Text(outcome.blurb, style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink) }
        if (game.note.isNotBlank()) {
            Text(
                game.note,
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(top = XpSpace.s),
            )
        }

        Spacer(Modifier.height(XpSpace.l))
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(XpRadius.m))
                .background(XpColors.Card)
                .padding(XpSpace.l),
        ) {
            Text("分享文案", style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
            Text(
                outcome.shareText,
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.Ink,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(XpSpace.m))
            XpButton(
                text = if (copied) "已复制" else "复制文案",
                onClick = {
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    manager.setPrimaryClip(ClipData.newPlainText("游戏结果", outcome.shareText))
                    copied = true
                    haptics.confirm()
                },
                style = XpButtonStyle.Secondary,
                size = XpButtonSize.Small,
            )
        }

        Spacer(Modifier.height(XpSpace.xl))
        XpButton("再玩一次", onReplay, Modifier.fillMaxWidth())
        Spacer(Modifier.height(XpSpace.s))
        XpButton("换一个游戏", onExit, Modifier.fillMaxWidth(), style = XpButtonStyle.Secondary)

        Spacer(Modifier.height(XpSpace.xl))
        Text(
            GAME_DISCLAIMER,
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(XpSpace.s))
    }
}

@Composable
private fun RadarCard(points: List<RadarPoint>, reduced: Boolean) {
    XpCard {
        Text("五维分布", style = MaterialTheme.typography.titleSmall, color = XpColors.Ink)
        RadarCanvas(
            points = points,
            reduced = reduced,
            modifier = Modifier.fillMaxWidth().height(210.dp).padding(top = XpSpace.s),
        )
        points.forEach { point ->
            Row(
                Modifier.fillMaxWidth().padding(top = XpSpace.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    point.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.Ink,
                    modifier = Modifier.width(56.dp),
                )
                Box(
                    Modifier.weight(1f).height(4.dp)
                        .clip(RoundedCornerShape(XpRadius.pill))
                        .background(XpColors.Fill),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(point.value)
                            .background(XpColors.Accent),
                    )
                }
                Text(
                    "${(point.value * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
    }
}

@Composable
private fun RadarCanvas(points: List<RadarPoint>, reduced: Boolean, modifier: Modifier) {
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) reveal.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
    }
    val accent = XpColors.Accent
    val fill = XpColors.AccentSoft
    val grid = XpColors.Hairline
    Canvas(modifier) {
        val n = points.size
        if (n < 3) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 4.dp.toPx()

        fun vertex(i: Int, r: Float): Offset {
            val angle = (-90.0 + i * 360.0 / n) * PI / 180.0
            return Offset(
                center.x + (r * cos(angle)).toFloat(),
                center.y + (r * sin(angle)).toFloat(),
            )
        }

        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
            val path = Path()
            for (i in 0 until n) {
                val v = vertex(i, radius * level)
                if (i == 0) path.moveTo(v.x, v.y) else path.lineTo(v.x, v.y)
            }
            path.close()
            drawPath(path, grid, style = Stroke(width = 1.dp.toPx()))
        }
        for (i in 0 until n) {
            drawLine(grid, center, vertex(i, radius), strokeWidth = 1.dp.toPx())
        }

        val data = Path()
        for (i in 0 until n) {
            val v = vertex(i, radius * points[i].value * reveal.value)
            if (i == 0) data.moveTo(v.x, v.y) else data.lineTo(v.x, v.y)
        }
        data.close()
        drawPath(data, fill)
        drawPath(data, accent, style = Stroke(width = 2.dp.toPx()))
        for (i in 0 until n) {
            drawCircle(
                color = accent,
                radius = 3.dp.toPx(),
                center = vertex(i, radius * points[i].value * reveal.value),
            )
        }
    }
}
