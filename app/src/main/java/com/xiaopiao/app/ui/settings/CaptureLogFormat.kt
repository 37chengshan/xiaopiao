package com.xiaopiao.app.ui.settings

enum class LogTone { GOOD, NEUTRAL, WARN }

data class LogResultLabel(val text: String, val tone: LogTone)

/** Turns the stored `capture_log.result` tag into friendly copy for the log viewer. */
object CaptureLogFormat {
    fun describe(result: String): LogResultLabel {
        val kind = result.substringBefore(':')
        val detail = result.substringAfter(':', "")
        return when (kind) {
            "PARSED" -> LogResultLabel("已记账 · $detail", LogTone.GOOD)
            "DUPLICATE" -> LogResultLabel("重复，已跳过 · $detail", LogTone.NEUTRAL)
            "IGNORED" -> LogResultLabel("已忽略 · $detail", LogTone.NEUTRAL)
            "UNPARSED" -> LogResultLabel("没认出（可以反馈给开发者）", LogTone.WARN)
            else -> LogResultLabel(result, LogTone.NEUTRAL)
        }
    }
}
