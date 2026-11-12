package com.example.tts.neural

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * Lightweight, dependency-free pitch shifting for persona voices.
 *
 * Pitch shift by `factor` (1.30 = 30% higher) while preserving duration:
 *   1) resample by `factor`  -> raises pitch, shortens duration
 *   2) WSOLA time-stretch by `factor` -> restores the original duration
 *
 * Good enough for dubbing personas; runs in a few ms per second of audio.
 */
object PitchTools {

    fun pitchShift(input: ShortArray, sampleRate: Int, factor: Float): ShortArray {
        if (input.isEmpty() || abs(factor - 1f) < 0.03f) return input
        val clamped = factor.coerceIn(0.55f, 1.75f)
        val resampled = resampleByFactor(input, clamped)
        return timeStretch(resampled, clamped)
    }

    /** Resamples by `factor`: pitch and playback rate scale together. */
    fun resampleByFactor(input: ShortArray, factor: Float): ShortArray {
        if (input.isEmpty()) return input
        val newLen = maxOf(1, (input.size / factor).toInt())
        val out = ShortArray(newLen)
        var pos = 0f
        for (i in 0 until newLen) {
            val i0 = pos.toInt().coerceIn(0, input.size - 1)
            val i1 = min(i0 + 1, input.size - 1)
            val frac = pos - i0
            out[i] = ((1f - frac) * input[i0] + frac * input[i1]).toInt().toShort()
            pos += factor
        }
        return out
    }

    /** Resamples between two sample rates (linear interpolation). */
    fun resampleTo(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (input.isEmpty() || fromRate == toRate) return input
        val ratio = fromRate.toFloat() / toRate.toFloat()
        val newLen = maxOf(1, (input.size.toLong() * toRate / fromRate).toInt())
        val out = ShortArray(newLen)
        var pos = 0f
        for (i in 0 until newLen) {
            val i0 = pos.toInt().coerceIn(0, input.size - 1)
            val i1 = min(i0 + 1, input.size - 1)
            val frac = pos - i0
            out[i] = ((1f - frac) * input[i0] + frac * input[i1]).toInt().toShort()
            pos += ratio
        }
        return out
    }

    /**
     * WSOLA (Waveform Similarity Overlap-Add) time stretching.
     * `stretch` = 1.30 means the output becomes 30% longer without changing pitch.
     */
    fun timeStretch(input: ShortArray, stretch: Float): ShortArray {
        if (input.size < 2048 || abs(stretch - 1f) < 0.01f) return input
        val s = stretch.coerceIn(0.5f, 2.0f)

        val frame = 512
        val hop = frame / 2
        val search = 192
        val templateLen = min(search, frame / 2)
        val inputHop = hop / s

        val outLen = input.size + (input.size * s).toInt() + frame
        val out = FloatArray(outLen)
        val win = FloatArray(frame) { i ->
            (0.5 - 0.5 * cos(2.0 * PI * i / (frame - 1))).toFloat()
        }

        var outPos = 0
        var nominal = 0f
        var template = IntArray(0)

        while (nominal.toInt() + frame + search < input.size && outPos + frame < outLen) {
            val start: Int
            if (template.isEmpty()) {
                start = nominal.toInt()
            } else {
                var best = 0
                var bestScore = Long.MIN_VALUE
                val base = nominal.toInt()
                var delta = -search
                while (delta <= search) {
                    val cand = base + delta
                    if (cand in 0..(input.size - frame)) {
                        var score = 0L
                        var k = 0
                        while (k < templateLen) {
                            score += input[cand + k].toLong() * template[k]
                            k += 2 // sample every other point: 2x faster, imperceptible
                        }
                        if (score > bestScore) { bestScore = score; best = delta }
                    }
                    delta += 2
                }
                (base + best).coerceIn(0, input.size - frame)
                start = (base + best).coerceIn(0, input.size - frame)
            }

            for (i in 0 until frame) {
                out[outPos + i] += input[start + i] * win[i]
            }

            // Natural continuation of this frame becomes the next alignment template
            template = IntArray(templateLen) { k ->
                val idx = start + frame - templateLen + k
                input[idx.coerceIn(0, input.size - 1)].toInt()
            }

            nominal += inputHop
            outPos += hop
        }

        val produced = (outPos + frame).coerceAtMost(outLen)
        val result = ShortArray(produced)
        for (i in 0 until produced) {
            result[i] = out[i].toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }
}
