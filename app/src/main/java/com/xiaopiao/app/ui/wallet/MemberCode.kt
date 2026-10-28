package com.xiaopiao.app.ui.wallet

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * 会员卡条码/二维码渲染（docs/APP-DESIGN.md §5.5）：zxing 只在本地把会员号画成位图，不联网、
 * 不上传会员号。二维码纠错用 M，条形码用 CODE_128（国内会员卡最常见）。
 */
object MemberCode {
    fun bitmap(text: String, format: com.xiaopiao.app.wallet.MemberCodeFormat, sizePx: Int): Bitmap? {
        if (text.isBlank()) return null
        val type = when (format) {
            com.xiaopiao.app.wallet.MemberCodeFormat.Qr -> BarcodeFormat.QR_CODE
            com.xiaopiao.app.wallet.MemberCodeFormat.Code128 -> BarcodeFormat.CODE_128
        }
        val width = if (type == BarcodeFormat.QR_CODE) sizePx else sizePx * 2
        val height = if (type == BarcodeFormat.QR_CODE) sizePx else sizePx / 2
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        return runCatching {
            val matrix: BitMatrix = MultiFormatWriter().encode(text, type, width, height, hints)
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until matrix.width) {
                    for (y in 0 until matrix.height) {
                        setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
            }
        }.getOrNull()
    }
}
