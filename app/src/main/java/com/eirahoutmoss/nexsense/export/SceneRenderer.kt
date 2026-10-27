package com.eirahoutmoss.nexsense.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.eirahoutmoss.nexsense.core.analysis.EstimateGrid
import com.eirahoutmoss.nexsense.core.layers.LayerData
import com.eirahoutmoss.nexsense.core.model.PlaneType
import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.scene.Bounds
import com.eirahoutmoss.nexsense.core.scene.OrbitCamera
import com.eirahoutmoss.nexsense.core.scene.Vec3
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Rapor için sahne görüntüleri (yazdırılabilir: açık zemin).
 * Renk yalnızca ölçülen noktalardadır; IDW tahmini ayrı, soluk ve taralı hücrelerle çizilir.
 */
object SceneRenderer {

    private val GRID = Color.rgb(225, 230, 236)
    private val SCAN = Color.rgb(150, 160, 172)
    private val PLANE = Color.rgb(8, 145, 178)
    private val TRACK = Color.rgb(47, 107, 255)
    private val INK = Color.rgb(20, 26, 33)
    private val UNMEASURED = Color.rgb(140, 150, 165)

    /** Kuşbakışı plan (x sağa, y yukarı). */
    fun plan(
        bundle: ProjectBundle,
        layer: LayerData?,
        estimate: EstimateGrid?,
        color: (Double) -> Int,
        w: Int = 1400,
        h: Int = 1000,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val all = allPoints(bundle)
        val b = Bounds.of(all) ?: Bounds(Vec3(-2.0, -2.0, 0.0), Vec3(2.0, 2.0, 0.0))
        val minX = min(b.min.x, 0.0) - 0.5; val maxX = max(b.max.x, 0.0) + 0.5
        val minY = min(b.min.y, 0.0) - 0.5; val maxY = max(b.max.y, 0.0) + 0.5
        val pad = 60f
        val scale = min((w - 2 * pad) / (maxX - minX), (h - 2 * pad) / (maxY - minY)).toFloat()
        fun px(x: Double) = pad + ((x - minX) * scale).toFloat()
        fun py(y: Double) = h - pad - ((y - minY) * scale).toFloat()

        // 1 m ızgara
        val gp = Paint().apply { this.color = GRID; strokeWidth = 1.5f }
        var gx = floor(minX)
        while (gx <= ceil(maxX)) { c.drawLine(px(gx), py(minY), px(gx), py(maxY), gp); gx += 1.0 }
        var gy = floor(minY)
        while (gy <= ceil(maxY)) { c.drawLine(px(minX), py(gy), px(maxX), py(gy), gp); gy += 1.0 }

        // Tahmin hücreleri (soluk + çapraz tarama: ölçüm değil)
        if (estimate != null) {
            val fill = Paint()
            val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(70, 255, 255, 255); strokeWidth = 1.5f }
            for (j in 0 until estimate.ny) for (i in 0 until estimate.nx) {
                val v = estimate.at(i, j) ?: continue
                val conf = estimate.confidenceAt(i, j)
                val t = layer?.normalized(v) ?: 0.5
                val col = color(t)
                fill.color = Color.argb((40 + 90 * conf).toInt(), Color.red(col), Color.green(col), Color.blue(col))
                val x0 = estimate.originX + i * estimate.cellM; val y0 = estimate.originY + j * estimate.cellM
                val l = px(x0); val r = px(x0 + estimate.cellM); val tp = py(y0 + estimate.cellM); val bt = py(y0)
                c.drawRect(l, tp, r, bt, fill)
                c.drawLine(l, bt, r, tp, hatch)
            }
        }

        // Tarama noktaları ve düzlemler
        val sp = Paint().apply { this.color = SCAN }
        val scanPts = bundle.scans.flatMap { it.points }
        val stride = max(1, scanPts.size / 8000)
        for (k in scanPts.indices step stride) { val p = scanPts[k]; c.drawCircle(px(p.x), py(p.y), 1.6f, sp) }
        val plp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = PLANE; style = Paint.Style.STROKE; strokeWidth = 3f }
        for (pl in bundle.scans.flatMap { it.planes }) {
            if (pl.type == PlaneType.VERTICAL) {
                // Duvar: plandaki izdüşümü bir çizgi (en uzak iki köşe)
                val pts = pl.polygon
                var best = 0.0; var a = pts[0]; var z = pts[0]
                for (u in pts) for (v in pts) { val d = (u[0] - v[0]) * (u[0] - v[0]) + (u[1] - v[1]) * (u[1] - v[1]); if (d > best) { best = d; a = u; z = v } }
                c.drawLine(px(a[0]), py(a[1]), px(z[0]), py(z[1]), plp)
            } else {
                val path = Path()
                pl.polygon.forEachIndexed { k, v -> if (k == 0) path.moveTo(px(v[0]), py(v[1])) else path.lineTo(px(v[0]), py(v[1])) }
                path.close()
                c.drawPath(path, Paint(plp).apply { strokeWidth = 1.5f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) })
            }
        }
        // İzler
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = TRACK; style = Paint.Style.STROKE; strokeWidth = 2.5f }
        for (sc in bundle.scans) {
            if (sc.trajectory.size < 2) continue
            val path = Path()
            sc.trajectory.forEachIndexed { k, t -> if (k == 0) path.moveTo(px(t.x), py(t.y)) else path.lineTo(px(t.x), py(t.y)) }
            c.drawPath(path, tp)
        }

        // Ölçüm noktaları
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = INK; textSize = 20f }
        for (p in bundle.points.filter { it.isPlaced }) {
            val cx = px(p.x!!); val cy = py(p.y!!)
            val v = layer?.valueOf(p.id)
            val dashed = p.positionSource == PositionSource.PDR
            if (v != null && layer != null) {
                c.drawCircle(cx, cy, 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color(layer.normalized(v.value)) })
                c.drawCircle(cx, cy, 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = INK; style = Paint.Style.STROKE; strokeWidth = 1.5f; if (dashed) pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f) })
            } else {
                c.drawCircle(cx, cy, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = UNMEASURED; style = Paint.Style.STROKE; strokeWidth = 3f; if (dashed) pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f) })
            }
            c.drawText(p.label, cx + 16f, cy - 8f, label)
        }

        // Orijin, ölçek çubuğu, kuzey oku
        val op = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = INK; strokeWidth = 3f }
        c.drawLine(px(0.0) - 14f, py(0.0), px(0.0) + 14f, py(0.0), op)
        c.drawLine(px(0.0), py(0.0) - 14f, px(0.0), py(0.0) + 14f, op)
        c.drawText("0,0", px(0.0) + 8f, py(0.0) + 26f, label)
        c.drawLine(pad, h - 24f, pad + scale, h - 24f, op)
        c.drawText("1 m", pad + scale + 8f, h - 18f, label)
        bundle.project.coordinateSystem.northAngleDeg?.let { a ->
            val r = Math.toRadians(a)
            val ax = w - 70f; val ay = 80f
            val ex = ax + (40 * Math.sin(r)).toFloat(); val ey = ay - (40 * Math.cos(r)).toFloat()
            c.drawLine(ax, ay, ex, ey, op)
            c.drawText("N", ex - 6f, ey - 8f, label)
        }
        c.drawText("x →", w - 80f, h - 18f, label)
        c.drawText("↑ y", 12f, pad - 20f, label)
        return bmp
    }

    /** Perspektif görünüm (uygulamadaki 3B sahneyle aynı izdüşüm). */
    fun perspective(bundle: ProjectBundle, layer: LayerData?, color: (Double) -> Int, w: Int = 1400, h: Int = 900): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(11, 20, 36))
        val all = allPoints(bundle)
        val cam = OrbitCamera.fit(Bounds.of(all))
        val wd = w.toDouble(); val hd = h.toDouble()
        val gp = Paint().apply { this.color = Color.argb(60, 52, 209, 245); strokeWidth = 1.5f }
        for (line in com.eirahoutmoss.nexsense.core.scene.Grid.lines(Bounds.of(all))) {
            val a = cam.project(line.a, wd, hd) ?: continue
            val b = cam.project(line.b, wd, hd) ?: continue
            c.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), gp)
        }
        val sp = Paint().apply { this.color = Color.argb(150, 180, 200, 220) }
        val scanPts = bundle.scans.flatMap { it.points }
        val stride = max(1, scanPts.size / 8000)
        for (k in scanPts.indices step stride) {
            val p = scanPts[k]
            val pr = cam.project(Vec3(p.x, p.y, p.z), wd, hd) ?: continue
            c.drawCircle(pr.x.toFloat(), pr.y.toFloat(), 1.8f, sp)
        }
        val pts = bundle.points.filter { it.isPlaced }
            .mapNotNull { p -> cam.project(Vec3(p.x!!, p.y!!, p.z!!), wd, hd)?.let { p to it } }
            .sortedByDescending { it.second.depth }
        for ((p, pr) in pts) {
            val v = layer?.valueOf(p.id)
            val r = (14 * 6 / pr.depth).toFloat().coerceIn(6f, 22f)
            if (v != null && layer != null) c.drawCircle(pr.x.toFloat(), pr.y.toFloat(), r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color(layer.normalized(v.value)) })
            else c.drawCircle(pr.x.toFloat(), pr.y.toFloat(), r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = UNMEASURED; style = Paint.Style.STROKE; strokeWidth = 3f })
        }
        return bmp
    }

    private fun allPoints(b: ProjectBundle): List<Vec3> =
        b.points.filter { it.isPlaced }.map { Vec3(it.x!!, it.y!!, it.z!!) } +
            b.scans.flatMap { s -> s.points.map { Vec3(it.x, it.y, it.z) } + s.trajectory.map { Vec3(it.x, it.y, it.z) } }
}
