package com.eirahoutmoss.nexsense.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eirahoutmoss.nexsense.R
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import kotlinx.coroutines.delay

/** ARCore bu cihazda destekleniyor mu (geçici yanıtlar için kısa bekleme ile). */
suspend fun arSupported(context: Context): Boolean {
    var a = ArCoreApk.getInstance().checkAvailability(context)
    var tries = 0
    while (a.isTransient && tries < 20) { delay(200); a = ArCoreApk.getInstance().checkAvailability(context); tries++ }
    return a.isSupported
}

/**
 * ARCore oturumunu hazırlar: kamera izni, gerekirse ARCore kurulumu, yapılandırma (düzlemler + destekleniyorsa derinlik).
 * Hazır olunca [content] çağrılır. Hata ya da ret durumunda [onFallback] (adım tabanlı yöntem) önerilir.
 */
@Composable
fun ArSessionGate(
    modifier: Modifier,
    onFallback: () -> Unit,
    onClose: () -> Unit,
    content: @Composable (Session) -> Unit,
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
            if (ArCoreApk.getInstance().requestInstall(activity, !installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true
                return@LaunchedEffect
            }
            val s = Session(context)
            s.configure(
                Config(s).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    depthMode = if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                },
            )
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
    val err = error
    when {
        err != null -> Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(err), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onFallback) { Text(stringResource(R.string.auto_use_pdr)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
        needPermission -> Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.auto_camera_why), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.auto_camera_allow)) }
            TextButton(onClick = onFallback) { Text(stringResource(R.string.auto_use_pdr)) }
        }
        s == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.auto_ar_preparing)) }
        else -> content(s)
    }
}

@Suppress("DEPRECATION")
fun displayRotationOf(context: Context): Int {
    val d = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display
    else (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    return d?.rotation ?: Surface.ROTATION_0
}
