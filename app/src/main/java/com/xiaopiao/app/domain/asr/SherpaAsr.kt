package com.xiaopiao.app.domain.asr

import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineStream

/**
 * The on-device recogniser. Nothing here touches the network: the model files were downloaded once by
 * [AsrModelManager] and every waveform stays in memory on this phone.
 *
 * Two shapes, one interface:
 * - streaming (small zipformer): partial text while the user talks, endpoint rules end a sentence;
 * - offline (SenseVoice): the sentence is decoded once the user lets go.
 */
interface AsrSession {
    /** Feeds 16 kHz mono PCM and returns the text so far (empty string when there is nothing yet). */
    fun accept(samples: FloatArray): String

    /** True when the model decided the sentence ended (streaming only). */
    val endpointed: Boolean

    /** Final text for what was heard so far; call after [accept] to pick up the last words. Also resets
     *  the session, because [SherpaEngine] caches it and the next press must start from silence. */
    fun finish(): String

    fun close()
}

/** Loads models out of the store; a broken or half-deleted file simply returns null. */
class SherpaEngine(private val store: AsrModelStore) {

    private var cached: Pair<String, AsrSession>? = null

    fun open(model: AsrModel, language: AsrLanguage): AsrSession? {
        cached?.let { (id, session) -> if (id == model.id) return session }
        close()
        val session = runCatching {
            if (model.streaming) openStreaming(model, language, withHotwords = hotwordsUsable(model, language))
            else openOffline(model, language)
        }.getOrNull()
            // A broken hotwords file must never cost the user their microphone: retry plain.
            ?: runCatching {
                if (model.streaming) openStreaming(model, language, withHotwords = false) else openOffline(model, language)
            }.getOrNull()
            ?: return null
        cached = model.id to session
        return session
    }

    /**
     * Hotwords need a tokenizer rule: the Chinese models are character-level (`cjkchar`), the English one
     * ships no BPE vocabulary in its release, so plain decoding is used there.
     */
    private fun hotwordsUsable(model: AsrModel, language: AsrLanguage): Boolean =
        language == AsrLanguage.ZH && store.hotwordsFile(language).isFile && store.hotwordsFile(language).length() > 0L

    fun close() {
        cached?.second?.close()
        cached = null
    }

    private fun openStreaming(model: AsrModel, language: AsrLanguage, withHotwords: Boolean): AsrSession {
        val encoder = store.file(model, model.encoder!!).absolutePath
        val decoder = store.file(model, model.decoder!!).absolutePath
        val joiner = store.file(model, model.joiner!!).absolutePath
        val tokens = store.file(model, model.tokens).absolutePath
        val hotwords = store.hotwordsFile(language)
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(encoder = encoder, decoder = decoder, joiner = joiner),
                tokens = tokens,
                numThreads = 2,
                modelType = "zipformer",
                // cjkchar is what lets sherpa tokenise a Chinese hotword phrase at all.
                modelingUnit = if (withHotwords) "cjkchar" else "",
                provider = "cpu",
            ),
            // modified_beam_search is the decoding that honours hotwords on a transducer.
            decodingMethod = if (withHotwords) "modified_beam_search" else "greedy_search",
            hotwordsFile = if (withHotwords) hotwords.absolutePath else "",
            hotwordsScore = 2.0f,
            enableEndpoint = true,
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0.0f),
                rule2 = EndpointRule(true, 1.2f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
        )
        val recognizer = OnlineRecognizer(config = config)
        val stream = recognizer.createStream()
        return StreamingSession(recognizer, stream)
    }

    private fun openOffline(model: AsrModel, language: AsrLanguage): AsrSession {
        val modelFile = store.file(model, model.model!!).absolutePath
        val tokens = store.file(model, model.tokens).absolutePath
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelFile,
                    language = if (language == AsrLanguage.EN) "en" else "zh",
                    useInverseTextNormalization = true,
                ),
                tokens = tokens,
                numThreads = 2,
                modelType = "sense_voice",
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        )
        return OfflineSession(OfflineRecognizer(config = config))
    }
}

private class StreamingSession(
    private val recognizer: OnlineRecognizer,
    private var stream: OnlineStream,
) : AsrSession {
    override var endpointed: Boolean = false
        private set

    override fun accept(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        stream.acceptWaveform(samples, 16_000)
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        endpointed = recognizer.isEndpoint(stream)
        return recognizer.getResult(stream).text.trim()
    }

    override fun finish(): String {
        stream.acceptWaveform(FloatArray(1_600), 16_000) // 100 ms of silence flushes the tail
        stream.inputFinished()
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        val text = recognizer.getResult(stream).text.trim()
        // A finished stream stays endpointed forever; reusing it cut every later press off after ~0.3 s.
        stream.release()
        stream = recognizer.createStream()
        endpointed = false
        return text
    }

    override fun close() {
        stream.release()
        recognizer.release()
    }
}

private class OfflineSession(private val recognizer: OfflineRecognizer) : AsrSession {
    private val samples = ArrayList<FloatArray>(64)
    private var total = 0
    private var text = ""

    override val endpointed: Boolean get() = false

    override fun accept(samples: FloatArray): String {
        if (samples.isEmpty()) return text
        // 60 s at 16 kHz is far more than one ledger sentence; beyond that the oldest audio is dropped.
        this.samples += samples.copyOf()
        total += samples.size
        while (total > 16_000 * 60) {
            val dropped = this.samples.removeAt(0)
            total -= dropped.size
        }
        return text
    }

    override fun finish(): String {
        if (total == 0) return ""
        val joined = FloatArray(total)
        var offset = 0
        for (chunk in samples) {
            chunk.copyInto(joined, offset)
            offset += chunk.size
        }
        samples.clear()
        total = 0
        text = ""
        val stream: OfflineStream = recognizer.createStream()
        return try {
            stream.acceptWaveform(joined, 16_000)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() {
        samples.clear()
        recognizer.release()
    }
}
