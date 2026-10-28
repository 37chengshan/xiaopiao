package com.xiaopiao.app.ui.today

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.util.Log
import android.media.SoundPool
import java.io.File

/**
 * The printer's voice. Samples are synthesised by [SoundSynth], written as tiny WAVs into cacheDir and played
 * through a [SoundPool] tagged USAGE_GAME: it rides the media volume. USAGE_GAME follows the
 * ring/system volume, which on many phones is 0 or ringer-silenced, so the printer was inaudible on a real device.
 * Measured on a V2405A in vibrate mode: ringer-muted streams are SYSTEM/RING/NOTIFICATION, not MUSIC, so a deliberate
 * print sound on the media stream stays audible; the in-app switch and media volume are the user's controls.
 * Call [tick] / [thump] / [tear] in the same frame as the matching haptic. [enabled] mirrors [ReceiptSoundPrefs].
 */
class ReceiptSound(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
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
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) ready.incrementAndGet() else Log.w(TAG, "load failed id=$id status=$status")
        }
        val dir = File(context.cacheDir, "receipt-sound").apply { mkdirs() }
        Thread {
            runCatching {
                for (i in 0 until TICK_VARIANTS) {
                    val pulses = 3 + i % 3
                    tickIds[i] = pool.load(write(dir, "tick$i", SoundSynth.thermalBurst(i + 1, pulses)), 1)
                }
                thumpId = pool.load(write(dir, "thump", SoundSynth.stampThump()), 1)
                tearId = pool.load(write(dir, "tear", SoundSynth.paperTear()), 1)
            }.onFailure { Log.w(TAG, "synth/load failed", it) }
        }.start()
    }

    private fun write(dir: File, name: String, samples: FloatArray): String =
        File(dir, "$name.wav").apply { writeBytes(SoundSynth.wav(SoundSynth.toPcm16(samples))) }.absolutePath

    private val loaded get() = ready.get() >= TICK_VARIANTS + 2

    /** False when muted by our own switch or not loaded yet. Media volume 0 is the system-level mute. */
    val audible: Boolean get() = enabled && loaded

    private fun play(id: Int, volume: Float, rate: Float, priority: Int) {
        val stream = if (audible) pool.play(id, volume, volume, priority, 0, rate) else 0
        Log.i(TAG, "play id=$id stream=$stream audible=$audible enabled=$enabled loaded=$loaded ringer=${audio.ringerMode}")
    }

    /** One printed line: a short burst of thermal chatter. */
    fun tick() {
        val n = count++
        play(tickIds[n % TICK_VARIANTS], 0.55f, SoundSynth.rateJitter(n), 1)
    }

    /** The stamp landing. */
    fun thump() {
        play(thumpId, 0.9f, 1f, 2)
    }

    /** The paper coming off. */
    fun tear() {
        play(tearId, 0.7f, 1f, 2)
    }

    fun release() = pool.release()

    private companion object { const val TICK_VARIANTS = 4; const val TAG = "ReceiptSound" }
}
