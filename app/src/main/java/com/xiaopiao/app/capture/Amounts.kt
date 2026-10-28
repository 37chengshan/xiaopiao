package com.xiaopiao.app.capture

import com.xiaopiao.app.domain.Cents
import java.math.BigDecimal
import java.math.RoundingMode

/** Shared money parsing: "¥1,234.5" -> 123450 cents. */
object Amounts {
    private val clean = Regex("""[¥￥,元\s]""")
    private val number = Regex("""\d+(\.\d+)?""")

    /** Returns cents, or null when [raw] is not a plain positive-or-zero number once symbols are stripped. */
    fun toCents(raw: String): Cents? {
        val s = clean.replace(raw, "")
        if (!number.matches(s) || s.length > 12) return null
        return BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}

/** Dedupe keys: stable across repeated posts of the same notification / re-imports. */
object DedupeKeys {
    private const val MINUTE_MS = 60_000L

    /** package + direction + amount + minute bucket + merchant */
    fun notification(pkg: String, directionTag: String, amountCents: Cents, postedAt: Long, merchant: String): String =
        "$pkg|$directionTag|$amountCents|${postedAt / MINUTE_MS}|$merchant"
}
