package com.eirahoutmoss.nexsense.core.acoustic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sonar: hoparlörden kısa doğrusal tarama (chirp), mikrofonda doğrudan ses ve yankılar.
 * Mesafe = (yankı − doğrudan ses) × c / 2. Ses hızı sıcaklığa bağlıdır (ortam sıcaklığı sensörü yoksa 20 °C varsayılır).
 * Telefon hoparlörü yönlü değildir: en güçlü yankı genellikle en yakın büyük yüzeydir, telefonun baktığı yüzey olmayabilir.
 */
object Sonar {
    fun soundSpeed(tempC: Double = 20.0) = 331.3 + 0.606 * tempC

    /** Doğrusal tarama, Hann pencereli. */
    fun chirp(fs: Int, f0: Double, f1: Double, durS: Double): DoubleArray {
        val n = (fs * durS).toInt()
        return DoubleArray(n) { i ->
            val t = i.toDouble() / fs
            val w = 0.5 - 0.5 * cos(2 * PI * i / (n - 1))
            w * sin(2 * PI * (f0 * t + (f1 - f0) / (2 * durS) * t * t))
        }
    }

    /** [x] içinde [ref]'in 0..[maxLag] kaydırmalarındaki ilişki büyüklüğü (eşleşik süzgeç). */
    fun correlate(x: DoubleArray, ref: DoubleArray, maxLag: Int): DoubleArray {
        val lags = min(maxLag, x.size - ref.size).coerceAtLeast(0)
        return DoubleArray(lags) { lag -> var s = 0.0; for (i in ref.indices) s += x[lag + i] * ref[i]; abs(s) }
    }

    /** Birkaç ölçümün ilişki dizilerini doğrudan ses tepesine hizalayıp ortalar (gürültü azalır). */
    fun averageAligned(corrs: List<DoubleArray>, fs: Int): DoubleArray {
        if (corrs.isEmpty()) return DoubleArray(0)
        val heads = corrs.map { c -> directIndex(c, fs) }
        val len = corrs.indices.minOf { corrs[it].size - heads[it] }
        if (len <= 0) return DoubleArray(0)
        return DoubleArray(len) { i -> corrs.indices.sumOf { k -> corrs[k][heads[k] + i] } / corrs.size }
    }

    /** İlk 20 ms içindeki en güçlü tepe: hoparlörden mikrofona doğrudan ses. */
    fun directIndex(corr: DoubleArray, fs: Int): Int {
        var d = 0
        val first = min(corr.size, fs / 50)
        for (i in 0 until first) if (corr[i] > corr[d]) d = i
        return d
    }

    data class Echo(val distanceM: Double, val confidence: Double)

    /**
     * En güçlü [count] yankı, uzaklığa göre sıralı. Yankılar arasında en az [minSeparationM] olmalı
     * (aynı yankının yan tepeleri sayılmasın). [confidence]: tepe / gürültü ortancası oranı.
     * Oranı [minRatio]'nun altındaki ya da en güçlü yankının [minRelative] katından zayıf tepeler yankı sayılmaz.
     */
    fun echoes(
        corr: DoubleArray,
        fs: Int,
        c: Double = soundSpeed(),
        minDistM: Double = 0.3,
        maxDistM: Double = 6.0,
        count: Int = 3,
        minSeparationM: Double = 0.2,
        minRatio: Double = 4.0,
        /** En güçlü yankının bu oranından zayıf tepeler (yan loblar) yankı sayılmaz. */
        minRelative: Double = 0.15,
    ): List<Echo> {
        if (corr.size < 10) return emptyList()
        val direct = directIndex(corr, fs)
        val from = direct + (2 * minDistM / c * fs).toInt()
        val to = min(corr.size - 1, direct + (2 * maxDistM / c * fs).toInt())
        if (from >= to) return emptyList()
        val window = corr.copyOfRange(from, to + 1)
        val noise = window.sorted()[window.size / 2].coerceAtLeast(1e-12)
        val sepSamples = (2 * minSeparationM / c * fs).toInt().coerceAtLeast(1)
        // Yerel tepeler, güce göre
        val peaks = (from + 1 until to).filter { corr[it] >= corr[it - 1] && corr[it] > corr[it + 1] }
            .sortedByDescending { corr[it] }
        val chosen = ArrayList<Int>()
        val strongest = peaks.firstOrNull()?.let { corr[it] } ?: return emptyList()
        for (p in peaks) {
            if (corr[p] / noise < minRatio || corr[p] < strongest * minRelative) break
            if (chosen.all { abs(it - p) >= sepSamples }) chosen += p
            if (chosen.size >= count) break
        }
        return chosen.sorted().map { Echo((it - direct).toDouble() / fs * c / 2, corr[it] / noise) }
    }

    /** [pings] adet taramadan oluşan çalma dizisi; taramalar arası [spacingS] saniye. */
    fun pingTrain(chirp: DoubleArray, fs: Int, pings: Int, spacingS: Double, amplitude: Double = 0.9): FloatArray {
        val spacing = (spacingS * fs).toInt()
        val play = FloatArray(spacing * pings)
        for (k in 0 until pings) for (i in chirp.indices) play[k * spacing + i] = (amplitude * chirp[i]).toFloat()
        return play
    }

    /**
     * Darbe dizisi kaydının ilişki eğrisinde her darbeyi doğrudan ses tepesine hizalayıp ortalar
     * (NexTools Sonar yöntemi). Hoparlör gecikmesi bilinmese de çalışır: ilk güçlü tepe aranır.
     * Dönen eğri doğrudan sesten [lead] örnek önce başlar; [echoes] doğrudan sesi ilk 20 ms'de bulur.
     */
    fun alignPings(corr: DoubleArray, fs: Int, spacingS: Double, pings: Int, c: Double, maxDistM: Double, lead: Int = 60): DoubleArray {
        if (corr.isEmpty()) return DoubleArray(0)
        val spacing = (spacingS * fs).toInt()
        val win = lead + (2 * maxDistM / c * fs).toInt() + 10
        val max = corr.max()
        if (max <= 0) return DoubleArray(0)
        val first = corr.indexOfFirst { it > 0.5 * max }.coerceAtLeast(0)
        fun localMax(around: Int, r: Int): Int {
            var b = around.coerceIn(0, corr.size - 1)
            for (i in (around - r).coerceAtLeast(0)..(around + r).coerceAtMost(corr.size - 1)) if (corr[i] > corr[b]) b = i
            return b
        }
        val d0 = localMax(first + 20, 40)
        val avg = DoubleArray(win)
        var used = 0
        for (k in 0 until pings) {
            val dk = localMax(d0 + k * spacing, 30)
            if (dk - lead < 0 || dk - lead + win > corr.size) continue
            for (i in 0 until win) avg[i] += corr[dk - lead + i]
            used++
        }
        if (used == 0) return DoubleArray(0)
        for (i in avg.indices) avg[i] /= used
        return avg
    }

    /** Yankı güvenini 0..1'e taşır: oran 4 → 0, oran 40+ → 1 (logaritmik). */
    fun confidence01(ratio: Double): Double = ((log10(max(ratio, 1.0)) - log10(4.0)) / (log10(40.0) - log10(4.0))).coerceIn(0.0, 1.0)
}

/** Ses basınç düzeyi. Telefon mikrofonu kalibre değildir; kullanıcı bir referans ölçerle ofset girer. */
object SoundPressure {
    /** Kayıttaki eşdeğer düzey (enerji ortalaması), dBFS. */
    fun leqDbfs(pcm: FloatArray): Double? {
        if (pcm.isEmpty()) return null
        var s = 0.0
        for (v in pcm) s += v.toDouble() * v
        val rms = sqrt(s / pcm.size)
        return if (rms <= 0) -120.0 else max(-120.0, 20 * log10(rms))
    }

    fun peakDbfs(pcm: FloatArray): Double? {
        if (pcm.isEmpty()) return null
        val p = pcm.maxOf { abs(it) }.toDouble()
        return if (p <= 0) -120.0 else max(-120.0, 20 * log10(p))
    }

    /** dB SPL ≈ dBFS + ofset. */
    fun spl(dbfs: Double, offsetDb: Double): Double = dbfs + offsetDb

    /** Referans ölçer [referenceDbSpl] gösterirken telefon [measuredDbfs] ölçtüyse ofset. */
    fun offsetFrom(referenceDbSpl: Double, measuredDbfs: Double): Double = referenceDbSpl - measuredDbfs

    /** Kayıttan baskın frekans (Hann, 2'nin kuvveti uzunlukta son bölüm). */
    fun dominantHz(pcm: FloatArray, fs: Int, minHz: Double = 40.0, maxHz: Double = 8000.0): Double? {
        var n = 1
        while (n * 2 <= pcm.size && n < 1 shl 15) n *= 2
        if (n < 1024) return null
        val x = DoubleArray(n) { pcm[pcm.size - n + it].toDouble() }
        return Spectra.dominant(Fft.amplitudeSpectrum(x), fs.toDouble(), n, minHz, maxHz)?.hz
    }
}
