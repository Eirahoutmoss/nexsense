package com.eirahoutmoss.nexsense.core.listen

import com.eirahoutmoss.nexsense.core.acoustic.Fft
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Mikrofon akışında olay algılayıcı (vuruş, ses benzeri, diğer ses).
 *
 * 10 ms çerçevelerin enerjisi (dBFS) izlenir. İlk [calibrationS] saniye gürültü tabanını öğrenir (ortanca);
 * sonra taban yavaşça uyarlanır. Taban + [thresholdDb] aşılınca olay başlar, taban + [thresholdDb] − 6 dB'nin
 * altında 30 ms kalınca biter. Ses örnekleri yalnızca olay süresince, en fazla 1 sn, bellekte tutulur ve
 * olay sınıflanınca atılır. **Ses diske yazılmaz.**
 */
class SoundEventDetector(
    val fs: Int,
    val calibrationS: Double = 2.0,
    val thresholdDb: Double = 12.0,
    val frameS: Double = 0.01,
    val maxEventS: Double = 3.0,
) {
    private val frameN = max(1, (fs * frameS).roundToInt())
    private val frame = FloatArray(frameN)
    private var frameFill = 0
    private var frameIndex = 0L
    private val calib = ArrayList<Double>()

    /** Gürültü tabanı (dBFS); kalibrasyon bitene kadar null. */
    var floorDbfs: Double? = null
        private set

    /** Son çerçeve, tabanın kaç dB üstünde (arayüz göstergesi). */
    var lastAboveFloorDb: Double = 0.0
        private set

    val calibrated: Boolean get() = floorDbfs != null

    private var inEvent = false
    private var evStartFrame = 0L
    private val evDb = ArrayList<Double>()
    private var evSamples = FloatArray(fs)
    private var evSampleN = 0
    private var below = 0

    /** Kayıt başından geçen süre (s). */
    val timeS: Double get() = frameIndex * frameS

    fun feed(block: FloatArray): List<ListenEvent> {
        val out = ArrayList<ListenEvent>()
        for (v in block) {
            frame[frameFill++] = v
            if (frameFill == frameN) {
                processFrame()?.let { out += it }
                frameFill = 0
            }
        }
        return out
    }

    /** Akış bittiğinde açık olayı kapatır. */
    fun flush(): List<ListenEvent> = if (inEvent) listOfNotNull(endEvent()) else emptyList()

    private fun processFrame(): ListenEvent? {
        var sq = 0.0
        for (v in frame) sq += v.toDouble() * v
        val db = 10 * log10(sq / frameN + 1e-12)
        frameIndex++
        val floor = floorDbfs
        if (floor == null) {
            calib += db
            if (calib.size * frameS >= calibrationS) floorDbfs = calib.sorted()[calib.size / 2]
            return null
        }
        lastAboveFloorDb = db - floor
        if (!inEvent) {
            if (db >= floor + thresholdDb) {
                inEvent = true
                evStartFrame = frameIndex - 1
                evDb.clear(); evSampleN = 0; below = 0
                addEventFrame(db)
                return null
            }
            // Taban uyarlaması: aşağı hızlı, yukarı yavaş (yalnızca sakin çerçevelerle).
            if (db < floor) floorDbfs = floor * 0.9 + db * 0.1
            else if (db < floor + 6) floorDbfs = floor * 0.995 + db * 0.005
            return null
        }
        addEventFrame(db)
        if (db < floor + thresholdDb - 6) below++ else below = 0
        if (below >= 3 || evDb.size * frameS >= maxEventS) return endEvent()
        return null
    }

    private fun addEventFrame(db: Double) {
        evDb += db
        val n = min(frameN, evSamples.size - evSampleN)
        if (n > 0) { System.arraycopy(frame, 0, evSamples, evSampleN, n); evSampleN += n }
    }

    private fun endEvent(): ListenEvent? {
        inEvent = false
        val floor = floorDbfs ?: return null
        // Sondaki sessiz çerçeveler olaya dahil değil.
        val active = evDb.dropLastWhile { it < floor + thresholdDb - 6 }.ifEmpty { evDb.take(1) }
        val duration = active.size * frameS
        val peak = active.max()
        val peakIdx = active.indexOf(peak)
        val voice = voiceScore(evSamples.copyOf(evSampleN), active)
        val long = evDb.size * frameS >= maxEventS
        val kind = when {
            duration <= 0.25 && peakIdx <= 3 -> ListenKind.KNOCK
            duration >= 0.3 && voice >= 0.5 -> ListenKind.VOICE_LIKE
            else -> ListenKind.SOUND
        }
        // Çok uzun süren düzgün ses (makine, rüzgâr) yeni taban olur; aksi hâlde sürekli olay üretir.
        if (long) floorDbfs = active.sorted()[active.size / 2]
        evSampleN = 0
        return ListenEvent(
            t = round3(evStartFrame * frameS),
            kind = kind,
            durationS = round3(duration),
            peakDb = round2(peak),
            aboveFloorDb = round2(peak - floor),
            voiceScore = round2(voice),
        )
    }

    /**
     * Ses benzerliği 0..1: konuşma bandının (300–3400 Hz) enerji payı ve hecemsi dalgalanma (çerçeve
     * düzeylerinin yayılımı). Bu bir insan sesi tanıma değildir; yalnızca "sese benziyor" ipucudur.
     */
    private fun voiceScore(x: FloatArray, frameDb: List<Double>): Double {
        if (x.size < fs / 4 || frameDb.size < 25) return 0.0
        var n = 1
        while (n * 2 <= x.size && n * 2 <= 16384) n *= 2
        val spec = Fft.amplitudeSpectrum(DoubleArray(n) { x[it].toDouble() })
        val bin = fs.toDouble() / n
        var band = 0.0; var total = 0.0
        for (k in spec.indices) {
            val hz = k * bin
            if (hz < 60 || hz > 8000) continue
            val p = spec[k] * spec[k]
            total += p
            if (hz in 300.0..3400.0) band += p
        }
        if (total <= 0) return 0.0
        val ratio = band / total
        val mean = frameDb.average()
        val std = sqrt(frameDb.sumOf { (it - mean) * (it - mean) } / frameDb.size)
        val bandScore = ((ratio - 0.4) / 0.5).coerceIn(0.0, 1.0)
        val modScore = ((std - 1.5) / 6.0).coerceIn(0.0, 1.0)
        return 0.6 * bandScore + 0.4 * modScore
    }
}

/**
 * İvmeölçerde ani sarsıntı. Telefon enkaz, boru ya da duvara dayalıyken yapıdan iletilen vuruşu yakalar.
 * İlk [calibrationS] saniye ortalama ve yayılım öğrenilir; sapma max([kSigma]·σ, [minDelta]) aşılınca olay.
 */
class VibrationDetector(
    val calibrationS: Double = 2.0,
    val kSigma: Double = 6.0,
    val minDelta: Double = 0.15,
    val refractoryS: Double = 0.15,
) {
    private val calib = ArrayList<Double>()
    private var t0: Double? = null
    private var mean = 0.0
    private var std = 0.0
    private var lastEventT = -1e9
    var calibrated = false
        private set

    fun feed(tS: Double, magnitude: Double): ListenEvent? {
        val start = t0 ?: tS.also { t0 = it }
        if (!calibrated) {
            calib += magnitude
            if (tS - start >= calibrationS && calib.size >= 10) {
                mean = calib.average()
                std = sqrt(calib.sumOf { (it - mean) * (it - mean) } / calib.size)
                calibrated = true
            }
            return null
        }
        val delta = abs(magnitude - mean)
        val limit = max(kSigma * std, minDelta)
        if (delta > limit) {
            if (tS - lastEventT < refractoryS) return null
            lastEventT = tS
            return ListenEvent(
                t = round3(tS), kind = ListenKind.VIBRATION, durationS = 0.0,
                peakDb = round2(20 * log10(delta)), aboveFloorDb = round2(20 * log10(delta / max(std, 1e-4))),
            )
        }
        mean = mean * 0.999 + magnitude * 0.001
        return null
    }
}

/** Vuruş deseni: gruplar ve tekrar. "3-3-3" gibi bilinçli vuruşlar rastgele seslerden bu şekilde ayrılır. */
data class KnockPattern(
    val times: List<Double>,
    val groups: List<List<Double>>,
    /** En az iki kez tekrarlanan grup büyüklüğü (≥ 2), yoksa null. */
    val repeatedSize: Int?,
    val repeats: Int,
    /** ≥ 4 vuruş düzenli aralıkla (değişim katsayısı < 0,25). */
    val regular: Boolean,
    val intervalS: Double?,
) {
    val looksDeliberate: Boolean get() = repeatedSize != null || regular
}

object Patterns {
    /**
     * Vuruş ve titreşim olaylarından desen. Mikrofon ve ivmeölçer aynı vuruşu 0,12 sn içinde yakalarsa tek sayılır.
     * Aralık [maxInGroupGapS]'den büyükse yeni grup başlar.
     */
    fun analyze(events: List<ListenEvent>, maxInGroupGapS: Double = 1.0): KnockPattern {
        val raw = events.filter { it.kind == ListenKind.KNOCK || it.kind == ListenKind.VIBRATION }.map { it.t }.sorted()
        val times = ArrayList<Double>()
        for (t in raw) if (times.isEmpty() || t - times.last() > 0.12) times += t
        val groups = ArrayList<MutableList<Double>>()
        for (t in times) {
            if (groups.isEmpty() || t - groups.last().last() > maxInGroupGapS) groups += mutableListOf(t) else groups.last() += t
        }
        val counts = groups.map { it.size }.filter { it >= 2 }.groupingBy { it }.eachCount()
        val best = counts.filter { it.value >= 2 }.maxWithOrNull(compareBy({ it.value }, { it.key }))
        var regular = false
        var interval: Double? = null
        if (times.size >= 4) {
            val d = times.zipWithNext { a, b -> b - a }
            val m = d.average()
            val sd = sqrt(d.sumOf { (it - m) * (it - m) } / d.size)
            if (m > 0 && sd / m < 0.25) { regular = true; interval = round3(m) }
        }
        return KnockPattern(times, groups, best?.key, best?.value ?: 0, regular, interval)
    }
}

internal fun round2(v: Double) = Math.round(v * 100.0) / 100.0
internal fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0
