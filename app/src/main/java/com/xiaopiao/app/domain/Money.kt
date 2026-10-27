package com.xiaopiao.app.domain

import java.util.Calendar

fun Cents.yuan(): String {
    val abs = kotlin.math.abs(this)
    val text = if (abs % 100 == 0L) "${abs / 100}" else "%d.%02d".format(abs / 100, abs % 100)
    return if (this < 0) "-$text" else text
}

fun startOfDay(epochMillis: Long): Long = Calendar.getInstance().run {
    timeInMillis = epochMillis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    timeInMillis
}

const val DAY_MS: Long = 24L * 60 * 60 * 1000
