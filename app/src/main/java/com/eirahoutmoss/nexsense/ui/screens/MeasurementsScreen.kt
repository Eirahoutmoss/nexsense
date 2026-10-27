package com.eirahoutmoss.nexsense.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.model.MeasurementPoint
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorSample
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.Tab
import com.eirahoutmoss.nexsense.ui.components.ConfirmDialog
import com.eirahoutmoss.nexsense.ui.components.DemoBadge
import com.eirahoutmoss.nexsense.ui.components.EmptyState
import com.eirahoutmoss.nexsense.ui.components.KeyValue
import com.eirahoutmoss.nexsense.ui.components.Pill
import com.eirahoutmoss.nexsense.ui.components.PointEditorDialog
import com.eirahoutmoss.nexsense.ui.theme.NexColors
import com.eirahoutmoss.nexsense.ui.theme.ValueStyle

private enum class PlaceFilter { ALL, PLACED, UNPLACED }

@Composable
fun MeasurementsScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onGo: (Tab) -> Unit) {
    if (bundle == null) {
        NoProjectState(modifier, onGo)
        return
    }
    val projectId = bundle.project.id
    var place by rememberSaveable(projectId) { mutableStateOf(PlaceFilter.ALL) }
    var sessionFilter by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var sensorFilter by rememberSaveable(projectId) { mutableStateOf<SensorType?>(null) }
    var openId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }

    val sensorTypes = remember(bundle) { bundle.samples.map { it.sensorType }.distinct().sortedBy { it.ordinal } }
    val filtered = remember(bundle, place, sessionFilter, sensorFilter) {
        bundle.points.filter { p ->
            (place == PlaceFilter.ALL || (place == PlaceFilter.PLACED) == p.isPlaced) &&
                (sessionFilter == null || p.sessionId == sessionFilter) &&
                (sensorFilter == null || bundle.samples.any { it.pointId == p.id && it.sensorType == sensorFilter })
        }.sortedByDescending { it.createdAt }
    }

    if (bundle.points.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(R.string.measurements_empty_title),
            body = stringResource(R.string.measurements_empty_body),
            modifier = modifier,
        ) { TextButton(onClick = { onGo(Tab.LIVE) }) { Text(stringResource(R.string.tab_live)) } }
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(place == PlaceFilter.ALL, { place = PlaceFilter.ALL }, { Text(stringResource(R.string.filter_all)) })
                    FilterChip(place == PlaceFilter.PLACED, { place = PlaceFilter.PLACED }, { Text(stringResource(R.string.filter_placed)) })
                    FilterChip(place == PlaceFilter.UNPLACED, { place = PlaceFilter.UNPLACED }, { Text(stringResource(R.string.filter_unplaced)) })
                }
                if (bundle.sessions.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(sessionFilter == null, { sessionFilter = null }, { Text(stringResource(R.string.filter_all_sessions)) })
                        bundle.sessions.forEachIndexed { i, s ->
                            FilterChip(sessionFilter == s.id, { sessionFilter = s.id }, { Text(stringResource(R.string.session_n, i + 1)) })
                        }
                    }
                }
                if (sensorTypes.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(sensorFilter == null, { sensorFilter = null }, { Text(stringResource(R.string.filter_all_sensors)) })
                        for (t in sensorTypes) {
                            FilterChip(sensorFilter == t, { sensorFilter = t }, { Text(sensorTypeName(t)) })
                        }
                    }
                }
                Text(
                    stringResource(R.string.measurements_count, filtered.size, bundle.points.size),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (filtered.isEmpty()) {
            item { Text(stringResource(R.string.filter_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(filtered, key = { it.id }) { p ->
            PointRow(p, bundle, onClick = { openId = p.id })
        }
    }

    openId?.let { id ->
        val p = bundle.points.firstOrNull { it.id == id }
        if (p == null) openId = null else PointDetailDialog(c, bundle, p, onDismiss = { openId = null })
    }
}

@Composable
private fun PointRow(p: MeasurementPoint, bundle: ProjectBundle, onClick: () -> Unit) {
    val samples = bundle.samplesOf(p.id)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (samples.any { it.source == SampleSource.DEMO }) DemoBadge()
                if (!p.isPlaced) Pill(stringResource(R.string.position_unknown_short), color = NexColors.Demo)
            }
            Text(
                Fmt.coord(p.x, p.y, p.z) ?: stringResource(R.string.position_unknown),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.point_row_meta, Fmt.dateTime(p.createdAt), samples.size),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PointDetailDialog(c: AppContainer, bundle: ProjectBundle, p: MeasurementPoint, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val samples = bundle.samplesOf(p.id)
    val session = bundle.sessions.indexOfFirst { it.id == p.sessionId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyValue(stringResource(R.string.detail_position), Fmt.coord(p.x, p.y, p.z) ?: stringResource(R.string.position_unknown))
                KeyValue(stringResource(R.string.detail_position_source), p.positionSource.name.lowercase())
                KeyValue(stringResource(R.string.detail_time), Fmt.dateTime(p.createdAt))
                if (session >= 0) KeyValue(stringResource(R.string.detail_session), stringResource(R.string.session_n, session + 1))
                if (p.notes.isNotBlank()) KeyValue(stringResource(R.string.point_notes), p.notes)
                samples.firstNotNullOfOrNull { it.deviceOrientation }?.let { o ->
                    KeyValue(
                        stringResource(R.string.orientation_title),
                        "${Fmt.num(o.azimuthDeg, 0)}° / ${Fmt.num(o.pitchDeg, 0)}° / ${Fmt.num(o.rollDeg, 0)}°",
                    )
                }
                HorizontalDivider()
                SampleTable(samples, detailed = true)
            }
        },
        confirmButton = { TextButton(onClick = { editing = true }) { Text(stringResource(R.string.edit)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
    )

    if (editing) {
        PointEditorDialog(
            title = stringResource(R.string.point_edit),
            initialLabel = p.label, initialNotes = p.notes,
            initialX = p.x, initialY = p.y, initialZ = p.z,
            info = null,
            onSave = { label, notes, x, y, z -> c.actions.updatePoint(bundle.project.id, p.id, label, notes, x, y, z) },
            onDismiss = { editing = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.point_delete),
            text = stringResource(R.string.point_delete_confirm, p.label),
            confirm = stringResource(R.string.delete),
            onConfirm = { c.actions.deletePoint(bundle.project.id, p.id); onDismiss() },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
fun sensorTypeName(t: SensorType): String = stringResource(
    when (t) {
        SensorType.MAGNETIC_FIELD -> R.string.ch_magnetometer
        SensorType.ACCELEROMETER -> R.string.ch_accelerometer
        SensorType.GYROSCOPE -> R.string.ch_gyroscope
        SensorType.LIGHT -> R.string.ch_light
        SensorType.PRESSURE -> R.string.ch_pressure
        SensorType.AMBIENT_TEMPERATURE -> R.string.ch_temperature
        SensorType.RELATIVE_HUMIDITY -> R.string.ch_humidity
        SensorType.VIBRATION_RMS -> R.string.ch_vibration
        SensorType.SOUND_LEVEL -> R.string.ch_microphone
        SensorType.SOUND_SPL -> R.string.st_spl
        SensorType.DOMINANT_FREQUENCY -> R.string.st_dominant
        SensorType.REVERB_TIME -> R.string.st_reverb
        SensorType.ECHO_DISTANCE -> R.string.st_echo
    },
)

@Composable
fun sourceName(s: SampleSource): String = stringResource(
    when (s) {
        SampleSource.DEVICE -> R.string.source_device
        SampleSource.EXTERNAL -> R.string.source_external
        SampleSource.DERIVED -> R.string.source_derived
        SampleSource.DEMO -> R.string.source_demo
    },
)

/** Ölçüm değerleri: her satırda sensör, bileşenler, birim, kaynak ve kalite. */
@Composable
fun SampleTable(samples: List<SensorSample>, detailed: Boolean = false) {
    if (samples.isEmpty()) {
        Text(stringResource(R.string.no_samples), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in samples.sortedBy { it.sensorType.ordinal }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sensorTypeName(s.sensorType), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        sourceName(s.source),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (s.source == SampleSource.DEMO) NexColors.Demo else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    s.values.entries.joinToString("   ") { (k, v) -> "${if (k == "value") "" else "$k "}${Fmt.num(v, Fmt.digitsFor(s.unit))}" } + "  " + s.unit,
                    style = ValueStyle.copy(fontSize = ValueStyle.fontSize * 0.85f),
                )
                if (detailed) {
                    Text(
                        stringResource(R.string.sample_meta, Fmt.time(s.timestamp), s.quality ?: "—", s.calibrationInfo ?: "—"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
