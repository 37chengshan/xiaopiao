package com.xiaopiao.app.merchant

import kotlin.math.abs
import kotlin.math.roundToInt

enum class InsightTone { Up, Down, Flat, Warn }

/** One sentence the 分析 screen shows. Every number is computed locally from the revenue book. */
enum class InsightKind { Decline, Week, Weekday, Ticket, Best }

data class MerchantInsight(val kind: InsightKind, val title: String, val body: String, val tone: InsightTone)

/**
 * Template-driven shop analysis. Offline by design: it reads [RevenueBook] numbers and writes sentences,
 * and it never invents a number it cannot compute from the owner's own rows.
 *
 * Cloud-model seam: when the app gains a server model, only the wording step is replaced — the numbers
 * keep coming from [RevenueBook] so a model can phrase a week but can never move a figure. No keys or
 * endpoints live here.
 */
object MerchantAi {
    private const val WINDOW_DAYS = 14

    fun draft(entries: List<RevenueEntry>, today: Long): List<MerchantInsight> {
        val book = RevenueBook.normalize(entries)
        if (book.isEmpty()) return emptyList()
        val out = mutableListOf<MerchantInsight>()

        val streak = RevenueBook.declineStreak(entries, today)
        if (streak >= 2) {
            val range = RevenueBook.declineRange(entries, today, streak)
            out += MerchantInsight(
                kind = InsightKind.Decline,
                title = "连续下滑",
                body = if (range != null) {
                    "已经连续 $streak 天比前一天少，从 ${money(range.first)} 落到 ${money(range.second)}。"
                } else {
                    "已经连续 $streak 天比前一天少。"
                },
                tone = InsightTone.Warn,
            )
        }

        val week = RevenueBook.weekToDateChange(entries, today)
        val weekDays = RevenueBook.window(entries, RevenueBook.weekStart(today), today).size
        if (week.nowCents > 0 || week.beforeCents > 0) {
            out += MerchantInsight(
                kind = InsightKind.Week,
                title = "本周进度",
                body = "本周 $weekDays 天共 ${money(week.nowCents)}，" +
                    (if (week.hasBase) "比上周同期${relative(week)}。" else "上周同期还没有记录。"),
                tone = toneOf(week),
            )
        }

        val weekdays = RevenueBook.weekdayAverages(entries, today)
        val known = weekdays.filter { it.days > 0 }
        if (known.size >= 2) {
            val high = known.maxBy { it.averageCents }
            val low = known.minBy { it.averageCents }
            val ratio = if (low.averageCents > 0) high.averageCents.toFloat() / low.averageCents else null
            out += MerchantInsight(
                kind = InsightKind.Weekday,
                title = "哪天最旺",
                body = "${high.label}平均 ${money(high.averageCents)} 最高，${low.label}平均 ${money(low.averageCents)} 最低" +
                    (ratio?.let { "，差 ${oneDecimal(it)} 倍。" } ?: "。"),
                tone = InsightTone.Flat,
            )
        }

        val ticket = RevenueBook.averageTicketChange(entries, today)
        if (ticket.nowCents > 0) {
            val diff = ticket.diffCents
            out += MerchantInsight(
                kind = InsightKind.Ticket,
                title = "客单价",
                body = "近 7 天客单价 ${money(ticket.nowCents)}，" + when {
                    !ticket.hasBase -> "前 7 天没有可比的订单数。"
                    diff == 0L -> "和前 7 天一样。"
                    else -> "比前 7 天${if (diff > 0) "高" else "低"} ${money(abs(diff))}" +
                        (RevenueBook.percentText(ticket.pct)?.removePrefix("+")?.removePrefix("-")
                            ?.let { "（$it）" } ?: "") + "。"
                },
                tone = toneOf(ticket),
            )
        }

        val best = RevenueBook.bestDay(entries, today, WINDOW_DAYS)
        val worst = RevenueBook.worstDay(entries, today, WINDOW_DAYS)
        if (best != null && best.revenueCents > 0) {
            out += MerchantInsight(
                kind = InsightKind.Best,
                title = "最好的一天",
                body = "${dayWords(best.dayStart)}卖了 ${money(best.revenueCents)}，是近 $WINDOW_DAYS 天最高的一天。" +
                    (worst?.takeIf { it.dayStart != best.dayStart && it.revenueCents < best.revenueCents }
                        ?.let { "最淡的 ${dayWords(it.dayStart)}只有 ${money(it.revenueCents)}。" } ?: ""),
                tone = InsightTone.Up,
            )
        }

        return out.take(5)
    }

    /** One line for the 经营日报小票, about the day the receipt is for. */
    fun receiptLine(entries: List<RevenueEntry>, day: Long): String {
        val entry = RevenueBook.entryFor(entries, day) ?: return "这天还没有记录，补上数字就能出日报。"
        val change = RevenueBook.sameWeekdayChange(entries, day)
        val tail = when {
            !change.hasBase -> "上周这一天没有记录，先留个底。"
            change.diffCents == 0L -> "和上周同一天持平。"
            else -> "比上周同一天${relative(change)}。"
        }
        return "这天做了 ${money(entry.revenueCents)}，$tail"
    }

    private fun toneOf(change: Change): InsightTone = when {
        !change.hasBase -> InsightTone.Flat
        change.diffCents > 0 -> InsightTone.Up
        change.diffCents < 0 -> InsightTone.Down
        else -> InsightTone.Flat
    }

    private fun relative(change: Change): String {
        val pct = RevenueBook.percentText(change.pct)?.removePrefix("+")?.removePrefix("-")
            ?: return "变化说不清"
        return if (change.diffCents >= 0) "多 $pct" else "少 ${pct.removePrefix("-")}"
    }

    private fun money(cents: Long): String = RevenueBook.money(cents)

    private fun dayWords(day: Long): String = "${RevenueBook.shortDate(day)}（${RevenueBook.weekdayLabel(day)}）"

    private fun oneDecimal(value: Float): String = ((value * 10).roundToInt() / 10f).toString()
}
