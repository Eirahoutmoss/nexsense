package com.eirahoutmoss.nexsense.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.BuildConfig
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.model.SCHEMA_VERSION
import com.eirahoutmoss.nexsense.core.update.UpdateCandidate
import com.eirahoutmoss.nexsense.settings.SamplingRate
import com.eirahoutmoss.nexsense.settings.ThemeMode
import com.eirahoutmoss.nexsense.ui.components.KeyValue
import com.eirahoutmoss.nexsense.ui.components.SectionCard
import com.eirahoutmoss.nexsense.update.UpdateState

@Composable
fun SettingsScreen(c: AppContainer, modifier: Modifier) {
    val context = LocalContext.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val update by c.updateManager.state.collectAsStateWithLifecycle()
    val micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(stringResource(R.string.settings_theme)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in ThemeMode.entries) {
                    FilterChip(
                        selected = settings.theme == m,
                        onClick = { c.settings.setTheme(m) },
                        label = {
                            Text(
                                stringResource(
                                    when (m) {
                                        ThemeMode.DARK -> R.string.theme_dark
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }

        SectionCard(stringResource(R.string.settings_sampling)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in SamplingRate.entries) {
                    FilterChip(
                        selected = settings.sampling == r,
                        onClick = { c.settings.setSampling(r) },
                        label = {
                            Text(
                                stringResource(
                                    when (r) {
                                        SamplingRate.LOW -> R.string.sampling_low
                                        SamplingRate.NORMAL -> R.string.sampling_normal
                                        SamplingRate.HIGH -> R.string.sampling_high
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Text(stringResource(R.string.sampling_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(stringResource(R.string.settings_permissions)) {
            KeyValue(
                stringResource(R.string.ch_microphone),
                stringResource(if (micGranted) R.string.permission_granted else R.string.permission_not_granted),
            )
            Text(stringResource(R.string.permissions_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text(stringResource(R.string.open_settings)) }
        }

        SectionCard(stringResource(R.string.settings_demo)) {
            Text(stringResource(R.string.demo_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { c.actions.addDemoProject() }) {
                Icon(Icons.Outlined.Science, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.demo_add))
            }
        }

        SectionCard(stringResource(R.string.settings_update)) {
            Text(stringResource(R.string.update_installed, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            UpdateBody(
                state = update,
                onCheck = { c.updateManager.check() },
                onInstall = { c.updateManager.downloadAndInstall(it) },
                onOpenPermission = { context.startActivity(c.updateManager.installPermissionIntent()) },
            )
            Text(stringResource(R.string.update_security_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(stringResource(R.string.settings_about)) {
            KeyValue(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
            KeyValue(stringResource(R.string.about_schema), SCHEMA_VERSION.toString())
            KeyValue(stringResource(R.string.about_source), "github.com/Eirahoutmoss/nexsense")
            Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpdateBody(
    state: UpdateState,
    onCheck: () -> Unit,
    onInstall: (UpdateCandidate) -> Unit,
    onOpenPermission: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> CheckButton(onCheck)
        UpdateState.Checking -> {
            Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator()
        }
        UpdateState.UpToDate -> {
            Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodySmall)
            CheckButton(onCheck)
        }
        is UpdateState.Available -> {
            Text(stringResource(R.string.update_available, state.candidate.version.toString()), style = MaterialTheme.typography.bodyMedium)
            if (state.candidate.notes.isNotBlank()) Text(state.candidate.notes.take(600), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onInstall(state.candidate) }) { Text(stringResource(R.string.update_install)) }
        }
        is UpdateState.Downloading -> {
            Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { state.progress })
        }
        UpdateState.Verifying -> {
            Text(stringResource(R.string.update_verifying), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator()
        }
        UpdateState.Installing -> Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.bodySmall)
        is UpdateState.NeedsInstallPermission -> {
            Text(stringResource(R.string.update_needs_permission), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenPermission) { Text(stringResource(R.string.update_open_permission)) }
                OutlinedButton(onClick = { onInstall(state.candidate) }) { Text(stringResource(R.string.update_install)) }
            }
        }
        is UpdateState.Failed -> {
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            CheckButton(onCheck)
        }
    }
}

@Composable
private fun CheckButton(onCheck: () -> Unit) {
    OutlinedButton(onClick = onCheck) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.update_check))
        }
    }
}
