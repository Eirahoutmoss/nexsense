package com.eirahoutmoss.nexsense.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.listen.DrillSignal
import com.eirahoutmoss.nexsense.core.listen.DrillSource
import com.eirahoutmoss.nexsense.core.listen.Fusion
import com.eirahoutmoss.nexsense.core.listen.FusionResult
import com.eirahoutmoss.nexsense.core.listen.ListenEvent
import com.eirahoutmoss.nexsense.core.listen.ListenKind
import com.eirahoutmoss.nexsense.core.listen.ListenPosition
import com.eirahoutmoss.nexsense.core.listen.ListenRecord
import com.eirahoutmoss.nexsense.core.listen.Patterns
import com.eirahoutmoss.nexsense.core.listen.SoundEventDetector
import com.eirahoutmoss.nexsense.core.listen.VibrationDetector
import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.sensors.AcousticException
import com.eirahoutmoss.nexsense.sensors.AcousticIo
import com.eirahoutmoss.nexsense.settings.SamplingRate
import com.eirahoutmoss.nexsense.ui.components.ConfirmDialog
import com.eirahoutmoss.nexsense.ui.components.SectionCard
import com.eirahoutmoss.nexsense.ui.theme.NexColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

private const val LISTEN_FS = 16_000
private const val DRILL_FS = 48_000

private enum class ListenTab { LISTEN, FUSE, DRILL }

private enum class Phase { IDLE, CALIBRATING, CALL, LISTEN, SAVED }

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun num(v: Double, d: Int = 1) = String.format(Locale.getDefault(), "%.${d}f", v)

fun kindName(k: ListenKind): Int = when (k) {
    ListenKind.KNOCK -> R.string.listen_kind_knock
    ListenKind.VOICE_LIKE -> R.string.listen_kind_voice
    ListenKind.SOUND -> R.string.listen_kind_sound
    ListenKind.VIBRATION -> R.string.listen_kind_vibration
}

private fun kindIcon(k: ListenKind): ImageVector = when (k) {
    ListenKind.KNOCK -> Icons.Outlined.SurroundSound
    ListenKind.VOICE_LIKE -> Icons.Outlined.Hearing
    ListenKind.SOUND -> Icons.Outlined.GraphicEq
    ListenKind.VIBRATION -> Icons.Outlined.Vibration
}

/**
 * Dinleme ve kaynak bulma. Arama-kurtarmada "sessiz an" uygulamasının telefonla yapılan biçimi:
 * herkes susar, telefonlar vuruş ve sese benzeyen sinyalleri dinler. Ses kaydedilmez; yalnızca olay zamanı ve düzeyi.
 * Birkaç telefonun kayıtları birleştirilince yaklaşık kaynak bölgesi çıkar. Tatbikat modu düzeni güvenle dener.
 */
@Composable
fun ListenScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier) {
    var tab by rememberSaveable { mutableStateOf(ListenTab.LISTEN) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(tab == ListenTab.LISTEN, { tab = ListenTab.LISTEN }, { Text(stringResource(R.string.listen_tab_listen)) })
            FilterChip(tab == ListenTab.FUSE, { tab = ListenTab.FUSE }, { Text(stringResource(R.string.listen_tab_fuse)) })
            FilterChip(tab == ListenTab.DRILL, { tab = ListenTab.DRILL }, { Text(stringResource(R.string.listen_tab_drill)) })
        }
        when (tab) {
            ListenTab.LISTEN -> ListenPane(c, bundle)
            ListenTab.FUSE -> FusePane(c)
            ListenTab.DRILL -> DrillPane(c, bundle)
        }
    }
}

/** Dinleyicinin konumu: konumsuz, projedeki bir nokta ya da elle x/y. */
@Composable
private fun PositionPicker(bundle: ProjectBundle?, value: ListenPosition?, onChange: (ListenPosition?) -> Unit) {
    var mx by rememberSaveable { mutableStateOf("") }
    var my by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.listen_position), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(value == null, { onChange(null) }, { Text(stringResource(R.string.listen_position_none)) })
        val points = bundle?.points?.filter { it.isPlaced }.orEmpty().take(15)
        for (p in points) {
            FilterChip(
                value?.pointId == p.id,
                { onChange(ListenPosition(p.x!!, p.y!!, p.z!!, p.id, estimated = p.positionSource != PositionSource.ARCORE)) },
                { Text(p.label) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(mx, { mx = it }, label = { Text("x (m)") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(my, { my = it }, label = { Text("y (m)") }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            val x = mx.replace(',', '.').toDoubleOrNull()
            val y = my.replace(',', '.').toDoubleOrNull()
            if (x != null && y != null) onChange(ListenPosition(x, y, 0.0, null, estimated = true))
        }) { Text(stringResource(R.string.listen_position_apply)) }
    }
    Text(
        value?.let { stringResource(R.string.listen_position_value, num(it.x, 2), num(it.y, 2)) + if (it.estimated) " · " + stringResource(R.string.guide_estimated) else "" }
            ?: stringResource(R.string.listen_position_unknown),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ListenPane(c: AppContainer, bundle: ProjectBundle?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val io = remember { AcousticIo(context) }
    var label by rememberSaveable { mutableStateOf(context.getString(R.string.listen_default_label)) }
    var position by remember { mutableStateOf<ListenPosition?>(null) }
    var drill by rememberSaveable { mutableStateOf(false) }
    var callOut by rememberSaveable { mutableStateOf(true) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var countdown by remember { mutableIntStateOf(0) }
    var cycle by remember { mutableIntStateOf(0) }
    var level by remember { mutableStateOf(0.0) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<ListenRecord?>(null) }
    val events = remember { mutableStateListOf<ListenEvent>() }
    val job = remember { arrayOfNulls<Job>(1) }
    val det = remember { arrayOfNulls<SoundEventDetector>(1) }
    val startMs = remember { longArrayOf(0L) }
    val queueRef = remember { arrayOfNulls<ConcurrentLinkedQueue<ListenEvent>>(1) }

    // Sesli çağrı
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        var e: TextToSpeech? = null
        e = TextToSpeech(context) { st -> if (st == TextToSpeech.SUCCESS) e?.language = Locale.getDefault() }
        tts[0] = e
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            job[0]?.cancel()
            c.sensorHub.accelListener = null
            c.sensorHub.stop()
            runCatching { e?.stop(); e?.shutdown() }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> c.settings.setMicDenied(!ok) }

    fun stop() {
        val d = det[0] ?: return
        job[0]?.cancel(); job[0] = null
        c.sensorHub.accelListener = null
        c.sensorHub.stop()
        runCatching { tts[0]?.stop() }
        val r = ListenRecord(
            id = UUID.randomUUID().toString(),
            label = label.trim().ifBlank { context.getString(R.string.listen_default_label) }.take(60),
            startEpochMs = startMs[0],
            durationS = Math.round(d.timeS * 1000.0) / 1000.0,
            fs = LISTEN_FS,
            micSource = io.sourceName,
            floorDbfs = d.floorDbfs?.let { Math.round(it * 100.0) / 100.0 },
            splOffsetDb = settings.splOffsetDb,
            position = position,
            drill = drill,
            projectId = bundle?.project?.id,
            events = (events.toList() + queueRef[0]?.toList().orEmpty() + d.flush()).sortedBy { it.t },
        )
        det[0] = null
        scope.launch {
            runCatching { c.listenStore.save(r) }.onFailure { error = it.message }
            saved = r
            phase = Phase.SAVED
        }
    }

    fun start() {
        if (!io.hasPermission()) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
        events.clear(); error = null; saved = null; cycle = 0
        val d = SoundEventDetector(LISTEN_FS)
        det[0] = d
        val vib = VibrationDetector()
        val queue = ConcurrentLinkedQueue<ListenEvent>()
        queueRef[0] = queue
        // Kendi çağrımız sırasında duyulan sesler sayılmaz.
        val muteUntil = doubleArrayOf(0.0)
        startMs[0] = System.currentTimeMillis()
        val startElapsed = SystemClock.elapsedRealtime()
        c.sensorHub.start(SamplingRate.HIGH)
        c.sensorHub.accelListener = { mag, tMs ->
            val ev = synchronized(vib) { vib.feed((tMs - startElapsed) / 1000.0, mag) }
            if (ev != null && ev.t > muteUntil[0]) queue.add(ev)
        }
        phase = Phase.CALIBRATING
        job[0] = scope.launch {
            launch {
                try {
                    io.stream(LISTEN_FS) { b -> for (e in d.feed(b)) if (e.t > muteUntil[0]) queue.add(e) }
                } catch (e: AcousticException) {
                    error = context.getString(R.string.listen_mic_error)
                }
            }
            launch {
                while (true) {
                    while (true) events += queue.poll() ?: break
                    level = d.lastAboveFloorDb
                    delay(100)
                }
            }
            while (!d.calibrated) delay(100)
            while (true) {
                if (callOut) {
                    phase = Phase.CALL
                    muteUntil[0] = d.timeS + 6.0
                    runCatching { tts[0]?.speak(context.getString(R.string.listen_call_text), TextToSpeech.QUEUE_FLUSH, null, "call") }
                    delay(6000)
                }
                phase = Phase.LISTEN
                for (s in 20 downTo 1) { countdown = s; delay(1000) }
                cycle++
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (phase) {
            Phase.IDLE -> {
                Text(stringResource(R.string.listen_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.listen_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(label, { label = it.take(60) }, label = { Text(stringResource(R.string.listen_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PositionPicker(bundle, position) { position = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(callOut, { callOut = it }); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.listen_call_toggle), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(drill, { drill = it }); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.listen_drill_toggle), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.listen_placement_tip), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { start() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Outlined.Hearing, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.listen_start))
                }
                Text(stringResource(R.string.listen_privacy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Phase.SAVED -> {
                val r = saved
                Text(stringResource(R.string.listen_saved_title), style = MaterialTheme.typography.titleLarge)
                if (r != null) {
                    if (r.drill) Text(stringResource(R.string.listen_drill_badge), color = NexColors.Demo, fontWeight = FontWeight.Bold)
                    EventSummary(r.events)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val uris = c.listenStore.shareUris(listOf(r))
                                val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uris.first())
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.listen_share)))
                            }
                        }) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.listen_share)) }
                        OutlinedButton(onClick = { phase = Phase.IDLE }) { Text(stringResource(R.string.listen_again)) }
                    }
                    Text(stringResource(R.string.listen_share_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = NexColors.Danger) }
            }
            else -> {
                if (drill) Text(stringResource(R.string.listen_drill_badge), color = NexColors.Demo, fontWeight = FontWeight.Bold)
                SectionCard(null) {
                    Text(
                        when (phase) {
                            Phase.CALIBRATING -> stringResource(R.string.listen_phase_calibrating)
                            Phase.CALL -> stringResource(R.string.listen_phase_call)
                            else -> stringResource(R.string.listen_phase_listen, countdown)
                        },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(if (phase == Phase.LISTEN) R.string.listen_silence_rule else R.string.listen_quiet_rule),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { (level / 40.0).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth().height(10.dp))
                    Text(stringResource(R.string.listen_level, num(level.coerceAtLeast(0.0)), cycle), style = MaterialTheme.typography.labelSmall)
                }
                EventSummary(events)
                Button(onClick = { stop() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.listen_stop)) }
                error?.let { Text(it, color = NexColors.Danger) }
            }
        }
    }
}

@Composable
private fun EventSummary(events: List<ListenEvent>) {
    val p = Patterns.analyze(events)
    if (p.looksDeliberate) {
        Text(
            if (p.repeatedSize != null) stringResource(R.string.listen_pattern_groups, p.repeatedSize ?: 0, p.repeats)
            else stringResource(R.string.listen_pattern_regular, num(p.intervalS ?: 0.0)),
            color = NexColors.Demo, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().background(NexColors.Demo.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(10.dp),
        )
    }
    val counts = ListenKind.entries.map { k -> k to events.count { it.kind == k } }
    Text(
        counts.map { (k, n) -> stringResource(kindName(k)) + ": " + n }.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
    )
    if (events.isEmpty()) {
        Text(stringResource(R.string.listen_no_events), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (e in events.takeLast(30).asReversed()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(kindIcon(e.kind), null, tint = NexColors.Cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(kindName(e.kind)) + " · " + stringResource(R.string.listen_event_line, num(e.t), num(e.aboveFloorDb, 0)) +
                    if (e.kind == ListenKind.VOICE_LIKE) " · " + stringResource(R.string.listen_voice_score, num(e.voiceScore, 2)) else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FusePane(c: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf<List<ListenRecord>>(emptyList()) }
    val selected = remember { mutableStateListOf<String>() }
    var result by remember { mutableStateOf<FusionResult?>(null) }
    var used by remember { mutableStateOf<List<ListenRecord>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ListenRecord?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) {
        val list = c.listenStore.list()
        if (records.isEmpty()) { selected.clear(); selected += list.map { it.id } }
        records = list
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            var ok = 0; var bad = 0
            for (u in uris) {
                try {
                    val r = c.listenStore.import(u)
                    if (r.id !in selected) selected += r.id
                    ok++
                } catch (e: Exception) {
                    // Bozuk ya da NexSense dinleme kaydı olmayan dosya (ListenFormatException) atlanır.
                    bad++
                }
            }
            message = context.getString(R.string.listen_import_result, ok, bad)
            refresh++
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.listen_fuse_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.listen_fuse_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.listen_import))
            }
            OutlinedButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val uris = c.listenStore.shareUris(records.filter { it.id in selected })
                        if (uris.isEmpty()) return@launch
                        val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/json").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.listen_share)))
                    }
                },
            ) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.listen_share)) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        if (records.isEmpty()) Text(stringResource(R.string.listen_no_records), style = MaterialTheme.typography.bodySmall)
        for (r in records) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(r.id in selected, { on -> if (on) selected += r.id else selected -= r.id })
                Column(Modifier.weight(1f)) {
                    Text(
                        r.label + (if (r.drillSource != null) " · " + stringResource(R.string.listen_drill_source_tag) else if (r.drill) " · " + stringResource(R.string.listen_drill_badge) else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(
                            R.string.listen_record_line,
                            timeFmt.format(Instant.ofEpochMilli(r.startEpochMs)), num(r.durationS, 0), r.events.size,
                            r.position?.let { "(${num(it.x, 1)}; ${num(it.y, 1)})" } ?: stringResource(R.string.listen_position_none),
                        ),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { deleting = r }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
            }
        }
        Button(
            enabled = !busy && records.count { it.id in selected } >= 2,
            onClick = {
                busy = true
                scope.launch {
                    val sel = records.filter { it.id in selected }
                    result = withContext(Dispatchers.Default) { Fusion.run(sel) }
                    used = sel
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.listen_fuse_run)) }
        result?.let { FusionView(it, used) }
        Text(stringResource(R.string.listen_fuse_honesty), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    deleting?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.listen_delete),
            text = stringResource(R.string.listen_delete_confirm, r.label),
            confirm = stringResource(R.string.delete),
            onConfirm = { scope.launch { c.listenStore.delete(r.id); selected -= r.id; refresh++ } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun FusionView(r: FusionResult, records: List<ListenRecord>) {
    SectionCard(stringResource(R.string.listen_result)) {
        if (r.isDrill || records.any { it.drill }) {
            Text(stringResource(R.string.listen_drill_badge), color = NexColors.Demo, fontWeight = FontWeight.Bold)
        }
        Text(stringResource(R.string.listen_groups, r.groups.size), style = MaterialTheme.typography.bodyMedium)
        records.forEachIndexed { i, rec ->
            val a = r.alignments.getOrNull(i)
            if (rec.drillSource == null && a != null) {
                Text(
                    "${i + 1}. ${rec.label}: " + if (a.aligned) stringResource(R.string.listen_clock_ok, num(a.offsetS, 2)) else stringResource(R.string.listen_clock_none),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val est = r.estimate
        if (est != null) {
            FusionMap(r, records, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(
                stringResource(R.string.listen_estimate, num(est.best.x, 1), num(est.best.y, 1), num(est.regionAreaM2, 1), num(est.residualDb, 1), est.listeners, est.groups),
                style = MaterialTheme.typography.bodySmall,
            )
            if (est.ambiguous) Text(stringResource(R.string.listen_ambiguous), color = NexColors.Demo, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(stringResource(R.string.listen_no_estimate), style = MaterialTheme.typography.bodySmall)
        }
        r.loudestRecord?.let { Text(stringResource(R.string.listen_loudest, records[it].label), style = MaterialTheme.typography.bodySmall) }
        r.drill?.let { d ->
            Text(
                d.errorM?.let { stringResource(R.string.listen_drill_error, num(it, 1), d.emitted) } ?: stringResource(R.string.listen_drill_no_error, d.emitted),
                style = MaterialTheme.typography.bodySmall, color = NexColors.Demo,
            )
            if (r.gainsDb.isNotEmpty()) {
                Text(
                    stringResource(R.string.listen_gains) + " " + r.gainsDb.entries.sortedBy { it.key }.joinToString(", ") { "${records[it.key].label} ${num(it.value, 1)} dB" },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Kuşbakışı: dinleyiciler (numaralı), belirsizlik bölgesi, en olası nokta, tatbikatta gerçek kaynak. */
@Composable
private fun FusionMap(r: FusionResult, records: List<ListenRecord>, modifier: Modifier) {
    val est = r.estimate ?: return
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Canvas(modifier.background(Color(0xFF0B1424), RoundedCornerShape(12.dp)).padding(10.dp)) {
        val ls = records.mapIndexedNotNull { i, rec -> rec.position?.takeIf { rec.drillSource == null }?.let { i to it } }
        val xs = ls.map { it.second.x } + est.region.map { it.x } + listOfNotNull(r.drill?.source?.x)
        val ys = ls.map { it.second.y } + est.region.map { it.y } + listOfNotNull(r.drill?.source?.y)
        val minX = xs.min() - 1; val maxX = xs.max() + 1
        val minY = ys.min() - 1; val maxY = ys.max() + 1
        val sc = minOf(size.width / (maxX - minX), size.height / (maxY - minY)).toFloat()
        fun o(x: Double, y: Double) = Offset(((x - minX) * sc).toFloat(), size.height - ((y - minY) * sc).toFloat())
        val cs = (est.cellM * sc).toFloat()
        for (p in est.region) {
            val q = o(p.x, p.y)
            drawRect(NexColors.Demo.copy(alpha = 0.28f), Offset(q.x - cs / 2, q.y - cs / 2), Size(cs, cs))
        }
        drawCircle(NexColors.Demo, 12f, o(est.best.x, est.best.y), style = Stroke(4f))
        r.drill?.source?.let { s ->
            val q = o(s.x, s.y)
            drawLine(NexColors.Cyan, Offset(q.x - 10, q.y - 10), Offset(q.x + 10, q.y + 10), 4f)
            drawLine(NexColors.Cyan, Offset(q.x - 10, q.y + 10), Offset(q.x + 10, q.y - 10), 4f)
        }
        val paint = android.graphics.Paint().apply { this.color = textColor; textSize = 30f; isAntiAlias = true }
        for ((i, p) in ls) {
            val q = o(p.x, p.y)
            drawCircle(NexColors.Blue, 10f, q)
            drawContext.canvas.nativeCanvas.drawText("${i + 1}", q.x + 12, q.y - 12, paint)
        }
    }
}

@Composable
private fun DrillPane(c: AppContainer, bundle: ProjectBundle?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val io = remember { AcousticIo(context) }
    var position by remember { mutableStateOf<ListenPosition?>(null) }
    var playing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<ListenRecord?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.listen_drill_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.listen_drill_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PositionPicker(bundle, position) { position = it }
        if (io.mediaVolumeRatio() < 0.6) Text(stringResource(R.string.listen_drill_volume), color = NexColors.Demo, style = MaterialTheme.typography.labelMedium)
        Button(
            enabled = !playing && position != null,
            onClick = {
                val p = position ?: return@Button
                playing = true; error = null; done = null
                scope.launch {
                    try {
                        val (sig, times) = DrillSignal.pattern(DRILL_FS)
                        val start = System.currentTimeMillis()
                        io.play(sig, DRILL_FS)
                        val r = ListenRecord(
                            id = UUID.randomUUID().toString(),
                            label = context.getString(R.string.listen_drill_source_label),
                            startEpochMs = start,
                            durationS = Math.round(sig.size * 1000.0 / DRILL_FS) / 1000.0,
                            fs = DRILL_FS,
                            drill = true,
                            drillSource = DrillSource(p.x, p.y, p.z, times),
                            projectId = bundle?.project?.id,
                        )
                        c.listenStore.save(r)
                        done = r
                    } catch (e: Exception) {
                        error = context.getString(R.string.listen_drill_failed)
                    }
                    playing = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if (playing) R.string.listen_drill_playing else R.string.listen_drill_play)) }
        if (position == null) Text(stringResource(R.string.listen_drill_need_position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        done?.let { Text(stringResource(R.string.listen_drill_done, it.drillSource?.emitT?.size ?: 0), style = MaterialTheme.typography.bodySmall, color = NexColors.Cyan) }
        error?.let { Text(it, color = NexColors.Danger) }
        Text(stringResource(R.string.listen_drill_honesty), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
