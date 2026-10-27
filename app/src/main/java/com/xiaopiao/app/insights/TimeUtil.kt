package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar

private fun cal(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }

fun hourOf(ms: Long): Int = cal(ms).get(Calendar.HOUR_OF_DAY)

/** Monday = 0 … Sunday = 6. */
fun weekdayIndex(ms: Long): Int = (cal(ms).get(Calendar.DAY_OF_WEEK) + 5) % 7

fun isWeekend(ms: Long): Boolean = weekdayIndex(ms) >= 5

fun addDays(ms: Long, n: Int): Long = cal(ms).apply { add(Calendar.DAY_OF_YEAR, n) }.timeInMillis

fun addMonths(ms: Long, n: Int): Long = cal(ms).apply { add(Calendar.MONTH, n) }.timeInMillis

/** Monday 00:00 of the week containing [ms]. */
fun startOfWeek(ms: Long): Long = addDays(startOfDay(ms), -weekdayIndex(ms))

/** Whole calendar days from the day of [a] to the day of [b]. */
fun daysBetween(a: Long, b: Long): Int = Math.round((startOfDay(b) - startOfDay(a)) / DAY_MS.toDouble()).toInt()

fun normalizeMerchant(name: String): String = name.trim().lowercase().replace(" ", "")
