package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import java.util.Calendar

/** Local-time timestamp helper. Month is 1-based. */
fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long = Calendar.getInstance().run {
    clear()
    set(y, m - 1, d, h, min, 0)
    timeInMillis
}

private var nextId = 1L

fun txn(
    time: Long,
    yuan: Double,
    merchant: String = "测试商家",
    category: Category = Category.OTHER,
    direction: Direction = Direction.OUT,
    note: String = "",
): Txn = Txn(
    id = nextId++,
    amountCents = Math.round(yuan * 100),
    direction = direction,
    merchant = merchant,
    category = category,
    occurredAt = time,
    source = Source.MANUAL,
    note = note,
)

/** Monday 2026-10-19 — the week used by most week tests (Mon 19 … Sun 25). */
val WEEK = at(2026, 10, 19, 0)

/** A moment safely after [WEEK] has completed. */
val AFTER_WEEK = at(2026, 10, 27, 9)

/** One old transaction so "history exists" without being the first-ever txn of the day under test. */
fun oldTxn(): Txn = txn(at(2026, 8, 1), 20.0, category = Category.MEAL)
