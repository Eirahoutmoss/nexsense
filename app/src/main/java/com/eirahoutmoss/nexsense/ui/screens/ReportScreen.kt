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

@Composable
fun ReportScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onGo: (Tab) -> Unit) {
    val projectId = bundle?.project?.id
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
}

/** Dosya adı için güvenli metin (Türkçe harfler korunur). */
private fun safeFileName(name: String): String =
    name.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-").trim('-').take(60).ifEmpty { "nexsense" }
