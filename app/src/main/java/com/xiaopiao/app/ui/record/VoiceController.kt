package com.xiaopiao.app.ui.record

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * On-device-first speech recognition (docs/APP-DESIGN.md §4.3): free, no audio leaves the phone to our own
 * servers, and no LLM is involved. The transcript then goes through the local parser.
 */
class VoiceController(private val context: Context) {
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

    private var recognizer: SpeechRecognizer? = null
    /** Whether [recognizer] is the on-device one; some phones advertise it but cannot dictate Chinese with it. */
    private var onDevice = false
    private var onDeviceFailed = false

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

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
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
            override fun onBeginningOfSpeech() { listening = true }
            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB runs roughly -2..10; normalise and ease so the bars breathe instead of jumping.
                val target = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                rms = rms + (target - rms) * 0.35f
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                // The on-device engine can refuse the language or fail to bind; fall back to the system service once.
                if (onDevice && error in ServiceErrors) {
                    onDeviceFailed = true
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    start()
                    return
                }
                this@VoiceController.error = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，再说一次"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别服务连不上，先手打吧"
                    in ServiceErrors -> NoServiceHint
                    else -> "语音没成功，再试一次"
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) error = "没听清，再说一次" else transcript = text
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

    fun start() {
        error = null
        partial = ""
        transcript = null
        if (!hasPermission) { error = "没有麦克风权限"; return }
        val r = ensureRecognizer() ?: run { error = NoServiceHint; return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        runCatching { r.startListening(intent) }.onFailure {
            listening = false
            error = "语音没启动，再试一次"
        }
    }

    /** Ask for the final result; the recogniser answers on onResults. */
    fun stop() {
        if (recognizer != null) runCatching { recognizer?.stopListening() }
        listening = false
    }

    fun cancel() {
        if (recognizer != null) runCatching { recognizer?.cancel() }
        listening = false
        rms = 0f
        partial = ""
        transcript = null
        error = null
    }

    fun consume(): String? = transcript.also { transcript = null }

    fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}

@Composable
fun rememberVoiceController(): VoiceController {
    val context = LocalContext.current
    val controller = remember { VoiceController(context.applicationContext) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/** Errors that mean "this speech service cannot serve us", as opposed to "it did not hear you". */
private val ServiceErrors = setOf(
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
)

/** When no usable service exists, point at the keyboard's own mic — every Chinese IME has one (tt#47). */
private const val NoServiceHint = "这台手机的语音服务用不了，点输入法上的麦克风说一句"
