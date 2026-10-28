package com.xiaopiao.app.ui.today

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

/**
 * The printer's voice. Samples are synthesised by [SoundSynth], written as tiny WAVs into cacheDir and played
 * through a [SoundPool] tagged USAGE_ASSISTANCE_SONIFICATION, so the system silent / DND switches mute it.
 * Call [tick] / [thump] / [tear] in the same frame as the matching haptic. [enabled] mirrors [ReceiptSoundPrefs].
 */
class ReceiptSound(context: Context) {
    @Volatile var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val tickIds = IntArray(TICK_VARIANTS)
    private var thumpId = 0
    private var tearId = 0
    private val ready = java.util.concurrent.atomic.AtomicInteger(0)
    private var count = 0

    init {
        pool.setOnLoadCompleteListener { _, _, status -> if (status == 0) ready.incrementAndGet() }
        val dir = File(context.cacheDir, "receipt-sound").apply { mkdirs() }
        Thread {
            runCatching {
                for (i in 0 until TICK_VARIANTS) {
                    val pulses = 3 + i % 3
                    tickIds[i] = pool.load(write(dir, "tick$i", SoundSynth.thermalBurst(i + 1, pulses)), 1)
                }
                thumpId = pool.load(write(dir, "thump", SoundSynth.stampThump()), 1)
                tearId = pool.load(write(dir, "tear", SoundSynth.paperTear()), 1)
            }
        }.start()
    }

    private fun write(dir: File, name: String, samples: FloatArray): String =
        File(dir, "$name.wav").apply { writeBytes(SoundSynth.wav(SoundSynth.toPcm16(samples))) }.absolutePath

    private val loaded get() = ready.get() >= TICK_VARIANTS + 2

    /** One printed line: a short burst of thermal chatter. */
    fun tick() {
        if (!enabled || !loaded) return
        val n = count++
        pool.play(tickIds[n % TICK_VARIANTS], 0.55f, 0.55f, 1, 0, SoundSynth.rateJitter(n))
    }

    /** The stamp landing. */
    fun thump() {
        if (!enabled || !loaded) return
        pool.play(thumpId, 0.9f, 0.9f, 2, 0, 1f)
    }

    /** The paper coming off. */
    fun tear() {
        if (!enabled || !loaded) return
        pool.play(tearId, 0.7f, 0.7f, 2, 0, 1f)
    }

    fun release() = pool.release()

    private companion object { const val TICK_VARIANTS = 4 }
}
