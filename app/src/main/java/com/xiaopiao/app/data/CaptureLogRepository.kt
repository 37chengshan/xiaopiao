package com.xiaopiao.app.data

import com.xiaopiao.app.capture.CaptureLogSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Raw captured notifications. Stays on the device and is auto-pruned to the latest [KEEP] rows. */
class CaptureLogRepository(private val dao: CaptureLogDao) : CaptureLogSink {
    fun observeRecent(limit: Int = KEEP): Flow<List<CaptureLogEntry>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(entry: CaptureLogEntry): Long {
        val id = dao.insert(entry.toEntity().copy(id = 0))
        dao.prune(KEEP)
        return id
    }

    suspend fun clear() = dao.deleteAll()

    companion object { const val KEEP = 200 }
}
