package com.eirahoutmoss.nexsense.core.acoustic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Yerinde, 2'nin kuvveti uzunlukta karmaşık FFT. */
object Fft {
    fun isPowerOfTwo(n: Int) = n > 0 && (n and (n - 1)) == 0

    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(isPowerOfTwo(n) && im.size == n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k]; val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br; im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br; im[i + k + len / 2] = ai - bi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun hann(n: Int): DoubleArray = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }

    /**
     * Gerçek sinyalin tek taraflı genlik spektrumu (n/2+1 kutu). Pencere uygulanır, ortalama çıkarılır.
     * Genlik, pencere kazancına göre düzeltilir: A genlikli sinüs ≈ A gösterir.
     */
    fun amplitudeSpectrum(x: DoubleArray, window: DoubleArray = hann(x.size), removeMean: Boolean = true): DoubleArray {
        val n = x.size
        val mean = if (removeMean) x.average() else 0.0
        val re = DoubleArray(n) { (x[it] - mean) * window[it] }
        val im = DoubleArray(n)
        transform(re, im)
        val gain = window.sum() / 2
        return DoubleArray(n / 2 + 1) { k -> sqrt(re[k] * re[k] + im[k] * im[k]) / gain }
    }
}

/** Spektrumda baskın tepe; frekans parabolik aradeğerlemeyle kutu aralığından incedir. */
data class Peak(val hz: Double, val amplitude: Double)

object Spectra {
    fun binHz(fs: Double, n: Int) = fs / n

    /** [minHz]..[maxHz] arasındaki en yüksek tepe. */
    fun dominant(spec: DoubleArray, fs: Double, n: Int, minHz: Double = 0.0, maxHz: Double = fs / 2): Peak? {
        val df = binHz(fs, n)
        val from = max(1, (minHz / df).toInt())
        val to = minOf(spec.size - 2, (maxHz / df).toInt())
        if (to <= from) return null
        var k = from
        for (i in from..to) if (spec[i] > spec[k]) k = i
        if (spec[k] <= 0) return null
        val a = spec[k - 1]; val b = spec[k]; val c = spec[k + 1]
        val denom = a - 2 * b + c
        val delta = if (denom == 0.0) 0.0 else (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5)
        return Peak((k + delta) * df, b - 0.25 * (a - c) * delta)
    }

    fun toDb(amplitude: Double, floor: Double = 1e-9): Double = 20 * log10(max(amplitude, floor))
}
