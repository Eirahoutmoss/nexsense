package com.eirahoutmoss.nexsense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.layers.LayerAnalysis
import com.eirahoutmoss.nexsense.core.layers.LayerData
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.core.layers.LayerSelection
import com.eirahoutmoss.nexsense.core.layers.LayerStatus
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.scene.Bounds
import com.eirahoutmoss.nexsense.core.scene.OrbitCamera
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.Overlay
import com.eirahoutmoss.nexsense.ui.Tab
import com.eirahoutmoss.nexsense.ui.scene.SceneScan
import com.eirahoutmoss.nexsense.core.analysis.Interpolation
import com.eirahoutmoss.nexsense.core.model.PositionSource
import androidx.compose.material3.AssistChip
import com.eirahoutmoss.nexsense.ui.components.DemoBadge
import com.eirahoutmoss.nexsense.ui.components.EmptyState
import com.eirahoutmoss.nexsense.ui.scene.SceneItem
import com.eirahoutmoss.nexsense.ui.scene.SceneView
import com.eirahoutmoss.nexsense.ui.scene.MapSnapshot
import com.eirahoutmoss.nexsense.ui.scene.SpaceMap
import com.eirahoutmoss.nexsense.core.spatial.Frontiers
import com.eirahoutmoss.nexsense.core.spatial.OccupancyGrid
import com.eirahoutmoss.nexsense.ui.theme.LayerColors
import com.eirahoutmoss.nexsense.ui.theme.NexColors

@Composable
fun layerName(kind: LayerKind): String = stringResource(
    when (kind) {
        LayerKind.GEOMETRY -> R.string.layer_geometry
        LayerKind.MAGNETIC -> R.string.layer_magnetic
        LayerKind.MOTION -> R.string.layer_motion
        LayerKind.ACOUSTIC -> R.string.layer_acoustic
        LayerKind.SPL -> R.string.layer_spl
        LayerKind.REVERB -> R.string.layer_reverb
        LayerKind.SCAN -> R.string.layer_scan
        LayerKind.TEMPERATURE -> R.string.layer_temperature
        LayerKind.HUMIDITY -> R.string.layer_humidity
        LayerKind.NOTES -> R.string.layer_notes
    },
)

@Composable
fun SpaceScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onGo: (Tab) -> Unit, onOverlay: (Overlay) -> Unit = {}) {
    if (bundle == null) {
        NoProject(modifier, onGo)
        return
    }
    val projectId = bundle.project.id
    // Zaman çizgisi: seçilen oturumun verisi (null = tümü)
    var sessionFilter by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    val view = remember(bundle, sessionFilter) {
        val sf = sessionFilter
        if (sf == null) bundle else bundle.copy(
            points = bundle.points.filter { it.sessionId == sf },
            samples = bundle.samples.filter { it.sessionId == sf },
            scans = bundle.scans.filter { it.sessionId == sf },
        )
    }
    val layers = remember(view) { LayerAnalysis.computeAll(view) }
    val items = remember(view) {
        view.points.filter { it.isPlaced }.map {
            SceneItem(it.id, it.label, Vec3(it.x!!, it.y!!, it.z!!), it.notes.isNotBlank(), it.positionSource == PositionSource.PDR)
        }
    }
    val scan = remember(view) {
        val all = view.scans.flatMap { it.points }
        val stride = maxOf(1, all.size / 4000)
        SceneScan(all.filterIndexed { i, _ -> i % stride == 0 }, view.scans.flatMap { it.planes }, view.scans.map { it.trajectory }.filter { it.size > 1 })
    }
    val unplaced = view.points.count { !it.isPlaced }
    var selection by remember(projectId) { mutableStateOf(LayerSelection()) }
    var showEstimate by rememberSaveable(projectId) { mutableStateOf(false) }
    var showFreeMap by rememberSaveable(projectId) { mutableStateOf(false) }
    val freeMap = remember(view, showFreeMap) {
        if (!showFreeMap) null else {
            val g = OccupancyGrid.build(view)
            MapSnapshot.of(g, Frontiers.find(g).size)
        }
    }
    val freePath = remember(view) { view.scans.flatMap { s -> s.trajectory.map { Vec3(it.x, it.y, it.z) } } }
    val sceneBounds = Bounds.of(items.map { it.position } + scan.points.map { Vec3(it.x, it.y, it.z) })
    var camera by remember(projectId) { mutableStateOf(OrbitCamera.fit(sceneBounds)) }
    var selectedId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var editingOrigin by remember { mutableStateOf(false) }
    val colorData: LayerData? = selection.colorLayer?.let { layers[it] }?.takeIf { it.status == LayerStatus.HAS_DATA }
    val estimate = remember(view, colorData, showEstimate) { if (showEstimate && colorData != null) Interpolation.idw(view, colorData, bundle.analysis) else null }

    Column(modifier.fillMaxSize()) {
        // Katman kontrolleri
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (kind in LayerKind.entries) {
                val data = layers.getValue(kind)
                val on = selection.isVisible(kind)
                FilterChip(
                    selected = on,
                    onClick = { selection = selection.toggle(kind) },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).background(LayerColors.swatch(kind), CircleShape))
                    },
                    label = {
                        Text(
                            if (data.status == LayerStatus.NO_DATA) stringResource(R.string.layer_no_data_suffix, layerName(kind)) else layerName(kind),
                        )
                    },
                )
            }
        }
        // Tahmin ve zaman çizgisi
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = showEstimate, enabled = colorData != null,
                onClick = { showEstimate = !showEstimate },
                label = { Text(stringResource(R.string.space_estimate)) },
            )
            FilterChip(
                selected = showFreeMap,
                onClick = { showFreeMap = !showFreeMap },
                label = { Text(stringResource(R.string.space_freemap)) },
            )
            AssistChip(onClick = { onOverlay(Overlay.ANALYSIS) }, label = { Text(stringResource(R.string.analysis_title)) })
            if (bundle.sessions.size > 1) {
                FilterChip(sessionFilter == null, { sessionFilter = null }, { Text(stringResource(R.string.filter_all_sessions)) })
                bundle.sessions.forEachIndexed { i, ss ->
                    FilterChip(sessionFilter == ss.id, { sessionFilter = ss.id }, { Text(stringResource(R.string.session_n, i + 1)) })
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val fm = freeMap
            if (fm != null) {
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    SpaceMap(fm, freePath, null, null, emptyList(), Modifier.weight(1f).fillMaxWidth())
                    Text(
                        stringResource(R.string.space_freemap_caption, String.format(java.util.Locale.getDefault(), "%.1f", fm.freeArea), String.format(java.util.Locale.getDefault(), "%.1f", fm.occupiedArea), fm.frontiers),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (bundle.hasDemoData) Text(stringResource(R.string.demo_badge), style = MaterialTheme.typography.labelSmall, color = NexColors.Demo)
                }
            } else if (items.isEmpty() && scan.isEmpty) {
                EmptyState(
                    icon = Icons.Outlined.MyLocation,
                    title = stringResource(R.string.space_empty_title),
                    body = stringResource(R.string.space_empty_body, unplaced),
                ) {
                    TextButton(onClick = { onOverlay(Overlay.AUTO_SCAN) }) { Text(stringResource(R.string.auto_title)) }
                    TextButton(onClick = { onGo(Tab.LIVE) }) { Text(stringResource(R.string.point_add)) }
                    TextButton(onClick = { onGo(Tab.MEASUREMENTS) }) { Text(stringResource(R.string.tab_measurements)) }
                }
            } else {
                SceneView(
                    items = items,
                    camera = camera,
                    onCamera = { camera = it },
                    showGeometry = selection.isVisible(LayerKind.GEOMETRY),
                    showNotes = selection.isVisible(LayerKind.NOTES),
                    colorLayer = colorData,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    description = stringResource(R.string.space_scene_description, items.size),
                    modifier = Modifier.fillMaxSize(),
                    scan = if (selection.isVisible(LayerKind.SCAN)) scan else null,
                    estimate = estimate,
                )
                Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    IconButton(onClick = { camera = OrbitCamera.fit(sceneBounds) }) {
                        Icon(Icons.Outlined.CenterFocusStrong, contentDescription = stringResource(R.string.space_fit), tint = NexColors.Cyan)
                    }
                }
                if (bundle.hasDemoData) DemoBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
                if (estimate != null) {
                    Text(
                        stringResource(R.string.space_estimate_badge, estimate.estimatedCells),
                        color = NexColors.Demo, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    )
                }
            }

            val sel = selectedId?.let { id -> bundle.points.firstOrNull { it.id == id } }
            if (sel != null) {
                PointDetailCard(
                    bundle = bundle, pointId = sel.id,
                    onClose = { selectedId = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }
        }

        Legend(
            colorData = colorData,
            colorLayer = selection.colorLayer,
            unplaced = unplaced,
            origin = bundle.project.coordinateSystem.origin,
            onEditOrigin = { editingOrigin = true },
        )
    }

    if (editingOrigin) {
        var text by rememberSaveable { mutableStateOf(bundle.project.coordinateSystem.origin) }
        AlertDialog(
            onDismissRequest = { editingOrigin = false },
            title = { Text(stringResource(R.string.origin_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.origin_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.origin_label)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { c.actions.setReferenceOrigin(projectId, text); editingOrigin = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editingOrigin = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Legend(colorData: LayerData?, colorLayer: LayerKind?, unplaced: Int, origin: String, onEditOrigin: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            colorLayer == null -> Text(stringResource(R.string.legend_no_color_layer), style = MaterialTheme.typography.bodySmall)
            colorData == null -> Text(stringResource(R.string.legend_layer_no_data, layerName(colorLayer)), style = MaterialTheme.typography.bodySmall)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(layerName(colorLayer), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    if (colorData.containsDemo) DemoBadge()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Fmt.num(colorData.min)} ", style = MaterialTheme.typography.labelSmall)
                    val ramp = LayerColors.ramp(colorLayer)
                    Box(
                        Modifier.weight(1f).height(8.dp)
                            .background(Brush.horizontalGradient(listOf(ramp.at(0.0), ramp.at(1.0))), RoundedCornerShape(4.dp)),
                    )
                    Text(" ${Fmt.num(colorData.max)} ${colorData.unit.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    stringResource(R.string.legend_measured_points, colorData.values.size),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(stringResource(R.string.legend_honesty), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (unplaced > 0) {
            Text(stringResource(R.string.legend_unplaced, unplaced), style = MaterialTheme.typography.labelSmall, color = NexColors.Demo)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.legend_origin, origin.ifBlank { stringResource(R.string.origin_unset) }),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEditOrigin, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.origin_title), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PointDetailCard(bundle: ProjectBundle, pointId: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val p = bundle.points.firstOrNull { it.id == pointId } ?: return
    val samples = bundle.samplesOf(pointId)
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
                }
            }
            Text(
                Fmt.coord(p.x, p.y, p.z) ?: stringResource(R.string.position_unknown),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(Fmt.dateTime(p.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodySmall)
            SampleTable(samples)
        }
    }
}

@Composable
private fun NoProject(modifier: Modifier, onGo: (Tab) -> Unit) {
    EmptyState(
        icon = Icons.Outlined.FolderOpen,
        title = stringResource(R.string.no_project_title),
        body = stringResource(R.string.no_project_body),
        modifier = modifier,
    ) {
        TextButton(onClick = { onGo(Tab.HOME) }) { Text(stringResource(R.string.go_projects)) }
    }
}

@Composable
fun NoProjectState(modifier: Modifier, onGo: (Tab) -> Unit) = NoProject(modifier, onGo)
