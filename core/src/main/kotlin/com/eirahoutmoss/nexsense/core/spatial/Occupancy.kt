package com.eirahoutmoss.nexsense.core.spatial

import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.core.model.ScanRay
import com.eirahoutmoss.nexsense.core.scene.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Her ortamda haritalama: dünya yatay düzlemde küçük hücrelere bölünür; her hücre üç durumdan birindedir:
 *  - DOLU: bir yüzey/yankı oradan döndü,
 *  - BOŞ: telefon oradan geçti ya da bir ölçüm ışını oradan geçti,
 *  - BİLİNMİYOR: hiçbir ölçüm ulaşmadı.
 * Oda, köşe ya da düz duvar varsayımı yoktur: mağara, tünel, enkaz boşluğu aynı biçimde haritalanır.
 *
 * Olasılıksal (log-odds) birikim: aynı hücreye gelen çelişkili ölçümler birbirini dengeler, güven |log-odds| ile artar.
 */
enum class CellState { UNKNOWN, FREE, OCCUPIED }

data class Cell(val i: Int, val j: Int, val state: CellState, val confidence: Double)

class OccupancyGrid(val cellM: Double = 0.1) {
    private val l = HashMap<Long, Float>()

    val size: Int get() = l.size

    fun key(i: Int, j: Int): Long = (i.toLong() shl 32) or (j.toLong() and 0xffffffffL)
    fun ix(x: Double): Int = floor(x / cellM).toInt()
    fun center(i: Int, j: Int): Vec3 = Vec3((i + 0.5) * cellM, (j + 0.5) * cellM, 0.0)

    fun logOdds(i: Int, j: Int): Double = (l[key(i, j)] ?: 0f).toDouble()

    fun state(i: Int, j: Int): CellState {
        val v = logOdds(i, j)
        return when {
            v > OCC_T -> CellState.OCCUPIED
            v < FREE_T -> CellState.FREE
            else -> CellState.UNKNOWN
        }
    }

    fun stateAt(x: Double, y: Double): CellState = state(ix(x), ix(y))

    /** 0..1: log-odds büyüklüğüne göre. */
    fun confidence(i: Int, j: Int): Double = (abs(logOdds(i, j)) / L_MAX).coerceIn(0.0, 1.0)

    private fun bump(i: Int, j: Int, d: Double) {
        val k = key(i, j)
        val v = ((l[k] ?: 0f) + d).coerceIn(-L_MAX, L_MAX)
        l[k] = v.toFloat()
    }

    fun addOccupied(x: Double, y: Double, weight: Double = 1.0) = bump(ix(x), ix(y), L_OCC * weight)

    fun addFree(x: Double, y: Double, weight: Double = 1.0) = bump(ix(x), ix(y), L_FREE * weight)

    /** Telefonun geçtiği yer kesin boştur (insan sığdı): küçük bir disk boş işaretlenir. */
    fun addFreeDisk(x: Double, y: Double, r: Double, weight: Double = 1.0) {
        val n = (r / cellM).toInt()
        val ci = ix(x); val cj = ix(y)
        for (di in -n..n) for (dj in -n..n) {
            if (di * di + dj * dj <= n * n) bump(ci + di, cj + dj, L_FREE * weight)
        }
    }

    /**
     * Işın: başlangıçtan uca kadar hücreler boş; [hit] ise uç hücre dolu.
     * Uç hücre ve hemen öncesi boşaltılmaz (yüzeyin kendisi).
     */
    fun addRay(ox: Double, oy: Double, ex: Double, ey: Double, hit: Boolean, weight: Double = 1.0) {
        val i0 = ix(ox); val j0 = ix(oy); val i1 = ix(ex); val j1 = ix(ey)
        val cells = bresenham(i0, j0, i1, j1)
        val stop = if (hit) cells.size - 1 else cells.size
        for (k in 0 until stop) bump(cells[k].first, cells[k].second, L_FREE * weight)
        if (hit) bump(i1, j1, L_OCC * weight)
    }

    fun cells(): List<Cell> = l.entries.map { (k, _) ->
        val i = (k shr 32).toInt(); val j = k.toInt()
        Cell(i, j, state(i, j), confidence(i, j))
    }

    fun count(state: CellState): Int = cells().count { it.state == state }

    /** Alan (m²). */
    fun area(state: CellState): Double = count(state) * cellM * cellM

    companion object {
        const val L_OCC = 0.85
        const val L_FREE = -0.4
        const val L_MAX = 4.0
        const val OCC_T = 0.3
        const val FREE_T = -0.15

        fun bresenham(i0: Int, j0: Int, i1: Int, j1: Int): List<Pair<Int, Int>> {
            val out = ArrayList<Pair<Int, Int>>()
            var x = i0; var y = j0
            val dx = abs(i1 - i0); val dy = -abs(j1 - j0)
            val sx = if (i0 < i1) 1 else -1; val sy = if (j0 < j1) 1 else -1
            var err = dx + dy
            var guard = 0
            while (guard++ < 100_000) {
                out += x to y
                if (x == i1 && y == j1) break
                val e2 = 2 * err
                if (e2 >= dy) { err += dy; x += sx }
                if (e2 <= dx) { err += dx; y += sy }
            }
            return out
        }

        /**
         * Projedeki tüm taramalardan harita kurar:
         *  - telefonun yolu (iz) → boş (0,25 m yarıçap),
         *  - ARCore noktaları, telefon yüksekliği çevresindeki bantta → dolu (güvenle ağırlıklı),
         *  - akustik radar ışınları → yol boyunca boş, yankıda dolu,
         *  - akustik yön taraması noktaları → zayıf dolu,
         *  - konumlu ölçüm noktaları → boş (orada biri durdu).
         */
        fun build(bundle: ProjectBundle, cellM: Double = 0.1, extraRays: List<ScanRay> = emptyList(), extraTrack: List<Vec3> = emptyList(), extraPoints: List<Vec3> = emptyList()): OccupancyGrid {
            val g = OccupancyGrid(cellM)
            val track = bundle.scans.flatMap { s -> s.trajectory.map { Vec3(it.x, it.y, it.z) } } + extraTrack
            val zRef = track.map { it.z }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
            for (p in track) g.addFreeDisk(p.x, p.y, 0.25, 0.5)
            for (p in bundle.points) if (p.isPlaced) g.addFreeDisk(p.x!!, p.y!!, 0.2, 0.5)
            fun inBand(z: Double) = zRef == null || (z >= zRef - 1.2 && z <= zRef + 0.8)
            for (s in bundle.scans) {
                when (s.method) {
                    ScanMethod.ARCORE, ScanMethod.DEMO -> for (p in s.points) if (inBand(p.z)) g.addOccupied(p.x, p.y, 0.4 + 0.6 * p.c)
                    ScanMethod.ACOUSTIC_ECHO -> for (p in s.points) g.addOccupied(p.x, p.y, 0.5 * p.c)
                    else -> Unit
                }
                for (r in s.rays) g.addRay(r.ox, r.oy, r.x, r.y, r.hit, if (r.hit) 0.4 + 0.6 * r.c else 0.3)
            }
            for (p in extraPoints) if (inBand(p.z)) g.addOccupied(p.x, p.y, 0.7)
            for (r in extraRays) g.addRay(r.ox, r.oy, r.x, r.y, r.hit, if (r.hit) 0.4 + 0.6 * r.c else 0.3)
            return g
        }
    }
}

/** Keşif sınırı: bilinen boş alanın bilinmeyenle buluştuğu yer. Kümenin merkezi ve büyüklüğü. */
data class Frontier(val cells: Int, val centroid: Vec3, val nearest: Vec3)

object Frontiers {
    /** Boş olup 4 komşusundan en az biri bilinmeyen hücreler; 8-bağlı kümeler; [minCells]'ten küçükler gürültüdür. */
    fun find(g: OccupancyGrid, minCells: Int = 4): List<Frontier> {
        val free = g.cells().filter { it.state == CellState.FREE }
        val isFrontier = HashSet<Long>()
        for (c in free) {
            val n4 = listOf(c.i + 1 to c.j, c.i - 1 to c.j, c.i to c.j + 1, c.i to c.j - 1)
            if (n4.any { (a, b) -> g.state(a, b) == CellState.UNKNOWN }) isFrontier += g.key(c.i, c.j)
        }
        val seen = HashSet<Long>()
        val out = ArrayList<Frontier>()
        for (c in free) {
            val k0 = g.key(c.i, c.j)
            if (k0 !in isFrontier || k0 in seen) continue
            val cluster = ArrayList<Pair<Int, Int>>()
            val queue = ArrayDeque<Pair<Int, Int>>()
            queue.add(c.i to c.j); seen += k0
            while (queue.isNotEmpty()) {
                val (i, j) = queue.removeFirst()
                cluster += i to j
                for (di in -1..1) for (dj in -1..1) {
                    if (di == 0 && dj == 0) continue
                    val k = g.key(i + di, j + dj)
                    if (k in isFrontier && k !in seen) { seen += k; queue.add(i + di to j + dj) }
                }
            }
            if (cluster.size < minCells) continue
            val cx = cluster.sumOf { g.center(it.first, it.second).x } / cluster.size
            val cy = cluster.sumOf { g.center(it.first, it.second).y } / cluster.size
            val near = cluster.minBy { (i, j) -> val p = g.center(i, j); (p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy) }
            out += Frontier(cluster.size, Vec3(cx, cy, 0.0), g.center(near.first, near.second))
        }
        return out.sortedByDescending { it.cells }
    }

    /**
     * Gidilecek sınırı seçer: büyük ve yakın olan, dönmesi az olan öne çıkar.
     * Puan = büyüklük / (1 + uzaklık) × (1 − 0,3 × |dönüş| / 180°). Çok yakın (< [minDist]) sınırlar atlanır.
     */
    fun choose(list: List<Frontier>, pos: Vec3, facing: Vec3?, minDist: Double = 0.5): Frontier? =
        list.filter { dist(it.nearest, pos) >= minDist }.maxByOrNull { f ->
            val d = dist(f.nearest, pos)
            val turn = facing?.let { abs(angleDeg(it, Vec3(f.nearest.x - pos.x, f.nearest.y - pos.y, 0.0))) } ?: 0.0
            f.cells / (1.0 + d) * (1.0 - 0.3 * turn / 180.0)
        }

    private fun dist(a: Vec3, b: Vec3) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    /** a'dan b'ye işaretli açı (derece, + = sola). */
    fun angleDeg(a: Vec3, b: Vec3): Double = Math.toDegrees(atan2(a.x * b.y - a.y * b.x, a.x * b.x + a.y * b.y))
}

/** Akustik radarın bir yöndeki okuması. [yawDeg]: proje çerçevesinde bakış açısı (atan2(y, x), derece). */
data class RadarReading(val yawDeg: Double, val ranges: List<Double>, val confidence: Double)

/**
 * Akustik radar taraması: kullanıcı olduğu yerde döner, her [sectors] dilimde bir sonar ölçümü alınır.
 * Sonuç kutupsal bir çevre profili (radar ekranı) ve haritaya işlenecek ışınlardır.
 *
 * Dürüstlük: telefon hoparlörü yönlü değildir; ilk yankı en yakın büyük yüzeyden gelebilir, baktığın yönden değil.
 * Bu yüzden her ışının güveni sonar güveniyle ve yön belirsizliğiyle sınırlanır (en fazla 0,6).
 */
class RadarSweep(val sectors: Int = 12, val maxRangeM: Double = 5.0) {
    private val readings = arrayOfNulls<RadarReading>(sectors)

    fun sectorOf(yawDeg: Double): Int {
        val a = ((yawDeg % 360.0) + 360.0) % 360.0
        return min(sectors - 1, floor(a / (360.0 / sectors)).toInt())
    }

    fun sectorCenterDeg(k: Int): Double = (k + 0.5) * 360.0 / sectors

    fun has(k: Int): Boolean = readings[k] != null
    val measured: Int get() = readings.count { it != null }
    val complete: Boolean get() = measured == sectors
    fun readings(): List<RadarReading> = readings.filterNotNull()
    fun reading(k: Int): RadarReading? = readings[k]

    fun add(r: RadarReading) { readings[sectorOf(r.yawDeg)] = r }

    /** En yakın ölçülmemiş dilime dönüş yönü: + sola (saat yönünün tersi), − sağa. Hepsi ölçüldüyse null. */
    fun turnToNext(yawDeg: Double): Double? {
        var best: Double? = null
        for (k in 0 until sectors) {
            if (readings[k] != null) continue
            var d = sectorCenterDeg(k) - yawDeg
            while (d > 180) d -= 360
            while (d < -180) d += 360
            if (best == null || abs(d) < abs(best)) best = d
        }
        return best
    }

    /** Haritaya işlenecek ışınlar ([origin]: telefonun konumu). Başarısız ölçüm (güven 0) atlanır. */
    fun rays(origin: Vec3): List<ScanRay> = readings().mapNotNull { rayFor(origin, it, maxRangeM) }

    /** Tüm yankılardan (ikinci, üçüncü) halka noktaları — görselleştirme için. */
    fun echoPoints(origin: Vec3): List<Vec3> = readings().flatMap { r ->
        val a = Math.toRadians(r.yawDeg)
        r.ranges.map { d -> Vec3(origin.x + d * cos(a), origin.y + d * sin(a), origin.z) }
    }

    companion object {
        /** Tek okumadan ışın. Yankı yoksa menzile kadar zayıf boşluk; güven 0 (ölçüm başarısız) → null. */
        fun rayFor(origin: Vec3, r: RadarReading, maxRangeM: Double = 5.0): ScanRay? {
            if (r.confidence <= 0.0) return null
            val a = Math.toRadians(r.yawDeg)
            val hit = r.ranges.isNotEmpty()
            val d = if (hit) r.ranges.first() else maxRangeM
            val c = if (hit) min(0.6, r.confidence) else 0.2
            return ScanRay(origin.x, origin.y, origin.z, origin.x + d * cos(a), origin.y + d * sin(a), origin.z, hit, Math.round(c * 1000.0) / 1000.0)
        }
    }
}
