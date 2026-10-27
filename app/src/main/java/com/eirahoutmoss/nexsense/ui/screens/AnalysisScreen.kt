package com.eirahoutmoss.nexsense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.analysis.AnalysisEngine
import com.eirahoutmoss.nexsense.core.analysis.Anomalies
import com.eirahoutmoss.nexsense.core.analysis.Correlations
import com.eirahoutmoss.nexsense.core.analysis.CoverageFlag
import com.eirahoutmoss.nexsense.core.analysis.Interpolation
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.layers.LayerStatus
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.Threshold
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.components.DemoBadge
import com.eirahoutmoss.nexsense.ui.components.KeyValue
import com.eirahoutmoss.nexsense.ui.components.SectionCard
import com.eirahoutmoss.nexsense.ui.theme.NexColors
import kotlin.math.abs

@Composable
fun flagText(f: CoverageFlag): String = stringResource(
    when (f) {
        CoverageFlag.NO_DATA -> R.string.flag_no_data
        CoverageFlag.FEW_POINTS -> R.string.flag_few_points
        CoverageFlag.MOSTLY_UNPLACED -> R.string.flag_mostly_unplaced
        CoverageFlag.LARGE_GAPS -> R.string.flag_large_gaps
        CoverageFlag.ONE_SESSION -> R.string.flag_one_session
    },
)

/**
 * Mekânsal örüntü motoru (Faz 5): ölçüm yeterliliği, eşik uyarıları, anomali adayları, katman ilişkileri,
 * zaman çizgisi, yönden bağımsız manyetik bileşenler ve tahmin (IDW) ayarları.
 * Hiçbir sonuç kesin bulgu olarak sunulmaz; yöntem ve sınırları her bölümde yazar.
 */
@Composable
fun AnalysisScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onDone: () -> Unit) {
    if (bundle == null) {
        NoProjectState(modifier) { onDone() }
        return
    }
    val a = remember(bundle) { AnalysisEngine.run(bundle) }
    val labels = remember(bundle) { bundle.points.associate { it.id to it.label } }
    var editing by remember { mutableStateOf<LayerKind?>(null) }
    var gridDialog by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.analysis_title), style = MaterialTheme.typography.titleLarge)
        if (bundle.hasDemoData) DemoBadge()
        Text(stringResource(R.string.analysis_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionCard(stringResource(R.string.analysis_coverage)) {
            for (cv in a.coverage) {
                val text = if (cv.flags.isEmpty()) stringResource(R.string.analysis_ok) else cv.flags.map { flagText(it) }.joinToString(" · ")
                KeyValue(layerName(cv.kind), "n=${cv.measured} · $text", Modifier)
            }
        }

        SectionCard(stringResource(R.string.analysis_alerts)) {
            Text(stringResource(R.string.analysis_alerts_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (k in LayerKind.entries.filter { it.isMeasurement }) {
                val t = bundle.analysis.thresholds.firstOrNull { it.layer == k.name }
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        layerName(k) + ": " + (if (t == null) "—" else "${t.min?.let { Fmt.num(it) } ?: "−∞"} … ${t.max?.let { Fmt.num(it) } ?: "+∞"}"),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(top = 12.dp),
                    )
                    TextButton(onClick = { editing = k }) { Text(stringResource(R.string.edit)) }
                }
            }
            if (a.alerts.isEmpty()) {
                Text(stringResource(if (bundle.analysis.thresholds.isEmpty()) R.string.analysis_no_thresholds else R.string.analysis_no_alerts), style = MaterialTheme.typography.bodySmall)
            }
            for (al in a.alerts.take(50)) {
                Text(
                    stringResource(if (al.above) R.string.analysis_alert_above else R.string.analysis_alert_below, labels[al.pointId] ?: "?", layerName(al.kind), Fmt.num(al.value), Fmt.num(al.limit)),
                    style = MaterialTheme.typography.bodySmall, color = NexColors.Danger,
                )
            }
        }

        SectionCard(stringResource(R.string.analysis_anomalies)) {
            Text(stringResource(R.string.analysis_anomalies_help, Anomalies.MIN_POINTS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.anomalies.isEmpty()) Text(stringResource(R.string.analysis_no_anomalies), style = MaterialTheme.typography.bodySmall)
            for (an in a.anomalies.take(30)) {
                Text(stringResource(R.string.analysis_anomaly_line, labels[an.pointId] ?: "?", layerName(an.kind), Fmt.num(an.value), Fmt.num(an.robustZ, 1)), style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard(stringResource(R.string.analysis_correlations)) {
            Text(stringResource(R.string.analysis_correlation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.correlations.isEmpty()) Text(stringResource(R.string.analysis_no_correlations_n, Correlations.MIN_PAIRS), style = MaterialTheme.typography.bodySmall)
            for (cr in a.correlations.take(15)) {
                val strength = when {
                    abs(cr.r) >= 0.7 -> R.string.corr_strong
                    abs(cr.r) >= 0.4 -> R.string.corr_moderate
                    else -> R.string.corr_weak
                }
                Text("${layerName(cr.a)} ↔ ${layerName(cr.b)}: r = ${Fmt.num(cr.r)} (n = ${cr.n}, ${stringResource(strength)})", style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard(stringResource(R.string.analysis_timeline)) {
            if (bundle.sessions.size < 2) Text(stringResource(R.string.analysis_timeline_one), style = MaterialTheme.typography.bodySmall)
            for (st in a.timeline) {
                KeyValue("${stringResource(R.string.session_n, st.index)} · ${layerName(st.kind)}", "n=${st.n} · ${stringResource(R.string.pdf_col_median)} ${Fmt.num(st.median)} (${Fmt.num(st.min)}…${Fmt.num(st.max)})")
            }
            if (a.changes.isNotEmpty()) {
                Text(stringResource(R.string.analysis_changes), style = MaterialTheme.typography.labelLarge)
                for (ch in a.changes.take(20)) {
                    Text(
                        stringResource(R.string.analysis_change_line, layerName(ch.kind), labels[ch.fromPointId] ?: "?", labels[ch.toPointId] ?: "?", ch.fromSession, ch.toSession, Fmt.num(ch.delta), Fmt.num(ch.distanceM)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.analysis_estimate)) {
            Text(stringResource(R.string.analysis_estimate_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KeyValue(stringResource(R.string.analysis_cell), "${Fmt.num(bundle.analysis.gridCellM)} m")
            KeyValue(stringResource(R.string.analysis_max_distance), "${Fmt.num(bundle.analysis.maxEstimateDistanceM)} m")
            for (d in a.layers.values.filter { it.kind.isMeasurement && it.status == LayerStatus.HAS_DATA }) {
                val g = Interpolation.idw(bundle, d)
                KeyValue(layerName(d.kind), if (g == null) stringResource(R.string.analysis_estimate_insufficient) else stringResource(R.string.analysis_estimate_cells, g.estimatedCells, g.sourceCount))
            }
            OutlinedButton(onClick = { gridDialog = true }) { Text(stringResource(R.string.edit)) }
        }

        if (a.magnetic.isNotEmpty()) {
            SectionCard(stringResource(R.string.analysis_magnetic_world)) {
                Text(stringResource(R.string.analysis_magnetic_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (m in a.magnetic.take(30)) {
                    Text(stringResource(R.string.analysis_magnetic_line, labels[m.pointId] ?: "?", Fmt.num(m.horizontal, 1), Fmt.num(m.vertical, 1), Fmt.num(m.inclinationDeg, 1)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        a.room?.let { r ->
            SectionCard(stringResource(R.string.pdf_room)) {
                Text("${Fmt.num(r.lengthM, 1)} × ${Fmt.num(r.widthM, 1)} × ${Fmt.num(r.heightM, 1)} m", style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.analysis_room_help, r.pointCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    editing?.let { k ->
        val t = bundle.analysis.thresholds.firstOrNull { it.layer == k.name }
        var lo by remember(k) { mutableStateOf(t?.min?.let { Fmt.num(it) } ?: "") }
        var hi by remember(k) { mutableStateOf(t?.max?.let { Fmt.num(it) } ?: "") }
        val loV = Fmt.parseOptional(lo); val hiV = Fmt.parseOptional(hi)
        val bad = (loV?.isNaN() == true) || (hiV?.isNaN() == true) || (loV != null && hiV != null && loV > hiV)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(layerName(k)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.analysis_threshold_help), style = MaterialTheme.typography.bodySmall)
                    val kb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    OutlinedTextField(lo, { lo = it }, label = { Text(stringResource(R.string.analysis_min)) }, singleLine = true, keyboardOptions = kb)
                    OutlinedTextField(hi, { hi = it }, label = { Text(stringResource(R.string.analysis_max)) }, singleLine = true, keyboardOptions = kb)
                    if (bad) Text(stringResource(R.string.err_bad_number), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !bad, onClick = {
                    val others = bundle.analysis.thresholds.filterNot { it.layer == k.name }
                    val next = if (loV == null && hiV == null) others else others + Threshold(k.name, loV, hiV)
                    c.actions.setAnalysis(bundle.project.id, bundle.analysis.copy(thresholds = next))
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (gridDialog) {
        var cell by remember { mutableStateOf(Fmt.num(bundle.analysis.gridCellM)) }
        var maxD by remember { mutableStateOf(Fmt.num(bundle.analysis.maxEstimateDistanceM)) }
        val cv = Fmt.parseOptional(cell); val mv = Fmt.parseOptional(maxD)
        val ok = cv != null && mv != null && !cv.isNaN() && !mv.isNaN() && cv in 0.05..5.0 && mv > 0 && mv <= 20.0
        AlertDialog(
            onDismissRequest = { gridDialog = false },
            title = { Text(stringResource(R.string.analysis_estimate)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val kb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    OutlinedTextField(cell, { cell = it }, label = { Text(stringResource(R.string.analysis_cell) + " (m)") }, singleLine = true, keyboardOptions = kb)
                    OutlinedTextField(maxD, { maxD = it }, label = { Text(stringResource(R.string.analysis_max_distance) + " (m)") }, singleLine = true, keyboardOptions = kb)
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    c.actions.setAnalysis(bundle.project.id, bundle.analysis.copy(gridCellM = cv!!, maxEstimateDistanceM = mv!!))
                    gridDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { gridDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
