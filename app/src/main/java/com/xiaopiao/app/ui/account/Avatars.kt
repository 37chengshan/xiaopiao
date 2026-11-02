package com.xiaopiao.app.ui.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.network.AccountClient
import com.xiaopiao.app.network.RemoteAvatar
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** The 12 preset avatars (`p1`…`p12`, server/src/avatar.mjs): a soft two-stop gradient and an animal. */
object AvatarPresets {
    data class Preset(val id: String, val emoji: String, val top: Long, val bottom: Long)

    val all = listOf(
        Preset("p1", "🐱", 0xFFFFD6A5, 0xFFFFADAD),
        Preset("p2", "🐶", 0xFFCDE7FF, 0xFF9EC9FF),
        Preset("p3", "🦊", 0xFFFFE0B2, 0xFFFFB074),
        Preset("p4", "🐼", 0xFFE8EAED, 0xFFC6CCD3),
        Preset("p5", "🐨", 0xFFD9E8E3, 0xFFA9C9BE),
        Preset("p6", "🐯", 0xFFFFF1B8, 0xFFFFCF5C),
        Preset("p7", "🦁", 0xFFFFE7C2, 0xFFE9B872),
        Preset("p8", "🐸", 0xFFDDF6D2, 0xFFA7DE8F),
        Preset("p9", "🐵", 0xFFEFDCCB, 0xFFD3AE8B),
        Preset("p10", "🐰", 0xFFFDE2F0, 0xFFF5B5D3),
        Preset("p11", "🐻", 0xFFE9DCCF, 0xFFC5A88A),
        Preset("p12", "🐧", 0xFFDDE6FF, 0xFFB0C1F5),
    )

    fun byId(id: String?): Preset? = all.firstOrNull { it.id == id }
}

/** Photos fetched from /v1/avatars, by their versioned URL (a replaced photo is a new URL). */
private object AvatarCache {
    val bitmaps = mutableMapOf<String, ImageBitmap>()
}

/**
 * One avatar everywhere: a preset, the uploaded photo, or the initial as a fallback. Photos load through the
 * signed-in session (they are private to the user and their circles).
 */
@Composable
fun XpAvatar(
    avatar: RemoteAvatar?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fallbackBackground: Color = XpColors.AccentSoft,
    fallbackInk: Color = XpColors.Accent,
) {
    val preset = AvatarPresets.byId(avatar?.preset)
    val url = avatar?.url
    val account = LocalAccount.current
    var photo by remember(url) { mutableStateOf(url?.let { AvatarCache.bitmaps[it] }) }
    LaunchedEffect(url, account) {
        if (url == null || photo != null || account == null) return@LaunchedEffect
        photo = runCatching { account.fetchBytes(url) }.getOrNull()
            ?.let { bytes -> withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } }
            ?.also { AvatarCache.bitmaps[url] = it }
    }
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        when {
            photo != null -> Image(photo!!, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            preset != null -> Box(
                Modifier.size(size).background(Brush.verticalGradient(listOf(Color(preset.top), Color(preset.bottom)))),
                contentAlignment = Alignment.Center,
            ) { Text(preset.emoji, fontSize = (size.value * 0.52f).sp) }
            else -> Box(Modifier.size(size).background(fallbackBackground), contentAlignment = Alignment.Center) {
                Text(
                    name.take(1).uppercase(),
                    style = if (size >= 34.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                    color = fallbackInk, maxLines = 1,
                )
            }
        }
    }
}

/** The session used to fetch avatar photos; provided at the app root. */
val LocalAccount = androidx.compose.runtime.staticCompositionLocalOf<AccountClient?> { null }

/** Square-crops, scales to 256px and JPEG-encodes under the server's 256 KiB limit. */
internal suspend fun avatarJpeg(context: android.content.Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: error(tr("读不了这张图片", "Couldn't read that image"))
    val side = minOf(source.width, source.height)
    val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
    var quality = 88
    var bytes: ByteArray
    do {
        bytes = ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }
        quality -= 12
    } while (bytes.size > 240 * 1024 && quality > 30)
    bytes
}

/** Choose a preset, pick a photo, or go back to the initial. Every choice saves at once and says so. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AvatarPickerSheet(client: AccountClient, current: RemoteAvatar?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun run(action: suspend () -> Unit) {
        busy = true; message = null; failed = false
        scope.launch {
            runCatching { action() }
                .onSuccess { message = tr("头像已更新", "Avatar updated") }
                .onFailure { failed = true; message = it.message ?: tr("上传失败，请重试", "Upload failed. Try again") }
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) run { client.setAvatarImage(avatarJpeg(context, uri)) }
    }

    XpSheet(onDismiss = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l)) {
            Text(tr("更换头像", "Change avatar"), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Text(
                tr("头像只给你自己和同圈子的人看。", "Only you and people in your circles see it."),
                style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary,
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = XpSpace.l),
                horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
                verticalArrangement = Arrangement.spacedBy(XpSpace.m),
                maxItemsInEachRow = 4,
            ) {
                AvatarPresets.all.forEach { p ->
                    val selected = current?.preset == p.id
                    Box(
                        Modifier.size(60.dp)
                            .then(if (selected) Modifier.border(2.5.dp, XpColors.Accent, CircleShape) else Modifier)
                            .padding(4.dp)
                            .pressableClick(enabled = !busy, scaleDown = 0.92f) { run { client.setAvatarPreset(p.id) } },
                        contentAlignment = Alignment.Center,
                    ) { XpAvatar(RemoteAvatar(p.id, null), "", size = 52.dp) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = XpSpace.l), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                XpButton(
                    if (busy) tr("上传中…", "Uploading…") else tr("从相册选择", "Choose a photo"),
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy, modifier = Modifier.weight(1f),
                )
                if (current != null) {
                    XpButton(tr("移除", "Remove"), { run { client.clearAvatar() } }, style = XpButtonStyle.Secondary, enabled = !busy, modifier = Modifier.weight(1f))
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) XpColors.Negative else XpColors.Positive, modifier = Modifier.padding(top = XpSpace.s))
            }
        }
    }
}
