package com.xiaopiao.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.xiaopiao.app.importer.BillCsvParser
import com.xiaopiao.app.importer.BillFormat
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The ＋ sheet (tt#71): three ways to hand the assistant something, all of them local.
 * Camera and gallery produce images that are OCR'd on this device; files go through the CSV importer.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
) {
    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(bottom = XpSpace.xl)) {
            Text(
                "添加到对话",
                style = MaterialTheme.typography.titleMedium,
                color = XpColors.Ink,
                modifier = Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s),
            )
            AttachmentRow(Icons.Outlined.CameraAlt, "拍一张小票", "用相机拍下来，本机识别") { onCamera(); onDismiss() }
            AttachmentRow(Icons.Outlined.PhotoLibrary, "从相册选", "最多 4 张账单截图") { onGallery(); onDismiss() }
            AttachmentRow(Icons.Outlined.Description, "选择文件", "CSV 账单，图片也可以") { onFile(); onDismiss() }
        }
    }
}

@Composable
private fun AttachmentRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressableClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(XpColors.Fill),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = XpColors.Ink, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = XpSpace.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), color = XpColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
        }
    }
}

/**
 * Runs ML Kit text recognition on a content uri **without leaving the device** and returns the raw text.
 * The image itself is never copied into the ledger or sent anywhere; only the parsed numbers are kept.
 */
suspend fun recognizeReceiptText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val image = InputImage.fromFilePath(context, uri)
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result -> continuation.resume(result.text) }
                .addOnFailureListener { continuation.resume(null) }
        }
    } catch (e: Exception) {
        null
    } finally {
        runCatching { recognizer.close() }
    }
}

/** Reads a CSV bill chosen through SAF as text; null when it cannot be opened. */
suspend fun readCsvText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        }
    }.getOrNull()
}

/** A parsed-but-not-yet-imported CSV attachment. Importing happens only after the user confirms. */
@kotlinx.serialization.Serializable
data class ChatCsvPreview(
    val label: String,
    val format: BillFormat,
    val rows: Int,
    val totalCents: Long,
    val failed: Int,
    val skipped: Int,
    val rawText: String,
) {
    companion object {
        fun from(label: String, text: String): ChatCsvPreview? {
            val parsed = BillCsvParser.parse(text)
            if (parsed.format == BillFormat.UNKNOWN || parsed.txns.isEmpty()) return null
            return ChatCsvPreview(
                label = label,
                format = parsed.format,
                rows = parsed.txns.size,
                totalCents = parsed.txns.filter { it.direction == com.xiaopiao.app.domain.Direction.OUT }
                    .sumOf { it.amountCents },
                failed = parsed.failed,
                skipped = parsed.skipped,
                rawText = text,
            )
        }
    }
}
