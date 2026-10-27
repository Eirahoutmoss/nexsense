package com.eirahoutmoss.nexsense.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.ar.ArCapture
import com.eirahoutmoss.nexsense.ar.ArStatus
import com.eirahoutmoss.nexsense.core.model.PositionSource
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.model.Scan
import com.eirahoutmoss.nexsense.core.model.ScanAlignment
import com.eirahoutmoss.nexsense.core.model.ScanMethod
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.sensors.Channel
import com.eirahoutmoss.nexsense.core.sensors.Reading
import com.eirahoutmoss.nexsense.core.spatial.AutoRecordPolicy
import com.eirahoutmoss.nexsense.core.spatial.FrameMath
import com.eirahoutmoss.nexsense.core.spatial.PdrTracker
import com.eirahoutmoss.nexsense.core.spatial.PeakStepDetector
import com.eirahoutmoss.nexsense.core.spatial.TrackRecorder
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.components.SectionCard
import com.eirahoutmoss.nexsense.ui.theme.NexColors
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

private enum class AutoMode { AR, PDR }

private sealed interface ArAvail {
    data object Checking : ArAvail
    data object Supported : ArAvail
    data object Unsupported : ArAvail
}

/** Kullanıcının seçtiği başlangıç: referans noktası (0,0,0) ya da var olan yerleşik bir nokta. */
private data class StartChoice(val pointId: String?, val heightM: Double, val yawDeg: Double)

/**
 * Otomatik ölçüm: "Başlat" denince telefon konumunu kendisi izler ve her [stepM] metrede bir ölçüm noktası
 * kaydeder. Kullanıcı koordinat yazmaz.
 * - ARCore modu: görsel-ataletsel izleme (cm düzeyi), nokta bulutu, düzlemler.
 * - Adım modu (ARCore yoksa): adım sayımı + pusula; kaba tahmin, "tahmini konum" olarak etiketlenir.
 */
@Composable
fun AutoScanScreen(c: AppContainer, bundle: ProjectBundle?, modifier: Modifier, onDone: () -> Unit) {
    if (bundle == null) {
        NoProjectState(modifier) { onDone() }
        return
    }
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    var avail by remember { mutableStateOf<ArAvail>(ArAvail.Checking) }
    var mode by remember { mutableStateOf<AutoMode?>(null) }
    var running by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(StartChoice(null, settings.pdrHoldHeightM, 0.0)) }
    var stepM by remember { mutableStateOf(settings.autoStepM) }

    LaunchedEffect(Unit) {
        var a = ArCoreApk.getInstance().checkAvailability(context)
        var tries = 0
        while (a.isTransient && tries < 20) { delay(200); a = ArCoreApk.getInstance().checkAvailability(context); tries++ }
        avail = if (a.isSupported) ArAvail.Supported else ArAvail.Unsupported
        if (mode == null) mode = if (a.isSupported) AutoMode.AR else AutoMode.PDR
    }

    // Sensörler bu ekran boyunca açık (noktalara değer yazılsın).
    DisposableEffect(Unit) {
        c.sensorHub.start(settings.sampling)
        onDispose { c.sensorHub.stop(); c.micMeter.stop() }
    }

    if (!running) {
        AutoSetup(
            c = c, bundle = bundle, avail = avail, mode = mode, onMode = { mode = it },
            start = start, onStart = { start = it }, stepM = stepM, onStep = { stepM = it; c.settings.setAutoStep(it) },
            modifier = modifier,
            onGo = { running = true },
        )
    } else {
        when (mode) {
            AutoMode.AR -> ArRun(c, bundle, start, stepM, modifier, onFinish = { running = false; onDone() }, onFallback = { mode = AutoMode.PDR; running = false })
            else -> PdrRun(c, bundle, start, stepM, modifier, onFinish = { running = false; onDone() })
        }
    }
}

@Composable
private fun AutoSetup(
    c: AppContainer,
    bundle: ProjectBundle,
    avail: ArAvail,
    mode: AutoMode?,
    onMode: (AutoMode) -> Unit,
    start: StartChoice,
    onStart: (StartChoice) -> Unit,
    stepM: Double,
    onStep: (Double) -> Unit,
    modifier: Modifier,
    onGo: () -> Unit,
) {
    val placed = bundle.points.filter { it.isPlaced }
    var heightText by remember { mutableStateOf(Fmt.num(start.heightM, 2)) }
    var yawText by remember { mutableStateOf(Fmt.num(start.yawDeg, 0)) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.auto_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.auto_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionCard(stringResource(R.string.auto_mode)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == AutoMode.AR, enabled = avail == ArAvail.Supported, onClick = { onMode(AutoMode.AR) },
                    leadingIcon = { Icon(Icons.Outlined.ViewInAr, null) }, label = { Text(stringResource(R.string.auto_mode_ar)) },
                )
                FilterChip(
                    selected = mode == AutoMode.PDR, onClick = { onMode(AutoMode.PDR) },
                    leadingIcon = { Icon(Icons.Outlined.DirectionsWalk, null) }, label = { Text(stringResource(R.string.auto_mode_pdr)) },
                )
            }
            Text(
                when {
                    avail == ArAvail.Checking -> stringResource(R.string.auto_ar_checking)
                    avail == ArAvail.Unsupported -> stringResource(R.string.auto_ar_unsupported)
                    mode == AutoMode.AR -> stringResource(R.string.auto_ar_note)
                    else -> stringResource(R.string.auto_pdr_note)
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(stringResource(R.string.auto_start_title)) {
            Text(stringResource(R.string.auto_start_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = start.pointId == null, onClick = { onStart(start.copy(pointId = null)) }, label = { Text(stringResource(R.string.auto_start_reference)) })
            }
            if (placed.isNotEmpty()) {
                Text(stringResource(R.string.auto_start_point), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in placed.takeLast(12)) {
                        FilterChip(selected = start.pointId == p.id, onClick = { onStart(start.copy(pointId = p.id)) }, label = { Text(p.label) })
                    }
                }
            }
            if (start.pointId == null) {
                OutlinedTextField(
                    heightText,
                    { heightText = it; Fmt.parseOptional(it)?.takeIf { v -> !v.isNaN() && v in 0.0..3.0 }?.let { v -> onStart(start.copy(heightM = v)) } },
                    label = { Text(stringResource(R.string.auto_start_height)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                yawText,
                { yawText = it; Fmt.parseOptional(it)?.takeIf { v -> !v.isNaN() }?.let { v -> onStart(start.copy(yawDeg = v)) } },
                label = { Text(stringResource(R.string.auto_start_yaw)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(stringResource(R.string.auto_step_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (v in listOf(0.25, 0.5, 1.0, 2.0)) {
                    FilterChip(selected = stepM == v, onClick = { onStep(v) }, label = { Text("${Fmt.num(v, 2)} m") })
                }
            }
            Text(stringResource(R.string.auto_step_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(onClick = onGo, enabled = mode != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.auto_start))
        }
        Text(stringResource(R.string.auto_privacy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Başlangıç seçimini tarama hizalamasına çevirir. */
private fun alignmentFor(bundle: ProjectBundle, s: StartChoice, method: ScanMethod): ScanAlignment {
    val p = s.pointId?.let { id -> bundle.points.firstOrNull { it.id == id && it.isPlaced } }
    return if (p != null) {
        ScanAlignment(
            startPointId = p.id, offsetX = p.x!!, offsetY = p.y!!,
            // ARCore: telefon başlangıçta noktanın tam konumunda. Adım modu: z ayrıca tutma yüksekliğidir.
            offsetZ = if (method == ScanMethod.ARCORE) p.z!! else 0.0,
            yawDeg = s.yawDeg, note = "start=point",
        )
    } else {
        ScanAlignment(offsetZ = if (method == ScanMethod.ARCORE) s.heightM else 0.0, yawDeg = s.yawDeg, note = "start=reference")
    }
}

/** O an okunan sensör değerleri (yalnızca gerçekten okunanlar). */
private fun liveReadings(c: AppContainer): Map<Channel, Reading> {
    val m = c.sensorHub.readings.value.filterValues { r -> r.values.values.any { it != null } }.toMutableMap()
    c.micMeter.reading.value?.let { m[Channel.MICROPHONE] = it }
    return m
}

@Composable
private fun ArRun(
    c: AppContainer,
    bundle: ProjectBundle,
    start: StartChoice,
    stepM: Double,
    modifier: Modifier,
    onFinish: () -> Unit,
    onFallback: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var session by remember { mutableStateOf<Session?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var needPermission by remember { mutableStateOf(false) }
    var installRequested by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { needPermission = false; retry++ } else error = R.string.auto_err_camera
    }

    LaunchedEffect(retry) {
        if (activity == null) { error = R.string.auto_err_start; return@LaunchedEffect }
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needPermission = true
            return@LaunchedEffect
        }
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return@LaunchedEffect }
                else -> Unit
            }
            val s = Session(context)
            val cfg = Config(s).apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                focusMode = Config.FocusMode.AUTO
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.DISABLED
                depthMode = if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            }
            s.configure(cfg)
            session = s
        } catch (e: Exception) {
            error = R.string.auto_err_start
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && installRequested && session == null) retry++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val s = session
    when {
        error != null -> Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(error!!), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onFallback) { Text(stringResource(R.string.auto_use_pdr)) }
            TextButton(onClick = onFinish) { Text(stringResource(R.string.close)) }
        }
        needPermission -> Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.auto_camera_why), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.auto_camera_allow)) }
            TextButton(onClick = onFallback) { Text(stringResource(R.string.auto_use_pdr)) }
        }
        s == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.auto_ar_preparing)) }
        else -> ArLive(c, bundle, s, alignmentFor(bundle, start, ScanMethod.ARCORE), stepM, modifier, onFinish)
    }
}

@Composable
private fun ArLive(
    c: AppContainer,
    bundle: ProjectBundle,
    session: Session,
    alignment: ScanAlignment,
    stepM: Double,
    modifier: Modifier,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val projectId = bundle.project.id
    var status by remember { mutableStateOf<ArStatus?>(ArStatus.SEARCHING) }
    var recorded by remember { mutableIntStateOf(0) }
    var cloud by remember { mutableIntStateOf(0) }
    var planes by remember { mutableIntStateOf(0) }
    var length by remember { mutableStateOf(0.0) }
    var paused by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var lastPose by remember { mutableStateOf<Vec3?>(null) }
    val policy = remember { AutoRecordPolicy(stepM = stepM) }
    val startedAt = remember { Instant.now().toString() }
    val main = remember { Handler(Looper.getMainLooper()) }

    fun record(p: Vec3, renderer: ArCapture) {
        val n = ++recorded
        renderer.markCurrent()
        val drafts = c.actions.draftsFrom(liveReadings(c), c.sensorHub.orientation.value)
        scope.launch {
            c.actions.addPointNow(projectId, "A$n", "", p.x, p.y, p.z, drafts, PositionSource.ARCORE, 0.9)
        }
    }

    val glView = remember {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        }
    }
    val rendererHolder = remember { arrayOfNulls<ArCapture>(1) }
    val renderer = remember {
        ArCapture(
            session = session,
            displayRotation = { displayRotation(context) },
            alignment = alignment,
            useDepth = session.config.depthMode == Config.DepthMode.AUTOMATIC,
            onStatus = { st -> main.post { status = st } },
            onPose = { p, t, tracking ->
                main.post {
                    if (tracking) lastPose = p
                    val r = rendererHolder[0]
                    if (r != null && !paused && !saving && policy.offer(p, t, tracking)) record(p, r)
                }
            },
        ).also {
            rendererHolder[0] = it
            glView.setRenderer(it)
            glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    LaunchedEffect(Unit) { c.actions.startSessionNow(projectId, "ARCore") }
    LaunchedEffect(Unit) {
        while (true) { cloud = renderer.pointCount; planes = renderer.planeCount; length = renderer.trackLength; delay(400) }
    }

    DisposableEffect(lifecycle) {
        fun resume() {
            try { session.resume(); glView.onResume() } catch (e: Exception) { status = ArStatus.CAMERA_UNAVAILABLE }
        }
        fun pause() { glView.onPause(); runCatching { session.pause() } }
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                else -> Unit
            }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        lifecycle.addObserver(obs)
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            lifecycle.removeObserver(obs)
            pause()
            runCatching { session.close() }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun finish() {
        if (saving) return
        saving = true
        renderer.capturing = false
        val res = renderer.result()
        scope.launch {
            if (res.points.isNotEmpty() || res.planes.isNotEmpty() || res.track.isNotEmpty()) {
                c.actions.addScanNow(
                    projectId,
                    Scan(
                        id = "", projectId = projectId, method = ScanMethod.ARCORE,
                        startedAt = startedAt, endedAt = Instant.now().toString(),
                        points = res.points, planes = res.planes, trajectory = res.track, alignment = alignment,
                        notes = "depth=${session.config.depthMode}",
                    ),
                )
            }
            c.actions.endSessionNow(projectId)
            onFinish()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopCenter).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        status?.let { stringResource(arStatusText(it)) } ?: stringResource(if (paused) R.string.auto_paused else R.string.auto_tracking),
                        color = if (status == null) NexColors.Cyan else NexColors.Demo, style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(stringResource(R.string.auto_counters, recorded, cloud, planes, Fmt.num(length, 1)), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    lastPose?.let { Text(Fmt.coord(it.x, it.y, it.z).orEmpty(), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { paused = !paused; renderer.capturing = !paused }, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (paused) R.string.auto_resume else R.string.auto_pause), color = Color.White)
            }
            OutlinedButton(onClick = { lastPose?.let { record(it, renderer) } }, enabled = lastPose != null && status == null, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_record_now), color = Color.White)
            }
            Button(onClick = { finish() }, enabled = !saving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.auto_finish)) }
        }
    }
}

fun arStatusText(s: ArStatus): Int = when (s) {
    ArStatus.SEARCHING -> R.string.ar_searching
    ArStatus.TOO_DARK -> R.string.ar_dark
    ArStatus.FEW_FEATURES -> R.string.ar_features
    ArStatus.TOO_FAST -> R.string.ar_fast
    ArStatus.CAMERA_UNAVAILABLE -> R.string.ar_camera
    ArStatus.GPU_UNSUPPORTED -> R.string.ar_gpu
    ArStatus.ERROR -> R.string.auto_err_start
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int {
    val d = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display
    else (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    return d?.rotation ?: Surface.ROTATION_0
}

@Composable
private fun PdrRun(
    c: AppContainer,
    bundle: ProjectBundle,
    start: StartChoice,
    stepM: Double,
    modifier: Modifier,
    onFinish: () -> Unit,
) {
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val projectId = bundle.project.id
    val alignment = remember { alignmentFor(bundle, start, ScanMethod.PDR) }
    var tracker by remember { mutableStateOf<PdrTracker?>(null) }
    val track = remember { TrackRecorder(minStepM = 0.05) }
    val policy = remember { AutoRecordPolicy(stepM = stepM, minIntervalS = 0.0) }
    val detector = remember { PeakStepDetector() }
    var steps by remember { mutableIntStateOf(0) }
    var recorded by remember { mutableIntStateOf(0) }
    val path = remember { mutableStateListOf<Vec3>() }
    val marks = remember { mutableStateListOf<Vec3>() }
    var saving by remember { mutableStateOf(false) }
    var noCompass by remember { mutableStateOf(false) }
    val startedAt = remember { Instant.now().toString() }
    val t0 = remember { System.currentTimeMillis() }
    val main = remember { Handler(Looper.getMainLooper()) }

    fun aligned(t: PdrTracker): Vec3 = FrameMath.align(t.position, alignment)

    fun record(p: Vec3, conf: Double) {
        val n = ++recorded
        marks += p
        val drafts = c.actions.draftsFrom(liveReadings(c), c.sensorHub.orientation.value)
        scope.launch { c.actions.addPointNow(projectId, "S$n", "", p.x, p.y, p.z, drafts, PositionSource.PDR, conf) }
    }

    LaunchedEffect(Unit) {
        c.actions.startSessionNow(projectId, "PDR")
        // Pusula oturana kadar kısa bekleme
        var tries = 0
        while (c.sensorHub.currentAzimuthDeg == null && tries < 30) { delay(100); tries++ }
        val az = c.sensorHub.currentAzimuthDeg
        if (az == null) { noCompass = true; return@LaunchedEffect }
        val t = PdrTracker(startHeadingDeg = az, stepLengthM = settings.pdrStepLengthM, holdHeightM = settings.pdrHoldHeightM)
        tracker = t
        val p0 = aligned(t)
        path += p0
        track.offer(0.0, p0)
        if (policy.offer(p0, 0.0, true)) record(p0, t.confidence)
    }

    DisposableEffect(Unit) {
        c.sensorHub.accelListener = { mag, tMs ->
            if (detector.feed(mag, tMs)) {
                main.post {
                    val t = tracker
                    val az = c.sensorHub.currentAzimuthDeg
                    if (t != null && az != null && !saving) {
                        t.onStep(az)
                        steps = t.steps
                        val p = aligned(t)
                        val ts = (System.currentTimeMillis() - t0) / 1000.0
                        path += p
                        track.offer(ts, p)
                        if (policy.offer(p, ts, true)) record(p, t.confidence)
                    }
                }
            }
        }
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            c.sensorHub.accelListener = null
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun finish() {
        if (saving) return
        saving = true
        scope.launch {
            val tr = track.points()
            if (tr.size >= 2) {
                c.actions.addScanNow(
                    projectId,
                    Scan(
                        id = "", projectId = projectId, method = ScanMethod.PDR, startedAt = startedAt, endedAt = Instant.now().toString(),
                        trajectory = tr, alignment = alignment,
                        notes = "step=${settings.pdrStepLengthM} m, height=${settings.pdrHoldHeightM} m (assumed)",
                    ),
                )
            }
            c.actions.endSessionNow(projectId)
            onFinish()
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.auto_mode_pdr), style = MaterialTheme.typography.titleLarge)
        if (noCompass) {
            Text(stringResource(R.string.auto_pdr_no_compass), color = MaterialTheme.colorScheme.error)
        } else {
            Text(stringResource(R.string.auto_pdr_running), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.auto_pdr_counters, steps, recorded, Fmt.num(track.length, 1)), style = MaterialTheme.typography.bodyMedium)
        tracker?.let { Text(stringResource(R.string.auto_pdr_confidence, Fmt.num(it.confidence * 100, 0)), style = MaterialTheme.typography.labelSmall, color = NexColors.Demo) }
        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0B1424), RoundedCornerShape(12.dp)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                val pts = path.toList()
                if (pts.isEmpty()) return@Canvas
                val minX = minOf(pts.minOf { it.x }, -1.0); val maxX = maxOf(pts.maxOf { it.x }, 1.0)
                val minY = minOf(pts.minOf { it.y }, -1.0); val maxY = maxOf(pts.maxOf { it.y }, 1.0)
                val sc = minOf(size.width / (maxX - minX), size.height / (maxY - minY)).toFloat()
                fun o(p: Vec3) = Offset(((p.x - minX) * sc).toFloat(), size.height - ((p.y - minY) * sc).toFloat())
                val pth = Path()
                pts.forEachIndexed { i, p -> if (i == 0) pth.moveTo(o(p).x, o(p).y) else pth.lineTo(o(p).x, o(p).y) }
                drawPath(pth, NexColors.Blue, style = Stroke(width = 4f))
                for (m in marks) drawCircle(NexColors.Demo, 9f, o(m))
                drawCircle(NexColors.Cyan, 12f, o(pts.last()))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tracker?.let { record(aligned(it), it.confidence) } }, enabled = tracker != null && !saving, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_record_now))
            }
            Button(onClick = { finish() }, enabled = !saving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.auto_finish)) }
        }
    }
}
