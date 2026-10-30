package com.xiaopiao.app.domain.asr

import java.io.File

/**
 * Minimal 16-bit PCM WAV reader, used by the on-device self-test (推一段 wav 走一遍离线识别) and by
 * nothing else. It walks the chunks properly, so afconvert/ffmpeg output with extra chunks still loads.
 */
object WavReader {

    fun read(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size > 44) { "not a wav" }
        require(bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) { "not a riff file" }

        var channels = 1
        var bits = 16
        var sampleRate = 16_000
        var dataStart = -1
        var dataLength = 0

        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = le32(bytes, pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    channels = le16(bytes, body + 2).coerceAtLeast(1)
                    sampleRate = le32(bytes, body + 4)
                    bits = le16(bytes, body + 14)
                }
                "data" -> {
                    dataStart = body
                    dataLength = minOf(size, bytes.size - body)
                }
            }
            pos = body + size + (size and 1)
        }
        require(dataStart >= 0 && dataLength > 0) { "no pcm data" }
        require(bits == 16) { "only 16-bit pcm" }
        require(sampleRate == 16_000) { "expected 16 kHz, got $sampleRate" }

        val frames = dataLength / 2 / channels
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += le16(bytes, dataStart + (i * channels + c) * 2)
            out[i] = (sum / channels) / 32_768f
        }
        return out
    }

    private fun le16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
