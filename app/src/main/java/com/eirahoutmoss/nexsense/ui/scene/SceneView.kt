package com.eirahoutmoss.nexsense.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.eirahoutmoss.nexsense.core.analysis.EstimateGrid
import com.eirahoutmoss.nexsense.core.layers.LayerData
import com.eirahoutmoss.nexsense.core.model.ScanPlane
import com.eirahoutmoss.nexsense.core.model.ScanPoint
import com.eirahoutmoss.nexsense.core.model.TrackPoint
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.scene.Bounds
import com.eirahoutmoss.nexsense.core.scene.Grid
import com.eirahoutmoss.nexsense.core.scene.OrbitCamera
import com.eirahoutmoss.nexsense.core.scene.ScenePicking
import com.eirahoutmoss.nexsense.core.scene.ScenePoint
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.ui.theme.LayerColors
import com.eirahoutmoss.nexsense.ui.theme.NexColors

/** Sahnede çizilecek nokta ve görünümü. */
data class SceneItem(
    val id: String,
    val label: String,
    val position: Vec3,
    val hasNote: Boolean,
    /** Konum tahmini (adım tabanlı) ise kesikli halka çizilir. */
    val estimatedPosition: Boolean = false,
)

/** Tarama katmanı: nokta bulutu (seyreltilmiş), düzlemler ve izler. */
class SceneScan(
    val points: List<ScanPoint>,
    val planes: List<ScanPlane>,
    val tracks: List<List<TrackPoint>>,
) {
    val isEmpty: Boolean get() = points.isEmpty() && planes.isEmpty() && tracks.isEmpty()
}

/**
 * Göreli 3B sahne (OpenGL'siz, Canvas üzerinde perspektif izdüşüm).
 *  - Tek parmak: döndür · iki parmak: yakınlaştır + kaydır · dokun: nokta seç.
 *  - z = 0 zemin ızgarası 1 m aralıklıdır; orijin kullanıcının referans noktasıdır.
 *  - Renk yalnızca ölçülen noktada vardır. Değeri olmayan nokta içi boş gri halkadır ("ölçülmedi").
 *  - Tahmin (IDW) yalnızca kullanıcı açarsa, zeminde soluk ve çapraz taralı hücreler olarak çizilir;
 *    ölçüm noktalarıyla karışmaz. Uzak hücreler boş kalır.
 */
@Composable
fun SceneView(
    items: List<SceneItem>,
    camera: OrbitCamera,
    onCamera: (OrbitCamera) -> Unit,
    showGeometry: Boolean,
    showNotes: Boolean,
    colorLayer: LayerData?,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    scan: SceneScan? = null,
    estimate: EstimateGrid? = null,
) {
    val measurer = rememberTextMeasurer()
    val cam by rememberUpdatedState(camera)
    val currentItems by rememberUpdatedState(items)
    val setCamera by rememberUpdatedState(onCamera)
    val select by rememberUpdatedState(onSelect)
    val bounds = Bounds.of(items.map { it.position } + (scan?.points?.map { Vec3(it.x, it.y, it.z) }.orEmpty()))
    val grid = Grid.lines(bounds)

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = 0f
                    var multi = false
                    // Bir hareket boyunca yerel kopya: kare başına birden çok olay gelirse adımlar kaybolmasın.
                    var local = cam
                    val slop = viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            multi = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            local = local.zoom(zoom.toDouble()).pan(pan.x.toDouble(), pan.y.toDouble(), size.height.toDouble())
                            setCamera(local)
                        } else if (pressed.size == 1 && !multi) {
                            val d = pressed[0].positionChange()
                            moved += d.getDistance()
                            if (moved > slop) {
                                local = local.rotate(d.x.toDouble(), d.y.toDouble())
                                setCamera(local)
                            }
                        }
                        event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (!multi && moved <= slop) {
                        val hit = ScenePicking.pick(
                            currentItems.map { ScenePoint(it.id, it.position) }, local,
                            size.width.toDouble(), size.height.toDouble(),
                            down.position.x.toDouble(), down.position.y.toDouble(),
                            radiusPx = pxOf(28, this),
                        )
                        select(hit)
                    }
                }
            },
    ) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF060B15))))
        val w = size.width.toDouble()
        val h = size.height.toDouble()

        if (showGeometry) {
            for (line in grid) {
                val a = camera.project(line.a, w, h) ?: continue
                val b = camera.project(line.b, w, h) ?: continue
                drawLine(
                    color = if (line.isAxis) NexColors.Cyan.copy(alpha = 0.55f) else NexColors.Cyan.copy(alpha = 0.12f),
                    start = Offset(a.x.toFloat(), a.y.toFloat()), end = Offset(b.x.toFloat(), b.y.toFloat()),
                    strokeWidth = if (line.isAxis) 2f else 1f,
                )
            }
            // Eksen etiketleri ve dikey (z) eksen
            axisLabel(camera, Vec3(1.0, 0.0, 0.0), "x", w, h, measurer)
            axisLabel(camera, Vec3(0.0, 1.0, 0.0), "y", w, h, measurer)
            val o = camera.project(Vec3.ZERO, w, h)
            val up = camera.project(Vec3(0.0, 0.0, 1.0), w, h)
            if (o != null && up != null) {
                drawLine(NexColors.Cyan.copy(alpha = 0.55f), Offset(o.x.toFloat(), o.y.toFloat()), Offset(up.x.toFloat(), up.y.toFloat()), 2f)
                axisLabel(camera, Vec3(0.0, 0.0, 1.1), "z", w, h, measurer)
                drawCircle(NexColors.Cyan, 5f, Offset(o.x.toFloat(), o.y.toFloat()))
            }
        }

        // Tahmin hücreleri (zemin, soluk + çapraz çizgi)
        if (estimate != null && colorLayer != null) {
            val ramp = LayerColors.ramp(colorLayer.kind)
            for (j in 0 until estimate.ny) for (i in 0 until estimate.nx) {
                val v = estimate.at(i, j) ?: continue
                val x0 = estimate.originX + i * estimate.cellM
                val y0 = estimate.originY + j * estimate.cellM
                val c0 = camera.project(Vec3(x0, y0, 0.0), w, h) ?: continue
                val c1 = camera.project(Vec3(x0 + estimate.cellM, y0, 0.0), w, h) ?: continue
                val c2 = camera.project(Vec3(x0 + estimate.cellM, y0 + estimate.cellM, 0.0), w, h) ?: continue
                val c3 = camera.project(Vec3(x0, y0 + estimate.cellM, 0.0), w, h) ?: continue
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c0.x.toFloat(), c0.y.toFloat()); lineTo(c1.x.toFloat(), c1.y.toFloat())
                    lineTo(c2.x.toFloat(), c2.y.toFloat()); lineTo(c3.x.toFloat(), c3.y.toFloat()); close()
                }
                val conf = estimate.confidenceAt(i, j).toFloat()
                drawPath(path, ramp.at(colorLayer.normalized(v)).copy(alpha = 0.12f + 0.3f * conf))
                drawLine(Color.White.copy(alpha = 0.10f), Offset(c0.x.toFloat(), c0.y.toFloat()), Offset(c2.x.toFloat(), c2.y.toFloat()), 1f)
            }
        }

        // Tarama: düzlemler, nokta bulutu, izler
        if (scan != null) {
            for (pl in scan.planes) {
                val pr = pl.polygon.mapNotNull { v -> camera.project(Vec3(v[0], v[1], v[2]), w, h) }
                if (pr.size < 3) continue
                val path = androidx.compose.ui.graphics.Path().apply {
                    pr.forEachIndexed { k, q -> if (k == 0) moveTo(q.x.toFloat(), q.y.toFloat()) else lineTo(q.x.toFloat(), q.y.toFloat()) }
                    close()
                }
                drawPath(path, NexColors.Cyan.copy(alpha = 0.07f))
                drawPath(path, NexColors.Cyan.copy(alpha = 0.45f), style = Stroke(width = 1.5f))
            }
            for (p in scan.points) {
                val pr = camera.project(Vec3(p.x, p.y, p.z), w, h) ?: continue
                val col = if (p.k == "a") Color(0xFFD08CFF) else Color(0xFFB4C8DC)
                drawCircle(col.copy(alpha = (0.25 + 0.5 * p.c).toFloat()), if (p.k == "a") 4f else 1.8f, Offset(pr.x.toFloat(), pr.y.toFloat()))
            }
            for (t in scan.tracks) {
                var prev: Offset? = null
                for (q in t) {
                    val pr = camera.project(Vec3(q.x, q.y, q.z), w, h)
                    val o = pr?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
                    if (o != null && prev != null) drawLine(NexColors.Blue.copy(alpha = 0.8f), prev, o, 2.5f)
                    prev = o
                }
            }
        }

        // Uzaktan yakına çiz (yakın noktalar üstte kalsın).
        val projected = items.mapNotNull { item -> camera.project(item.position, w, h)?.let { item to it } }
            .sortedByDescending { it.second.depth }
        val focal = (h / 2.0) / kotlin.math.tan(Math.toRadians(camera.fovDeg) / 2.0)
        for ((item, p) in projected) {
            val value = colorLayer?.valueOf(item.id)
            if (!showGeometry && value == null && !(showNotes && item.hasNote)) continue
            val center = Offset(p.x.toFloat(), p.y.toFloat())
            val radius = (0.09 * focal / p.depth).toFloat().coerceIn(5f, 26f)

            // Zemine iniş çizgisi: yüksekliği okumayı kolaylaştırır.
            if (showGeometry) {
                camera.project(Vec3(item.position.x, item.position.y, 0.0), w, h)?.let { f ->
                    drawLine(
                        NexColors.Cyan.copy(alpha = 0.35f), center, Offset(f.x.toFloat(), f.y.toFloat()), 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
            }

            when {
                colorLayer != null && value != null -> {
                    val col = LayerColors.ramp(colorLayer.kind).at(colorLayer.normalized(value.value))
                    drawCircle(col.copy(alpha = 0.25f), radius * 1.8f, center)
                    drawCircle(col, radius, center)
                }
                colorLayer != null -> {
                    // Bu noktada bu katman ölçülmedi.
                    drawCircle(NexColors.Unmeasured, radius, center, style = Stroke(width = 2.5f))
                }
                else -> drawCircle(NexColors.Cyan, radius * 0.8f, center)
            }
            if (showNotes && item.hasNote) {
                drawCircle(LayerColors.swatch(LayerKind.NOTES), 5f, center + Offset(radius * 0.9f, -radius * 0.9f))
            }
            if (item.estimatedPosition) {
                drawCircle(NexColors.Demo, radius + 4f, center, style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
            }
            if (item.id == selectedId) {
                drawCircle(Color.White, radius + 6f, center, style = Stroke(width = 3f))
                val t = measurer.measure(item.label, TextStyle(color = Color.White, fontSize = 12.sp))
                drawText(t, topLeft = center + Offset(radius + 8f, -t.size.height / 2f))
            }
        }
    }
}

private fun pxOf(dp: Int, density: androidx.compose.ui.unit.Density): Double = (dp * density.density).toDouble()

private fun DrawScope.axisLabel(
    camera: OrbitCamera,
    at: Vec3,
    text: String,
    w: Double,
    h: Double,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val p = camera.project(at, w, h) ?: return
    val t = measurer.measure(text, TextStyle(color = NexColors.Cyan, fontSize = 12.sp))
    drawText(t, topLeft = Offset(p.x.toFloat() + 4f, p.y.toFloat() - t.size.height))
}
