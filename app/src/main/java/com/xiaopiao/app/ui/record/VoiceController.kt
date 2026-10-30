package com.xiaopiao.app.ui.record

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.xiaopiao.app.domain.asr.AsrChoice
import com.xiaopiao.app.domain.asr.AsrLanguage
import com.xiaopiao.app.domain.asr.AsrModel
import com.xiaopiao.app.domain.asr.AsrModelManager
import com.xiaopiao.app.domain.asr.AsrPreference
import com.xiaopiao.app.domain.asr.AsrPrefs
import com.xiaopiao.app.domain.asr.AsrSession
import com.xiaopiao.app.domain.asr.SherpaEngine
import com.xiaopiao.app.domain.asr.SpokenAmounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Speech recognition, on-device first (docs/APP-DESIGN.md §4.3).
 *
 * Order of preference:
 * 1. a downloaded sherpa-onnx model for the phone's language — audio never leaves the device;
 * 2. the system recogniser, but only if it actually starts: vivo's copilot service binds and then never
 *    calls back, so a 2-second watchdog now declares it dead instead of leaving the bar on 「在听」;
 * 3. neither → [pendingModel] (offer a download) or a hint to use the keyboard's own mic.
 *
 * The transcript goes through [SpokenAmounts] before the local parser, so 「三十五块五」 becomes `35.5`.
 */
class VoiceController(
    private val context: Context,
    private val models: AsrModelManager,
    private val prefs: AsrPrefs,
    private val hotwordSeed: suspend () -> List<String>,
) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var transcript by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 0..1, smoothed loudness for the waveform. */
    var rms by mutableStateOf(0f)
        private set

    /** Set when the user has no model yet: the UI offers a one-tap download of this one. */
    var pendingModel by mutableStateOf<AsrModel?>(null)
        private set

    private val language = AsrLanguage.system()
    private val engine = SherpaEngine(models.modelStore)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var preference = AsrPreference.SMALL
    private var session: AsrSession? = null
    private var capture: Job? = null
    /** Set by [stop]: the capture loop finalises the stream itself, so two threads never touch it. */
    @Volatile
    private var stopRequested = false
    private var watchdog: Job? = null

    // System recogniser, kept only as a fallback.
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false
    private var onDeviceFailed = false
    private var systemReady = false
    private var systemUnusable = false

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    init {
        scope.launch { preference = prefs.observe().first() }
    }

    /** The language the models follow; the settings page shows it. */
    val activeLanguage: AsrLanguage get() = language

    fun start() {
        error = null
        partial = ""
        transcript = null
        pendingModel = null
        stopRequested = false
        if (!hasPermission) {
            error = "没有麦克风权限"
            return
        }
        when (val choice = AsrChoice.pick(language, preference, models.installedIds())) {
            is AsrChoice.Ready -> startLocal(choice.model)
            is AsrChoice.NeedsDownload -> pendingModel = choice.model
            AsrChoice.Off -> startSystem()
        }
    }

    private fun startLocal(model: AsrModel) {
        scope.launch {
            // Hotwords are a Chinese-model feature: the English small model ships no BPE vocabulary.
            if (language == AsrLanguage.ZH) {
                val hotwords = runCatching { hotwordSeed() }.getOrDefault(emptyList())
                runCatching { models.writeHotwords(language, hotwords) }
            }
            val opened = engine.open(model, language)
            if (opened == null) {
                // Half-deleted model or an ABI we did not ship: do not strand the user.
                models.delete(model)
                pendingModel = model
                return@launch
            }
            session = opened
            listening = true
            capture?.cancel()
            capture = scope.launch { record(opened) }
        }
    }

    private fun record(session: AsrSession) {
        val minBuffer = AudioRecord.getMinBufferSize(
            16_000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            error = "这台手机录不了音"
            listening = false
            return
        }
        val buffer = ShortArray(maxOf(minBuffer / 2, 1_600))
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            16_000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error = "麦克风被占用了，等一会儿再试"
            listening = false
            return
        }
        try {
            recorder.startRecording()
            while (listening) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val samples = FloatArray(read) { buffer[it] / 32_768f }
                rms = rms + (level(samples) - rms) * 0.35f
                val text = session.accept(samples)
                if (text.isNotBlank()) partial = SpokenAmounts.normalize(text)
                if (session.endpointed || stopRequested) {
                    finishLocal(partial)
                    return
                }
            }
        } catch (e: Exception) {
            error = "录音出错了，再试一次"
            listening = false
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun finishLocal(fallback: String) {
        val text = runCatching { session?.finish().orEmpty() }.getOrDefault("")
        val spoken = text.ifBlank { fallback }
        listening = false
        if (spoken.isBlank()) {
            error = "没听清，再说一次"
        } else {
            transcript = SpokenAmounts.normalize(spoken)
        }
    }

    /** Ask for the final result; the capture loop finalises a local stream, the system one answers later. */
    fun stop() {
        if (session != null) {
            stopRequested = true
            return
        }
        if (recognizer != null) runCatching { recognizer?.stopListening() }
        listening = false
    }

    fun cancel() {
        stopRequested = true
        capture?.cancel()
        capture = null
        watchdog?.cancel()
        session = null
        if (recognizer != null) runCatching { recognizer?.cancel() }
        listening = false
        rms = 0f
        partial = ""
        transcript = null
        error = null
    }

    fun consume(): String? = transcript.also { transcript = null }

    fun release() {
        cancel()
        engine.close()
        runCatching { recognizer?.destroy() }
        recognizer = null
        scope.cancel()
    }

    /** Called by the prompt after the model finished downloading: listen straight away. */
    fun startAfterDownload() {
        clearPending()
        start()
    }

    fun clearPending() {
        pendingModel = null
    }

    // ------------------------------------------------------------------ system fallback

    private fun startSystem() {
        if (systemUnusable) {
            error = NoModelHint
            return
        }
        val r = ensureRecognizer() ?: run { error = NoModelHint; return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == AsrLanguage.EN) "en-US" else "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        systemReady = false
        listening = true
        runCatching { r.startListening(intent) }.onFailure {
            listening = false
            error = "语音没启动，再试一次"
            return
        }
        // tt's phone binds the service and never calls back; do not sit on 「在听」 forever.
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(2_000)
            if (!systemReady && listening) {
                listening = false
                systemUnusable = true
                runCatching { recognizer?.cancel(); recognizer?.destroy() }
                recognizer = null
                error = NoModelHint
            }
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val useOnDevice = !onDeviceFailed &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!useOnDevice && !SpeechRecognizer.isRecognitionAvailable(context)) return null
        val r = runCatching {
            if (useOnDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull() ?: return null
        onDevice = useOnDevice
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                systemReady = true
                watchdog?.cancel()
                listening = true
            }

            override fun onBeginningOfSpeech() {
                systemReady = true
                listening = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                val target = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                rms = rms + (target - rms) * 0.35f
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(errorCode: Int) {
                listening = false
                if (onDevice && errorCode in ServiceErrors) {
                    onDeviceFailed = true
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    start()
                    return
                }
                this@VoiceController.error = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，再说一次"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别服务连不上，先手打吧"
                    in ServiceErrors -> NoModelHint
                    else -> "语音没成功，再试一次"
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) error = "没听清，再说一次" else transcript = SpokenAmounts.normalize(text)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { partial = it }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        recognizer = r
        return r
    }

    private fun level(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s * s
        val value = sqrt(sum / samples.size).toFloat()
        return (value * 8f).coerceIn(0f, 1f)
    }
}

/** Errors that mean "this speech service cannot serve us", as opposed to "it did not hear you". */
private val ServiceErrors = setOf(
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
)

private const val NoModelHint = "这台手机的语音服务用不了，去设置 › 语音模型 下载一个，或点输入法上的麦克风"
