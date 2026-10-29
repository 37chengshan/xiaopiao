package com.xiaopiao.app.ui.wallet

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.xiaopiao.app.wallet.RevealWindow
import kotlinx.coroutines.delay

/**
 * 复制卡号（docs/APP-DESIGN.md §5.5）：Android 13+ 标记为敏感内容（系统不预览、不进剪贴板历史），
 * 60 秒后自动清空，且只清掉我们刚放进去的那一条。
 */
object SensitiveClipboard {
    fun copy(context: Context, label: String, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clip.description.extras = extras
        }
        manager.setPrimaryClip(clip)
    }

    /** 到点后清空；内容已经被别人覆盖时不动。 */
    suspend fun autoClear(context: Context, copiedText: String, clearAfterMs: Long = RevealWindow.CLIP_CLEAR_MS) {
        delay(clearAfterMs)
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val current = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (current == copiedText) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
