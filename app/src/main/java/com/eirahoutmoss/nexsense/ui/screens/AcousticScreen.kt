package com.eirahoutmoss.nexsense.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.acoustic.AcousticsNote
import com.eirahoutmoss.nexsense.core.acoustic.Quality
import com.eirahoutmoss.nexsense.core.acoustic.RoomVerdict
import com.eirahoutmoss.nexsense.core.acoustic.Sonar
import com.eirahoutmoss.nexsense.core.analysis.RoomEstimator
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.Scan
import com.eirahoutmoss.nexsense.core.model.ScanAlignment
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.core.model.ScanPoint
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.sensors.Channel
import com.eirahoutmoss.nexsense.core.spatial.FrameMath
import com.eirahoutmoss.nexsense.data.AcousticLab
import com.eirahoutmoss.nexsense.sensors.AcousticException
import com.eirahoutmoss.nexsense.sensors.AcousticIo
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.components.KeyValue
import com.eirahoutmoss.nexsense.ui.components.PointEditorDialog
import com.eirahoutmoss.nexsense.ui.components.SectionCard
import com.eirahoutmoss.nexsense.ui.theme.NexColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant

private enum class AcJob { ROOM, ECHO, LEVEL, DIRECTION }

/**
 * Sabit nokta akustik ölçümleri (Faz 4). Telefon sabit bir yüzeye konur; hoparlör çalar, mikrofon dinler.
 * Sonuçlar seçilen ölçüm noktasına kaydedilir. Yön taraması 3B yapıya akustik noktalar ekler (deneysel).
 */
@Composable
fun AcousticScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onDone: () -> Unit) {
    if (bundle == null) {
        NoProjectState(modifier) { onDone() }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val io = remember { AcousticIo(context) }
    val lab = remember { AcousticLab(io) }
    val projectId = bundle.project.id
    var pointId by remember { mutableStateOf(bundle.points.lastOrNull()?.id) }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<AcJob?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var errorCode by remember { mutableStateOf<Int?>(null) }
    var granted by remember { mutableStateOf(io.hasPermission()) }
    var room by remember { mutableStateOf<AcousticLab.RoomResult?>(null) }
    var echo by remember { mutableStateOf<AcousticLab.EchoResult?>(null) }
    var level by remember { mutableStateOf<AcousticLab.LevelResult?>(null) }
    val dirPoints = remember { mutableStateListOf<ScanPoint>() }
    val point = bundle.points.firstOrNull { it.id == pointId }

    DisposableEffect(Unit) {
        c.sensorHub.start(settings.sampling)
        onDispose { c.sensorHub.stop() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        c.settings.setMicDenied(!ok)
    }

    fun run(job: AcJob, block: suspend () -> Unit) {
        if (busy != null) return
        if (!io.hasPermission()) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
        busy = job; progress = 0f; errorCode = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AcousticException) {
                errorCode = e.code
            } catch (e: Exception) {
                errorCode = 0
            } finally {
                busy = null
            }
        }
    }

    val ambientTemp = c.sensorHub.readings.collectAsStateWithLifecycle().value[Channel.AMBIENT_TEMPERATURE]?.values?.get("value")

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.acoustic_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.acoustic_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (io.mediaVolumeRatio() < 0.6) Text(stringResource(R.string.acoustic_volume_low), color = NexColors.Demo, style = MaterialTheme.typography.bodySmall)
        if (!granted) {
            Text(stringResource(R.string.mic_permission_why), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.mic_allow)) }
        }

        SectionCard(stringResource(R.string.acoustic_point)) {
            Text(stringResource(R.string.acoustic_point_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { creating = true }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, label = { Text(stringResource(R.string.acoustic_new_point)) })
                for (p in bundle.points.takeLast(15).asReversed()) {
                    FilterChip(selected = pointId == p.id, onClick = { pointId = p.id }, label = { Text(p.label) })
                }
            }
            Text(
                point?.let { "${it.label} · " + (Fmt.coord(it.x, it.y, it.z) ?: stringResource(R.string.position_unknown)) } ?: stringResource(R.string.acoustic_no_point),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        busy?.let {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.acoustic_running), style = MaterialTheme.typography.bodySmall)
        }
        errorCode?.let { Text(stringResource(acousticError(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        // 1) Oda akustiği
        SectionCard(stringResource(R.string.acoustic_room_title)) {
            Text(stringResource(R.string.acoustic_room_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { run(AcJob.ROOM) { room = lab.room(RoomEstimator.of(bundle)) { progress = it } } }, enabled = busy == null) {
                Text(stringResource(R.string.acoustic_measure))
            }
            room?.let { r ->
                val mid = r.result.rt60MidS
                KeyValue(stringResource(R.string.acoustic_rt60_mid), mid?.let { "${Fmt.num(it)} s · ${stringResource(verdictText(RoomVerdict.of(it)))}" } ?: stringResource(R.string.acoustic_unmeasured))
                KeyValue(stringResource(R.string.acoustic_quality), stringResource(qualityText(r.result.quality)))
                for (b in r.result.bands) {
                    KeyValue("${b.centerHz.toInt()} Hz", b.rt60S?.takeIf { b.quality != Quality.UNRELIABLE }?.let { "${Fmt.num(it)} s (${stringResource(qualityText(b.quality))})" } ?: "—")
                }
                for (n in r.result.notes) Text(stringResource(noteText(n)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.modes.isNotEmpty() && r.room != null) {
                    Text(
                        stringResource(R.string.acoustic_modes, Fmt.num(r.room.lengthM, 1), Fmt.num(r.room.widthM, 1), Fmt.num(r.room.heightM, 1)) + " " +
                            r.modes.take(8).joinToString { "${it.frequencyHz.toInt()} Hz" },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                SaveRow(enabled = point != null && busy == null) {
                    scope.launch { c.actions.addSamplesNow(projectId, point!!.id, listOf(lab.roomDraft(r, c.sensorHub.orientation.value))) }
                }
            }
        }

        // 2) Yankı mesafesi
        SectionCard(stringResource(R.string.acoustic_echo_title)) {
            Text(stringResource(R.string.acoustic_echo_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { run(AcJob.ECHO) { echo = lab.echo(ambientTemp) { progress = it } } }, enabled = busy == null) { Text(stringResource(R.string.acoustic_measure)) }
            echo?.let { e ->
                if (e.echoes.isEmpty()) Text(stringResource(R.string.acoustic_no_echo), style = MaterialTheme.typography.bodySmall)
                e.echoes.forEachIndexed { i, x ->
                    KeyValue("${i + 1}.", "${Fmt.num(x.distanceM)} m · ${stringResource(R.string.acoustic_confidence)} ${Fmt.num(Sonar.confidence01(x.confidence) * 100, 0)}%")
                }
                Text(
                    stringResource(if (e.tempMeasured) R.string.acoustic_speed_measured else R.string.acoustic_speed_assumed, Fmt.num(e.speedMs, 1), Fmt.num(e.tempC, 1)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SaveRow(enabled = point != null && busy == null && e.echoes.isNotEmpty()) {
                    scope.launch { c.actions.addSamplesNow(projectId, point!!.id, listOf(lab.echoDraft(e, c.sensorHub.orientation.value))) }
                }
            }
        }

        // 3) Ses düzeyi
        SectionCard(stringResource(R.string.acoustic_level_title)) {
            Text(
                if (settings.splOffsetDb == null) stringResource(R.string.acoustic_level_uncal) else stringResource(R.string.acoustic_level_cal, Fmt.num(settings.splOffsetDb, 1)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { run(AcJob.LEVEL) { level = lab.level(settings.splOffsetDb) { progress = it } } }, enabled = busy == null) { Text(stringResource(R.string.acoustic_measure)) }
            level?.let { l ->
                KeyValue("Leq", "${Fmt.num(l.leqDbfs, 1)} dBFS" + (l.leqSpl?.let { " ≈ ${Fmt.num(it, 1)} dB SPL" } ?: ""))
                KeyValue(stringResource(R.string.comp_peak), "${Fmt.num(l.peakDbfs, 1)} dBFS" + (l.peakSpl?.let { " ≈ ${Fmt.num(it, 1)} dB SPL" } ?: ""))
                KeyValue(stringResource(R.string.acoustic_dominant), l.dominantHz?.let { "${Fmt.num(it, 0)} Hz" } ?: "—")
                SaveRow(enabled = point != null && busy == null) {
                    scope.launch { c.actions.addSamplesNow(projectId, point!!.id, lab.levelDrafts(l, c.sensorHub.orientation.value)) }
                }
            }
        }

        // 4) Akustik yön taraması (3B yapıya katkı)
        SectionCard(stringResource(R.string.acoustic_dir_title)) {
            Text(stringResource(R.string.acoustic_dir_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val north = bundle.project.coordinateSystem.northAngleDeg
            Text(
                if (north == null) stringResource(R.string.acoustic_north_unset) else stringResource(R.string.acoustic_north_set, Fmt.num(north, 0)),
                style = MaterialTheme.typography.labelSmall, color = if (north == null) NexColors.Demo else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                c.sensorHub.currentAzimuthDeg?.let { az -> c.actions.setNorthAngle(projectId, (360.0 - az) % 360.0) }
            }) { Text(stringResource(R.string.acoustic_north_now)) }
            val anchor = point?.takeIf { it.isPlaced }
            if (anchor == null) Text(stringResource(R.string.acoustic_dir_needs_position), style = MaterialTheme.typography.bodySmall, color = NexColors.Demo)
            Button(
                onClick = {
                    run(AcJob.DIRECTION) {
                        val e = lab.echo(ambientTemp) { progress = it }
                        val first = e.echoes.firstOrNull() ?: return@run
                        val m = c.sensorHub.orientation.value?.rotationMatrix ?: return@run
                        // Telefonun üst kenarı (cihaz +y) yüzeye doğrultulur.
                        val enu = FrameMath.mul(m, Vec3(0.0, 1.0, 0.0)).normalized()
                        val dir = FrameMath.enuToProject(enu, north ?: 0.0)
                        val a = Vec3(anchor!!.x!!, anchor.y!!, anchor.z!!)
                        val p = a + dir * first.distanceM
                        dirPoints += ScanPoint(r3(p.x), r3(p.y), r3(p.z), r3(Sonar.confidence01(first.confidence)), "a")
                    }
                },
                enabled = busy == null && anchor != null,
            ) { Text(stringResource(R.string.acoustic_dir_measure)) }
            Text(stringResource(R.string.acoustic_dir_count, dirPoints.size), style = MaterialTheme.typography.bodySmall)
            if (dirPoints.isNotEmpty() && anchor != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val pts = dirPoints.toList()
                        scope.launch {
                            val ok = c.actions.addScanNow(
                                projectId,
                                Scan(
                                    id = "", projectId = projectId, method = ScanMethod.ACOUSTIC_ECHO,
                                    startedAt = Instant.now().toString(), endedAt = Instant.now().toString(), points = pts,
                                    alignment = ScanAlignment(startPointId = anchor.id, note = if (north == null) "unaligned: magnetic north assumed = +y" else "magnetic-north=$north"),
                                    notes = "experimental; phone speaker is not directional",
                                ),
                            )
                            if (ok != null) dirPoints.clear()
                        }
                    }) { Text(stringResource(R.string.acoustic_dir_save)) }
                    TextButton(onClick = { dirPoints.clear() }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
        Text(stringResource(R.string.acoustic_privacy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (creating) {
        PointEditorDialog(
            title = stringResource(R.string.acoustic_new_point),
            initialLabel = "", initialNotes = "", initialX = null, initialY = null, initialZ = null,
            info = stringResource(R.string.acoustic_new_point_info),
            onSave = { label, notes, x, y, z ->
                scope.launch {
                    val p = c.actions.addPointNow(projectId, label.ifBlank { "AK" }, notes, x, y, z, emptyList(), null, null, quiet = false)
                    if (p != null) pointId = p.id
                }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SaveRow(enabled: Boolean, onSave: () -> Unit) {
    OutlinedButton(onClick = onSave, enabled = enabled) { Text(stringResource(R.string.acoustic_save_to_point)) }
}

private fun r3(v: Double) = Math.round(v * 1000.0) / 1000.0

fun acousticError(code: Int): Int = when (code) {
    2 -> R.string.acoustic_err_mic
    3 -> R.string.acoustic_err_speaker
    5 -> R.string.mic_denied
    else -> R.string.acoustic_err_generic
}

fun qualityText(q: Quality): Int = when (q) {
    Quality.OK -> R.string.quality_ok
    Quality.LOW -> R.string.quality_low
    Quality.UNRELIABLE -> R.string.quality_unreliable
}

fun verdictText(v: RoomVerdict.Verdict): Int = when (v) {
    RoomVerdict.Verdict.DEAD -> R.string.verdict_dead
    RoomVerdict.Verdict.BALANCED -> R.string.verdict_balanced
    RoomVerdict.Verdict.SLIGHTLY_LIVE -> R.string.verdict_slightly_live
    RoomVerdict.Verdict.LIVE -> R.string.verdict_live
}

fun noteText(n: AcousticsNote): Int = when (n) {
    AcousticsNote.MID_UNMEASURED -> R.string.note_mid_unmeasured
    AcousticsNote.LOW_RANGE -> R.string.note_low_range
    AcousticsNote.SPEAKER_BASS_WEAK -> R.string.note_speaker_bass
    AcousticsNote.BASS_NEEDS_EXTERNAL -> R.string.note_bass_external
}
