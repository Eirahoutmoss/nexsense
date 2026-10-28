package com.eirahoutmoss.nexsense.export

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.eirahoutmoss.nexsense.BuildConfig
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.analysis.AnalysisEngine
import com.eirahoutmoss.nexsense.core.analysis.AnalysisResult
import com.eirahoutmoss.nexsense.core.analysis.CoverageFlag
import com.eirahoutmoss.nexsense.core.analysis.Interpolation
import com.eirahoutmoss.nexsense.core.analysis.Stats
import com.eirahoutmoss.nexsense.core.export.CsvExporter
import com.eirahoutmoss.nexsense.core.export.JsonCodec
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.layers.LayerStatus
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.report.CaseFile
import com.eirahoutmoss.nexsense.core.report.PlyWriter
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.theme.LayerColors
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Faz 6: PDF rapor, 3B model (PLY), vaka dosyası (ZIP) ve paylaşım dosyası. */
class ReportExporter(private val context: Context) {

    private fun s(id: Int, vararg a: Any): String = context.getString(id, *a)

    fun layerName(k: LayerKind): String = s(
        when (k) {
            LayerKind.GEOMETRY -> R.string.layer_geometry
            LayerKind.MAGNETIC -> R.string.layer_magnetic
            LayerKind.MOTION -> R.string.layer_motion
            LayerKind.ACOUSTIC -> R.string.layer_acoustic
            LayerKind.SPL -> R.string.layer_spl
            LayerKind.REVERB -> R.string.layer_reverb
            LayerKind.TEMPERATURE -> R.string.layer_temperature
            LayerKind.HUMIDITY -> R.string.layer_humidity
            LayerKind.NOTES -> R.string.layer_notes
            LayerKind.SCAN -> R.string.layer_scan
        },
    )

    fun flagName(f: CoverageFlag): String = s(
        when (f) {
            CoverageFlag.NO_DATA -> R.string.flag_no_data
            CoverageFlag.FEW_POINTS -> R.string.flag_few_points
            CoverageFlag.MOSTLY_UNPLACED -> R.string.flag_mostly_unplaced
            CoverageFlag.LARGE_GAPS -> R.string.flag_large_gaps
            CoverageFlag.ONE_SESSION -> R.string.flag_one_session
        },
    )

    private fun ramp(k: LayerKind): (Double) -> Int = { t -> LayerColors.ramp(k).at(t).toArgb() }

    fun pdf(b: ProjectBundle): ByteArray {
        val a = AnalysisEngine.run(b)
        val pw = PdfWriter("NexSense ${BuildConfig.VERSION_NAME} · ${b.project.name}")
        pw.newPage()
        pw.title(s(R.string.pdf_title))
        pw.text(b.project.name, 13f, bold = true)
        if (b.project.description.isNotBlank()) pw.text(b.project.description, 10f, PdfWriter.MUTED)
        pw.text(s(R.string.pdf_generated, Fmt.dateTime(Instant.now().toString())), 9f, PdfWriter.MUTED)
        if (b.hasDemoData) { pw.space(4f); pw.text(s(R.string.pdf_demo_warning), 10f, android.graphics.Color.rgb(200, 120, 0), bold = true) }
        pw.space(6f)
        pw.text(s(R.string.about_disclaimer), 8.5f, PdfWriter.MUTED)

        pw.heading(s(R.string.report_summary))
        pw.table(
            listOf(
                s(R.string.report_points) to "${b.points.size} (${b.points.count { it.isPlaced }} ${s(R.string.report_placed)})",
                s(R.string.report_samples) to b.samples.size.toString(),
                s(R.string.report_sessions) to b.sessions.size.toString(),
                s(R.string.pdf_scans) to b.scans.joinToString { "${scanMethodName(it.method)}: ${it.points.size} / ${it.planes.size} / ${Fmt.num(trackLength(it), 1)} m" }.ifEmpty { "—" },
                s(R.string.legend_origin, b.project.coordinateSystem.origin.ifBlank { s(R.string.origin_unset) }) to "",
                s(R.string.pdf_room) to (a.room?.let { "${Fmt.num(it.lengthM, 1)} × ${Fmt.num(it.widthM, 1)} × ${Fmt.num(it.heightM, 1)} m" } ?: "—"),
            ).map { (k, v) -> k to v },
        )

        // Katman başına plan + tahmin
        val measured = a.layers.values.filter { it.kind.isMeasurement && it.status == LayerStatus.HAS_DATA }
        pw.heading(s(R.string.pdf_section_scene))
        val first = measured.firstOrNull()
        pw.image(SceneRenderer.perspective(b, first, first?.let { ramp(it.kind) } ?: { _: Double -> 0 }), 230f)
        pw.text(s(R.string.pdf_perspective_caption, first?.let { layerName(it.kind) } ?: "—"), 8f, PdfWriter.MUTED)
        for (d in measured.take(4)) {
            val est = Interpolation.idw(b, d)
            pw.ensure(300f)
            pw.text(layerName(d.kind) + (if (d.containsDemo) " · ${s(R.string.demo_badge)}" else ""), 11f, bold = true)
            pw.image(SceneRenderer.plan(b, d, est, ramp(d.kind)), 280f)
            pw.text(
                s(R.string.pdf_plan_caption, Fmt.num(d.min), Fmt.num(d.max), d.unit.orEmpty()) + " " +
                    (if (est != null) s(R.string.pdf_estimate_caption, est.estimatedCells, Fmt.num(b.analysis.maxEstimateDistanceM, 1)) else s(R.string.pdf_no_estimate)),
                8f, PdfWriter.MUTED,
            )
        }

        // Boşluk haritası (her ortam: köşe/duvar varsayımı yok)
        val occ = com.eirahoutmoss.nexsense.core.spatial.OccupancyGrid.build(b)
        if (occ.count(com.eirahoutmoss.nexsense.core.spatial.CellState.FREE) > 0) {
            pw.heading(s(R.string.pdf_section_freemap))
            pw.image(SceneRenderer.occupancy(b, occ), 280f)
            pw.text(
                s(
                    R.string.pdf_freemap_caption,
                    Fmt.num(occ.area(com.eirahoutmoss.nexsense.core.spatial.CellState.FREE), 1),
                    Fmt.num(occ.area(com.eirahoutmoss.nexsense.core.spatial.CellState.OCCUPIED), 1),
                    com.eirahoutmoss.nexsense.core.spatial.Frontiers.find(occ).size,
                    b.scans.sumOf { it.rays.size },
                ),
                8f, PdfWriter.MUTED,
            )
        }

        pw.heading(s(R.string.pdf_section_layers))
        pw.grid(
            listOf(s(R.string.pdf_col_layer), "n", "min", s(R.string.pdf_col_median), "max", s(R.string.pdf_col_unit), s(R.string.pdf_col_flags)),
            a.coverage.map { cv ->
                val d = a.layers.getValue(cv.kind)
                listOf(
                    layerName(cv.kind), cv.measured.toString(), Fmt.num(d.min), Fmt.num(Stats.median(d.values.map { it.value })), Fmt.num(d.max),
                    d.unit.orEmpty(), cv.flags.joinToString { flagName(it) }.ifEmpty { "—" },
                )
            },
            listOf(2.2f, 0.6f, 1f, 1f, 1f, 0.9f, 3f),
        )

        analysisSections(pw, b, a)

        pw.heading(s(R.string.pdf_section_points))
        val byPoint = b.samples.groupBy { it.pointId }
        pw.grid(
            listOf(s(R.string.point_label), s(R.string.detail_position), s(R.string.detail_position_source), s(R.string.pdf_col_values)),
            b.points.map { p ->
                val vals = byPoint[p.id].orEmpty().sortedBy { it.sensorType.ordinal }.joinToString("; ") { smp ->
                    val main = smp.values.entries.firstOrNull { it.value != null }
                    "${smp.sensorType.key}=${Fmt.num(main?.value)} ${smp.unit}"
                }
                listOf(p.label, Fmt.coord(p.x, p.y, p.z) ?: s(R.string.position_unknown), p.positionSource.name.lowercase(), vals.ifEmpty { "—" })
            },
            listOf(1.2f, 2.2f, 1f, 4f),
        )

        pw.heading(s(R.string.pdf_section_methods))
        pw.text(s(R.string.pdf_methods), 8.5f)
        pw.text(s(R.string.pdf_methods_idw, Interpolation.METHOD), 8.5f, PdfWriter.MUTED)

        val out = ByteArrayOutputStream()
        pw.save(out)
        return out.toByteArray()
    }

    private fun analysisSections(pw: PdfWriter, b: ProjectBundle, a: AnalysisResult) {
        val labels = b.points.associate { it.id to it.label }
        pw.heading(s(R.string.analysis_alerts))
        if (a.alerts.isEmpty()) pw.text(s(if (b.analysis.thresholds.isEmpty()) R.string.analysis_no_thresholds else R.string.pdf_none), 9f, PdfWriter.MUTED)
        for (al in a.alerts.take(40)) {
            pw.text(s(if (al.above) R.string.analysis_alert_above else R.string.analysis_alert_below, labels[al.pointId] ?: "?", layerName(al.kind), Fmt.num(al.value), Fmt.num(al.limit)), 9f)
        }
        pw.heading(s(R.string.analysis_anomalies))
        if (a.anomalies.isEmpty()) pw.text(s(R.string.analysis_no_anomalies), 9f, PdfWriter.MUTED)
        for (an in a.anomalies.take(30)) {
            pw.text(s(R.string.analysis_anomaly_line, labels[an.pointId] ?: "?", layerName(an.kind), Fmt.num(an.value), Fmt.num(an.robustZ, 1)), 9f)
        }
        pw.heading(s(R.string.analysis_correlations))
        pw.text(s(R.string.analysis_correlation_note), 8.5f, PdfWriter.MUTED)
        if (a.correlations.isEmpty()) pw.text(s(R.string.analysis_no_correlations), 9f, PdfWriter.MUTED)
        for (cr in a.correlations.take(20)) {
            pw.text("${layerName(cr.a)} ↔ ${layerName(cr.b)}: r = ${Fmt.num(cr.r)} (n = ${cr.n})", 9f)
        }
        pw.heading(s(R.string.analysis_timeline))
        if (a.timeline.isEmpty()) pw.text(s(R.string.pdf_none), 9f, PdfWriter.MUTED)
        else pw.grid(
            listOf(s(R.string.detail_session), s(R.string.pdf_col_layer), "n", s(R.string.pdf_col_median), "min", "max"),
            a.timeline.map { listOf(s(R.string.session_n, it.index), layerName(it.kind), it.n.toString(), Fmt.num(it.median), Fmt.num(it.min), Fmt.num(it.max)) },
            listOf(1.2f, 2f, 0.6f, 1f, 1f, 1f),
        )
        if (a.changes.isNotEmpty()) {
            pw.text(s(R.string.analysis_changes), 9.5f, bold = true)
            for (ch in a.changes.take(20)) {
                pw.text(s(R.string.analysis_change_line, layerName(ch.kind), labels[ch.fromPointId] ?: "?", labels[ch.toPointId] ?: "?", ch.fromSession, ch.toSession, Fmt.num(ch.delta), Fmt.num(ch.distanceM)), 9f)
            }
        }
        pw.heading(s(R.string.analysis_acoustic))
        val ac = b.samples.filter { it.sensorType in setOf(SensorType.REVERB_TIME, SensorType.ECHO_DISTANCE, SensorType.SOUND_SPL, SensorType.DOMINANT_FREQUENCY) }
        if (ac.isEmpty()) pw.text(s(R.string.pdf_none), 9f, PdfWriter.MUTED)
        for (smp in ac.take(40)) {
            val vals = smp.values.entries.filter { it.value != null }.joinToString("  ") { "${it.key} ${Fmt.num(it.value)}" }
            pw.text("${labels[smp.pointId] ?: "—"} · ${smp.sensorType.key}: $vals ${smp.unit}" + (smp.quality?.let { " · $it" } ?: ""), 9f)
        }
        if (a.magnetic.isNotEmpty()) {
            pw.heading(s(R.string.analysis_magnetic_world))
            for (m in a.magnetic.take(30)) {
                pw.text(s(R.string.analysis_magnetic_line, labels[m.pointId] ?: "?", Fmt.num(m.horizontal, 1), Fmt.num(m.vertical, 1), Fmt.num(m.inclinationDeg, 1)), 9f)
            }
        }
    }

    fun ply(b: ProjectBundle, colorLayer: LayerKind?): ByteArray {
        val layer = colorLayer?.let { com.eirahoutmoss.nexsense.core.layers.LayerAnalysis.compute(b, it) }
        return PlyWriter.render(b, layer?.takeIf { it.status == LayerStatus.HAS_DATA }).toByteArray(Charsets.US_ASCII)
    }

    fun caseFile(b: ProjectBundle): ByteArray {
        val base = CaseFile.safeName(b.project.name)
        val firstLayer = LayerKind.entries.firstOrNull { k ->
            k.isMeasurement && com.eirahoutmoss.nexsense.core.layers.LayerAnalysis.compute(b, k).status == LayerStatus.HAS_DATA
        }
        val entries = listOf(
            "$base.nexsense.json" to JsonCodec.encode(b).toByteArray(Charsets.UTF_8),
            "$base.csv" to CsvExporter.export(b).toByteArray(Charsets.UTF_8),
            "$base-rapor.pdf" to pdf(b),
            "$base-model.ply" to ply(b, firstLayer),
        )
        val readme = s(R.string.case_readme, b.project.name, Instant.now().toString(), BuildConfig.VERSION_NAME, CaseFile.MANIFEST)
        val out = ByteArrayOutputStream()
        CaseFile.write(out, entries, readme)
        return out.toByteArray()
    }

    /** Paylaşım için önbelleğe vaka dosyası yazar (eski paylaşım dosyaları silinir). */
    fun shareFile(b: ProjectBundle): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "${CaseFile.safeName(b.project.name)}-${LocalDate.now()}.zip")
        f.writeBytes(caseFile(b))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    }

    fun scanMethodName(m: ScanMethod): String = s(
        when (m) {
            ScanMethod.ARCORE -> R.string.scan_arcore
            ScanMethod.PDR -> R.string.scan_pdr
            ScanMethod.ACOUSTIC_ECHO -> R.string.scan_acoustic
            ScanMethod.ACOUSTIC_RADAR -> R.string.scan_radar
            ScanMethod.DEMO -> R.string.demo_badge
        },
    )

    private fun trackLength(sc: com.eirahoutmoss.nexsense.core.model.Scan): Double =
        sc.trajectory.zipWithNext().sumOf { (p, q) -> Math.sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y) + (p.z - q.z) * (p.z - q.z)) }
}
