package com.eirahoutmoss.nexsense.ui.screens

import android.Manifest
import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.ArrowCircleUp
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.ar.ArCapture
import com.eirahoutmoss.nexsense.ar.ArSessionGate
import com.eirahoutmoss.nexsense.ar.ArStatus
import com.eirahoutmoss.nexsense.ar.arSupported
import com.eirahoutmoss.nexsense.ar.displayRotationOf
import com.eirahoutmoss.nexsense.core.analysis.RoomEstimator
import com.eirahoutmoss.nexsense.core.guide.Capture
import com.eirahoutmoss.nexsense.core.guide.CaptureKind
import com.eirahoutmoss.nexsense.core.guide.GuideEngine
import com.eirahoutmoss.nexsense.core.guide.GuideFrame
import com.eirahoutmoss.nexsense.core.guide.GuideSignals
import com.eirahoutmoss.nexsense.core.guide.GuideStep
import com.eirahoutmoss.nexsense.core.guide.Hint
import com.eirahoutmoss.nexsense.core.guide.Mission
import com.eirahoutmoss.nexsense.core.guide.MissionSummary
import com.eirahoutmoss.nexsense.core.guide.Missions
import com.eirahoutmoss.nexsense.core.guide.Need
import com.eirahoutmoss.nexsense.core.guide.Quality
import com.eirahoutmoss.nexsense.core.guide.StepOutcome
import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.Scan
import com.eirahoutmoss.nexsense.core.model.ScanAlignment
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.sensors.Channel
import com.eirahoutmoss.nexsense.core.sensors.Reading
import com.eirahoutmoss.nexsense.core.spatial.PdrTracker
import com.eirahoutmoss.nexsense.core.spatial.PeakStepDetector
import com.eirahoutmoss.nexsense.data.AcousticLab
import com.eirahoutmoss.nexsense.sensors.AcousticIo
import com.eirahoutmoss.nexsense.ui.components.SectionCard
import com.eirahoutmoss.nexsense.ui.theme.NexColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ---------------- metin eşlemeleri ----------------

fun missionTitle(id: String): Int = when (id) {
    "first" -> R.string.mission_first
    "room" -> R.string.mission_room
    "magnetic" -> R.string.mission_magnetic
    else -> R.string.mission_sound
}

fun missionDesc(id: String): Int = when (id) {
    "first" -> R.string.mission_first_desc
    "room" -> R.string.mission_room_desc
    "magnetic" -> R.string.mission_magnetic_desc
    else -> R.string.mission_sound_desc
}

fun stepTitle(step: GuideStep): Int = when (step.id) {
    "still" -> R.string.step_still
    "figure8" -> R.string.step_figure8
    "low" -> R.string.step_low
    "mid" -> R.string.step_mid
    "high" -> R.string.step_high
    "turn" -> R.string.step_turn
    "forward" -> R.string.step_forward
    "return" -> R.string.step_return
    "corner1" -> R.string.step_corner1
    "corner2" -> R.string.step_corner2
    "corner3" -> R.string.step_corner3
    "corner4" -> R.string.step_corner4
    "wall_right" -> R.string.step_wall_right
    "wall_left" -> R.string.step_wall_left
    else -> R.string.step_place
}

fun stepWhy(step: GuideStep): Int = when (step) {
    is GuideStep.HoldStill -> R.string.why_still
    is GuideStep.Figure8 -> R.string.why_figure8
    is GuideStep.Height -> R.string.why_height
    is GuideStep.Move -> if (step.everyM < 0.5) R.string.why_wall else R.string.why_forward
    is GuideStep.Return -> R.string.why_return
    is GuideStep.Turn -> R.string.why_turn
    is GuideStep.Mark -> R.string.why_corner
    is GuideStep.PlaceFlat -> R.string.why_place
}

fun hintText(h: Hint): Int = when (h) {
    Hint.HOLD_STILL -> R.string.hint_hold_still
    Hint.MOVE_LESS -> R.string.hint_move_less
    Hint.RAISE -> R.string.hint_raise
    Hint.LOWER -> R.string.hint_lower
    Hint.HOLD_HERE -> R.string.hint_hold_here
    Hint.KEEP_GOING -> R.string.hint_keep_going
    Hint.SLOW_DOWN -> R.string.hint_slow_down
    Hint.WRONG_WAY -> R.string.hint_wrong_way
    Hint.DRIFT_LEFT -> R.string.hint_drift_left
    Hint.DRIFT_RIGHT -> R.string.hint_drift_right
    Hint.TURN_LEFT -> R.string.hint_turn_left
    Hint.TURN_RIGHT -> R.string.hint_turn_right
    Hint.TURN_SLOWER -> R.string.hint_turn_slower
    Hint.TILT_UP -> R.string.hint_tilt_up
    Hint.TILT_DOWN -> R.string.hint_tilt_down
    Hint.LAY_FLAT -> R.string.hint_lay_flat
    Hint.MOVE_AWAY -> R.string.hint_move_away
    Hint.WORKING -> R.string.hint_working
    Hint.LOST_TRACKING -> R.string.hint_lost
    Hint.NEED_CONFIRM -> R.string.hint_confirm
}

private fun hintIcon(h: Hint?): ImageVector = when (h) {
    Hint.RAISE -> Icons.Outlined.ArrowUpward
    Hint.LOWER -> Icons.Outlined.ArrowDownward
    Hint.KEEP_GOING -> Icons.AutoMirrored.Outlined.DirectionsWalk
    Hint.SLOW_DOWN -> Icons.Outlined.Speed
    Hint.WRONG_WAY -> Icons.Outlined.SwapVert
    Hint.DRIFT_LEFT -> Icons.AutoMirrored.Outlined.ArrowBack
    Hint.DRIFT_RIGHT -> Icons.AutoMirrored.Outlined.ArrowForward
    Hint.TURN_LEFT -> Icons.AutoMirrored.Outlined.RotateLeft
    Hint.TURN_RIGHT -> Icons.AutoMirrored.Outlined.RotateRight
    Hint.TURN_SLOWER -> Icons.Outlined.HourglassBottom
    Hint.TILT_UP -> Icons.Outlined.ArrowCircleUp
    Hint.TILT_DOWN -> Icons.Outlined.ArrowCircleDown
    Hint.LAY_FLAT -> Icons.Outlined.Smartphone
    Hint.MOVE_AWAY -> Icons.Outlined.OpenWith
    Hint.WORKING -> Icons.Outlined.GraphicEq
    Hint.LOST_TRACKING -> Icons.Outlined.GpsOff
    Hint.NEED_CONFIRM -> Icons.Outlined.TouchApp
    else -> Icons.Outlined.PanTool
}

// ---------------- ekran ----------------

/**
 * Rehberli Keşif: meraklı kullanıcı için. Uygulama tek tek eylem söyler (kaldır, indir, yürü, dön, bırak),
 * sensörlerle eylemi denetler ve gereken veriyi kendisi kaydeder. Mühendis için elle ölçüm yolu ayrıca durur.
 */
@Composable
fun GuidedScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onDone: () -> Unit) {
    if (bundle == null) {
        NoProjectState(modifier) { onDone() }
        return
    }
    val context = LocalContext.current
    var mission by remember { mutableStateOf<Mission?>(null) }
    var useAr by remember { mutableStateOf<Boolean?>(null) }
    var voice by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<MissionSummary?>(null) }
    LaunchedEffect(Unit) { useAr = runCatching { arSupported(context) }.getOrDefault(false) }

    val m = mission
    val sum = summary
    when {
        sum != null && m != null -> SummaryView(m, sum, modifier, onAgain = { summary = null; mission = null }, onDone = onDone)
        m == null -> MissionPicker(c, useAr, voice, onVoice = { voice = it }, modifier) { mission = it }
        useAr == true -> ArSessionGate(modifier, onFallback = { useAr = false }, onClose = { mission = null }) { session ->
            GuidedRun(c, bundle, m, session, voice, onVoice = { voice = it }, modifier) { summary = it }
        }
        useAr == false -> GuidedRun(c, bundle, m, null, voice, onVoice = { voice = it }, modifier) { summary = it }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.auto_ar_checking)) }
    }
}

@Composable
private fun MissionPicker(
    c: AppContainer,
    useAr: Boolean?,
    voice: Boolean,
    onVoice: (Boolean) -> Unit,
    modifier: Modifier,
    onPick: (Mission) -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(
                when (useAr) {
                    true -> R.string.guide_method_ar
                    false -> R.string.guide_method_pdr
                    null -> R.string.auto_ar_checking
                },
            ),
            style = MaterialTheme.typography.labelMedium, color = NexColors.Cyan,
        )
        if (!c.sensorHub.hasHardware(Channel.PRESSURE) && useAr == false) {
            Text(stringResource(R.string.guide_no_height), style = MaterialTheme.typography.labelSmall, color = NexColors.Demo)
        }
        for (m in Missions.ALL) {
            Card(
                Modifier.fillMaxWidth().clickable(enabled = useAr != null) { onPick(m) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(missionTitle(m.id)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.guide_minutes, m.minutes, m.steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(missionDesc(m.id)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onVoice(!voice) }) { Icon(if (voice) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff, null) }
            Text(stringResource(if (voice) R.string.guide_voice_on else R.string.guide_voice_off), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.guide_principles), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Konum kaynağının paylaşılan son durumu (AR ya da adım). */
private class PositionState {
    @Volatile var position: Vec3? = null
    @Volatile var tracking = false
    @Volatile var arStatus: ArStatus? = ArStatus.SEARCHING
}

private fun liveReadingsOf(c: AppContainer): Map<Channel, Reading> {
    val m = c.sensorHub.readings.value.filterValues { r -> r.values.values.any { it != null } }.toMutableMap()
    c.micMeter.reading.value?.let { m[Channel.MICROPHONE] = it }
    return m
}

@Composable
private fun GuidedRun(
    c: AppContainer,
    bundle: ProjectBundle,
    mission: Mission,
    session: com.google.ar.core.Session?,
    voice: Boolean,
    onVoice: (Boolean) -> Unit,
    modifier: Modifier,
    onFinished: (MissionSummary) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val projectId = bundle.project.id
    val hub = c.sensorHub
    val pos = remember { PositionState() }
    val main = remember { Handler(Looper.getMainLooper()) }
    val alignment = remember { ScanAlignment(offsetZ = settings.pdrHoldHeightM, note = "start=reference (guided)") }

    // Kullanılabilir veri: motor gerekli sensör olmayan adımları dürüstçe "elle onay"a düşürür.
    val hasAccel = hub.hasHardware(Channel.ACCELEROMETER)
    val hasCompass = hasAccel && hub.hasHardware(Channel.MAGNETOMETER)
    val hasBaro = hub.hasHardware(Channel.PRESSURE)
    val available = remember {
        buildSet {
            if (hasAccel) add(Need.MOTION)
            if (hasCompass) add(Need.ORIENTATION)
            if (session != null || hasBaro) add(Need.HEIGHT)
            if (session != null || hasCompass) add(Need.POSITION)
        }
    }
    val engine = remember { GuideEngine(mission, available) }
    var frame by remember { mutableStateOf<GuideFrame?>(null) }
    var captured by remember { mutableIntStateOf(0) }
    var showWhy by remember { mutableStateOf(true) }
    var acousticBusy by remember { mutableStateOf(false) }
    var acousticProgress by remember { mutableStateOf(0f) }
    var finishing by remember { mutableStateOf(false) }
    val path = remember { mutableStateListOf<Vec3>() }
    val marks = remember { mutableStateListOf<Vec3>() }
    val t0 = remember { System.currentTimeMillis() }
    val lab = remember { AcousticLab(AcousticIo(context)) }

    // Sesli yönerge
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        var engineRef: TextToSpeech? = null
        engineRef = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) engineRef?.language = Locale.getDefault()
        }
        tts[0] = engineRef
        onDispose { runCatching { engineRef?.stop(); engineRef?.shutdown() } }
    }
    fun say(text: String) {
        if (voice) runCatching { tts[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guide") }
    }

    // Mikrofon izni (ses görevi için baştan istenir)
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> c.settings.setMicDenied(!ok) }
    LaunchedEffect(Unit) {
        if (mission.steps.any { it is GuideStep.PlaceFlat } && !c.micMeter.hasPermission()) micPermission.launch(Manifest.permission.RECORD_AUDIO)
        c.actions.startSessionNow(projectId, "guided:${mission.id}")
    }

    // Sensörler + ekran açık
    DisposableEffect(Unit) {
        hub.start(settings.sampling)
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            hub.accelListener = null
            hub.stop()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Adım modu (ARCore yoksa): adım + pusula
    var tracker by remember { mutableStateOf<PdrTracker?>(null) }
    var startAz by remember { mutableStateOf<Double?>(null) }
    if (session == null && hasCompass) {
        val detector = remember { PeakStepDetector() }
        LaunchedEffect(Unit) {
            var tries = 0
            while (hub.currentAzimuthDeg == null && tries < 50) { delay(100); tries++ }
            val az = hub.currentAzimuthDeg ?: return@LaunchedEffect
            startAz = az
            val t = PdrTracker(az, settings.pdrStepLengthM, settings.pdrHoldHeightM)
            tracker = t
            pos.position = t.position; pos.tracking = true
            path += t.position
        }
        DisposableEffect(Unit) {
            hub.accelListener = { mag, tMs ->
                if (detector.feed(mag, tMs)) main.post {
                    val t = tracker
                    val az = hub.currentAzimuthDeg
                    if (t != null && az != null) { t.onStep(az); pos.position = t.position; path += t.position }
                }
            }
            onDispose { hub.accelListener = null }
        }
    }

    // ARCore modu
    var renderer by remember { mutableStateOf<ArCapture?>(null) }
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }
    if (session != null) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(session) {
            val gv = GLSurfaceView(context).apply {
                preserveEGLContextOnPause = true
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            }
            val r = ArCapture(
                session = session,
                displayRotation = { displayRotationOf(context) },
                alignment = alignment,
                useDepth = session.config.depthMode == com.google.ar.core.Config.DepthMode.AUTOMATIC,
                onStatus = { st -> pos.arStatus = st },
                onPose = { p, _, tracking -> pos.tracking = tracking; if (tracking) pos.position = p },
            )
            gv.setRenderer(r)
            gv.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            renderer = r; glView = gv
            fun resume() { try { session.resume(); gv.onResume() } catch (e: Exception) { pos.arStatus = ArStatus.CAMERA_UNAVAILABLE } }
            fun pause() { gv.onPause(); runCatching { session.pause() } }
            val obs = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_RESUME -> resume()
                    Lifecycle.Event.ON_PAUSE -> pause()
                    else -> Unit
                }
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
            lifecycle.addObserver(obs)
            onDispose {
                lifecycle.removeObserver(obs)
                pause()
                runCatching { session.close() }
            }
        }
    }

    // Barometreden göreli yükseklik (adım modunda)
    val p0 = remember { arrayOfNulls<Double>(1) }
    fun baroHeight(): Double? {
        val p = hub.readings.value[Channel.PRESSURE]?.values?.get("value") ?: return null
        if (p0[0] == null) p0[0] = p
        return (p0[0]!! - p) * 8.43 // ~8,4 m/hPa (deniz seviyesi yakını)
    }

    fun signals(tS: Double): GuideSignals {
        val o = hub.orientation.value
        val acc = hub.readings.value[Channel.ACCELEROMETER]?.values?.get("magnitude")
        val magAcc = hub.readings.value[Channel.MAGNETOMETER]?.accuracy
        return if (session != null) {
            val p = pos.position
            GuideSignals(
                tS = tS, tracking = pos.tracking, position = p, facing = renderer?.lastFacing,
                heightM = if (pos.tracking) p?.z else null,
                pitchDeg = o?.pitchDeg, rollDeg = o?.rollDeg, azimuthDeg = o?.azimuthDeg,
                accelMagnitude = acc, magAccuracy = magAcc,
            )
        } else {
            val h = baroHeight()
            val t = tracker
            val sa = startAz
            val az = hub.currentAzimuthDeg
            val facing = if (sa != null && az != null) {
                val d = Math.toRadians(az - sa)
                Vec3(sin(d), cos(d), 0.0)
            } else null
            val p = t?.position?.let { Vec3(it.x, it.y, it.z + (h ?: 0.0)) }
            GuideSignals(
                tS = tS, tracking = t != null, position = p, facing = facing, heightM = h,
                pitchDeg = o?.pitchDeg, rollDeg = o?.rollDeg, azimuthDeg = o?.azimuthDeg,
                accelMagnitude = acc, magAccuracy = magAcc,
            )
        }
    }

    fun labelFor(cap: Capture): String {
        val step = mission.steps.firstOrNull { it.id == cap.stepId }
        val title = step?.let { context.getString(stepTitle(it)) } ?: cap.stepId
        return "$title ${cap.index + 1}"
    }

    fun handle(cap: Capture) {
        captured++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        renderer?.markCurrent()
        cap.position?.let { marks += it }
        val p = cap.position
        val src = when {
            p == null -> null
            session != null -> PositionSource.ARCORE
            else -> PositionSource.PDR
        }
        val conf = when {
            p == null -> null
            cap.estimated -> 0.2
            session != null -> 0.9
            else -> tracker?.confidence
        }
        val notes = context.getString(R.string.guide_point_note, context.getString(missionTitle(mission.id))) +
            if (cap.estimated) " · " + context.getString(R.string.guide_estimated) else ""
        when (cap.kind) {
            CaptureKind.POINT, CaptureKind.BASELINE -> {
                val drafts = c.actions.draftsFrom(liveReadingsOf(c), hub.orientation.value)
                scope.launch { c.actions.addPointNow(projectId, labelFor(cap), notes, p?.x, p?.y, p?.z, drafts, src, conf) }
            }
            CaptureKind.ACOUSTIC_SUITE -> {
                acousticBusy = true
                scope.launch {
                    val ok = try {
                        val point = c.actions.addPointNow(projectId, labelFor(cap), notes, p?.x, p?.y, p?.z, emptyList(), src, conf)
                        if (point == null || !c.micMeter.hasPermission()) {
                            false
                        } else {
                            val o = hub.orientation.value
                            val room = lab.room(RoomEstimator.of(bundle)) { acousticProgress = it / 3f }
                            val echo = lab.echo(hub.readings.value[Channel.AMBIENT_TEMPERATURE]?.values?.get("value")) { acousticProgress = 0.33f + it / 3f }
                            val level = lab.level(settings.splOffsetDb) { acousticProgress = 0.66f + it / 3f }
                            c.actions.addSamplesNow(projectId, point.id, listOf(lab.roomDraft(room, o), lab.echoDraft(echo, o)) + lab.levelDrafts(level, o))
                        }
                    } catch (e: Exception) {
                        false
                    }
                    acousticBusy = false
                    engine.completeExternal(ok)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            val r = renderer
            if (r != null) {
                r.capturing = false
                val res = r.result()
                if (res.points.isNotEmpty() || res.planes.isNotEmpty() || res.track.size > 1) {
                    c.actions.addScanNow(
                        projectId,
                        Scan(
                            id = "", projectId = projectId, method = ScanMethod.ARCORE,
                            startedAt = Instant.ofEpochMilli(t0).toString(), endedAt = Instant.now().toString(),
                            points = res.points, planes = res.planes, trajectory = res.track, alignment = alignment,
                            notes = "guided:${mission.id}",
                        ),
                    )
                }
            }
            c.actions.endSessionNow(projectId)
            onFinished(engine.summary())
        }
    }

    // Motor döngüsü (10 Hz)
    var lastSpoken by remember { mutableStateOf("") }
    var lastSpokenAt by remember { mutableStateOf(0L) }
    var lastStep by remember { mutableIntStateOf(-1) }
    LaunchedEffect(engine) {
        while (!finishing) {
            val f = engine.update(signals((System.currentTimeMillis() - t0) / 1000.0))
            frame = f
            f.captures.forEach { handle(it) }
            if (f.finished) { finish(); break }
            val step = f.step
            val now = System.currentTimeMillis()
            if (step != null && f.stepIndex != lastStep) {
                lastStep = f.stepIndex
                showWhy = true
                if (f.stepIndex > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val text = context.getString(stepTitle(step))
                say(text); lastSpoken = text; lastSpokenAt = now
            } else if (f.hint != null && now - lastSpokenAt > 3500) {
                val h = f.hint ?: Hint.KEEP_GOING
                val text = context.getString(hintText(h))
                if (text != lastSpoken || now - lastSpokenAt > 9000) { say(text); lastSpoken = text; lastSpokenAt = now }
            }
            delay(100)
        }
    }

    // ---------------- arayüz ----------------
    val f = frame
    val step = f?.step ?: mission.steps.first()
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(0.42f).background(Color(0xFF0B1424))) {
            val gv = glView
            if (session != null && gv != null) {
                AndroidView(factory = { gv }, modifier = Modifier.fillMaxSize())
            } else {
                MiniMap(path, marks, Modifier.fillMaxSize().padding(12.dp))
            }
            Row(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Text(
                    if (session != null) (pos.arStatus?.let { stringResource(arStatusText(it)) } ?: stringResource(R.string.auto_tracking))
                    else stringResource(R.string.guide_pdr_status, tracker?.steps ?: 0),
                    color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(6.dp),
                )
            }
            IconButton(onClick = { onVoice(!voice) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(if (voice) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff, null, tint = Color.White)
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(0.58f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.guide_step_of, (f?.stepIndex ?: 0) + 1, mission.steps.size, captured),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(stepTitle(step)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            HintBanner(if (acousticBusy) Hint.WORKING else f?.hint)
            LinearProgressIndicator(
                progress = { if (acousticBusy) acousticProgress else (f?.progress ?: 0.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            if (showWhy) {
                Text(
                    stringResource(stepWhy(step)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showWhy = false },
                )
            } else {
                TextButton(onClick = { showWhy = true }) { Text(stringResource(R.string.guide_why)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (f?.manual == true || step is GuideStep.Height || step is GuideStep.Mark) {
                    Button(onClick = { engine.confirm().forEach { handle(it) } }, enabled = !acousticBusy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.guide_done_manual))
                    }
                }
                OutlinedButton(onClick = { engine.skip() }, enabled = !acousticBusy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.guide_skip)) }
                OutlinedButton(onClick = { finish() }, enabled = !finishing, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.guide_stop)) }
            }
            if (f?.manual == true) {
                Text(stringResource(R.string.guide_manual_note), style = MaterialTheme.typography.labelSmall, color = NexColors.Demo)
            }
        }
    }
}

@Composable
private fun HintBanner(hint: Hint?) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val k by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "k")
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(hintIcon(hint), null, tint = NexColors.Cyan, modifier = Modifier.size(44.dp).scale(if (hint != null) k else 1f))
        Spacer(Modifier.width(14.dp))
        Text(
            hint?.let { stringResource(hintText(it)) } ?: stringResource(R.string.guide_good),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MiniMap(path: List<Vec3>, marks: List<Vec3>, modifier: Modifier) {
    Canvas(modifier) {
        val pts = path + marks
        if (pts.isEmpty()) return@Canvas
        val minX = minOf(pts.minOf { it.x }, -1.0); val maxX = maxOf(pts.maxOf { it.x }, 1.0)
        val minY = minOf(pts.minOf { it.y }, -1.0); val maxY = maxOf(pts.maxOf { it.y }, 1.0)
        val sc = minOf(size.width / (maxX - minX), size.height / (maxY - minY)).toFloat()
        fun o(p: Vec3) = Offset(((p.x - minX) * sc).toFloat(), size.height - ((p.y - minY) * sc).toFloat())
        if (path.size > 1) {
            val pth = Path()
            path.forEachIndexed { i, p -> if (i == 0) pth.moveTo(o(p).x, o(p).y) else pth.lineTo(o(p).x, o(p).y) }
            drawPath(pth, NexColors.Blue, style = Stroke(width = 4f))
        }
        for (m in marks) drawCircle(NexColors.Demo, 9f, o(m))
        path.lastOrNull()?.let { drawCircle(NexColors.Cyan, 12f, o(it)) }
    }
}

@Composable
private fun SummaryView(mission: Mission, s: MissionSummary, modifier: Modifier, onAgain: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.guide_summary_title, stringResource(missionTitle(mission.id))), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        val (qText, qColor) = when (s.quality) {
            Quality.GOOD -> R.string.guide_quality_good to NexColors.Cyan
            Quality.FAIR -> R.string.guide_quality_fair to NexColors.Demo
            Quality.POOR -> R.string.guide_quality_poor to NexColors.Danger
        }
        Text(stringResource(qText), style = MaterialTheme.typography.headlineSmall, color = qColor)
        SectionCard(null) {
            Text(stringResource(R.string.guide_summary_counts, s.auto, s.manual, s.skipped, s.captures), style = MaterialTheme.typography.bodyMedium)
            if (s.trackingLosses > 0) Text(stringResource(R.string.guide_summary_losses, s.trackingLosses), style = MaterialTheme.typography.bodySmall)
            for ((id, out) in s.outcomes) {
                val step = mission.steps.firstOrNull { it.id == id } ?: continue
                Text(
                    "• " + stringResource(stepTitle(step)) + " — " + stringResource(
                        when (out) {
                            StepOutcome.AUTO -> R.string.guide_out_auto
                            StepOutcome.MANUAL -> R.string.guide_out_manual
                            StepOutcome.SKIPPED -> R.string.guide_out_skipped
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            stringResource(
                when (s.quality) {
                    Quality.GOOD -> R.string.guide_tip_good
                    Quality.FAIR -> R.string.guide_tip_fair
                    Quality.POOR -> R.string.guide_tip_poor
                },
            ),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_see_space)) }
        OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_another)) }
        Text(stringResource(R.string.guide_honesty), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
