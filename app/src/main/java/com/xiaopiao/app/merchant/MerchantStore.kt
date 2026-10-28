package com.xiaopiao.app.merchant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Process-wide holder for [MerchantState], persisted as a small JSON file in app storage (no Room). */
class MerchantStore private constructor(private val file: File) {
    private class Loaded(val state: MerchantState, val freshlySeeded: Boolean)

    private val loaded = load()
    private val _state = MutableStateFlow(loaded.state)
    val state: StateFlow<MerchantState> = _state.asStateFlow()
    val crowd: List<CrowdMember> by lazy { DemoCrowd.generate() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    init {
        if (loaded.freshlySeeded) persist(loaded.state)
    }

    fun update(change: (MerchantState) -> MerchantState) {
        val next = _state.value.let(change)
        _state.value = next
        persist(next)
    }

    private fun persist(state: MerchantState) = scope.launch {
        writeLock.withLock {
            runCatching { file.writeText(MerchantState.encode(state)) }
        }
    }

    /**
     * First launch gets a demo revenue book (every row marked [RevenueSource.SEED]) so the shop screens
     * have shape to show. Deleting rows later never brings it back: [MerchantState.demoSeededAt] stays set.
     */
    private fun load(): Loaded {
        val saved = runCatching { if (file.exists()) MerchantState.decode(file.readText()) else null }.getOrNull()
        val now = System.currentTimeMillis()
        val today = RevenueBook.startOfDay(now)
        if (saved == null || (saved.demoSeededAt <= 0L && saved.revenue.isEmpty())) {
            return Loaded(
                MerchantState(revenue = DemoRevenue.generate(today), demoSeededAt = now),
                freshlySeeded = true,
            )
        }
        if (saved.demoSeededAt <= 0L || saved.revenue.isEmpty()) return Loaded(saved, freshlySeeded = false)
        // Reopened days later on a purely demo book: extend the demo forward so 今日 still has a shape.
        // Past rows the owner deleted stay deleted, and a book with any real row is never appended to.
        if (saved.revenue.all { it.seeded }) {
            val last = saved.revenue.maxOf { it.dayStart }
            if (last < today) {
                val missing = DemoRevenue.generate(today).filter { it.dayStart > last }
                if (missing.isNotEmpty()) {
                    return Loaded(saved.copy(revenue = saved.revenue + missing), freshlySeeded = true)
                }
            }
        }
        return Loaded(saved, freshlySeeded = false)
    }

    companion object {
        @Volatile private var instance: MerchantStore? = null

        fun get(context: Context): MerchantStore = instance ?: synchronized(this) {
            instance ?: MerchantStore(File(context.applicationContext.filesDir, "merchant_state.json")).also { instance = it }
        }
    }
}
