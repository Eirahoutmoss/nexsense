package com.eirahoutmoss.nexsense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.ui.components.DemoBadge
import com.eirahoutmoss.nexsense.ui.components.LoadingState
import com.eirahoutmoss.nexsense.ui.screens.AcousticScreen
import com.eirahoutmoss.nexsense.ui.screens.AnalysisScreen
import com.eirahoutmoss.nexsense.ui.screens.AutoScanScreen
import com.eirahoutmoss.nexsense.ui.screens.GuidedScreen
import com.eirahoutmoss.nexsense.ui.screens.HomeScreen
import com.eirahoutmoss.nexsense.ui.screens.ListenScreen
import com.eirahoutmoss.nexsense.ui.screens.LiveScreen
import com.eirahoutmoss.nexsense.ui.screens.MeasurementsScreen
import com.eirahoutmoss.nexsense.ui.screens.ReportScreen
import com.eirahoutmoss.nexsense.ui.screens.SettingsScreen
import com.eirahoutmoss.nexsense.ui.screens.SpaceScreen

enum class Tab(val label: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Outlined.Home),
    LIVE(R.string.tab_live, Icons.Outlined.Sensors),
    SPACE(R.string.tab_space, Icons.Outlined.Layers),
    MEASUREMENTS(R.string.tab_measurements, Icons.AutoMirrored.Outlined.ListAlt),
    REPORT(R.string.tab_report, Icons.Outlined.SaveAlt),
}

/** Alt sekmelerin üstünde açılan tam ekranlar. */
enum class Overlay(val title: Int) {
    SETTINGS(R.string.settings_title),
    AUTO_SCAN(R.string.auto_title),
    GUIDED(R.string.guide_title),
    ACOUSTIC(R.string.acoustic_title),
    ANALYSIS(R.string.analysis_title),
    LISTEN(R.string.listen_title),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexSenseRoot(c: AppContainer) {
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val loaded by c.actions.loaded.collectAsStateWithLifecycle()
    val revision by c.repository.revision.collectAsStateWithLifecycle()
    // Etkin projenin güncel hâli: her depo değişikliğinde yeniden okunur.
    val bundle = remember(revision, settings.activeProjectId, loaded) {
        settings.activeProjectId?.let { c.repository.get(it) }
    }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    val showSettings = overlay != null
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        c.actions.messages.collect { snackbar.showSnackbar(context.getString(it.resId, it.arg)) }
    }
    BackHandler(enabled = showSettings) { overlay = null }
    BackHandler(enabled = !showSettings && tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { overlay = null }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            overlay?.let { stringResource(it.title) } ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (!showSettings && bundle != null) {
                            Text(
                                bundle.project.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (!showSettings && bundle?.hasDemoData == true) DemoBadge(Modifier.padding(end = 4.dp))
                    if (!showSettings) {
                        IconButton(onClick = { overlay = Overlay.SETTINGS }) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    for (t in Tab.entries) {
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        val open: (Overlay) -> Unit = { overlay = it }
        val back: () -> Unit = { overlay = null; tab = Tab.SPACE }
        when {
            overlay == Overlay.SETTINGS -> SettingsScreen(c, m)
            !loaded -> LoadingState(m)
            overlay == Overlay.AUTO_SCAN -> AutoScanScreen(c, bundle, m, onDone = back)
            overlay == Overlay.GUIDED -> GuidedScreen(c, bundle, m, onDone = back)
            overlay == Overlay.ACOUSTIC -> AcousticScreen(c, bundle, m, onDone = back)
            overlay == Overlay.ANALYSIS -> AnalysisScreen(c, bundle, m, onDone = { overlay = null })
            overlay == Overlay.LISTEN -> ListenScreen(c, bundle, m)
            else -> when (tab) {
                Tab.HOME -> HomeScreen(c, bundle, m, onGo = { tab = it })
                Tab.LIVE -> LiveScreen(c, bundle, m, onGo = { tab = it }, onOverlay = open)
                Tab.SPACE -> SpaceScreen(c, bundle, m, onGo = { tab = it }, onOverlay = open)
                Tab.MEASUREMENTS -> MeasurementsScreen(c, bundle, m, onGo = { tab = it })
                Tab.REPORT -> ReportScreen(c, bundle, m, onGo = { tab = it }, onOverlay = open)
            }
        }
    }
}

