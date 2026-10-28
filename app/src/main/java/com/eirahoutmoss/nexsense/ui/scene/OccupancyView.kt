package com.eirahoutmoss.nexsense.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.spatial.Cell
import com.eirahoutmoss.nexsense.core.spatial.CellState
import com.eirahoutmoss.nexsense.core.spatial.OccupancyGrid
import com.eirahoutmoss.nexsense.ui.theme.NexColors

/** Ekranda çizilen boşluk haritasının anlık görüntüsü. */
class MapSnapshot(val cellM: Double, val cells: List<Cell>, val frontiers: Int, val freeArea: Double, val occupiedArea: Double) {
    companion object {
        fun of(g: OccupancyGrid, frontiers: Int) = MapSnapshot(g.cellM, g.cells(), frontiers, g.area(CellState.FREE), g.area(CellState.OCCUPIED))
    }
}

/** Boşluk haritası: boş (koyu mavi), dolu (açık), bilinmeyen (çizilmez), yol, yankılar, hedef sınır. */
@Composable
fun SpaceMap(snap: MapSnapshot, path: List<Vec3>, me: Vec3?, target: Vec3?, echoes: List<Vec3>, modifier: Modifier) {
    Canvas(modifier) {
        val cells = snap.cells.filter { it.state != CellState.UNKNOWN }
        val xs = cells.map { (it.i + 0.5) * snap.cellM } + path.map { it.x } + listOfNotNull(me?.x, target?.x)
        val ys = cells.map { (it.j + 0.5) * snap.cellM } + path.map { it.y } + listOfNotNull(me?.y, target?.y)
        if (xs.isEmpty()) return@Canvas
        val minX = minOf(xs.min(), -1.0) - 0.3; val maxX = maxOf(xs.max(), 1.0) + 0.3
        val minY = minOf(ys.min(), -1.0) - 0.3; val maxY = maxOf(ys.max(), 1.0) + 0.3
        val sc = minOf(size.width / (maxX - minX), size.height / (maxY - minY)).toFloat()
        fun o(x: Double, y: Double) = Offset(((x - minX) * sc).toFloat(), size.height - ((y - minY) * sc).toFloat())
        val cs = (snap.cellM * sc).toFloat()
        for (c in cells) {
            val tl = o(c.i * snap.cellM, (c.j + 1) * snap.cellM)
            val col = if (c.state == CellState.FREE) NexColors.Blue.copy(alpha = (0.15 + 0.35 * c.confidence).toFloat())
            else Color.White.copy(alpha = (0.35 + 0.55 * c.confidence).toFloat())
            drawRect(col, tl, androidx.compose.ui.geometry.Size(cs + 0.5f, cs + 0.5f))
        }
        for (e in echoes) drawCircle(NexColors.Cyan.copy(alpha = 0.6f), 4f, o(e.x, e.y))
        if (path.size > 1) {
            val pth = Path()
            path.forEachIndexed { i, p -> val q = o(p.x, p.y); if (i == 0) pth.moveTo(q.x, q.y) else pth.lineTo(q.x, q.y) }
            drawPath(pth, NexColors.Blue, style = Stroke(width = 3f))
        }
        target?.let { drawCircle(NexColors.Demo, 11f, o(it.x, it.y), style = Stroke(4f)) }
        me?.let { drawCircle(NexColors.Cyan, 9f, o(it.x, it.y)) }
    }
}

