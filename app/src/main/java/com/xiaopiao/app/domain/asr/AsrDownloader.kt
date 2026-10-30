package com.xiaopiao.app.domain.asr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads a model's files over plain HTTPS, one at a time, into [AsrModelStore].
 *
 * - Resumes: an interrupted file keeps its `.part` and continues from its length with a Range request.
 * - Mirrors: Hugging Face first, then hf-mirror; a failed or corrupt file falls through to the next mirror.
 * - Verifies: the finished file must match the byte count and, once the catalog carries it, the SHA-256.
 *
 * The audio itself never goes anywhere: this only fetches the model weights.
 */
class AsrDownloader(private val store: AsrModelStore) {

    data class Progress(val modelId: String, val fileName: String, val written: Long, val total: Long) {
        val fraction: Float get() = if (total <= 0L) 0f else (written.toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * Makes sure every file of [model] is on disk. Returns normally when it is, throws [IOException] when
     * every mirror failed. Cancelling the calling coroutine leaves the partial files for the next attempt.
     */
    suspend fun ensure(
        model: AsrModel,
        onProgress: (Progress) -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        var alreadyDone = 0L
        for (file in model.files) {
            currentCoroutineContext().ensureActive()
            val target = store.file(model, file)
            if (target.isFile && target.length() == file.bytes) {
                alreadyDone += file.bytes
                onProgress(Progress(model.id, file.name, alreadyDone, model.totalBytes))
                continue
            }
            downloadFile(model, file, alreadyDone, onProgress)
            alreadyDone += file.bytes
        }
        if (!store.installed(model)) throw IOException("files missing after download")
    }

    private suspend fun downloadFile(
        model: AsrModel,
        file: AsrFile,
        alreadyDone: Long,
        onProgress: (Progress) -> Unit,
    ) {
        val target = store.file(model, file)
        val part = store.partFile(model, file)
        part.parentFile?.mkdirs()
        var lastError: String? = null

        for (mirror in AsrCatalog.mirrors) {
            currentCoroutineContext().ensureActive()
            // A part left by a previous mirror is still valid: same bytes, same endpoint.
            val start = if (part.isFile) part.length() else 0L
            if (start == file.bytes) {
                if (finish(file, part, target)) return
                part.delete()
                continue
            }
            try {
                fetch(mirror, model, file, start, part, alreadyDone, onProgress)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                lastError = "${mirror}: ${e.message}"
                continue
            }
            if (finish(file, part, target)) return
            // Wrong bytes: throw the part away and try the next mirror from zero.
            part.delete()
            lastError = "checksum mismatch from $mirror"
        }
        throw IOException(lastError ?: "download failed")
    }

    private suspend fun fetch(
        mirror: String,
        model: AsrModel,
        file: AsrFile,
        start: Long,
        part: File,
        alreadyDone: Long,
        onProgress: (Progress) -> Unit,
    ) {
        val url = URL(AsrCatalog.url(mirror, model, file))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            if (start > 0L) setRequestProperty("Range", "bytes=$start-")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val append = code == HttpURLConnection.HTTP_PARTIAL && start > 0L
            val bodyLength = connection.contentLengthLong.takeIf { it > 0 } ?: (file.bytes - if (append) start else 0L)

            RandomAccessFile(part, "rw").use { raf ->
                if (!append) raf.setLength(0L) else raf.seek(start)
                var written = if (append) start else 0L
                val buffer = ByteArray(64 * 1024)
                connection.inputStream.use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        written += read
                        onProgress(Progress(model.id, file.name, alreadyDone + written, model.totalBytes))
                    }
                }
                if (bodyLength > 0 && written < bodyLength) throw IOException("short body $written/$bodyLength")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Moves the part file into place once its size and hash match; false when the hash disagrees. */
    private fun finish(file: AsrFile, part: File, target: File): Boolean {
        if (!part.isFile || part.length() != file.bytes) return false
        if (file.sha256.length == 64 && !file.sha256.startsWith("PENDING")) {
            if (!sha256(part).equals(file.sha256, ignoreCase = true)) return false
        }
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        return part.renameTo(target) || runCatching { part.copyTo(target, overwrite = true); part.delete() }.isSuccess
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
