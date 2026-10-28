package com.xiaopiao.app.merchant

import java.util.Calendar

/** One data line of an imported file, kept even when it failed so the preview can explain why. */
data class RevenueCsvRow(
    val line: Int,
    val raw: String,
    val dayStart: Long? = null,
    val revenueCents: Long? = null,
    val orders: Int = 0,
    val note: String = "",
    val error: String? = null,
    /** The day already exists in the book (or appeared earlier in the same file). */
    val duplicate: Boolean = false,
) {
    val ok: Boolean get() = error == null
}

/** What an import would change, before anything is written. */
data class RevenueCsvPreview(
    val rows: List<RevenueCsvRow>,
    val fileName: String = "",
) {
    val importable: List<RevenueCsvRow> get() = rows.filter { it.ok && !it.duplicate }
    val duplicates: List<RevenueCsvRow> get() = rows.filter { it.ok && it.duplicate }
    val invalid: List<RevenueCsvRow> get() = rows.filterNot { it.ok }

    val isEmpty: Boolean get() = rows.isEmpty()
    val importTotalCents: Long get() = importable.sumOf { it.revenueCents ?: 0L }
    val importDays: Int get() = importable.size
}

/**
 * A tolerant reader for a shop's own daily totals: `date,revenue,orders[,note]`.
 * Accepts `2026-10-27,1280.5,86`, `2026/10/27,"1,280.50",86`, `10/27,1280`, a BOM, a header line,
 * blank lines and `#` comments. Never guesses a day: anything unreadable is reported, not imported.
 */
object RevenueCsv {
    private const val MAX_ORDERS = 100_000

    private val fullDate = Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$")
    private val shortDate = Regex("^(\\d{1,2})[-/.](\\d{1,2})$")
    private val money = Regex("^(\\d{1,12})(?:\\.(\\d{1,6}))?$")

    fun parse(text: String, todayStart: Long, existingDays: Set<Long> = emptySet(), fileName: String = ""): RevenueCsvPreview {
        val rows = mutableListOf<RevenueCsvRow>()
        val seen = existingDays.toMutableSet()
        var headerChecked = false

        text.removePrefix("\uFEFF").split("\r\n", "\n", "\r").forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val fields = splitRow(line)
            if (!headerChecked) {
                headerChecked = true
                if (looksLikeHeader(fields)) return@forEachIndexed
            }

            val parsed = parseRow(index + 1, line, fields, todayStart)
            if (parsed.ok) {
                val duplicate = parsed.dayStart in seen
                if (!duplicate) seen += parsed.dayStart!!
                rows += parsed.copy(duplicate = duplicate)
            } else {
                rows += parsed
            }
        }
        return RevenueCsvPreview(rows, fileName)
    }

    /** Entries for the rows the preview would actually add. CSV rows are user-entered, not demo data. */
    fun entries(preview: RevenueCsvPreview): List<RevenueEntry> =
        preview.importable.map {
            RevenueEntry(
                dayStart = it.dayStart!!,
                revenueCents = it.revenueCents!!,
                orders = it.orders,
                note = it.note,
                source = RevenueSource.CSV,
            )
        }

    private fun parseRow(line: Int, raw: String, fields: List<String>, todayStart: Long): RevenueCsvRow {
        fun fail(message: String) = RevenueCsvRow(line, raw, error = message)
        if (fields.size < 2) return fail("至少要有「日期,营收」两列")

        val day = parseDay(fields[0], todayStart) ?: return fail("日期看不懂（用 2026-10-27 这种写法）")
        if (day > todayStart) return fail("日期在未来")
        val revenue = parseMoney(fields[1]) ?: return fail("营收看不懂（用 1280.5 这种写法）")
        if (revenue < 0L) return fail("营收不能是负数")

        val ordersText = fields.getOrNull(2)?.trim().orEmpty()
        val orders = if (ordersText.isEmpty()) 0 else ordersText.toIntOrNull() ?: return fail("订单数看不懂")
        if (orders < 0 || orders > MAX_ORDERS) return fail("订单数不合理")

        return RevenueCsvRow(
            line = line,
            raw = raw,
            dayStart = day,
            revenueCents = revenue,
            orders = orders,
            note = fields.getOrNull(3)?.trim()?.trim('"').orEmpty().take(40),
        )
    }

    private fun looksLikeHeader(fields: List<String>): Boolean {
        val head = fields.take(2).joinToString("").lowercase()
        if (head.contains("日期") || head.contains("date")) return true
        return fields.size >= 2 && fields[0].any { it.isLetter() } && parseDay(fields[0], 0L) == null
    }

    /** `2026-10-27`, `2026/1/5`, `2026.1.5`, `10/27`, `10-27` → day-start, or null. */
    fun parseDay(text: String, todayStart: Long): Long? {
        val t = text.trim().trim('"').replace(" ", "")
        val thisYear = Calendar.getInstance().apply { timeInMillis = todayStart }.get(Calendar.YEAR)
        val match = fullDate.find(t)
        val year: Int
        val month: Int
        val day: Int
        if (match != null) {
            year = match.groupValues[1].toInt()
            month = match.groupValues[2].toInt()
            day = match.groupValues[3].toInt()
        } else {
            val short = shortDate.find(t) ?: return null
            year = thisYear
            month = short.groupValues[1].toInt()
            day = short.groupValues[2].toInt()
        }
        if (month !in 1..12 || day !in 1..31) return null
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        // Calendar is lenient: 2026-02-31 would silently roll into March, so read the fields back.
        if (calendar.get(Calendar.YEAR) != year ||
            calendar.get(Calendar.MONTH) != month - 1 ||
            calendar.get(Calendar.DAY_OF_MONTH) != day
        ) return null
        return calendar.timeInMillis
    }

    /** `1280.5` → 128050, `¥1,280.50` → 128050, `1280.567` → rounds to the nearest cent. Null when unreadable. */
    fun parseMoney(text: String): Long? {
        var t = text.trim().trim('"').trim()
        if (t.isEmpty()) return null
        val negative = t.startsWith("-")
        t = t.removePrefix("-").removePrefix("¥").removePrefix("￥").replace(",", "").replace(" ", "")
        val match = money.find(t) ?: return null
        val whole = match.groupValues[1].toLongOrNull() ?: return null
        val fracText = match.groupValues[2]
        val frac = when {
            fracText.isEmpty() -> 0L
            fracText.length == 1 -> fracText.toLong() * 10
            fracText.length == 2 -> fracText.toLong()
            else -> (fracText.take(3).toLong() + 5) / 10
        }
        val cents = whole * 100 + frac
        return if (negative) -cents else cents
    }

    /** Comma split with `"…"` quoting and `""` escapes; tolerant of ragged rows. */
    private fun splitRow(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"'); i++
                } else quoted = !quoted
                c == ',' && !quoted -> { out += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }
}
