package com.xiaopiao.app.ui.record

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn

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

/**
 * A draft amount that can hold one pending operation (`12 ＋ 8`), which is what the `−` / `＋` keys on the
 * keypad do: a simple two-term sum, never a calculator history. Kept pure so the keypad rules are unit-tested.
 */
data class RecordDraft(
    val amount: String = "",
    val left: String? = null,
    val op: Char? = null,
) {
    val isEmpty: Boolean get() = amount.isEmpty() && left == null

    /** What the big number shows: `12＋8`, `12＋`, or just `0` (full-width operator glyphs). */
    val expression: String
        get() {
            val symbol = RecordDraftMath.glyph(op ?: '+')
            return when {
                op == null -> amount.ifEmpty { "0" }
                amount.isEmpty() -> "$left$symbol"
                else -> "$left$symbol$amount"
            }
        }
}

/** Two-term arithmetic plus the draft rules the keypad needs. */
object RecordDraftMath {
    const val PLUS = "＋"
    const val MINUS = "−"

    fun press(draft: RecordDraft, key: String): RecordDraft = when (key) {
        PLUS -> applyOperator(draft, '+')
        MINUS -> applyOperator(draft, '-')
        RecordAmount.BACKSPACE -> when {
            draft.amount.isNotEmpty() -> draft.copy(amount = RecordAmount.press(draft.amount, key))
            draft.op != null -> RecordDraft(amount = draft.left.orEmpty()) // cancels the pending operation
            else -> draft
        }
        else -> draft.copy(amount = RecordAmount.press(draft.amount, key))
    }

    /** Display glyph for an operator; the model itself keeps ASCII so the arithmetic stays trivial. */
    fun glyph(op: Char): String = if (op == '-') MINUS else PLUS

    private fun applyOperator(draft: RecordDraft, op: Char): RecordDraft {
        if (draft.amount.isEmpty()) return if (draft.op == null) draft else draft.copy(op = op)
        if (draft.op == null) return draft.copy(left = draft.amount, op = op, amount = "")
        // Fold the finished term, then start the next one: 12＋8 → 20＋ .
        val folded = cents(draft)
        return RecordDraft(amount = "", left = yuanText(folded), op = op)
    }

    /** Value of the whole expression in cents; a missing second term counts as 0. */
    fun cents(draft: RecordDraft): Cents {
        val right = RecordAmount.toCents(draft.amount)
        val left = draft.left
        if (left == null || draft.op == null) return right
        val base = RecordAmount.toCents(left)
        return when (draft.op) {
            '+' -> base + right
            '-' -> (base - right).coerceAtLeast(0L)
            else -> right
        }
    }

    /** Left operand as keypad text ("2000" cents → "20"), so a folded term can be typed on. */
    private fun yuanText(cents: Cents): String {
        val major = cents / 100
        val minor = cents % 100
        return if (minor == 0L) "$major" else (major.toString() + "." + minor.toString().padStart(2, '0').trimEnd('0'))
    }
}

/** Turns a draft into a [Txn]; null when there is nothing to save (0 元). */
object RecordComposer {
    fun txn(
        draft: RecordDraft,
        direction: Direction,
        category: Category,
        merchant: String,
        at: Long,
    ): Txn? {
        val cents = RecordDraftMath.cents(draft)
        if (cents <= 0L) return null
        return Txn(
            amountCents = cents,
            direction = direction,
            merchant = merchant.trim().ifBlank { category.label },
            category = category,
            occurredAt = at,
            source = Source.MANUAL,
        )
    }
}
