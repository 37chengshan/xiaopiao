package com.xiaopiao.app

import android.content.Context
import com.xiaopiao.app.data.InMemoryLedgerRepository
import com.xiaopiao.app.domain.LedgerRepository

/** Tiny service locator. Owned by the data layer; UI only reads from it. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val ledger: LedgerRepository = InMemoryLedgerRepository()
}
