package com.eirahoutmoss.nexsense.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * A4 PDF yazıcı (NexTools'tan uyarlandı): sayfa taşınca yeni sayfa açar.
 * Yazdırılabilir olsun diye açık zemin, koyu yazı.
 */
internal class PdfWriter(private val footer: String) {
    val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    var y = 0f
        private set

    val canvas: Canvas get() = page!!.canvas

    companion object {
        const val W = 595
        const val H = 842
        const val M = 42f
        val ACCENT = Color.rgb(8, 145, 178)
        val INK = Color.rgb(20, 26, 33)
        val MUTED = Color.rgb(95, 105, 115)
        val RULE = Color.rgb(210, 216, 222)
    }

    private fun paint(size: Float, color: Int = INK, bold: Boolean = false, mono: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun newPage() {
        finishPage()
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        y = M
    }

    private fun finishPage() {
        val p = page ?: return
        val c = p.canvas
        val fp = paint(7.5f, MUTED)
        c.drawLine(M, H - 30f, W - M, H - 30f, Paint().apply { color = RULE; strokeWidth = 0.6f })
        c.drawText(footer, M, H - 18f, fp)
        val num = "$pageNo"
        c.drawText(num, W - M - fp.measureText(num), H - 18f, fp)
        doc.finishPage(p)
        page = null
    }

    fun ensure(height: Float) {
        if (page == null || y + height > H - 44f) newPage()
    }

    fun space(h: Float) { y += h }

    fun text(s: String, size: Float = 10f, color: Int = INK, bold: Boolean = false, mono: Boolean = false, indent: Float = 0f) {
        val tp = paint(size, color, bold, mono)
        val width = (W - 2 * M - indent).toInt()
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, tp, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f).build()
        ensure(layout.height.toFloat())
        canvas.save()
        canvas.translate(M + indent, y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height
    }

    fun title(s: String) {
        ensure(40f)
        text(s, 18f, INK, bold = true)
        space(2f)
    }

    fun heading(s: String) {
        ensure(34f)
        space(10f)
        text(s.uppercase(), 9f, ACCENT, bold = true)
        canvas.drawLine(M, y + 2f, W - M, y + 2f, Paint().apply { color = RULE; strokeWidth = 0.6f })
        space(6f)
    }

    /** İki sütunlu tablo (ad · değer). */
    fun table(rows: List<Pair<String, String>>, keyWidth: Float = 170f) {
        val kp = paint(9.5f, MUTED)
        val vp = paint(9.5f, INK, mono = false)
        for ((k, v) in rows) {
            val vw = (W - 2 * M - keyWidth).toInt()
            val vl = StaticLayout.Builder.obtain(v, 0, v.length, vp, vw).build()
            val kl = StaticLayout.Builder.obtain(k, 0, k.length, kp, keyWidth.toInt() - 8).build()
            val h = max(vl.height, kl.height) + 4f
            ensure(h)
            canvas.save(); canvas.translate(M, y); kl.draw(canvas); canvas.restore()
            canvas.save(); canvas.translate(M + keyWidth, y); vl.draw(canvas); canvas.restore()
            y += h
        }
    }

    /** Çok sütunlu tablo; ilk satır başlık. */
    fun grid(header: List<String>, rows: List<List<String>>, weights: List<Float>) {
        val total = weights.sum()
        val widths = weights.map { (W - 2 * M) * it / total }
        fun row(cells: List<String>, bold: Boolean, bg: Int?) {
            val p = paint(8.5f, if (bold) INK else INK, bold)
            val layouts = cells.mapIndexed { i, c -> StaticLayout.Builder.obtain(c, 0, c.length, p, (widths[i] - 6).toInt()).build() }
            val h = layouts.maxOf { it.height } + 5f
            ensure(h)
            if (bg != null) canvas.drawRect(M, y - 1f, W - M, y + h - 2f, Paint().apply { color = bg })
            var x = M
            layouts.forEachIndexed { i, l -> canvas.save(); canvas.translate(x + 2f, y + 1f); l.draw(canvas); canvas.restore(); x += widths[i] }
            y += h
        }
        row(header, true, Color.rgb(236, 240, 243))
        rows.forEachIndexed { i, r -> row(r, false, if (i % 2 == 1) Color.rgb(248, 249, 250) else null) }
    }

    fun image(bmp: Bitmap, maxHeight: Float = 260f) {
        val maxW = W - 2 * M
        val scale = minOf(maxW / bmp.width, maxHeight / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        ensure(h + 6f)
        canvas.drawBitmap(bmp, null, RectF(M, y, M + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
        y += h + 6f
    }

    /** Grafik açıklaması: renkli nokta + ad. */
    fun legend(items: List<Pair<String, Int>>) {
        ensure(14f)
        val p = paint(8f, MUTED)
        var x = M + 38f
        for ((label, color) in items) {
            canvas.drawCircle(x + 3f, y + 4f, 3.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            canvas.drawText(label, x + 10f, y + 7f, p)
            x += 18f + p.measureText(label)
        }
        y += 14f
    }

    fun save(out: OutputStream) {
        finishPage()
        doc.writeTo(out)
        doc.close()
    }
}
