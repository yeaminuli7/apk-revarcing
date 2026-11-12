package com.example.tts.neural

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

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

    // ------------------------------------------------------------------ FX
    // Character timbres for the English cast (monster, robot, ghost, ...).
    // Applied after pitch/speed so it costs one pass over 16 kHz audio.

    enum class Fx {
        NONE,
        GROWL,   // ring modulation + soft saturation -> monster, demon, giant
        METAL,   // fast ring modulation + bit reduction -> robot, alien
        WHISPER  // low-pass + tremolo -> ghost, whisper, witch
    }

    /**
     * @param intensity 0..1 strength of the effect.
     */
    fun applyFx(input: ShortArray, sampleRate: Int, fx: Fx, intensity: Float = 1f): ShortArray {
        if (input.isEmpty() || fx == Fx.NONE) return input
        val k = intensity.coerceIn(0f, 1f)
        if (k <= 0.01f) return input
        return when (fx) {
            Fx.GROWL -> growl(input, sampleRate, k)
            Fx.METAL -> metal(input, sampleRate, k)
            Fx.WHISPER -> whisper(input, sampleRate, k)
            Fx.NONE -> input
        }
    }

    private fun growl(input: ShortArray, sampleRate: Int, k: Float): ShortArray {
        val out = ShortArray(input.size)
        val modHz = 44.0 + 18.0 * k
        val depth = 0.42f * k
        val drive = 1.0f + 1.4f * k
        val norm = tanh(drive).toFloat()
        for (i in input.indices) {
            val x = input[i] / 32768f
            val mod = 1f - depth + depth * sin(2.0 * PI * modHz * i / sampleRate).toFloat()
            val y = tanh((x * mod * drive).toDouble()).toFloat() / norm
            out[i] = (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun metal(input: ShortArray, sampleRate: Int, k: Float): ShortArray {
        val out = ShortArray(input.size)
        val modHz = 150.0 + 90.0 * k
        val depth = 0.55f * k
        val levels = 48f - 20f * k   // crude bit reduction
        for (i in input.indices) {
            val x = input[i] / 32768f
            val mod = (1f - depth) + depth * (if (sin(2.0 * PI * modHz * i / sampleRate) >= 0.0) 1f else -1f)
            val quant = kotlin.math.round(x * mod * levels) / levels
            out[i] = (quant.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun whisper(input: ShortArray, sampleRate: Int, k: Float): ShortArray {
        val out = ShortArray(input.size)
        // one-pole low-pass (breathier, less body) + slow tremolo
        val cutoff = 3200f - 1400f * k
        val alpha = (1.0 - exp(-2.0 * PI * cutoff / sampleRate)).toFloat()
        var lp = 0f
        for (i in input.indices) {
            val x = input[i] / 32768f
            lp += alpha * (x - lp)
            val trem = 1f - 0.18f * k * (0.5f + 0.5f * sin(2.0 * PI * 4.5 * i / sampleRate).toFloat())
            out[i] = (lp * trem * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
