package com.eirahoutmoss.nexsense.core.signal

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

object VectorMath {
    fun magnitude(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)
}

/**
 * Titreşim: doğrusal ivme (yerçekimi çıkarılmış) büyüklüğünün kayan penceredeki RMS değeri.
 * Yöntem SensorSample.calibrationInfo içine yazılır.
 */
class VibrationWindow(private val capacity: Int = 100) {
    private val buf = DoubleArray(capacity)
    private var count = 0
    private var next = 0

    fun add(x: Double, y: Double, z: Double) {
        buf[next] = VectorMath.magnitude(x, y, z)
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    val size: Int get() = count

    /** Yeterli örnek yoksa null (ölçülmedi); sıfır döndürülmez. */
    fun rms(minSamples: Int = 10): Double? {
        if (count < minSamples) return null
        var s = 0.0
        for (i in 0 until count) s += buf[i] * buf[i]
        return sqrt(s / count)
    }

    fun clear() {
        count = 0; next = 0
    }

    companion object {
        const val METHOD = "rms(|linear_acceleration|), sliding window"
    }
}

/** Mikrofon seviyesi. Tam ölçeğe göre desibel (dBFS). Kalibre değildir: dB SPL olarak sunulmaz. */
object SoundLevel {
    const val FLOOR_DBFS = -120.0
    const val METHOD = "PCM16 mono; 20*log10(rms/32768); uncalibrated, not dB SPL"

    data class Level(val rmsDbfs: Double, val peakDbfs: Double)

    fun measure(pcm: ShortArray, length: Int = pcm.size): Level? {
        if (length <= 0) return null
        var sum = 0.0
        var peak = 0
        for (i in 0 until length) {
            val v = pcm[i].toInt()
            sum += v.toDouble() * v
            val a = abs(v)
            if (a > peak) peak = a
        }
        val rms = sqrt(sum / length)
        return Level(toDbfs(rms), toDbfs(peak.toDouble()))
    }

    fun toDbfs(amplitude: Double): Double =
        if (amplitude <= 0.0) FLOOR_DBFS else (20.0 * log10(amplitude / 32768.0)).coerceAtLeast(FLOOR_DBFS)
}
