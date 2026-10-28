package com.xiaopiao.app.ui.today

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.xiaopiao.app.ui.theme.XpColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Shares a rendered receipt as a PNG through FileProvider; falls back to plain text if anything fails. */
object ReceiptShare {
    private const val CAPTION = "我的今日小票 🧾"

    /** Frames the receipt on the canvas colour with a soft shadow and a tiny signature. */
    fun frame(receipt: Bitmap): Bitmap {
        val pad = 56
        val out = Bitmap.createBitmap(receipt.width + pad * 2, receipt.height + pad * 2 + 40, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(XpColors.Canvas.toArgb())
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x00000000; setShadowLayer(26f, 0f, 10f, 0x33000000)
        }
        c.drawRect(RectF(pad.toFloat(), pad.toFloat(), (pad + receipt.width).toFloat(), (pad + receipt.height).toFloat()), shadow)
        c.drawBitmap(receipt, pad.toFloat(), pad.toFloat(), null)
        val sign = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = XpColors.InkSecondary.toArgb(); textSize = 26f; textAlign = Paint.Align.CENTER
        }
        c.drawText("今日小票 · 每天印一张", out.width / 2f, out.height - 28f, sign)
        return out
    }

    suspend fun share(context: Context, image: ImageBitmap?, fallbackText: String, fileKey: String) {
        val intent = runCatching { image?.let { imageIntent(context, it.asAndroidBitmap(), fileKey) } }.getOrNull()
            ?: textIntent(fallbackText)
        launch(context, Intent.createChooser(intent, "分享今日小票"))
    }

    private suspend fun imageIntent(context: Context, receipt: Bitmap, fileKey: String): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "xiaopiao-$fileKey.png")
        val framed = frame(receipt)
        file.outputStream().use { framed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, CAPTION)
            clipData = android.content.ClipData.newRawUri("小票", uri)
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
