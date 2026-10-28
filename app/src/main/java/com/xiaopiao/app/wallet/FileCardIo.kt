package com.xiaopiao.app.wallet

import android.content.Context
import java.io.File

/** 卡包文件落在 app 私有目录的 `wallet/cards.json`，备份规则把整个 wallet/ 排除（§5.5）。 */
class FileCardIo(private val file: File) : CardIo {
    override fun read(): String? = if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    override fun write(text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    companion object {
        fun inAppFiles(context: Context): FileCardIo = FileCardIo(File(context.applicationContext.filesDir, "wallet/cards.json"))
    }
}
