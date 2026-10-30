package com.xiaopiao.app.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.motion.XpTiming
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bottom input card (docs/APP-DESIGN.md §3.4; tt#71). One round button owns the right side: voice while the
 * field is empty, send once there is text or an attachment, stop while a reply streams — no second mic icon.
 * The card is one row when idle and opens to text + tool row the moment there is something to say; the text
 * grows to six lines and then scrolls. Enter inserts a newline (the button sends), the placeholder hides as
 * soon as the caret appears, and staged attachments sit in a chip row above the card.
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
    attachments: List<ChatAttachment> = emptyList(),
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val reduced = com.xiaopiao.app.ui.components.rememberReducedMotion()
    val plan = ChatInput.plan(value, streaming, attachments.size)
    val shape = RoundedCornerShape(30.dp)

    Column(modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s)) {
        if (attachments.isNotEmpty()) {
            AttachmentChips(attachments, onRemoveAttachment)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = shape, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
                .clip(shape)
                .background(XpColors.Card)
                .then(if (reduced) Modifier else Modifier.animateContentSize(tween(XpTiming.PopOut, easing = XpEase.out))),
        ) {
            if (!plan.expanded) {
                // Idle: one 56dp row. Tapping the placeholder focuses the field; the button is voice.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = XpSpace.s, vertical = XpSpace.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlusButton(onPlus)
                    Field(
                        value = value,
                        onValueChange = onValueChange,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f).padding(horizontal = XpSpace.s),
                    )
                    PrimaryButton(plan.primary, onSend = onSend, onVoice = onVoice, onStop = onStop)
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(start = XpSpace.m, end = XpSpace.s, top = XpSpace.s, bottom = 6.dp)) {
                    Field(
                        value = value,
                        onValueChange = onValueChange,
                        focusRequester = focusRequester,
                        modifier = Modifier.fillMaxWidth().padding(end = XpSpace.xs),
                    )
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlusButton(onPlus)
                        Box(Modifier.weight(1f))
                        PrimaryButton(plan.primary, onSend = onSend, onVoice = onVoice, onStop = onStop)
                    }
                }
            }
        }
    }
}

/** The one round action: waveform (voice) → ↑ (send) → ■ (stop). Accent fill, white glyph, fixed 44dp. */
@Composable
private fun PrimaryButton(
    action: ChatPrimaryAction,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onStop: () -> Unit,
) {
    val reduced = com.xiaopiao.app.ui.components.rememberReducedMotion()
    val (icon, description, onClick) = when (action) {
        ChatPrimaryAction.Voice -> Triple(Icons.Outlined.GraphicEq, "用语音记一笔", onVoice)
        ChatPrimaryAction.Send -> Triple(Icons.Outlined.ArrowUpward, "发送", onSend)
        ChatPrimaryAction.Stop -> Triple(Icons.Outlined.Stop, "停止回答", onStop)
    }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(XpColors.Accent)
            .pressableClick(scaleDown = 0.94f, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Icon change crossfades + scales in place; the circle itself never moves or resizes.
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) else (fadeIn(tween(170, easing = XpEase.out)) + scaleIn(tween(170, easing = XpEase.out), initialScale = 0.72f)) togetherWith
                    (fadeOut(tween(140, easing = XpEase.out)) + scaleOut(tween(140, easing = XpEase.out), targetScale = 0.72f))
            },
            label = "primaryIcon",
        ) { target ->
            Icon(target, contentDescription = description, tint = XpColors.OnAccent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PlusButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).pressableClick(scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "添加图片或文件", tint = XpColors.Ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = XpColors.Ink, fontSize = 17.sp, lineHeight = 24.sp),
        cursorBrush = SolidColor(XpColors.Accent),
        // Six lines, then the field scrolls inside the card (never grows taller than that).
        minLines = 1,
        maxLines = ChatInput.MAX_LINES,
        // Enter inserts a newline; the round button is the only way to send.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                // Hide the placeholder as soon as the caret is present, so the two never overlap.
                if (value.isEmpty() && !focused) {
                    Text(
                        "问问你的账本…",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
                        color = XpColors.InkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

/** Staged attachments: image thumbnails or a file chip, each with its own remove button. */
@Composable
private fun AttachmentChips(attachments: List<ChatAttachment>, onRemove: (ChatAttachment) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = XpSpace.s),
        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        attachments.forEach { attachment ->
            AttachmentChip(attachment, onRemove)
        }
    }
}

@Composable
private fun AttachmentChip(attachment: ChatAttachment, onRemove: (ChatAttachment) -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, attachment.uriString) {
        value = if (!attachment.isImage) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(attachment.uriString))?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(Modifier.size(64.dp)) {
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).background(XpColors.Fill),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumbnail
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else if (attachment.isImage) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = XpColors.InkTertiary, modifier = Modifier.size(20.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = XpColors.InkSecondary, modifier = Modifier.size(20.dp))
                    Text(
                        attachment.name?.substringAfterLast('/')?.take(10) ?: "文件",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = XpColors.InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).clip(CircleShape)
                .background(XpColors.Ink.copy(alpha = 0.72f))
                .pressableClick(scaleDown = 0.9f) { onRemove(attachment) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "移除附件", tint = XpColors.Canvas, modifier = Modifier.size(12.dp))
        }
    }
}

/** The attachment sheet's motion budget (kept next to the composer so both stay consistent). */
internal val ComposerChipSpring = spring<Float>(dampingRatio = 1f, stiffness = XpTiming.Press * 4f)
