package com.eirahoutmoss.nexsense.core.spatial

import com.eirahoutmoss.nexsense.core.model.ScanAlignment
import com.eirahoutmoss.nexsense.core.model.ScanPoint
import com.eirahoutmoss.nexsense.core.model.TrackPoint
import com.eirahoutmoss.nexsense.core.scene.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Koordinat dönüşümleri.
 *
 * ARCore dünya çerçevesi: y yukarı (yerçekimine göre), x/z yatay, oturumun başladığı yerde orijin.
 * NexSense proje çerçevesi: z yukarı, sağ el kuralı. Eşleme: (x, y, z)ₚ = (x, −z, y)ₐᵣ.
 * Böylece oturum başında kameranın baktığı yön (ARCore −z) proje +y (ileri) olur.
 */
object FrameMath {
    fun arToScan(x: Double, y: Double, z: Double): Vec3 = Vec3(x, -z, y)

    /** z ekseni etrafında saat yönünün tersine [deg] derece döndürür (yukarıdan bakınca). */
    fun rotateZ(p: Vec3, deg: Double): Vec3 {
        val a = deg * PI / 180.0
        val c = cos(a); val s = sin(a)
        return Vec3(p.x * c - p.y * s, p.x * s + p.y * c, p.z)
    }

    /** Tarama çerçevesindeki noktayı proje çerçevesine taşır: önce döndür, sonra ötele. */
    fun align(p: Vec3, a: ScanAlignment): Vec3 = rotateZ(p, a.yawDeg) + Vec3(a.offsetX, a.offsetY, a.offsetZ)

    /** Satır sıralı 3×3 matrisle çarpım (cihaz → dünya). */
    fun mul(m: List<Double>, v: Vec3): Vec3 {
        require(m.size == 9)
        return Vec3(
            m[0] * v.x + m[1] * v.y + m[2] * v.z,
            m[3] * v.x + m[4] * v.y + m[5] * v.z,
            m[6] * v.x + m[7] * v.y + m[8] * v.z,
        )
    }

    /**
     * Doğu-Kuzey-Yukarı (ENU) yönünü proje çerçevesine çevirir.
     * [northAngleDeg]: manyetik kuzeyin proje +y ekseninden saat yönünde açısı.
     */
    fun enuToProject(v: Vec3, northAngleDeg: Double): Vec3 = rotateZ(v, -northAngleDeg)
}

/**
 * Nokta bulutunu sabit boyutlu küplere (voksel) indirger: her küpte en güvenilir nokta kalır.
 * Bellek ve dosya boyutu sınırlı kalsın diye en fazla [maxPoints] nokta tutulur.
 */
class VoxelGrid(private val sizeM: Double = 0.05, private val maxPoints: Int = 30_000) {
    private val cells = LinkedHashMap<Long, ScanPoint>()

    val size: Int get() = cells.size

    /** Yeni küpe düştüyse ya da mevcut noktadan daha güvenilirse true. */
    fun add(p: ScanPoint): Boolean {
        if (!p.x.isFinite() || !p.y.isFinite() || !p.z.isFinite()) return false
        val k = key(p.x, p.y, p.z)
        val old = cells[k]
        if (old == null) {
            if (cells.size >= maxPoints) return false
            cells[k] = p
            return true
        }
        if (p.c > old.c) { cells[k] = p; return true }
        return false
    }

    fun points(): List<ScanPoint> = cells.values.toList()

    private fun key(x: Double, y: Double, z: Double): Long {
        fun q(v: Double): Long = (floor(v / sizeM).toLong() + OFFSET) and MASK
        return (q(x) shl 42) or (q(y) shl 21) or q(z)
    }

    private companion object {
        const val OFFSET = 1L shl 20
        const val MASK = (1L shl 21) - 1
    }
}

/**
 * Otomatik ölçüm noktası kuralı: telefon [stepM] kadar yer değiştirdiğinde (ve en az [minIntervalS]
 * geçtiyse) yeni nokta kaydedilir. İlk nokta başlangıçta (referans noktası) alınır.
 * İzleme yokken (ARCore konumu kaybettiğinde) asla kayıt yapılmaz: sahte konum olmasın.
 */
class AutoRecordPolicy(
    var stepM: Double = 0.5,
    var minIntervalS: Double = 1.0,
) {
    private var last: Vec3? = null
    private var lastT = Double.NEGATIVE_INFINITY

    var count = 0
        private set

    fun offer(p: Vec3, tS: Double, tracking: Boolean): Boolean {
        if (!tracking) return false
        val l = last
        val ok = l == null || ((p - l).length >= stepM && tS - lastT >= minIntervalS)
        if (ok) { last = p; lastT = tS; count++ }
        return ok
    }

    fun reset() { last = null; lastT = Double.NEGATIVE_INFINITY; count = 0 }
}

/** İz (yol) seyreltici: [minStepM]'den küçük hareketler yazılmaz. */
class TrackRecorder(private val minStepM: Double = 0.1, private val maxPoints: Int = 20_000) {
    private val pts = ArrayList<TrackPoint>()
    fun offer(t: Double, p: Vec3) {
        val l = pts.lastOrNull()
        if (pts.size >= maxPoints) return
        if (l == null || sqrt((l.x - p.x) * (l.x - p.x) + (l.y - p.y) * (l.y - p.y) + (l.z - p.z) * (l.z - p.z)) >= minStepM) {
            pts += TrackPoint(t, p.x, p.y, p.z)
        }
    }
    fun points(): List<TrackPoint> = pts.toList()
    val length: Double
        get() = pts.zipWithNext().sumOf { (a, b) -> sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z)) }
}

/**
 * Adım tabanlı konum (PDR). ARCore yoksa kullanılır. Kaba bir tahmindir:
 * - Başlangıç = referans noktası, başlangıçtaki yön = proje +y (kullanıcı ileri doğru tutar).
 * - Her adımda [stepLengthM] kadar, o anki yönde ilerlenir. Yön manyetometreye dayanır; iç mekânda sapabilir.
 * - z kullanıcının telefonu tuttuğu yüksekliktir ([holdHeightM]); ölçülmez, varsayımdır ve öyle etiketlenir.
 */
class PdrTracker(
    private val startHeadingDeg: Double,
    var stepLengthM: Double = 0.7,
    var holdHeightM: Double = 1.0,
) {
    var x = 0.0
        private set
    var y = 0.0
        private set
    var steps = 0
        private set

    val position: Vec3 get() = Vec3(x, y, holdHeightM)

    /** Güven: başlangıçta 0,6; her adımda biraz düşer, 0,1'in altına inmez. */
    val confidence: Double get() = (0.6 - 0.01 * steps).coerceAtLeast(0.1)

    /** [headingDeg]: pusula yönü (kuzeyden saat yönünde). */
    fun onStep(headingDeg: Double): Vec3 {
        val d = (headingDeg - startHeadingDeg) * PI / 180.0
        x += stepLengthM * sin(d)
        y += stepLengthM * cos(d)
        steps++
        return position
    }
}

/**
 * Adım algılayıcı (telefonda TYPE_STEP_DETECTOR yoksa): ivme büyüklüğünde tepe arar.
 * Tepe, kayan ortalamanın [thresholdMs2] üstüne çıkıp geri inince ve son adımdan [minIntervalMs] geçtiyse sayılır.
 */
class PeakStepDetector(private val thresholdMs2: Double = 1.2, private val minIntervalMs: Long = 300) {
    private var mean = 9.81
    private var above = false
    private var lastStepMs = Long.MIN_VALUE / 2

    fun feed(magnitude: Double, tMs: Long): Boolean {
        mean += 0.02 * (magnitude - mean)
        val high = magnitude - mean > thresholdMs2
        var step = false
        if (above && !high && tMs - lastStepMs >= minIntervalMs) {
            step = true
            lastStepMs = tMs
        }
        above = high
        return step
    }
}
