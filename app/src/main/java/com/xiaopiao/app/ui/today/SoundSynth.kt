package com.xiaopiao.app.ui.today

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Pure sample synthesis for the printer (no asset files): thermal-head chatter, the dull stamp thump and a paper rip.
 * Everything is deterministic for a given seed so it can be unit-tested; [ReceiptSound] plays the result.
 */
object SoundSynth {
    const val RATE = 22_050

    /** Percussive envelope: linear [attackS] ramp then exponential decay with time constant [decayS]. */
    fun envelope(t: Float, attackS: Float, decayS: Float): Float {
        if (t < 0f) return 0f
        if (t < attackS) return t / attackS
        return exp(-(t - attackS) / decayS)
    }

    /**
     * A short chatter of [pulses] filtered-noise ticks about [spacingMs] apart (jittered): each pulse is
     * high-passed then low-passed noise (a bright "tss") under a ~2 ms decay.
     */
    fun thermalBurst(seed: Int, pulses: Int = 4, spacingMs: Float = 8.5f, tailMs: Float = 14f): FloatArray {
        val rnd = Random(seed.toLong() * 7919L + 17L)
        val n = ((pulses * spacingMs * 1.15f + tailMs) / 1000f * RATE).toInt()
        val out = FloatArray(n)
        var hpPrev = 0f
        var lp = 0f
        for (p in 0 until pulses) {
            val start = ((p * spacingMs + rnd.nextFloat() * spacingMs * 0.3f) / 1000f * RATE).toInt()
            val amp = 0.55f + rnd.nextFloat() * 0.45f
            val decay = 0.0016f + rnd.nextFloat() * 0.0012f
            var i = start
            while (i < n) {
                val t = (i - start) / RATE.toFloat()
                val e = envelope(t, 0.0004f, decay)
                if (e < 0.003f && t > 0.004f) break
                val x = rnd.nextFloat() * 2f - 1f
                val hp = x - hpPrev; hpPrev = x
                lp += 0.55f * (hp - lp)
                out[i] += lp * e * amp
                i++
            }
        }
        return normalize(out, 0.62f)
    }

    /** Dull "ka-thump": a 100 -> 52 Hz sine drop plus a soft low-passed click, saturated a touch. */
    fun stampThump(durationMs: Int = 260): FloatArray {
        val n = durationMs * RATE / 1000
        val out = FloatArray(n)
        val rnd = Random(42L)
        var phase = 0.0
        var lp = 0f
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val freq = 52f + 48f * exp(-t / 0.028f)
            phase += 2.0 * PI * freq / RATE
            val body = sin(phase).toFloat() * envelope(t, 0.0012f, 0.075f)
            lp += 0.12f * ((rnd.nextFloat() * 2f - 1f) - lp)
            val click = lp * envelope(t, 0.0005f, 0.012f) * 2.2f
            out[i] = tanh((body * 1.25f + click * 0.55f).toDouble()).toFloat()
        }
        return normalize(out, 0.85f)
    }

    /** A quick rip: gated high-passed noise swelling then dying over [durationMs]. */
    fun paperTear(durationMs: Int = 190): FloatArray {
        val n = durationMs * RATE / 1000
        val out = FloatArray(n)
        val rnd = Random(7L)
        var prev = 0f
        var gate = 0.5f
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val x = rnd.nextFloat() * 2f - 1f
            val hp = x - prev * 0.92f; prev = x
            if (i % 90 == 0) gate = 0.35f + rnd.nextFloat() * 0.65f
            val swell = (t / 0.03f).coerceAtMost(1f) * exp(-t / (durationMs / 1000f * 0.45f))
            out[i] = hp * gate * swell
        }
        return normalize(out, 0.55f)
    }

    /** Scales so the loudest sample is [peak]; silence stays silence. */
    fun normalize(samples: FloatArray, peak: Float): FloatArray {
        var max = 0f
        for (s in samples) if (kotlin.math.abs(s) > max) max = kotlin.math.abs(s)
        if (max <= 1e-6f) return samples
        val k = peak / max
        for (i in samples.indices) samples[i] *= k
        return samples
    }

    fun toPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }

    /** Mono 16-bit PCM WAV bytes (what SoundPool loads from cacheDir). */
    fun wav(pcm: ShortArray, rate: Int = RATE): ByteArray {
        val dataLen = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataLen)
        pcm.forEach { buf.putShort(it) }
        return buf.array()
    }

    /** Playback-rate jitter (0.92..1.10) so repeated ticks never sound machine-gunned. */
    fun rateJitter(step: Int): Float = 0.92f + ((step * 2654435761L) ushr 7 and 0xFF).toInt() / 255f * 0.18f
}
