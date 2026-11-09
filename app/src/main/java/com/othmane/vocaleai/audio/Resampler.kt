package com.othmane.vocaleai.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Ricampionamento band-limited (sinc con finestra di Hann), a fasi polifase precalcolate.
 * Per 48 kHz -> 16 kHz filtra sotto i ~7.6 kHz prima di decimare, evitando aliasing.
 */
object Resampler {
    private const val ZERO_CROSSINGS = 8

    fun resample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate || input.isEmpty()) return input
        val g = gcd(srcRate, dstRate)
        val up = dstRate / g      // numero di fasi
        val down = srcRate / g
        val ratio = dstRate.toDouble() / srcRate
        val cutoff = min(1.0, ratio) * 0.95
        val halfWidth = ZERO_CROSSINGS / cutoff
        val w = ceil(halfWidth).toInt()
        val taps = 2 * w + 2

        // kernels[p][j]: peso del campione (base + j - w) per la fase p
        val phases = if (up <= 4096) up else 0
        val kernels = Array(phases) { p -> buildKernel(p.toDouble() / up, w, taps, cutoff, halfWidth) }

        val outLen = ((input.size.toLong() * up) / down).toInt()
        val out = FloatArray(outLen)
        val last = input.size - 1
        for (i in 0 until outLen) {
            val pos = i.toLong() * down
            val base = (pos / up).toInt()
            val phase = (pos % up).toInt()
            val kernel = if (phases > 0) kernels[phase] else buildKernel(phase.toDouble() / up, w, taps, cutoff, halfWidth)
            var acc = 0f
            for (j in 0 until taps) {
                val k = base + j - w
                if (k < 0 || k > last) continue
                acc += input[k] * kernel[j]
            }
            out[i] = acc
        }
        return out
    }

    private fun buildKernel(frac: Double, w: Int, taps: Int, cutoff: Double, halfWidth: Double): FloatArray {
        val kernel = FloatArray(taps)
        for (j in 0 until taps) {
            val d = frac - (j - w)          // distanza tra istante di uscita e campione
            if (abs(d) > halfWidth) continue
            val x = cutoff * d
            val sinc = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)
            val win = 0.5 * (1.0 + cos(PI * d / halfWidth))
            kernel[j] = (cutoff * sinc * win).toFloat()
        }
        return kernel
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
