package com.xiaopiao.app.domain.asr

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.asrPrefs by preferencesDataStore(name = "asr_prefs")

/** The user's choice in 设置 › 语音模型: small model (default), the big one, or none at all. */
class AsrPrefs(private val context: Context) {
    private val key = stringPreferencesKey("preference")

    fun observe(): Flow<AsrPreference> = context.asrPrefs.data.map { prefs ->
        runCatching { AsrPreference.valueOf(prefs[key] ?: AsrPreference.SMALL.name) }.getOrDefault(AsrPreference.SMALL)
    }

    suspend fun set(preference: AsrPreference) {
        context.asrPrefs.edit { it[key] = preference.name }
    }
}

/** What the settings row and the mic prompt need to know about one model. */
sealed interface AsrInstallState {
    data object Installed : AsrInstallState
    data object Missing : AsrInstallState
    data class Downloading(val written: Long, val total: Long, val fileName: String) : AsrInstallState {
        val fraction: Float get() = if (total <= 0L) 0f else (written.toFloat() / total).coerceIn(0f, 1f)
    }
    data class Failed(val message: String) : AsrInstallState
}

/**
 * Owns downloads and their progress for the whole app, so the settings page and the mic prompt watch the
 * same state and a download keeps running when the settings page closes.
 */
class AsrModelManager(
    private val store: AsrModelStore,
    private val downloader: AsrDownloader = AsrDownloader(store),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    constructor(context: Context) : this(AsrModelStore(context))

    private val _states = MutableStateFlow<Map<String, AsrInstallState>>(emptyMap())
    val states: StateFlow<Map<String, AsrInstallState>> = _states.asStateFlow()
    private val jobs = HashMap<String, Job>()

    val modelStore: AsrModelStore get() = store

    fun stateOf(model: AsrModel): AsrInstallState =
        _states.value[model.id] ?: if (store.installed(model)) AsrInstallState.Installed else AsrInstallState.Missing

    fun installedIds(): Set<String> = store.installedIds()

    /** Starts (or resumes) a download; calling it again while running is a no-op. */
    fun start(model: AsrModel) {
        if (jobs[model.id]?.isActive == true) return
        jobs[model.id] = scope.launch {
            _states.value = _states.value + (model.id to AsrInstallState.Downloading(0, model.totalBytes, ""))
            try {
                downloader.ensure(model) { progress ->
                    _states.value = _states.value + (
                        model.id to AsrInstallState.Downloading(progress.written, progress.total, progress.fileName)
                        )
                }
                _states.value = _states.value + (model.id to AsrInstallState.Installed)
            } catch (cancel: CancellationException) {
                _states.value = _states.value + (model.id to AsrInstallState.Missing)
                throw cancel
            } catch (e: Exception) {
                _states.value = _states.value + (model.id to AsrInstallState.Failed(e.message ?: "download failed"))
            } finally {
                jobs.remove(model.id)
            }
        }
    }

    fun cancel(model: AsrModel) {
        jobs.remove(model.id)?.cancel()
        _states.value = _states.value + (model.id to AsrInstallState.Missing)
    }

    suspend fun delete(model: AsrModel) = withContext(Dispatchers.IO) {
        jobs.remove(model.id)?.cancel()
        store.delete(model)
        _states.value = _states.value + (model.id to AsrInstallState.Missing)
    }

    /** Download-then-listen: suspends until every file is verified. */
    suspend fun ensure(model: AsrModel) {
        stateOf(model).let { if (it is AsrInstallState.Installed) return }
        start(model)
        while (true) {
            when (val state = stateOf(model)) {
                AsrInstallState.Installed -> return
                is AsrInstallState.Failed -> throw IllegalStateException(state.message)
                else -> kotlinx.coroutines.delay(120)
            }
        }
    }

    /** Writes the hotwords file for [language]; the engine reads it at recogniser creation. */
    suspend fun writeHotwords(language: AsrLanguage, dynamic: List<String>) = withContext(Dispatchers.IO) {
        val words = AsrHotwords.build(language, dynamic)
        store.hotwordsFile(language).apply {
            parentFile?.mkdirs()
            writeText(AsrHotwords.serialize(words))
        }
        words
    }
}
