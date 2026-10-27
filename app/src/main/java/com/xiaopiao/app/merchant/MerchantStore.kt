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
    private val _state = MutableStateFlow(load())
    val state: StateFlow<MerchantState> = _state.asStateFlow()
    val crowd: List<CrowdMember> by lazy { DemoCrowd.generate() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    fun update(change: (MerchantState) -> MerchantState) {
        val next = _state.value.let(change)
        _state.value = next
        scope.launch {
            writeLock.withLock {
                runCatching { file.writeText(MerchantState.encode(next)) }
            }
        }
    }

    private fun load(): MerchantState =
        runCatching { if (file.exists()) MerchantState.decode(file.readText()) else MerchantState() }
            .getOrDefault(MerchantState())

    companion object {
        @Volatile private var instance: MerchantStore? = null

        fun get(context: Context): MerchantStore = instance ?: synchronized(this) {
            instance ?: MerchantStore(File(context.applicationContext.filesDir, "merchant_state.json")).also { instance = it }
        }
    }
}
