package com.xiaopiao.app.capture

/**
 * Pulls the four digits a payment notification uses to name the card (docs/APP-DESIGN.md §5.5).
 * Pure string work so the notification pipeline stays testable on the JVM.
 */
object CardTail {
    private val patterns = listOf(
        Regex("""尾号\s*(\d{4})"""),
        Regex("""(?:卡号|账号)(?:后四位|末四位|最后四位)?\s*[:：]?\s*(?:[*•\s]*)(\d{4})"""),
        Regex("""[*•]{4,}\s*(\d{4})"""),
        Regex("""\((\d{4})\)"""),
        Regex("""（(\d{4})）"""),
    )

    /** Last occurrence wins: the tail usually closes the sentence ("… 招商银行(1234)"). */
    fun extract(text: String): String? {
        if (text.isBlank()) return null
        patterns.forEach { pattern ->
            val found = pattern.findAll(text).lastOrNull()?.groupValues?.get(1)
            if (found != null && found.length == 4) return found
        }
        return null
    }
}
