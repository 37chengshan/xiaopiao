package com.xiaopiao.app.ui.record

import com.xiaopiao.app.domain.Cents

/**
 * Keypad amount entry kept as text so "5." and "0.0" stay editable.
 * Conversion goes straight from the digit string to [Cents]; no Double is involved.
 */
internal object RecordAmount {
    private const val MAX_INTEGER_DIGITS = 7
    const val BACKSPACE = "⌫"

    fun press(current: String, key: String): String = when (key) {
        BACKSPACE -> current.dropLast(1)
        "." -> when {
            '.' in current -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        else -> if (key.length == 1 && key[0].isDigit()) appendDigit(current, key) else current
    }

    private fun appendDigit(current: String, digit: String): String {
        val dot = current.indexOf('.')
        return when {
            dot >= 0 -> if (current.length - dot - 1 >= 2) current else current + digit
            current == "0" -> digit
            current.length >= MAX_INTEGER_DIGITS -> current
            else -> current + digit
        }
    }

    /** "18.5" -> 1850, "18.55" -> 1855, "0." -> 0. Never rounds through a floating-point type. */
    fun toCents(text: String): Cents {
        val trimmed = text.trim().trimEnd('.')
        if (trimmed.isEmpty()) return 0L
        val dot = trimmed.indexOf('.')
        val majorText = (if (dot >= 0) trimmed.substring(0, dot) else trimmed).ifEmpty { "0" }
        val fractionText = if (dot >= 0) trimmed.substring(dot + 1) else ""
        val major = majorText.toLongOrNull() ?: return 0L
        val fraction = (fractionText.take(2) + "00").take(2).toLongOrNull() ?: 0L
        return major * 100L + fraction
    }

    fun display(text: String): String = text.ifEmpty { "0" }
}
