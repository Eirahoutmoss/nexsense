package com.eirahoutmoss.nexsense.core.scene

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(this dot this)
    fun normalized(): Vec3 = length.let { if (it == 0.0) this else Vec3(x / it, y / it, z / it) }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UP = Vec3(0.0, 0.0, 1.0)
    }
}

/** Ekrana izdüşürülmüş nokta. [depth] kameraya uzaklıktır (büyük = uzak). */
data class Projected(val x: Double, val y: Double, val depth: Double)

/**
 * Hedef etrafında dönen yörünge kamerası. Dünya koordinatları projeninkiyle aynıdır: z yukarı.
 * Tamamen değişmezdir (immutable); hareketler yeni kamera döndürür.
 */
data class OrbitCamera(
    val yawDeg: Double = -60.0,
    val pitchDeg: Double = 30.0,
    val distance: Double = 8.0,
    val target: Vec3 = Vec3.ZERO,
    val fovDeg: Double = 50.0,
) {
    private fun rad(d: Double) = d * PI / 180.0

    val eye: Vec3
        get() {
            val p = rad(pitchDeg)
            val y = rad(yawDeg)
            return target + Vec3(cos(p) * cos(y), cos(p) * sin(y), sin(p)) * distance
        }

    private fun basis(): Triple<Vec3, Vec3, Vec3> {
        val f = (target - eye).normalized()
        var r = (f cross Vec3.UP).normalized()
        if (r.length == 0.0) r = Vec3(1.0, 0.0, 0.0)
        val u = r cross f
        return Triple(r, u, f)
    }

    /** Noktayı ekran koordinatına (piksel, sol üst köşe 0,0) izdüşürür; kameranın arkasındaysa null. */
    fun project(p: Vec3, width: Double, height: Double): Projected? {
        val (r, u, f) = basis()
        val d = p - eye
        val cz = d dot f
        if (cz <= NEAR) return null
        val focal = (height / 2.0) / tan(rad(fovDeg) / 2.0)
        val sx = width / 2.0 + (d dot r) * focal / cz
        val sy = height / 2.0 - (d dot u) * focal / cz
        return Projected(sx, sy, cz)
    }

    /** Tek parmak sürükleme: döndür. */
    fun rotate(dxPx: Double, dyPx: Double): OrbitCamera =
        copy(yawDeg = (yawDeg - dxPx * ROT_SPEED) % 360.0, pitchDeg = (pitchDeg + dyPx * ROT_SPEED).coerceIn(MIN_PITCH, MAX_PITCH))

    /** İki parmak kıstırma: yakınlaştır (scale > 1 yaklaşır). */
    fun zoom(scale: Double): OrbitCamera =
        if (scale <= 0.0 || !scale.isFinite()) this else copy(distance = (distance / scale).coerceIn(MIN_DIST, MAX_DIST))

    /** İki parmak kaydırma: hedefi ekran düzleminde taşı. */
    fun pan(dxPx: Double, dyPx: Double, heightPx: Double): OrbitCamera {
        val (r, u, _) = basis()
        val worldPerPx = 2.0 * distance * tan(rad(fovDeg) / 2.0) / heightPx.coerceAtLeast(1.0)
        return copy(target = target - r * (dxPx * worldPerPx) + u * (dyPx * worldPerPx))
    }

    companion object {
        const val NEAR = 0.05
        const val ROT_SPEED = 0.3
        const val MIN_PITCH = -85.0
        const val MAX_PITCH = 85.0
        const val MIN_DIST = 0.5
        const val MAX_DIST = 500.0

        /** Kutuyu görecek şekilde kamera. Kutu boşsa orijin çevresi. */
        fun fit(bounds: Bounds?): OrbitCamera {
            if (bounds == null) return OrbitCamera()
            val ext = max(max(bounds.size.x, bounds.size.y), bounds.size.z)
            return OrbitCamera(target = bounds.center, distance = (ext * 1.8 + 3.0).coerceIn(MIN_DIST, MAX_DIST))
        }
    }
}

data class Bounds(val min: Vec3, val max: Vec3) {
    val center: Vec3 get() = (min + max) * 0.5
    val size: Vec3 get() = max - min

    companion object {
        fun of(points: Collection<Vec3>): Bounds? {
            if (points.isEmpty()) return null
            return Bounds(
                Vec3(points.minOf { it.x }, points.minOf { it.y }, points.minOf { it.z }),
                Vec3(points.maxOf { it.x }, points.maxOf { it.y }, points.maxOf { it.z }),
            )
        }
    }
}

/** Sahnede seçilebilir öğe. */
data class ScenePoint(val id: String, val position: Vec3)

object ScenePicking {
    /**
     * Dokunulan yere en yakın noktayı seçer. Yarıçap içindeki adaylardan ekranda en yakın olan;
     * eşitlikte kameraya yakın olan kazanır. Hiçbiri yoksa null.
     */
    fun pick(
        points: List<ScenePoint>,
        camera: OrbitCamera,
        width: Double,
        height: Double,
        tapX: Double,
        tapY: Double,
        radiusPx: Double,
    ): String? {
        var best: String? = null
        var bestD = Double.MAX_VALUE
        var bestDepth = Double.MAX_VALUE
        for (p in points) {
            val pr = camera.project(p.position, width, height) ?: continue
            val dx = pr.x - tapX
            val dy = pr.y - tapY
            val d = sqrt(dx * dx + dy * dy)
            if (d > radiusPx) continue
            if (d < bestD - 0.5 || (kotlin.math.abs(d - bestD) <= 0.5 && pr.depth < bestDepth)) {
                best = p.id; bestD = d; bestDepth = pr.depth
            }
        }
        return best
    }
}

/** Zemin ızgarası (z = 0 düzlemi), 1 m aralıklı. */
object Grid {
    data class Line(val a: Vec3, val b: Vec3, val isAxis: Boolean)

    fun lines(bounds: Bounds?, margin: Double = 1.0, step: Double = 1.0): List<Line> {
        val minX = floor((bounds?.min?.x ?: -2.0).coerceAtMost(0.0) - margin)
        val maxX = ceil((bounds?.max?.x ?: 2.0).coerceAtLeast(0.0) + margin)
        val minY = floor((bounds?.min?.y ?: -2.0).coerceAtMost(0.0) - margin)
        val maxY = ceil((bounds?.max?.y ?: 2.0).coerceAtLeast(0.0) + margin)
        val out = mutableListOf<Line>()
        var x = minX
        while (x <= maxX + 1e-9) {
            out += Line(Vec3(x, minY, 0.0), Vec3(x, maxY, 0.0), x == 0.0)
            x += step
        }
        var y = minY
        while (y <= maxY + 1e-9) {
            out += Line(Vec3(minX, y, 0.0), Vec3(maxX, y, 0.0), y == 0.0)
            y += step
        }
        return out
    }
}
