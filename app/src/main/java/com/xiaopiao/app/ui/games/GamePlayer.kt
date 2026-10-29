package com.xiaopiao.app.ui.games

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.domain.games.ChoiceLayout
import com.xiaopiao.app.domain.games.ChoiceQuestion
import com.xiaopiao.app.domain.games.GameAnswer
import com.xiaopiao.app.domain.games.GameAxis
import com.xiaopiao.app.domain.games.GameDefinition
import com.xiaopiao.app.domain.games.GameMode
import com.xiaopiao.app.domain.games.GameOption
import com.xiaopiao.app.domain.games.GameOutcome
import com.xiaopiao.app.domain.games.GameQuestion
import com.xiaopiao.app.domain.games.GameScoring
import com.xiaopiao.app.domain.games.PriceQuestion
import com.xiaopiao.app.domain.games.RadarPoint
import com.xiaopiao.app.domain.games.RangeQuestion
import com.xiaopiao.app.domain.games.ScaleQuestion
import com.xiaopiao.app.domain.games.ScoreStyle
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
import com.xiaopiao.app.ui.theme.Contrast
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where a single run is: the front door, the questions, or the result. */
private enum class GameStage { Intro, Play, Result }

/** How an answer chip should read right now. */
private enum class AnswerTone { Idle, Selected, Correct, Wrong, Muted }

/** The big number a score/radar result prints; empty [value] means the result has no number (persona). */
private data class ScoreFigure(val value: String, val unit: String, val label: String)

/**
 * One player for every [GameQuestion] variant (docs/APP-DESIGN.md §5.3).
 *
 * Three states share the page: an intro card that says what the game is and carries the disclaimer, the
 * question deck with a percentage progress bar and per-game accent, and a result that is a persona card,
 * a score figure, or a real five-axis radar. Scoring stays in [GameScoring]; this file only renders.
 */
@Composable
fun GamePlayer(game: GameDefinition, onExit: () -> Unit) {
    val haptics = rememberXpHaptics()
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val skin = GameSkins.of(game.id)

    var run by remember { mutableIntStateOf(0) }
    // Not keyed on [run]: replay jumps straight back into the deck instead of replaying the intro.
    var stage by remember(game.id) { mutableStateOf(GameStage.Intro) }
    val deck = remember(game.id, run) { GameScoring.deckFor(game, System.nanoTime()) }
    var index by remember(game.id, run) { mutableIntStateOf(0) }
    val answers = remember(game.id, run) { mutableStateListOf<GameAnswer>() }
    var feedback by remember(game.id, run, index) { mutableStateOf<Boolean?>(null) }
    var picked by remember(game.id, run, index) { mutableStateOf<String?>(null) }

    fun advance() {
        if (index + 1 >= deck.size) {
            stage = GameStage.Result
            haptics.thud()
        } else {
            index += 1
        }
    }

    fun answer(question: GameQuestion, value: GameAnswer) {
        if (stage != GameStage.Play || picked != null || answers.size != index) return
        picked = when (value) {
            is GameAnswer.Pick -> value.optionId
            is GameAnswer.Scale -> value.value.toString()
        }
        answers.add(value)
        when (question) {
            is PriceQuestion, is RangeQuestion, is TrueFalseQuestion -> {
                val correct = GameScoring.scoreFor(question, value) == 1
                feedback = correct
                if (correct) haptics.confirm() else haptics.reject()
            }
            is ChoiceQuestion -> {
                haptics.select()
                // Let the choice be seen for a beat before the next question slides in.
                scope.launch { delay(160); advance() }
            }
            is ScaleQuestion -> {
                haptics.select()
                scope.launch { delay(200); advance() }
            }
            is TaskQuestion -> {
                if (GameScoring.scoreFor(question, value) == 1) haptics.confirm() else haptics.select()
                scope.launch { delay(160); advance() }
            }
        }
    }

    BackHandler { onExit() }

    NavScaffold(
        title = game.title,
        scroll = NavScrollNone,
        onBack = onExit,
        backLabel = stringResource(R.string.games_back),
    ) { top ->
        // The bar floats: its height is handed to each stage as scroll padding, so a page can run under the glass.
        Box(Modifier.fillMaxSize()) {
            when (stage) {
                GameStage.Intro -> IntroView(
                    game = game,
                    topPad = top,
                    skin = skin,
                    onStart = {
                        haptics.confirm()
                        stage = GameStage.Play
                    },
                    onExit = onExit,
                )
                GameStage.Play -> PlayView(
                    topPad = top,
                    deck = deck,
                    index = index,
                    skin = skin,
                    reduced = reduced,
                    feedback = feedback,
                    picked = picked,
                    onAnswer = { question, value -> answer(question, value) },
                    onContinue = {
                        feedback = null
                        picked = null
                        advance()
                    },
                )
                GameStage.Result -> ResultView(
                    game = game,
                    topPad = top,
                    deck = deck,
                    answers = answers,
                    outcome = GameScoring.outcome(game, deck, answers),
                    skin = skin,
                    reduced = reduced,
                    onReplay = {
                        run += 1
                        stage = GameStage.Play
                    },
                    onExit = onExit,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ intro

/**
 * The front door. A full-bleed accent header carries the game's graphic, name and length; the lower half
 * explains how it plays and owns the two actions. The disclaimer is part of the intro, not fine print
 * hidden on the result.
 */
@Composable
private fun IntroView(
    game: GameDefinition,
    skin: GameSkin,
    onStart: () -> Unit,
    onExit: () -> Unit,
    topPad: Dp = 0.dp,
) {
    val accent = skin.accentColor()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerHeight = (maxHeight * 0.56f).coerceIn(240.dp, 420.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = topPad)) {
            Box(
                Modifier.fillMaxWidth()
                    .height(headerHeight)
                    .clipToBounds()
                    .background(skin.heroGradient()),
            ) {
                GameMotifGraphic(skin = skin, modifier = Modifier.matchParentSize(), strength = 0.24f)
                Column(
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = XpSpace.page, vertical = XpSpace.xl),
                ) {
                    Text(
                        gameModeLabel(game.mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                    Box(
                        Modifier.padding(top = XpSpace.l)
                            .size(96.dp)
                            .clip(RoundedCornerShape(XpRadius.xl))
                            .background(XpColors.Card),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(game.emoji, style = MaterialTheme.typography.displayLarge)
                    }
                    Text(
                        game.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = XpColors.Ink,
                        modifier = Modifier.padding(top = XpSpace.l),
                    )
                    Text(
                        stringResource(R.string.games_meta, game.rounds, game.minutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = XpColors.InkSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = XpSpace.page, vertical = XpSpace.l),
            ) {
                Text(
                    stringResource(skin.howTo),
                    style = MaterialTheme.typography.bodyLarge,
                    color = XpColors.Ink,
                )
                Spacer(Modifier.height(XpSpace.xl))
                XpButton(
                    text = stringResource(R.string.games_intro_start),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(XpSpace.s))
                XpButton(
                    text = stringResource(R.string.games_intro_exit),
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    style = XpButtonStyle.Plain,
                )
                Spacer(Modifier.height(XpSpace.l))
                Text(
                    stringResource(R.string.games_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(XpSpace.s))
            }
        }
    }
}

@Composable
private fun gameModeLabel(mode: GameMode): String = stringResource(
    when (mode) {
        GameMode.Persona -> R.string.games_mode_persona
        GameMode.Score -> R.string.games_mode_challenge
        GameMode.Radar -> R.string.games_mode_radar
    },
)

// ------------------------------------------------------------------------------------------- play

@Composable
private fun PlayView(
    topPad: Dp = 0.dp,
    deck: List<GameQuestion>,
    index: Int,
    skin: GameSkin,
    reduced: Boolean,
    feedback: Boolean?,
    picked: String?,
    onAnswer: (GameQuestion, GameAnswer) -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        GameProgress(index = index, total = deck.size, skin = skin, reduced = reduced)
        AnimatedContent(
            targetState = index,
            transitionSpec = { questionTransition(reduced) },
            label = "gameQuestion",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { i ->
            QuestionView(
                topPad = topPad,
                question = deck[i],
                index = i,
                total = deck.size,
                skin = skin,
                reduced = reduced,
                feedback = feedback,
                picked = picked,
                onAnswer = { onAnswer(deck[i], it) },
                onContinue = onContinue,
            )
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
private fun GameProgress(index: Int, total: Int, skin: GameSkin, reduced: Boolean) {
    val accent = skin.accentColor()
    val fraction = ((index + 1).toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = 1f, stiffness = 700f),
        label = "gameProgress",
    )
    Column(
        Modifier.fillMaxWidth()
            .padding(start = XpSpace.page, end = XpSpace.page, top = XpSpace.s),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.games_q_counter, index + 1, total),
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.InkSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.games_percent, (fraction * 100).roundToInt()),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }
        Box(
            Modifier.fillMaxWidth()
                .padding(top = XpSpace.s)
                .height(6.dp)
                .clip(RoundedCornerShape(XpRadius.pill))
                .background(XpColors.Fill),
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun QuestionView(
    topPad: Dp = 0.dp,
    question: GameQuestion,
    index: Int,
    total: Int,
    skin: GameSkin,
    reduced: Boolean,
    feedback: Boolean?,
    picked: String?,
    onAnswer: (GameAnswer) -> Unit,
    onContinue: () -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(feedback) {
        if (feedback != null) {
            // The panel is laid out on the next frame; read maxValue only after it exists.
            withFrameNanos { }
            withFrameNanos { }
            val target = scroll.maxValue
            if (reduced) scroll.scrollTo(target) else scroll.animateScrollTo(target)
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    // The glass tab bar floats over the page; keep the last control above it. The top gap is
                    // scroll padding, so a long question can slide under the bar.
                    .padding(start = XpSpace.page, end = XpSpace.page, top = topPad + XpSpace.l, bottom = 104.dp),
            ) {
                Text(
                    question.prompt,
                    style = MaterialTheme.typography.titleLarge,
                    color = XpColors.Ink,
                )
                Spacer(Modifier.height(XpSpace.l))

                when (question) {
                    is ChoiceQuestion -> ChoiceBlock(question, skin, picked, onAnswer)
                    is PriceQuestion -> PriceBlock(question, skin, picked, onAnswer)
                    is RangeQuestion -> RangeBlock(question, skin, picked, onAnswer)
                    is TrueFalseQuestion -> TrueFalseBlock(question, skin, picked, onAnswer)
                    is TaskQuestion -> TaskBlock(question, skin, onAnswer)
                    is ScaleQuestion -> ScaleBlock(question, skin, picked, onAnswer)
                }

                AnimatedVisibility(
                    visible = feedback != null,
                    enter = if (reduced) {
                        EnterTransition.None
                    } else {
                        fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 6 }
                    },
                    exit = ExitTransition.None,
                ) {
                    Column {
                        Spacer(Modifier.height(XpSpace.l))
                        feedback?.let { FeedbackPanel(question, it) }
                        Spacer(Modifier.height(XpSpace.l))
                        XpButton(
                            text = stringResource(
                                if (index + 1 >= total) R.string.games_result_cta else R.string.games_next,
                            ),
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(XpSpace.l))
            }
        }
        Text(
            stringResource(R.string.games_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = XpSpace.s),
        )
    }
}

// ------------------------------------------------------------------------------- answer renderers

@Composable
private fun ChoiceBlock(
    question: ChoiceQuestion,
    skin: GameSkin,
    picked: String?,
    onAnswer: (GameAnswer) -> Unit,
) {
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
                DuelCard(
                    option = option,
                    tone = toneFor(option.id, picked, correctId = null),
                    skin = skin,
                    enabled = picked == null,
                    modifier = Modifier.weight(1f),
                    onClick = { onAnswer(GameAnswer.Pick(option.id)) },
                )
            }
        }
        ChoiceLayout.List -> Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            question.options.forEach { option ->
                OptionRow(
                    option = option,
                    tone = toneFor(option.id, picked, correctId = null),
                    skin = skin,
                    enabled = picked == null,
                    onClick = { onAnswer(GameAnswer.Pick(option.id)) },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    option: GameOption,
    tone: AnswerTone,
    skin: GameSkin,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fg = toneForeground(tone, skin)
    val shape = RoundedCornerShape(XpRadius.l)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(toneBackground(tone, skin))
            .then(if (tone.hasEdge) Modifier.border(1.5.dp, fg, shape) else Modifier)
            .pressableClick(enabled = enabled, scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.emoji.isNotBlank()) {
            Box(
                Modifier.size(38.dp)
                    .clip(RoundedCornerShape(XpRadius.m))
                    .background(if (tone == AnswerTone.Idle) XpColors.Card else skin.washColor()),
                contentAlignment = Alignment.Center,
            ) {
                Text(option.emoji, style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(
            Modifier.weight(1f).padding(start = if (option.emoji.isNotBlank()) XpSpace.m else 0.dp),
        ) {
            Text(option.label, style = MaterialTheme.typography.titleSmall, color = fg)
            if (option.note.isNotBlank()) {
                Text(
                    option.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        when (tone) {
            AnswerTone.Selected, AnswerTone.Correct -> Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
            AnswerTone.Wrong -> Icon(
                Icons.Outlined.Close,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
            else -> Text("›", style = MaterialTheme.typography.titleMedium, color = XpColors.InkTertiary)
        }
    }
}

@Composable
private fun DuelCard(
    option: GameOption,
    tone: AnswerTone,
    skin: GameSkin,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fg = toneForeground(tone, skin)
    val shape = RoundedCornerShape(XpRadius.l)
    Column(
        modifier
            .height(176.dp)
            .clip(shape)
            .background(toneBackground(tone, skin))
            .then(if (tone.hasEdge) Modifier.border(1.5.dp, fg, shape) else Modifier)
            .pressableClick(enabled = enabled, scaleDown = 0.96f, onClick = onClick)
            .padding(XpSpace.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(option.emoji, style = MaterialTheme.typography.displayLarge)
        Text(
            option.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (tone == AnswerTone.Idle) XpColors.Ink else fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = XpSpace.s),
        )
    }
}

@Composable
private fun PriceBlock(
    question: PriceQuestion,
    skin: GameSkin,
    picked: String?,
    onAnswer: (GameAnswer) -> Unit,
) {
    ItemCard(question.emoji, question.item, question.hint, skin)
    Spacer(Modifier.height(XpSpace.l))
    GuessGrid(
        chips = question.options.map { "¥${it.yuan()}" to it.toString() },
        correctId = question.actualCents.toString(),
        picked = picked,
        skin = skin,
        onPick = { onAnswer(GameAnswer.Pick(it)) },
    )
}

@Composable
private fun RangeBlock(
    question: RangeQuestion,
    skin: GameSkin,
    picked: String?,
    onAnswer: (GameAnswer) -> Unit,
) {
    ItemCard(question.emoji, question.item, question.hint, skin)
    Spacer(Modifier.height(XpSpace.l))
    GuessGrid(
        chips = question.ranges.map { it.label to it.label },
        correctId = question.ranges.firstOrNull { it.contains(question.actualCents) }?.label.orEmpty(),
        picked = picked,
        skin = skin,
        onPick = { onAnswer(GameAnswer.Pick(it)) },
    )
}

@Composable
private fun TrueFalseBlock(
    question: TrueFalseQuestion,
    skin: GameSkin,
    picked: String?,
    onAnswer: (GameAnswer) -> Unit,
) {
    val correctId = question.answer.toString()
    Text(
        stringResource(R.string.games_tf_prompt),
        style = MaterialTheme.typography.bodySmall,
        color = XpColors.InkSecondary,
        modifier = Modifier.padding(bottom = XpSpace.s),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        GuessChip(
            label = "✅ " + stringResource(R.string.games_tf_true),
            tone = toneFor("true", picked, correctId),
            skin = skin,
            enabled = picked == null,
            modifier = Modifier.weight(1f),
            onClick = { onAnswer(GameAnswer.Pick("true")) },
        )
        GuessChip(
            label = "❌ " + stringResource(R.string.games_tf_false),
            tone = toneFor("false", picked, correctId),
            skin = skin,
            enabled = picked == null,
            modifier = Modifier.weight(1f),
            onClick = { onAnswer(GameAnswer.Pick("false")) },
        )
    }
}

@Composable
private fun GuessGrid(
    chips: List<Pair<String, String>>,
    correctId: String,
    picked: String?,
    skin: GameSkin,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        chips.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                row.forEach { (label, id) ->
                    GuessChip(
                        label = label,
                        tone = toneFor(id, picked, correctId),
                        skin = skin,
                        enabled = picked == null,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(id) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GuessChip(
    label: String,
    tone: AnswerTone,
    skin: GameSkin,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fg = toneForeground(tone, skin)
    val shape = RoundedCornerShape(XpRadius.l)
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(toneBackground(tone, skin))
            .then(if (tone.hasEdge) Modifier.border(1.5.dp, fg, shape) else Modifier)
            .pressableClick(enabled = enabled, scaleDown = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (tone == AnswerTone.Idle) XpColors.Ink else fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TaskBlock(question: TaskQuestion, skin: GameSkin, onAnswer: (GameAnswer) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp)
                .clip(RoundedCornerShape(XpRadius.xl))
                .background(XpColors.Card),
            contentAlignment = Alignment.Center,
        ) {
            Text(question.emoji, style = MaterialTheme.typography.displayMedium)
        }
        Text(
            question.tip,
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = XpSpace.m),
        )
        Spacer(Modifier.height(XpSpace.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            XpButton(
                text = stringResource(R.string.games_task_done),
                onClick = { onAnswer(GameAnswer.Pick(GameScoring.TASK_DONE)) },
                modifier = Modifier.weight(1f),
            )
            XpButton(
                text = stringResource(R.string.games_task_skip),
                onClick = { onAnswer(GameAnswer.Pick(GameScoring.TASK_SKIP)) },
                modifier = Modifier.weight(1f),
                style = XpButtonStyle.Secondary,
            )
        }
    }
}

@Composable
private fun ScaleBlock(
    question: ScaleQuestion,
    skin: GameSkin,
    picked: String?,
    onAnswer: (GameAnswer) -> Unit,
) {
    val accent = skin.accentColor()
    val onAccent = Contrast.onAccent(accent)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        (1..5).forEach { value ->
            val selected = picked == value.toString()
            val shape = RoundedCornerShape(XpRadius.l)
            Box(
                Modifier.weight(1f)
                    .height(56.dp)
                    .clip(shape)
                    .background(if (selected) accent else XpColors.Fill)
                    .then(if (selected) Modifier.border(1.5.dp, accent, shape) else Modifier)
                    .pressableClick(enabled = picked == null, scaleDown = 0.94f) {
                        onAnswer(GameAnswer.Scale(value))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$value",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) onAccent else XpColors.Ink,
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
private fun ItemCard(emoji: String, item: String, hint: String, skin: GameSkin) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.xl))
            .background(skin.cardGradient()),
    ) {
        GameMotifGraphic(skin = skin, modifier = Modifier.matchParentSize(), strength = 0.22f)
        Column(
            Modifier.fillMaxWidth().padding(XpSpace.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp)
                    .clip(RoundedCornerShape(XpRadius.l))
                    .background(XpColors.Card),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = MaterialTheme.typography.displayMedium)
            }
            Text(
                item,
                style = MaterialTheme.typography.titleMedium,
                color = XpColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = XpSpace.m),
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FeedbackPanel(question: GameQuestion, correct: Boolean) {
    val tone = if (correct) XpColors.Positive else XpColors.Negative
    val tint = if (correct) XpColors.PositiveSoft else XpColors.Negative.copy(alpha = 0.1f)
    val icon = if (correct) Icons.Outlined.Check else Icons.Outlined.Close
    val title = stringResource(
        if (correct) R.string.games_feedback_correct else R.string.games_feedback_wrong,
    )
    val reveal = when (question) {
        is PriceQuestion -> stringResource(R.string.games_feedback_price, question.actualCents.yuan())
        is RangeQuestion -> {
            val band = question.ranges.firstOrNull { it.contains(question.actualCents) }?.label ?: "—"
            stringResource(R.string.games_feedback_range, question.actualCents.yuan(), band)
        }
        is TrueFalseQuestion ->
            stringResource(
                if (question.answer) R.string.games_feedback_true else R.string.games_feedback_false,
            ) + " " + question.explain
        else -> ""
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.l))
            .background(tint)
            .padding(XpSpace.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp)
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .background(tone),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Contrast.onAccent(tone),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = tone,
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

private fun toneFor(id: String, picked: String?, correctId: String?): AnswerTone = when {
    picked == null -> AnswerTone.Idle
    correctId == null -> if (id == picked) AnswerTone.Selected else AnswerTone.Muted
    id == correctId -> AnswerTone.Correct
    id == picked -> AnswerTone.Wrong
    else -> AnswerTone.Muted
}

private val AnswerTone.hasEdge: Boolean
    get() = this == AnswerTone.Selected || this == AnswerTone.Correct || this == AnswerTone.Wrong

@Composable
private fun toneBackground(tone: AnswerTone, skin: GameSkin): Color = when (tone) {
    AnswerTone.Idle -> XpColors.Fill
    AnswerTone.Selected -> skin.washColor()
    AnswerTone.Correct -> XpColors.PositiveSoft
    AnswerTone.Wrong -> XpColors.Negative.copy(alpha = 0.1f)
    AnswerTone.Muted -> XpColors.Fill.copy(alpha = 0.55f)
}

@Composable
private fun toneForeground(tone: AnswerTone, skin: GameSkin): Color = when (tone) {
    AnswerTone.Idle -> XpColors.Ink
    AnswerTone.Muted -> XpColors.InkTertiary
    AnswerTone.Selected -> skin.accentColor()
    AnswerTone.Correct -> XpColors.Positive
    AnswerTone.Wrong -> XpColors.Negative
}

// ----------------------------------------------------------------------------------------- result

@Composable
private fun ResultView(
    game: GameDefinition,
    topPad: Dp = 0.dp,
    deck: List<GameQuestion>,
    answers: List<GameAnswer>,
    outcome: GameOutcome,
    skin: GameSkin,
    reduced: Boolean,
    onReplay: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberXpHaptics()
    var copied by remember(outcome) { mutableStateOf(false) }
    val figure = resultFigure(game, deck, answers, outcome)
    val persona = game.mode == GameMode.Persona

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = XpSpace.page, end = XpSpace.page, top = topPad, bottom = 104.dp),
    ) {
        Spacer(Modifier.height(XpSpace.m))
        ResultHero(outcome = outcome, skin = skin, figure = figure, showBody = persona)

        if (outcome.radar.isNotEmpty()) {
            Spacer(Modifier.height(XpSpace.m))
            RadarCard(points = outcome.radar, axes = game.axes, skin = skin, reduced = reduced)
        }

        if (!persona) {
            Spacer(Modifier.height(XpSpace.m))
            XpCard { Text(outcome.blurb, style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink) }
        }
        if (game.note.isNotBlank()) {
            Text(
                game.note,
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(top = XpSpace.s),
            )
        }

        Spacer(Modifier.height(XpSpace.xl))
        Text(
            stringResource(R.string.games_share_title),
            style = MaterialTheme.typography.titleSmall,
            color = XpColors.Ink,
        )
        Spacer(Modifier.height(XpSpace.s))
        ShareCard(game = game, outcome = outcome, skin = skin, figure = figure)

        Spacer(Modifier.height(XpSpace.s))
        XpButton(
            text = stringResource(if (copied) R.string.games_copied else R.string.games_copy),
            onClick = {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(
                    ClipData.newPlainText(context.getString(R.string.games_clip_label), outcome.shareText),
                )
                copied = true
                haptics.confirm()
            },
            style = XpButtonStyle.Secondary,
            size = XpButtonSize.Small,
        )

        Spacer(Modifier.height(XpSpace.xl))
        XpButton(
            text = stringResource(R.string.games_again),
            onClick = onReplay,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(XpSpace.s))
        XpButton(
            text = stringResource(R.string.games_another),
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            style = XpButtonStyle.Secondary,
        )

        Spacer(Modifier.height(XpSpace.l))
        Text(
            stringResource(R.string.games_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(XpSpace.xl))
    }
}

@Composable
private fun resultFigure(
    game: GameDefinition,
    deck: List<GameQuestion>,
    answers: List<GameAnswer>,
    outcome: GameOutcome,
): ScoreFigure {
    val rounds = deck.size
    return when (game.mode) {
        GameMode.Persona -> ScoreFigure("", "", "")
        GameMode.Radar -> {
            val top = outcome.radar.maxByOrNull { it.value }
            ScoreFigure(
                value = stringResource(R.string.games_percent, ((top?.value ?: 0f) * 100f).roundToInt()),
                unit = "",
                label = stringResource(R.string.games_result_radar),
            )
        }
        GameMode.Score -> {
            val total = GameScoring.totalScore(deck, answers)
            when (game.scoreStyle) {
                ScoreStyle.Correct -> ScoreFigure(
                    value = "$total",
                    unit = stringResource(R.string.games_result_of, rounds),
                    label = stringResource(R.string.games_result_correct),
                )
                ScoreStyle.TasksDone -> ScoreFigure(
                    value = "$total",
                    unit = stringResource(R.string.games_result_of, rounds),
                    label = stringResource(R.string.games_result_tasks),
                )
                ScoreStyle.Points -> ScoreFigure(
                    value = "$total",
                    unit = stringResource(R.string.games_result_points_unit),
                    label = stringResource(R.string.games_result_points),
                )
                ScoreStyle.SavedCents -> ScoreFigure(
                    value = "¥${total.toLong().yuan()}",
                    unit = "",
                    label = stringResource(R.string.games_result_saved),
                )
            }
        }
    }
}

/** Persona card / score card: one accent surface with the big emoji, the title and the headline figure. */
@Composable
private fun ResultHero(outcome: GameOutcome, skin: GameSkin, figure: ScoreFigure, showBody: Boolean) {
    val accent = skin.accentColor()
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.xl))
            .background(skin.cardGradient()),
    ) {
        GameMotifGraphic(skin = skin, modifier = Modifier.matchParentSize(), strength = 0.22f)
        Column(Modifier.fillMaxWidth().padding(XpSpace.page)) {
            Text(
                stringResource(R.string.games_result_your),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
            Row(
                Modifier.padding(top = XpSpace.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(76.dp)
                        .clip(RoundedCornerShape(XpRadius.l))
                        .background(XpColors.Card),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(outcome.emoji, style = MaterialTheme.typography.displayMedium)
                }
                Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
                    Text(
                        outcome.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = XpColors.Ink,
                    )
                    if (figure.value.isNotBlank()) {
                        Row(
                            Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                figure.value,
                                style = MaterialTheme.typography.displayMedium,
                                color = accent,
                            )
                            if (figure.unit.isNotBlank()) {
                                Text(
                                    figure.unit,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = XpColors.InkSecondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (figure.label.isNotBlank()) {
                Text(
                    figure.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = XpSpace.s),
                )
            }
            if (showBody) {
                Text(
                    outcome.blurb,
                    style = MaterialTheme.typography.bodyLarge,
                    color = XpColors.Ink,
                    modifier = Modifier.padding(top = XpSpace.m),
                )
                Text(
                    outcome.scoreLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = XpSpace.s),
                )
            }
        }
    }
}

@Composable
private fun RadarCard(
    points: List<RadarPoint>,
    axes: List<GameAxis>,
    skin: GameSkin,
    reduced: Boolean,
) {
    XpCard {
        Text(
            stringResource(R.string.games_result_radar_title),
            style = MaterialTheme.typography.titleSmall,
            color = XpColors.Ink,
        )
        RadarChart(
            points = points,
            skin = skin,
            reduced = reduced,
            showLabels = true,
            modifier = Modifier.fillMaxWidth().height(250.dp).padding(top = XpSpace.s),
        )
        points.forEach { point ->
            val emoji = axes.firstOrNull { it.label == point.label }?.emoji.orEmpty()
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (emoji.isBlank()) point.label else "$emoji ${point.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(88.dp),
                )
                Box(
                    Modifier.weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(XpRadius.pill))
                        .background(XpColors.Fill),
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(point.value.coerceIn(0f, 1f))
                            .background(skin.accentColor()),
                    )
                }
                Text(
                    stringResource(R.string.games_percent, (point.value * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
    }
}

/**
 * The radar itself: five (or n) axes, four ring levels, the data polygon and the axis labels, drawn with
 * Compose Canvas only. The polygon grows from the centre once unless the system asked for reduced motion.
 */
@Composable
private fun RadarChart(
    points: List<RadarPoint>,
    skin: GameSkin,
    reduced: Boolean,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) reveal.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
    }
    val accent = skin.accentColor()
    val fill = accent.copy(alpha = 0.16f)
    val grid = XpColors.Hairline
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = XpColors.InkSecondary)

    Canvas(modifier) {
        val n = points.size
        if (n < 3) return@Canvas
        val labelGap = if (showLabels) 28.dp.toPx() else 4.dp.toPx()
        val radius = minOf(size.width / 2f, size.height / 2f) - labelGap
        if (radius <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)

        fun vertex(i: Int, r: Float): Offset {
            val angle = (-90.0 + i * 360.0 / n) * PI / 180.0
            return Offset(
                center.x + (r * cos(angle)).toFloat(),
                center.y + (r * sin(angle)).toFloat(),
            )
        }

        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
            val ring = Path()
            for (i in 0 until n) {
                val v = vertex(i, radius * level)
                if (i == 0) ring.moveTo(v.x, v.y) else ring.lineTo(v.x, v.y)
            }
            ring.close()
            drawPath(ring, grid, style = Stroke(width = 1.dp.toPx()))
        }
        for (i in 0 until n) {
            drawLine(grid, center, vertex(i, radius), strokeWidth = 1.dp.toPx())
        }

        val data = Path()
        for (i in 0 until n) {
            val value = points[i].value.coerceIn(0f, 1f) * reveal.value
            val v = vertex(i, radius * value)
            if (i == 0) data.moveTo(v.x, v.y) else data.lineTo(v.x, v.y)
        }
        data.close()
        drawPath(data, fill)
        drawPath(data, accent, style = Stroke(width = 2.dp.toPx()))
        for (i in 0 until n) {
            drawCircle(
                color = accent,
                radius = 3.dp.toPx(),
                center = vertex(i, radius * points[i].value.coerceIn(0f, 1f) * reveal.value),
            )
        }

        if (showLabels) {
            points.forEachIndexed { i, point ->
                val label = measurer.measure(AnnotatedString(point.label), labelStyle)
                val anchor = vertex(i, radius + labelGap * 0.5f + 4.dp.toPx())
                drawText(
                    label,
                    topLeft = Offset(
                        anchor.x - label.size.width / 2f,
                        anchor.y - label.size.height / 2f,
                    ),
                )
            }
        }
    }
}

/** A shareable card built from Compose surfaces (not a bitmap): the result, the figure and the disclaimer. */
@Composable
private fun ShareCard(
    game: GameDefinition,
    outcome: GameOutcome,
    skin: GameSkin,
    figure: ScoreFigure,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.xl))
            .background(XpColors.Card),
    ) {
        Column(Modifier.padding(XpSpace.page)) {
            Box(
                Modifier.fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(skin.accentColor(), skin.accentEndColor()))),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(outcome.emoji, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.games_share_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkTertiary,
                    modifier = Modifier.padding(start = XpSpace.s),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    game.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = XpSpace.s),
                )
            }
            Text(
                outcome.title,
                style = MaterialTheme.typography.headlineMedium,
                color = XpColors.Ink,
                modifier = Modifier.padding(top = XpSpace.m),
            )
            Text(
                outcome.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (figure.value.isNotBlank()) {
                Row(
                    Modifier.padding(top = XpSpace.m),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        figure.value,
                        style = MaterialTheme.typography.displayMedium,
                        color = skin.accentColor(),
                    )
                    if (figure.unit.isNotBlank()) {
                        Text(
                            figure.unit,
                            style = MaterialTheme.typography.titleMedium,
                            color = XpColors.InkSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                    }
                }
            } else {
                Text(
                    outcome.scoreLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (outcome.radar.isNotEmpty()) {
                RadarChart(
                    points = outcome.radar,
                    skin = skin,
                    reduced = true,
                    showLabels = false,
                    modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = XpSpace.m),
                )
            }
            Text(
                stringResource(R.string.games_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(top = XpSpace.l),
            )
        }
    }
}
