package com.eirahoutmoss.nexsense.core.listen

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tatbikat sinyali: hoparlörden çalınan yapay vuruşlar. Ekip, gerçek bir olaydan önce dinleme düzenini
 * (telefon yerleşimi, sessiz an, birleştirme) güvenle dener. Tatbikat kayıtları her ekranda "TATBİKAT" etiketlidir.
 */
object DrillSignal {
    /** Tek vuruş: ~60 ms sönümlenen gürültü patlaması + 180 Hz gövde tınısı. Tohumlu: her seferinde aynı. */
    fun knock(fs: Int): FloatArray {
        val n = (fs * 0.06).toInt()
        val rnd = Random(7)
        return FloatArray(n) { i ->
            val t = i.toDouble() / fs
            val env = exp(-t / 0.012)
            (0.85 * env * (0.6 * (rnd.nextDouble() * 2 - 1) + 0.4 * sin(2 * PI * 180.0 * t))).toFloat()
        }
    }

    /**
     * [groups] kez [groupSize] vuruş ("3-3-3" uluslararası yardım işareti biçimi). Sinyal ve her vuruşun
     * başlangıç zamanı (s) döner. Başta [leadS] sessizlik vardır.
     */
    fun pattern(
        fs: Int,
        groupSize: Int = 3,
        groups: Int = 3,
        knockGapS: Double = 0.35,
        groupGapS: Double = 2.5,
        leadS: Double = 0.5,
    ): Pair<FloatArray, List<Double>> {
        val k = knock(fs)
        val times = ArrayList<Double>()
        var t = leadS
        repeat(groups) {
            repeat(groupSize) { j ->
                times += t
                if (j < groupSize - 1) t += knockGapS
            }
            t += groupGapS
        }
        val out = FloatArray(((t + 0.5) * fs).toInt())
        for (s in times) {
            val i0 = (s * fs).toInt()
            for (i in k.indices) if (i0 + i < out.size) out[i0 + i] += k[i]
        }
        return out to times
    }
}
