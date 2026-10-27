package com.eirahoutmoss.nexsense.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.Tab
import com.eirahoutmoss.nexsense.ui.components.DemoBadge
import com.eirahoutmoss.nexsense.ui.components.KeyValue
import com.eirahoutmoss.nexsense.ui.components.SectionCard
import com.eirahoutmoss.nexsense.ui.components.SessionBar
import java.time.LocalDate
import android.content.Intent
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.eirahoutmoss.nexsense.core.layers.LayerAnalysis
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.layers.LayerStatus
import com.eirahoutmoss.nexsense.core.model.Scan
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.ui.Overlay
import com.eirahoutmoss.nexsense.ui.components.ConfirmDialog
import kotlinx.coroutines.launch

@Composable
fun ReportScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onGo: (Tab) -> Unit, onOverlay: (Overlay) -> Unit = {}) {
    val projectId = bundle?.project?.id
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var busyShare by remember { mutableStateOf(false) }
    var deletingScan by remember { mutableStateOf<String?>(null) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && projectId != null) c.actions.exportPdf(projectId, uri)
    }
    val plyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && projectId != null) c.actions.exportPly(projectId, uri, LayerKind.entries.firstOrNull { k -> k.isMeasurement && bundle?.let { LayerAnalysis.compute(it, k).status == LayerStatus.HAS_DATA } == true })
    }
    val caseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && projectId != null) c.actions.exportCase(projectId, uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && projectId != null) c.actions.exportCsv(projectId, uri)
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && projectId != null) c.actions.exportJson(projectId, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.actions.importJson(uri)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (bundle == null) {
            SectionCard(stringResource(R.string.no_project_title)) {
                Text(stringResource(R.string.report_no_project), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onGo(Tab.HOME) }) { Text(stringResource(R.string.go_projects)) }
            }
        } else {
            val base = safeFileName(bundle.project.name) + "-" + LocalDate.now()
            SectionCard(stringResource(R.string.report_summary)) {
                if (bundle.hasDemoData) DemoBadge()
                KeyValue(stringResource(R.string.project_name), bundle.project.name)
                KeyValue(stringResource(R.string.report_points), "${bundle.points.size} (${bundle.points.count { it.isPlaced }} ${stringResource(R.string.report_placed)})")
                KeyValue(stringResource(R.string.report_samples), bundle.samples.size.toString())
                KeyValue(stringResource(R.string.report_sensors), bundle.samples.map { it.sensorType }.distinct().size.toString())
                KeyValue(stringResource(R.string.report_created), Fmt.dateTime(bundle.project.createdAt))
                KeyValue(stringResource(R.string.report_schema), bundle.schemaVersion.toString())
            }

            SectionCard(stringResource(R.string.report_export)) {
                Text(stringResource(R.string.report_export_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { csvLauncher.launch("$base.csv") }, enabled = bundle.samples.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.TableChart, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_csv))
                }
                Button(onClick = { jsonLauncher.launch("$base.nexsense.json") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.DataObject, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_json))
                }
                if (bundle.samples.isEmpty()) {
                    Text(stringResource(R.string.export_csv_empty), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionCard(stringResource(R.string.report_reports)) {
                Text(stringResource(R.string.report_reports_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { pdfLauncher.launch("$base-rapor.pdf") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_pdf))
                }
                OutlinedButton(onClick = { plyLauncher.launch("$base-model.ply") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ViewInAr, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_ply))
                }
                OutlinedButton(onClick = { caseLauncher.launch("$base-vaka.zip") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Inventory2, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_case))
                }
                OutlinedButton(onClick = { sharing = true }, enabled = !busyShare, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.share_case))
                }
                OutlinedButton(onClick = { onOverlay(Overlay.ANALYSIS) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Insights, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.analysis_title))
                }
            }

            if (bundle.scans.isNotEmpty()) {
                SectionCard(stringResource(R.string.report_scans)) {
                    for (sc in bundle.scans) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(scanMethodLabel(sc.method), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    stringResource(R.string.report_scan_line, sc.points.size, sc.planes.size, Fmt.num(trackLength(sc), 1), Fmt.dateTime(sc.startedAt)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { deletingScan = sc.id }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.report_sessions)) {
                SessionBar(c, bundle)
                if (bundle.sessions.isEmpty()) {
                    Text(stringResource(R.string.report_no_sessions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                bundle.sessions.asReversed().forEachIndexed { revIndex, s ->
                    val n = bundle.sessions.size - revIndex
                    val count = bundle.points.count { it.sessionId == s.id }
                    if (revIndex > 0) HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.session_n, n), style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${Fmt.dateTime(s.startedAt)} → ${if (s.endedAt == null) stringResource(R.string.session_ongoing) else Fmt.time(s.endedAt)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(R.string.session_points, count), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.report_import)) {
            Text(stringResource(R.string.report_import_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.import_json))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FileDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (sharing && bundle != null) {
        // Açık onay: neyin, kime gideceği söylenir. Onaysız hiçbir veri cihazdan çıkmaz.
        AlertDialog(
            onDismissRequest = { sharing = false },
            title = { Text(stringResource(R.string.share_title)) },
            text = { Text(stringResource(R.string.share_consent, bundle.project.name, bundle.points.size, bundle.samples.size, bundle.scans.sumOf { it.points.size })) },
            confirmButton = {
                TextButton(onClick = {
                    sharing = false
                    busyShare = true
                    scope.launch {
                        val uri = c.actions.prepareShare(bundle.project.id)
                        busyShare = false
                        if (uri != null) {
                            val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_case)))
                        }
                    }
                }) { Text(stringResource(R.string.share_confirm)) }
            },
            dismissButton = { TextButton(onClick = { sharing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deletingScan?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.scan_delete),
            text = stringResource(R.string.scan_delete_confirm),
            confirm = stringResource(R.string.delete),
            onConfirm = { bundle?.let { c.actions.deleteScan(it.project.id, id) } },
            onDismiss = { deletingScan = null },
        )
    }
}

@Composable
fun scanMethodLabel(m: ScanMethod): String = stringResource(
    when (m) {
        ScanMethod.ARCORE -> R.string.scan_arcore
        ScanMethod.PDR -> R.string.scan_pdr
        ScanMethod.ACOUSTIC_ECHO -> R.string.scan_acoustic
        ScanMethod.DEMO -> R.string.demo_badge
    },
)

private fun trackLength(sc: Scan): Double =
    sc.trajectory.zipWithNext().sumOf { (p, q) -> Math.sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y) + (p.z - q.z) * (p.z - q.z)) }

/** Dosya adı için güvenli metin (Türkçe harfler korunur). */
private fun safeFileName(name: String): String =
    name.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-").trim('-').take(60).ifEmpty { "nexsense" }
