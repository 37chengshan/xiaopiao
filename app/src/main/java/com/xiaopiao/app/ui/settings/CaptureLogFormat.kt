package com.xiaopiao.app.ui.settings

import com.xiaopiao.app.ui.tr

enum class LogTone { GOOD, NEUTRAL, WARN }

data class LogResultLabel(val text: String, val tone: LogTone)

/** Turns the stored `capture_log.result` tag into friendly copy for the log viewer. */
object CaptureLogFormat {
    fun describe(result: String): LogResultLabel {
        val kind = result.substringBefore(':')
        val detail = result.substringAfter(':', "")
        return when (kind) {
            "PARSED" -> LogResultLabel(tr("已记账 · $detail", "Recorded · $detail"), LogTone.GOOD)
            "DUPLICATE" -> LogResultLabel(tr("重复，已跳过 · $detail", "Duplicate, skipped · $detail"), LogTone.NEUTRAL)
            "IGNORED" -> LogResultLabel(tr("已忽略 · $detail", "Ignored · $detail"), LogTone.NEUTRAL)
            "NEEDS_AMOUNT" -> LogResultLabel(tr("已记入账单，待填金额", "Added to the ledger — amount to fill in"), LogTone.GOOD)
            "UNPARSED" -> LogResultLabel(tr("没认出（可以反馈给开发者）", "Not recognised (you can report it)"), LogTone.WARN)
            else -> LogResultLabel(result, LogTone.NEUTRAL)
        }
    }
}
