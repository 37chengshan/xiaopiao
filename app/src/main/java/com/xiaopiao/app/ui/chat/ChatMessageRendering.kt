package com.xiaopiao.app.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.*

/**
 * One-line rolling thought stream: the current real operation scrolls in, older ones rotate through while the
 * turn runs, and the full list expands on tap. Operational summaries only — never hidden model reasoning.
 */
@Composable
fun ThoughtPanel(steps: List<ChatItem.Tool>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    var expanded by rememberSaveable(steps.first().id) { mutableStateOf(false) }
    val reduced = rememberReducedMotion()
    val running = steps.any { it.running }
    // Two rounds report the same status; the stream shows each real step once.
    val labels = steps.map { listOfNotNull(it.label, it.detail).joinToString(" · ") }.distinct()
    var index by remember(steps.first().id) { mutableIntStateOf(labels.lastIndex.coerceAtLeast(0)) }
    // A new step always takes the line; older ones keep cycling so the stream reads as live work.
    LaunchedEffect(labels.lastIndex) { index = labels.lastIndex.coerceAtLeast(0) }
    LaunchedEffect(running, labels.size, reduced) {
        if (!running || reduced || labels.size < 2) return@LaunchedEffect
        while (true) {
            delay(1_800)
            index = (index + 1) % labels.size
        }
    }
    // Finished turns settle into a one-line count instead of a stale「正在…」.
    val en = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "en"
    val streamLabel = if (en) "Thought stream" else "思考流"
    val current = if (running) labels.getOrElse(index) { "" } else "$streamLabel · ${steps.size}"
    Column(modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.xs)
        .clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Surface2)
        .then(if (reduced) Modifier else Modifier.animateContentSize(tween(180, easing = XpEase.out)))
        .clickable { expanded = !expanded }
        .semantics { stateDescription = if (expanded) "已展开" else "已收起"; contentDescription = if (en) "Thought stream, tap to expand" else "思考流，点击展开或收起" }
        .padding(XpSpace.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = XpColors.Accent)
            else Text("✓", style = MaterialTheme.typography.labelSmall, color = XpColors.Positive)
            if (expanded) {
                Text("$streamLabel · ${steps.size}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            } else if (reduced) {
                Text(current, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier.weight(1f).height(20.dp), contentAlignment = Alignment.CenterStart) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            (slideInVertically(tween(200)) { it } + fadeIn(tween(200))) togetherWith
                                (slideOutVertically(tween(160)) { -it } + fadeOut(tween(160)))
                        },
                        label = "thought-line",
                    ) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text(if (expanded) "收起 ⌃" else "展开 ⌄", color = XpColors.InkSecondary, style = MaterialTheme.typography.labelSmall)
        }
        if (expanded) Column(Modifier.padding(top = XpSpace.s), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            steps.forEach { step -> Text("${if (step.running) "·" else "✓"} ${listOfNotNull(step.label, step.detail).joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary) }
        }
    }
}

/** Safe markdown subset: text only, no HTML, images or remote resource loads. Partial fences stay readable. */
@Composable
fun MessageMarkdown(text: String, modifier: Modifier = Modifier) {
    // Provider protocol tags sometimes leak from a model; they are never part of an answer.
    val lines = remember(text) { text.replace(Regex("</?(?:tool_calls?|tool_use|tool_code|function_calls?|function|invoke|parameter)\\b[^>]*>\\s*", RegexOption.IGNORE_CASE), "").lines() }
    SelectionContainer {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
            var code = false
            lines.forEach { line ->
                if (line.startsWith("```")) { code = !code }
                else {
                    val heading = line.startsWith("#") && line.takeWhile { it == '#' }.length <= 3
                    val display = if (heading) line.trimStart('#', ' ') else if (line.startsWith("- ")) "• ${line.drop(2)}" else line
                    val annotated = buildAnnotatedString {
                        var at = 0
                        val matches = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`").findAll(display)
                        matches.forEach { match ->
                            append(display.substring(at, match.range.first))
                            val bold = match.value.startsWith("**")
                            withStyle(SpanStyle(fontWeight = if (bold) FontWeight.SemiBold else null, fontFamily = if (bold) null else FontFamily.Monospace)) { append(match.groupValues[if (bold) 1 else 2]) }
                            at = match.range.last + 1
                        }
                        append(display.substring(at))
                    }
                    Text(annotated, modifier = if (code) Modifier.fillMaxWidth().background(XpColors.Fill).padding(horizontal = XpSpace.s, vertical = XpSpace.xs) else Modifier,
                        style = if (heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontFamily = if (code) FontFamily.Monospace else null, color = XpColors.Ink)
                }
            }
        }
    }
}
