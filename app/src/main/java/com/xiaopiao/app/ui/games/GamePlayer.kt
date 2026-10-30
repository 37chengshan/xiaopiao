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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.domain.games.ChoiceLayout
import com.xiaopiao.app.domain.games.ChoiceQuestion
import com.xiaopiao.app.domain.games.GameAnswer
import com.xiaopiao.app.domain.games.GameAxis
import com.xiaopiao.app.domain.games.GameDefinition
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.xiaopiao.app.ui.components.LocalGlass
import com.xiaopiao.app.ui.theme.XpEase
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
                // 卡片翻面要 340ms，留到翻完再切下一题，否则只看到半张卡。
                scope.launch { delay(520); advance() }
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
        glassGround = true,
    ) { top ->
        Box(Modifier.fillMaxSize().padding(top = top)) {
            when (stage) {
                GameStage.Intro -> IntroView(
                    game = game,
                    skin = skin,
                    onStart = {
                        haptics.confirm()
                        stage = GameStage.Play
                    },
                    onExit = onExit,
                )
                GameStage.Play -> PlayView(
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
private fun IntroView(game: GameDefinition, skin: GameSkin, onStart: () -> Unit, onExit: () -> Unit) {
    val accent = skin.accentColor()
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XpSpace.page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(XpSpace.xxl))
        Text(
            gameModeLabel(game.mode),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
        Text(
            game.title,
            style = MaterialTheme.typography.headlineLarge,
            color = XpColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = XpSpace.s),
        )
        Text(
            stringResource(R.string.games_meta, game.rounds, game.minutes),
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(XpSpace.xl))
        GlassPanel(shape = RoundedCornerShape(XpRadius.l)) {
            Column(Modifier.fillMaxWidth().padding(XpSpace.l)) {
                Text(
                    stringResource(skin.howTo),
                    style = MaterialTheme.typography.bodyLarge,
                    color = XpColors.Ink,
                )
            }
        }
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
        Spacer(Modifier.height(XpSpace.l))
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
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    // The glass tab bar floats over the page; keep the last control above it.
                    .padding(start = XpSpace.page, end = XpSpace.page, top = XpSpace.l, bottom = 104.dp),
            ) {
                Text(
                    question.prompt,
                    style = MaterialTheme.typography.titleLarge,
                    color = XpColors.Ink,
                )
                Spacer(Modifier.height(XpSpace.l))

                when (question) {
                    is ChoiceQuestion -> ChoiceBlock(question, skin, picked, reduced, onAnswer)
                    is PriceQuestion -> PriceBlock(question, skin, picked, onAnswer)
                    is RangeQuestion -> RangeBlock(question, skin, picked, onAnswer)
                    is TrueFalseQuestion -> TrueFalseBlock(question, skin, picked, reduced, onAnswer)
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
    reduced: Boolean,
    onAnswer: (GameAnswer) -> Unit,
) {
    if (question.hint.isNotBlank()) {
        Text(
            question.hint,
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = XpSpace.m),
        )
    }
    val options = question.options
    if (question.layout == ChoiceLayout.Duel) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.page),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            options.forEach { option ->
                FlipChoiceCard(
                    option = option,
                    tone = toneFor(option.id, picked, correctId = null),
                    enabled = picked == null,
                    reduced = reduced,
                    modifier = Modifier.weight(1f).height(168.dp),
                    onClick = { onAnswer(GameAnswer.Pick(option.id)) },
                )
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.page),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            options.forEach { option ->
                FlipChoiceCard(
                    option = option,
                    tone = toneFor(option.id, picked, correctId = null),
                    enabled = picked == null,
                    reduced = reduced,
                    modifier = Modifier.fillMaxWidth(0.92f).heightIn(min = 72.dp),
                    onClick = { onAnswer(GameAnswer.Pick(option.id)) },
                )
            }
        }
    }
}

/** 液态玻璃面板：页面有 GlassGround 就折射地面，没有则退回实色卡。 */
@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(XpRadius.l),
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val rim = Color.Black.copy(alpha = if (XpColors.isDark) 0.24f else 0.06f)
    val film = XpColors.Card.copy(alpha = if (XpColors.isDark) 0.52f else 0.62f)
    val surface = if (glass == null) {
        modifier.clip(shape).background(XpColors.Card)
    } else {
        modifier.drawBackdrop(
            backdrop = glass,
            shape = { shape },
            effects = {
                vibrancy()
                blur(10.dp.toPx())
                lens(8.dp.toPx(), 12.dp.toPx())
            },
            shadow = { Shadow(radius = 16.dp, color = rim) },
            onDrawSurface = { drawRect(film) },
        )
    }
    Box(surface, content = content)
}

/**
 * 居中的翻卡选择项：正面是选项，选中后沿 Y 轴翻到背面给结论。
 * 只有被选中的那张翻；没选中的保持正面，避免整屏一起动。
 */
@Composable
private fun FlipChoiceCard(
    option: GameOption,
    tone: AnswerTone,
    enabled: Boolean,
    reduced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只有被选中的那张翻面；同题的另一张只是变淡，不然两张都翻过去像都选上了。
    val flipped = tone == AnswerTone.Selected || tone == AnswerTone.Correct || tone == AnswerTone.Wrong
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(flipped) {
        if (reduced) rotation.snapTo(if (flipped) 180f else 0f)
        else rotation.animateTo(if (flipped) 180f else 0f, tween(340, easing = XpEase.out))
    }
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(XpRadius.l)
    val accent = if (tone == AnswerTone.Wrong) XpColors.Warning else XpColors.Accent
    // 点击与翻面都挂在外层 Box：玻璃面板自己带 backdrop 图层，放在里面会把点击吃掉。
    Box(
        modifier
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 12f * density
                alpha = if (tone == AnswerTone.Muted) 0.55f else 1f
            }
            .pressableClick(enabled = enabled, scaleDown = 0.97f, haptic = true, onClick = onClick),
    ) {
        GlassPanel(modifier = Modifier.fillMaxSize(), shape = shape) {
            if (!flipped || rotation.value < 90f) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = XpSpace.m, vertical = XpSpace.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = XpColors.Ink,
                        textAlign = TextAlign.Center,
                    )
                    if (option.note.isNotBlank()) {
                        Text(
                            option.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = XpColors.InkSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .padding(horizontal = XpSpace.m, vertical = XpSpace.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (tone == AnswerTone.Wrong) Icons.Outlined.Close else Icons.Outlined.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        option.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = XpColors.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = XpSpace.s),
                    )
                }
            }
        }
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
    reduced: Boolean,
    onAnswer: (GameAnswer) -> Unit,
) {
    val correctId = question.answer.toString()
    Text(
        stringResource(R.string.games_tf_prompt),
        style = MaterialTheme.typography.bodySmall,
        color = XpColors.InkSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = XpSpace.m),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.page),
        horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        listOf("true" to R.string.games_tf_true, "false" to R.string.games_tf_false).forEach { (id, labelRes) ->
            val tone = toneFor(id, picked, correctId)
            val option = GameOption(id = id, label = stringResource(labelRes))
            Box(Modifier.weight(1f).height(132.dp)) {
                FlipChoiceCard(
                    option = option,
                    tone = tone,
                    enabled = picked == null,
                    reduced = reduced,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { onAnswer(GameAnswer.Pick(id)) },
                )
                if (picked == null) {
                    Icon(
                        if (id == "true") Icons.Outlined.Check else Icons.Outlined.Close,
                        contentDescription = null,
                        tint = XpColors.InkTertiary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(16.dp),
                    )
                }
            }
        }
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
                    .height(64.dp)
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
                    style = MaterialTheme.typography.titleLarge,
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
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(XpRadius.xl)) {
        Column(
            Modifier.fillMaxWidth().padding(XpSpace.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
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
            // 底部浮着标签栏（约 96dp）＋安全区，结果页最后两颗按钮要留出这段。
            .padding(start = XpSpace.page, end = XpSpace.page, bottom = 168.dp),
    ) {
        Spacer(Modifier.height(XpSpace.m))
        ResultHero(outcome = outcome, figure = figure, showBody = persona, accent = skin.accentColor())

        if (outcome.radar.isNotEmpty()) {
            Spacer(Modifier.height(XpSpace.m))
            RadarCard(points = outcome.radar, axes = game.axes, skin = skin, reduced = reduced)
        }

        if (!persona) {
            Spacer(Modifier.height(XpSpace.m))
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    outcome.blurb,
                    style = MaterialTheme.typography.bodyLarge,
                    color = XpColors.Ink,
                    modifier = Modifier.padding(XpSpace.l),
                )
            }
        }
        if (outcome.insights.isNotEmpty()) {
            Spacer(Modifier.height(XpSpace.m))
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(XpSpace.l)) {
                    Text(
                        stringResource(R.string.games_result_analysis),
                        style = MaterialTheme.typography.titleSmall,
                        color = XpColors.Ink,
                    )
                    outcome.insights.forEach { line ->
                        Row(Modifier.padding(top = XpSpace.s)) {
                            Text("·", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = XpColors.Ink,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
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

/** 结果抬头：一张玻璃卡，字数最少的那种——类型名 + 一句判断 + 关键数字。 */
@Composable
private fun ResultHero(outcome: GameOutcome, figure: ScoreFigure, showBody: Boolean, accent: Color) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(XpRadius.xl)) {
        Column(Modifier.fillMaxWidth().padding(XpSpace.page)) {
            Text(
                stringResource(R.string.games_result_your),
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkSecondary,
            )
            Text(
                outcome.title,
                style = MaterialTheme.typography.headlineLarge,
                color = XpColors.Ink,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (figure.value.isNotBlank()) {
                Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.Bottom) {
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
            if (figure.label.isNotBlank()) {
                Text(
                    figure.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = XpSpace.xs),
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
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(XpRadius.xl)) {
        Column(Modifier.fillMaxWidth().padding(XpSpace.l)) {
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
                val axis = axes.firstOrNull { it.label == point.label }
                Column(Modifier.fillMaxWidth().padding(top = XpSpace.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            point.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = XpColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(64.dp),
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
                    val note = axis?.band(point.value) ?: axis?.blurb
                    if (!note.isNullOrBlank()) {
                        Text(
                            note,
                            style = MaterialTheme.typography.labelSmall,
                            color = XpColors.InkSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
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
                    .background(skin.accentColor()),
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
