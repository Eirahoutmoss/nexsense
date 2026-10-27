package com.eirahoutmoss.nexsense.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.model.DeviceOrientation
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.sensors.Channel
import com.eirahoutmoss.nexsense.core.sensors.ChannelState
import com.eirahoutmoss.nexsense.core.sensors.PermissionStatus
import com.eirahoutmoss.nexsense.core.sensors.Reading
import com.eirahoutmoss.nexsense.core.sensors.SensorPanel
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.Overlay
import com.eirahoutmoss.nexsense.ui.Tab
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.ViewInAr
import com.eirahoutmoss.nexsense.ui.components.PointEditorDialog
import com.eirahoutmoss.nexsense.ui.components.SessionBar
import com.eirahoutmoss.nexsense.ui.theme.NexColors
import com.eirahoutmoss.nexsense.ui.theme.ValueStyle

/** Ölçüm noktası eklenirken alınan anlık görüntü. */
private data class Snapshot(val readings: Map<Channel, Reading>, val orientation: DeviceOrientation?)

@Composable
fun LiveScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onGo: (Tab) -> Unit, onOverlay: (Overlay) -> Unit = {}) {
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val readings by c.sensorHub.readings.collectAsStateWithLifecycle()
    val orientation by c.sensorHub.orientation.collectAsStateWithLifecycle()
    val micReading by c.micMeter.reading.collectAsStateWithLifecycle()
    val micFailed by c.micMeter.failed.collectAsStateWithLifecycle()
    // Kaydedilmez: ekran dönünce/yeniden açılınca mikrofon kendiliğinden açılmasın.
    var micEnabled by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }

    // Sensörler yalnızca bu ekran görünürken çalışır (pil). Mikrofon ekrandan çıkınca kapanır.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, settings.sampling) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> c.sensorHub.start(settings.sampling)
                Lifecycle.Event.ON_RESUME -> resumeTick++
                Lifecycle.Event.ON_STOP -> { c.sensorHub.stop(); c.micMeter.stop(); micEnabled = false }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            c.sensorHub.start(settings.sampling)
            // Örnekleme hızı değişince etki yeniden kurulur; kullanıcı açtıysa mikrofon da yeniden başlar.
            if (micEnabled && c.micMeter.hasPermission()) c.micMeter.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            c.sensorHub.stop()
            c.micMeter.stop()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        c.settings.setMicDenied(!granted)
        micEnabled = granted
        if (granted) c.micMeter.start()
    }

    // resumeTick anahtarı: kullanıcı izni ayarlardan verip dönünce yeniden denetlenir.
    val micPermission = remember(resumeTick, settings.micDenied, micEnabled) {
        when {
            c.micMeter.hasPermission() -> PermissionStatus.GRANTED
            settings.micDenied -> PermissionStatus.DENIED
            else -> PermissionStatus.NOT_ASKED
        }
    }

    val states: Map<Channel, ChannelState> = Channel.entries.associateWith { ch ->
        if (ch == Channel.MICROPHONE) {
            SensorPanel.state(ch, c.micMeter.hasHardware, micPermission, micEnabled, micReading)
        } else {
            SensorPanel.state(ch, c.sensorHub.hasHardware(ch), PermissionStatus.GRANTED, true, readings[ch])
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (bundle != null) {
                    SessionBar(c, bundle)
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.live_no_project), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onGo(Tab.HOME) }) { Text(stringResource(R.string.go_projects)) }
                        }
                    }
                }
            }
            item {
                // Otomatik ölçüm: koordinat yazmadan, yürüyerek. Akustik: telefon sabit noktada.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Button(
                        onClick = { onOverlay(Overlay.AUTO_SCAN) }, enabled = bundle != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Outlined.ViewInAr, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.live_auto))
                    }
                    OutlinedButton(
                        onClick = { onOverlay(Overlay.ACOUSTIC) }, enabled = bundle != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Outlined.SurroundSound, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.live_acoustic))
                    }
                }
                Text(
                    stringResource(R.string.live_auto_hint),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.live_axes_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(Channel.entries, key = { it.name }) { ch ->
                ChannelCard(
                    channel = ch,
                    state = states.getValue(ch),
                    source = if (ch == Channel.MICROPHONE) stringResource(R.string.source_mic) else c.sensorHub.sensorInfo(ch),
                    micFailed = ch == Channel.MICROPHONE && micFailed,
                    onMicStart = {
                        if (c.micMeter.hasPermission()) { micEnabled = true; c.micMeter.start() }
                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onMicStop = { micEnabled = false; c.micMeter.stop() },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
            item { OrientationCard(orientation) }
        }

        ExtendedFloatingActionButton(
            onClick = {
                if (bundle != null) snapshot = Snapshot(SensorPanel.recordable(states), orientation)
            },
            icon = { Icon(Icons.Outlined.AddLocationAlt, null) },
            text = { Text(stringResource(R.string.point_add)) },
            expanded = true,
            containerColor = if (bundle != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (bundle != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    val snap = snapshot
    if (snap != null && bundle != null) {
        PointEditorDialog(
            title = stringResource(R.string.point_add),
            initialLabel = "", initialNotes = "",
            initialX = null, initialY = null, initialZ = null,
            info = pluralStringResource(R.plurals.point_add_info, snap.readings.size, snap.readings.size),
            onSave = { label, notes, x, y, z ->
                c.actions.addPoint(bundle.project.id, label, notes, x, y, z, snap.readings, snap.orientation)
            },
            onDismiss = { snapshot = null },
        )
    }
}

private fun Channel.icon(): ImageVector = when (this) {
    Channel.MAGNETOMETER -> Icons.Outlined.Explore
    Channel.ACCELEROMETER -> Icons.Outlined.Speed
    Channel.GYROSCOPE -> Icons.Outlined.ScreenRotation
    Channel.VIBRATION -> Icons.Outlined.Vibration
    Channel.LIGHT -> Icons.Outlined.Lightbulb
    Channel.PRESSURE -> Icons.Outlined.Compress
    Channel.AMBIENT_TEMPERATURE -> Icons.Outlined.Thermostat
    Channel.HUMIDITY -> Icons.Outlined.WaterDrop
    Channel.MICROPHONE -> Icons.Outlined.Mic
}

@Composable
fun channelName(ch: Channel): String = stringResource(
    when (ch) {
        Channel.MAGNETOMETER -> R.string.ch_magnetometer
        Channel.ACCELEROMETER -> R.string.ch_accelerometer
        Channel.GYROSCOPE -> R.string.ch_gyroscope
        Channel.VIBRATION -> R.string.ch_vibration
        Channel.LIGHT -> R.string.ch_light
        Channel.PRESSURE -> R.string.ch_pressure
        Channel.AMBIENT_TEMPERATURE -> R.string.ch_temperature
        Channel.HUMIDITY -> R.string.ch_humidity
        Channel.MICROPHONE -> R.string.ch_microphone
    },
)

@Composable
private fun channelNote(ch: Channel): String? = when (ch) {
    Channel.AMBIENT_TEMPERATURE -> stringResource(R.string.note_temperature)
    Channel.HUMIDITY -> stringResource(R.string.note_external)
    Channel.VIBRATION -> stringResource(R.string.note_vibration)
    Channel.MICROPHONE -> stringResource(R.string.note_microphone)
    Channel.MAGNETOMETER -> stringResource(R.string.note_magnetometer)
    else -> null
}

@Composable
private fun ChannelCard(
    channel: Channel,
    state: ChannelState,
    source: String?,
    micFailed: Boolean,
    onMicStart: () -> Unit,
    onMicStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val muted = state is ChannelState.Unsupported
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    channel.icon(), null,
                    tint = if (muted) NexColors.Unmeasured else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(channelName(channel), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(channel.sensorType.unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state) {
                ChannelState.Unsupported -> Text(
                    stringResource(R.string.state_unsupported),
                    style = MaterialTheme.typography.bodyMedium, color = NexColors.Unmeasured,
                )
                ChannelState.Waiting -> Text(
                    stringResource(R.string.state_waiting),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChannelState.Off -> OutlinedButton(onClick = onMicStart) { Text(stringResource(R.string.mic_start)) }
                ChannelState.NeedsPermission -> Column {
                    Text(stringResource(R.string.mic_permission_why), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onMicStart) { Text(stringResource(R.string.mic_allow)) }
                }
                ChannelState.PermissionDenied -> Column {
                    Text(stringResource(R.string.mic_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onMicStart) { Text(stringResource(R.string.mic_ask_again)) }
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    }
                }
                is ChannelState.Live -> {
                    LiveValues(channel, state.reading)
                    Text(
                        stringResource(R.string.live_meta, Fmt.time(state.reading.timestampIso), state.reading.accuracy ?: "—"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (channel == Channel.MICROPHONE) TextButton(onClick = onMicStop) { Text(stringResource(R.string.mic_stop)) }
                }
            }
            if (micFailed) Text(stringResource(R.string.mic_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (source != null && !muted) {
                Text(stringResource(R.string.live_source, source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            channelNote(channel)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LiveValues(channel: Channel, r: Reading) {
    val unit = channel.sensorType.unit
    val digits = Fmt.digitsFor(unit)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        for (comp in channel.sensorType.components) {
            Column {
                Text(componentLabel(comp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.num(r.values[comp], digits), style = ValueStyle)
            }
        }
    }
}

@Composable
fun componentLabel(comp: String): String = when (comp) {
    "magnitude" -> stringResource(R.string.comp_magnitude)
    "value" -> stringResource(R.string.comp_value)
    "rms" -> "RMS"
    "peak" -> stringResource(R.string.comp_peak)
    else -> comp
}

@Composable
private fun OrientationCard(o: DeviceOrientation?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.orientation_title), style = MaterialTheme.typography.titleSmall)
            if (o == null) {
                Text(stringResource(R.string.orientation_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for ((k, v) in listOf(R.string.orientation_azimuth to o.azimuthDeg, R.string.orientation_pitch to o.pitchDeg, R.string.orientation_roll to o.rollDeg)) {
                        Column {
                            Text(stringResource(k), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fmt.num(v, 1) + "°", style = ValueStyle)
                        }
                    }
                }
                Text(stringResource(R.string.orientation_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
