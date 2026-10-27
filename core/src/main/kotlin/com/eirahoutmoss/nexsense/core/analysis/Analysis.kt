package com.eirahoutmoss.nexsense.core.analysis

import com.eirahoutmoss.nexsense.core.layers.LayerAnalysis
import com.eirahoutmoss.nexsense.core.layers.LayerData
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.layers.LayerStatus
import com.eirahoutmoss.nexsense.core.model.AnalysisSettings
import com.eirahoutmoss.nexsense.core.model.MeasurementPoint
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.model.Threshold
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.spatial.FrameMath
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Temel istatistikler. Boş listede null döner (sıfır değil). */
object Stats {
    fun mean(v: List<Double>): Double? = if (v.isEmpty()) null else v.sum() / v.size

    fun median(v: List<Double>): Double? {
        if (v.isEmpty()) return null
        val s = v.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    /** Ortanca mutlak sapma. */
    fun mad(v: List<Double>): Double? {
        val m = median(v) ?: return null
        return median(v.map { abs(it - m) })
    }

    /** Pearson korelasyonu; varyans sıfırsa ya da 3'ten az çift varsa null. */
    fun pearson(a: List<Double>, b: List<Double>): Double? {
        require(a.size == b.size)
        val n = a.size
        if (n < 3) return null
        val ma = a.sum() / n; val mb = b.sum() / n
        var sab = 0.0; var saa = 0.0; var sbb = 0.0
        for (i in 0 until n) {
            val da = a[i] - ma; val db = b[i] - mb
            sab += da * db; saa += da * da; sbb += db * db
        }
        if (saa <= 0 || sbb <= 0) return null
        return (sab / sqrt(saa * sbb)).coerceIn(-1.0, 1.0)
    }
}

/** Ölçüm yetersizliği işaretleri. Metinleri uygulamada yerelleştirilir. */
enum class CoverageFlag { NO_DATA, FEW_POINTS, MOSTLY_UNPLACED, LARGE_GAPS, ONE_SESSION }

data class LayerCoverage(
    val kind: LayerKind,
    val measured: Int,
    val placed: Int,
    /** Ölçüm alanının (1 m hücreler) içinde, 1 m yakınında ölçüm olmayan hücre oranı; hesaplanamadıysa null. */
    val gapFraction: Double?,
    val sessions: Int,
    val flags: List<CoverageFlag>,
)

object Coverage {
    const val MIN_POINTS = 4

    fun of(bundle: ProjectBundle, data: LayerData): LayerCoverage {
        val pts = bundle.points.associateBy { it.id }
        val placed = data.values.filter { it.placed }.mapNotNull { pts[it.pointId] }
        val gap = gapFraction(placed)
        val sessions = data.values.mapNotNull { pts[it.pointId]?.sessionId }.toSet().size
        val flags = buildList {
            if (data.status == LayerStatus.NO_DATA) add(CoverageFlag.NO_DATA)
            else {
                if (data.values.size < MIN_POINTS) add(CoverageFlag.FEW_POINTS)
                if (placed.size * 2 < data.values.size) add(CoverageFlag.MOSTLY_UNPLACED)
                if (gap != null && gap > 0.4) add(CoverageFlag.LARGE_GAPS)
                if (sessions <= 1) add(CoverageFlag.ONE_SESSION)
            }
        }
        return LayerCoverage(data.kind, data.values.size, placed.size, gap, sessions, flags)
    }

    /** Noktaların kapsadığı dikdörtgende (1 m hücre) en yakın ölçüme 1 m'den uzak hücrelerin oranı. */
    fun gapFraction(points: List<MeasurementPoint>, cellM: Double = 1.0): Double? {
        if (points.size < 2) return null
        val xs = points.map { it.x!! }; val ys = points.map { it.y!! }
        val nx = max(1, ceil((xs.max() - xs.min()) / cellM).toInt())
        val ny = max(1, ceil((ys.max() - ys.min()) / cellM).toInt())
        var empty = 0
        for (i in 0 until nx) for (j in 0 until ny) {
            val cx = xs.min() + (i + 0.5) * cellM
            val cy = ys.min() + (j + 0.5) * cellM
            val near = points.any { p -> val dx = p.x!! - cx; val dy = p.y!! - cy; dx * dx + dy * dy <= cellM * cellM }
            if (!near) empty++
        }
        return empty.toDouble() / (nx * ny)
    }
}

/**
 * Ara değer kestirimi (ters uzaklık ağırlıklı, IDW, p = 2), yatay düzlemde (x, y).
 * - Yalnızca ölçülen noktalardan EN FAZLA [AnalysisSettings.maxEstimateDistanceM] uzaklıktaki hücreler kestirilir;
 *   daha uzaktaki hücreler "bilinmiyor" (null) kalır.
 * - En az [Coverage.MIN_POINTS] yerleşik ölçüm yoksa ızgara üretilmez.
 * - Yükseklik (z) yok sayılır; farklı yüksekliklerdeki ölçümler aynı düzlemde birleşir — bu yöntem notunda yazar.
 * - Güven: en yakın ölçüme uzaklıkla doğrusal düşer (ölçüm noktasında 1, sınırda 0).
 */
data class EstimateGrid(
    val kind: LayerKind,
    val originX: Double,
    val originY: Double,
    val cellM: Double,
    val nx: Int,
    val ny: Int,
    val values: Array<Double?>,
    val confidence: DoubleArray,
    val sourceCount: Int,
    val method: String,
) {
    fun at(i: Int, j: Int): Double? = values[j * nx + i]
    fun confidenceAt(i: Int, j: Int): Double = confidence[j * nx + i]
    val estimatedCells: Int get() = values.count { it != null }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

object Interpolation {
    const val METHOD = "IDW p=2, horizontal (x,y), z ignored, no extrapolation beyond max distance"

    fun idw(bundle: ProjectBundle, data: LayerData, settings: AnalysisSettings = bundle.analysis): EstimateGrid? {
        val pts = bundle.points.associateBy { it.id }
        val src = data.values.filter { it.placed }.mapNotNull { v -> pts[v.pointId]?.let { Triple(it.x!!, it.y!!, v.value) } }
        if (src.size < Coverage.MIN_POINTS) return null
        val cell = settings.gridCellM
        val maxD = settings.maxEstimateDistanceM
        val minX = floor((src.minOf { it.first } - maxD) / cell) * cell
        val minY = floor((src.minOf { it.second } - maxD) / cell) * cell
        val nx = min(400, ceil((src.maxOf { it.first } + maxD - minX) / cell).toInt())
        val ny = min(400, ceil((src.maxOf { it.second } + maxD - minY) / cell).toInt())
        val vals = arrayOfNulls<Double>(nx * ny)
        val conf = DoubleArray(nx * ny)
        for (j in 0 until ny) for (i in 0 until nx) {
            val cx = minX + (i + 0.5) * cell
            val cy = minY + (j + 0.5) * cell
            var num = 0.0; var den = 0.0; var dmin = Double.MAX_VALUE; var exact: Double? = null
            for ((x, y, v) in src) {
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                if (d < dmin) dmin = d
                if (d < 1e-9) { exact = v; break }
                val w = 1.0 / (d * d)
                num += w * v; den += w
            }
            if (dmin > maxD) continue
            vals[j * nx + i] = exact ?: (num / den)
            conf[j * nx + i] = (1.0 - dmin / maxD).coerceIn(0.0, 1.0)
        }
        return EstimateGrid(data.kind, minX, minY, cell, nx, ny, vals, conf, src.size, METHOD)
    }
}

data class ThresholdAlert(val kind: LayerKind, val pointId: String, val value: Double, val above: Boolean, val limit: Double)

object Thresholds {
    fun check(data: Map<LayerKind, LayerData>, thresholds: List<Threshold>): List<ThresholdAlert> = buildList {
        for (t in thresholds) {
            val kind = LayerKind.entries.firstOrNull { it.name == t.layer } ?: continue
            val d = data[kind] ?: continue
            for (v in d.values) {
                t.max?.let { if (v.value > it) add(ThresholdAlert(kind, v.pointId, v.value, true, it)) }
                t.min?.let { if (v.value < it) add(ThresholdAlert(kind, v.pointId, v.value, false, it)) }
            }
        }
    }
}

/** Robust z-puanı |z| > 3,5 olan değerler (Iglewicz & Hoaglin). Kesin bulgu değil, "bakılmaya değer aday". */
data class AnomalyCandidate(val kind: LayerKind, val pointId: String, val value: Double, val robustZ: Double)

object Anomalies {
    const val MIN_POINTS = 6
    const val Z_LIMIT = 3.5

    fun find(data: LayerData): List<AnomalyCandidate> {
        if (data.values.size < MIN_POINTS) return emptyList()
        val v = data.values.map { it.value }
        val med = Stats.median(v)!!
        val mad = Stats.mad(v)!!
        if (mad < 1e-12) return emptyList()
        return data.values.mapNotNull {
            val z = 0.6745 * (it.value - med) / mad
            if (abs(z) > Z_LIMIT) AnomalyCandidate(data.kind, it.pointId, it.value, z) else null
        }.sortedByDescending { abs(it.robustZ) }
    }
}

/** İki katmanın aynı noktalardaki değerleri arasındaki ilişki. Korelasyon nedensellik değildir. */
data class LayerCorrelation(val a: LayerKind, val b: LayerKind, val r: Double, val n: Int)

object Correlations {
    const val MIN_PAIRS = 5

    fun all(data: Map<LayerKind, LayerData>): List<LayerCorrelation> {
        val kinds = data.values.filter { it.kind.isMeasurement && it.status == LayerStatus.HAS_DATA }.map { it.kind }
        val out = ArrayList<LayerCorrelation>()
        for (i in kinds.indices) for (j in i + 1 until kinds.size) {
            val a = data.getValue(kinds[i]).values.associate { it.pointId to it.value }
            val b = data.getValue(kinds[j]).values.associate { it.pointId to it.value }
            val common = a.keys.intersect(b.keys).toList()
            if (common.size < MIN_PAIRS) continue
            val r = Stats.pearson(common.map { a.getValue(it) }, common.map { b.getValue(it) }) ?: continue
            out += LayerCorrelation(kinds[i], kinds[j], r, common.size)
        }
        return out.sortedByDescending { abs(it.r) }
    }
}

/** Zaman çizgisi: oturum bazında katman özeti ve oturumlar arası değişim. */
data class SessionLayerStat(val sessionId: String, val index: Int, val kind: LayerKind, val n: Int, val median: Double, val min: Double, val max: Double)

data class PointChange(val kind: LayerKind, val fromPointId: String, val toPointId: String, val fromSession: Int, val toSession: Int, val delta: Double, val distanceM: Double)

object Timeline {
    fun stats(bundle: ProjectBundle): List<SessionLayerStat> = buildList {
        bundle.sessions.forEachIndexed { idx, s ->
            val sub = bundle.copy(points = bundle.points.filter { it.sessionId == s.id }, samples = bundle.samples.filter { it.sessionId == s.id })
            for (k in LayerKind.entries.filter { it.isMeasurement }) {
                val d = LayerAnalysis.compute(sub, k)
                if (d.values.isEmpty()) continue
                val v = d.values.map { it.value }
                add(SessionLayerStat(s.id, idx + 1, k, v.size, Stats.median(v)!!, v.min(), v.max()))
            }
        }
    }

    /**
     * Ardışık oturumlarda birbirine [radiusM]'den yakın (yerleşik) noktaların değer farkı.
     * Aynı yerin farklı zamanlardaki ölçümlerini karşılaştırır. Büyük farka göre sıralı.
     */
    fun changes(bundle: ProjectBundle, radiusM: Double = 0.5): List<PointChange> {
        val out = ArrayList<PointChange>()
        val all = LayerAnalysis.computeAll(bundle)
        val pts = bundle.points.associateBy { it.id }
        val sessionIndex = bundle.sessions.mapIndexed { i, s -> s.id to i + 1 }.toMap()
        for (k in LayerKind.entries.filter { it.isMeasurement }) {
            val vals = all.getValue(k).values.filter { it.placed }
            for (i in 1 until bundle.sessions.size) {
                val prev = vals.filter { pts[it.pointId]?.sessionId == bundle.sessions[i - 1].id }
                val cur = vals.filter { pts[it.pointId]?.sessionId == bundle.sessions[i].id }
                for (c in cur) {
                    val pc = pts.getValue(c.pointId)
                    val best = prev.minByOrNull { dist(pts.getValue(it.pointId), pc) } ?: continue
                    val d = dist(pts.getValue(best.pointId), pc)
                    if (d <= radiusM) {
                        out += PointChange(k, best.pointId, c.pointId, sessionIndex.getValue(bundle.sessions[i - 1].id), sessionIndex.getValue(bundle.sessions[i].id), c.value - best.value, d)
                    }
                }
            }
        }
        return out.sortedByDescending { abs(it.delta) }
    }

    private fun dist(a: MeasurementPoint, b: MeasurementPoint): Double {
        val dx = a.x!! - b.x!!; val dy = a.y!! - b.y!!; val dz = a.z!! - b.z!!
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * Yönelimden bağımsız manyetik bileşenler. Cihaz eksenlerindeki vektör, kayıtlı dönüşüm matrisiyle
 * Doğu-Kuzey-Yukarı çerçevesine çevrilir. Matris aynı manyetometreden hesaplandığı için doğu bileşeni
 * tanım gereği ~0'dır; anlamlı olanlar yatay (H) ve düşey (Z) bileşen ile eğim (inklinasyon) açısıdır.
 */
data class MagneticWorld(val pointId: String, val horizontal: Double, val vertical: Double, val inclinationDeg: Double)

object MagneticFrame {
    fun of(bundle: ProjectBundle): List<MagneticWorld> = bundle.samples
        .filter { it.sensorType == SensorType.MAGNETIC_FIELD && it.pointId != null }
        .mapNotNull { s ->
            val m = s.deviceOrientation?.rotationMatrix ?: return@mapNotNull null
            if (m.size != 9) return@mapNotNull null
            val x = s.values["x"] ?: return@mapNotNull null
            val y = s.values["y"] ?: return@mapNotNull null
            val z = s.values["z"] ?: return@mapNotNull null
            val w = FrameMath.mul(m, Vec3(x, y, z))
            val h = sqrt(w.x * w.x + w.y * w.y)
            MagneticWorld(s.pointId!!, h, w.z, Math.toDegrees(atan2(-w.z, h)))
        }
}

/** Nokta bulutundan yaklaşık oda boyutu (%2–%98 yüzdelik kutusu). Akustik oda modları için kullanılır. */
data class RoomEstimate(val lengthM: Double, val widthM: Double, val heightM: Double, val pointCount: Int)

object RoomEstimator {
    fun of(bundle: ProjectBundle): RoomEstimate? {
        val pts = bundle.scans.filter { it.method == ScanMethod.ARCORE || it.method == ScanMethod.DEMO }.flatMap { it.points }
        if (pts.size < 50) return null
        fun span(v: List<Double>): Double {
            val s = v.sorted()
            return s[(s.size * 0.98).toInt().coerceAtMost(s.size - 1)] - s[(s.size * 0.02).toInt()]
        }
        val a = span(pts.map { it.x }); val b = span(pts.map { it.y })
        return RoomEstimate(max(a, b), min(a, b), span(pts.map { it.z }), pts.size)
    }
}

/** Raporda ve Analiz ekranında kullanılan toplu sonuç. */
data class AnalysisResult(
    val layers: Map<LayerKind, LayerData>,
    val coverage: List<LayerCoverage>,
    val alerts: List<ThresholdAlert>,
    val anomalies: List<AnomalyCandidate>,
    val correlations: List<LayerCorrelation>,
    val timeline: List<SessionLayerStat>,
    val changes: List<PointChange>,
    val magnetic: List<MagneticWorld>,
    val room: RoomEstimate?,
)

object AnalysisEngine {
    fun run(bundle: ProjectBundle): AnalysisResult {
        val layers = LayerAnalysis.computeAll(bundle)
        val measured = layers.values.filter { it.kind.isMeasurement }
        return AnalysisResult(
            layers = layers,
            coverage = measured.map { Coverage.of(bundle, it) },
            alerts = Thresholds.check(layers, bundle.analysis.thresholds),
            anomalies = measured.flatMap { Anomalies.find(it) },
            correlations = Correlations.all(layers),
            timeline = Timeline.stats(bundle),
            changes = Timeline.changes(bundle),
            magnetic = MagneticFrame.of(bundle),
            room = RoomEstimator.of(bundle),
        )
    }
}
