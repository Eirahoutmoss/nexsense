package com.eirahoutmoss.nexsense.core.report

import com.eirahoutmoss.nexsense.core.integrity.Sha256
import com.eirahoutmoss.nexsense.core.layers.LayerData
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 0..255 RGB. */
data class Rgb(val r: Int, val g: Int, val b: Int)

/** İki renk arasında doğrusal geçiş (PLY ve PDF için; uygulama temasından bağımsız). */
data class Ramp(val low: Rgb, val high: Rgb) {
    fun at(t: Double): Rgb {
        val k = t.coerceIn(0.0, 1.0)
        fun m(a: Int, b: Int) = (a + (b - a) * k).toInt().coerceIn(0, 255)
        return Rgb(m(low.r, high.r), m(low.g, high.g), m(low.b, high.b))
    }

    companion object {
        val DEFAULT = Ramp(Rgb(47, 107, 255), Rgb(255, 59, 47))
    }
}

/**
 * ASCII PLY (Stanford) 3B model. MeshLab, CloudCompare, Blender açar.
 * Köşeler: tarama noktaları (gri/cyan), ölçüm noktaları (renk katmanına göre; değeri yoksa beyaz).
 * Yüzler: algılanan düzlemlerin çokgenleri (yelpaze üçgenleme). Birim metre, z yukarı.
 */
object PlyWriter {
    fun write(bundle: ProjectBundle, colorLayer: LayerData?, ramp: Ramp = Ramp.DEFAULT, out: Appendable) {
        data class V(val x: Double, val y: Double, val z: Double, val c: Rgb)
        val verts = ArrayList<V>()
        val faces = ArrayList<IntArray>()
        for (sc in bundle.scans) {
            for (p in sc.points) {
                val g = (90 + 140 * p.c).toInt().coerceIn(0, 255)
                verts += V(p.x, p.y, p.z, if (p.k == "a") Rgb(200, 120, 255) else Rgb(g / 2, g, g))
            }
            for (pl in sc.planes) {
                val start = verts.size
                for (v in pl.polygon) verts += V(v[0], v[1], v[2], Rgb(52, 209, 245))
                for (i in 1 until pl.polygon.size - 1) faces += intArrayOf(start, start + i, start + i + 1)
            }
        }
        for (p in bundle.points) {
            if (!p.isPlaced) continue
            val v = colorLayer?.valueOf(p.id)
            val c = if (v != null) ramp.at(colorLayer.normalized(v.value)) else Rgb(255, 255, 255)
            verts += V(p.x!!, p.y!!, p.z!!, c)
        }
        out.append("ply\nformat ascii 1.0\n")
        out.append("comment NexSense ").append(bundle.project.name.replace('\n', ' ')).append('\n')
        out.append("comment units metres, z up, project relative frame\n")
        out.append("element vertex ").append(verts.size.toString()).append('\n')
        out.append("property float x\nproperty float y\nproperty float z\n")
        out.append("property uchar red\nproperty uchar green\nproperty uchar blue\n")
        out.append("element face ").append(faces.size.toString()).append('\n')
        out.append("property list uchar int vertex_indices\nend_header\n")
        for (v in verts) {
            out.append(String.format(Locale.ROOT, "%.4f %.4f %.4f %d %d %d\n", v.x, v.y, v.z, v.c.r, v.c.g, v.c.b))
        }
        for (f in faces) out.append("3 ").append(f[0].toString()).append(' ').append(f[1].toString()).append(' ').append(f[2].toString()).append('\n')
    }

    fun render(bundle: ProjectBundle, colorLayer: LayerData?): String = StringBuilder().also { write(bundle, colorLayer, out = it) }.toString()
}

/**
 * Vaka dosyası: tek bir ZIP içinde proje JSON'u, CSV, PDF rapor, 3B model ve bir bütünlük listesi.
 * MANIFEST.sha256 her dosyanın SHA-256 özetini `sha256sum -c` biçiminde içerir; dosyanın sonradan
 * değiştirilip değiştirilmediği denetlenebilir.
 */
object CaseFile {
    const val MANIFEST = "MANIFEST.sha256"

    fun manifest(entries: List<Pair<String, ByteArray>>): String =
        entries.joinToString("") { (name, bytes) -> "${Sha256.hex(bytes)}  $name\n" }

    fun write(out: OutputStream, entries: List<Pair<String, ByteArray>>, readme: String) {
        val all = entries + ("BENIOKU.txt" to readme.toByteArray(Charsets.UTF_8))
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in all) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest(all).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /** Dosya adı için güvenli metin (Türkçe harfler korunur). */
    fun safeName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-").trim('-').take(60).ifEmpty { "nexsense" }
}
