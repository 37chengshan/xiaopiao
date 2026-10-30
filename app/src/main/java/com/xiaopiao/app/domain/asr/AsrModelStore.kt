package com.xiaopiao.app.domain.asr

import android.content.Context
import java.io.File

/**
 * Where downloaded models live: `filesDir/asr/<model-id>/`, one folder per model so deleting one never
 * touches the other, plus a single hotwords file per language the engine reads at startup.
 *
 * Everything here is a local file operation — no audio or text ever leaves the phone.
 */
class AsrModelStore(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "asr"))

    fun dir(model: AsrModel): File = File(root, model.id)

    fun file(model: AsrModel, file: AsrFile): File = File(dir(model), file.name)

    /** Part file of a cancelled download; kept so the next attempt resumes instead of restarting. */
    fun partFile(model: AsrModel, file: AsrFile): File = File(dir(model), "${file.name}.part")

    /** Complete only when every file is there with the expected size. */
    fun installed(model: AsrModel): Boolean = model.files.all { f ->
        val target = file(model, f)
        target.isFile && target.length() == f.bytes
    }

    /** Bytes this model actually occupies, including half-downloaded parts. */
    fun bytesOnDisk(model: AsrModel): Long {
        val dir = dir(model)
        if (!dir.isDirectory) return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun delete(model: AsrModel): Boolean {
        val dir = dir(model)
        if (!dir.exists()) return true
        return dir.deleteRecursively()
    }

    fun hotwordsFile(language: AsrLanguage): File = File(root, "hotwords-${language.tag}.txt")

    fun installedIds(): Set<String> = AsrCatalog.all.filter { installed(it) }.map { it.id }.toSet()

    fun totalBytes(): Long = AsrCatalog.all.sumOf { bytesOnDisk(it) }
}

/** How much of a model is already on disk, for the settings row. */
fun AsrModelStore.downloadedBytes(model: AsrModel): Long = bytesOnDisk(model).coerceAtMost(model.totalBytes)
