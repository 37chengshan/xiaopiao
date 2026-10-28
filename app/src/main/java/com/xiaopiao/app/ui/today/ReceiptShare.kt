package com.xiaopiao.app.ui.today

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Share / save / copy for a rendered receipt card. Images go out through FileProvider; text is the fallback. */
object ReceiptShare {
    private const val CAPTION = "我的今日小票 🧾"

    /** Opens the system share sheet with the PNG; falls back to plain text if the image could not be written. */
    suspend fun share(context: Context, card: Bitmap?, fallbackText: String, fileKey: String) {
        val intent = runCatching { card?.let { imageIntent(context, it, fileKey) } }.getOrNull() ?: textIntent(fallbackText)
        launch(context, Intent.createChooser(intent, "分享今日小票"))
    }

    /** Saves the PNG to Pictures/今日小票 through MediaStore (Android 10+, no permission needed). Returns success. */
    suspend fun save(context: Context, card: Bitmap, fileKey: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "xiaopiao-$fileKey.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/今日小票")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            resolver.openOutputStream(uri)?.use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }.isSuccess
    }

    fun copyText(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("今日小票", text))
    }

    private suspend fun imageIntent(context: Context, card: Bitmap, fileKey: String): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "xiaopiao-$fileKey.png")
        file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, CAPTION)
            clipData = ClipData.newRawUri("小票", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun textIntent(text: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$CAPTION\n\n$text")
    }

    private fun launch(context: Context, intent: Intent) {
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
