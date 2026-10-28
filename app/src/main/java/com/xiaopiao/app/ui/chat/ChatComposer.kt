package com.xiaopiao.app.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * The bottom input card (docs/APP-DESIGN.md §3.4, ChatGPT-shaped):
 * empty = one 56dp row (＋ · placeholder · mic · one round action);
 * typing = two rows (text above, tools below); streaming = the round action becomes ■ stop.
 *
 * Everything colour-wise stays in our system: white card, ink round button, no brand orange, no gradient.
 */
@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onStop: () -> Unit,
    onPlus: () -> Unit,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val mode = ChatInput.mode(value, streaming)
    val action = ChatInput.primary(mode)
    val expanded = ChatInput.isExpanded(mode)
    val reduced = rememberReducedMotion()
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = XpSpace.l, vertical = XpSpace.s)
            .shadow(elevation = 10.dp, shape = shape, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
            .clip(shape)
            .background(XpColors.Card)
            .animateContentSize(tween(220, easing = XpEase.out)),
    ) {
        if (!expanded) {
            // One row: tap anywhere focuses the field; the round button is voice.
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(start = XpSpace.s, end = XpSpace.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.Outlined.Add, "添加", XpColors.Ink, onPlus, size = 40)
                Field(
                    value = value,
                    onValueChange = onValueChange,
                    onSend = onSend,
                    focusRequester = focusRequester,
                    placeholder = "问问你的账本…",
                    singleLineCentered = true,
                    modifier = Modifier.weight(1f).padding(horizontal = XpSpace.s),
                )
                CircleIconButton(Icons.Outlined.Mic, "按住说话", XpColors.Ink, onVoice, size = 40, iconSize = 20)
                PrimaryButton(action, onSend = onSend, onVoice = onVoice, onStop = onStop)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(start = XpSpace.m, end = XpSpace.s, top = XpSpace.s, bottom = 6.dp)) {
                Field(
                    value = value,
                    onValueChange = onValueChange,
                    onSend = onSend,
                    focusRequester = focusRequester,
                    placeholder = "问问你的账本…",
                    singleLineCentered = false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp, max = 132.dp).padding(end = XpSpace.xs),
                )
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.xs),
                ) {
                    CircleIconButton(Icons.Outlined.Add, "添加", XpColors.Ink, onPlus, size = 40)
                    Box(Modifier.weight(1f))
                    CircleIconButton(Icons.Outlined.Mic, "按住说话", XpColors.Ink, onVoice, size = 40, iconSize = 20)
                    PrimaryButton(action, onSend = onSend, onVoice = onVoice, onStop = onStop)
                }
            }
        }
    }
}

/** The one round action: waveform (voice) → ↑ (send) → ■ (stop). Ink solid, white glyph, 44dp. */
@Composable
private fun PrimaryButton(
    action: ChatPrimaryAction,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onStop: () -> Unit,
) {
    val (icon, description, onClick) = when (action) {
        ChatPrimaryAction.Voice -> Triple(Icons.Outlined.GraphicEq, "用语音记一笔", onVoice)
        ChatPrimaryAction.Send -> Triple(Icons.Outlined.ArrowUpward, "发送", onSend)
        ChatPrimaryAction.Stop -> Triple(Icons.Outlined.Stop, "停止回答", onStop)
    }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(XpColors.Primary)
            .pressableClick(scaleDown = 0.94f, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = XpColors.OnPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    size: Int,
    iconSize: Int = 22,
) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .pressableClick(scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester?,
    placeholder: String,
    singleLineCentered: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
            color = XpColors.Ink,
            fontSize = 17.sp,
        ),
        cursorBrush = SolidColor(XpColors.Accent),
        maxLines = if (singleLineCentered) 1 else 5,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }),
        decorationBox = { inner ->
            Box(contentAlignment = if (singleLineCentered) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        color = XpColors.InkTertiary,
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                    )
                }
                inner()
            }
        },
    )
}
