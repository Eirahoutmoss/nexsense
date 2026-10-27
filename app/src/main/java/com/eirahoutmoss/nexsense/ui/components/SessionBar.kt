package com.eirahoutmoss.nexsense.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.ui.Fmt

/** Açık oturum bilgisi ve başlat/bitir düğmesi. */
@Composable
fun SessionBar(c: AppContainer, bundle: ProjectBundle, modifier: Modifier = Modifier) {
    val open = bundle.openSession
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (open != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (open != null) stringResource(R.string.session_open, Fmt.time(open.startedAt)) else stringResource(R.string.session_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.session_hint),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open != null) {
                OutlinedButton(onClick = { c.actions.endSession(bundle.project.id) }) { Text(stringResource(R.string.session_end)) }
            } else {
                OutlinedButton(onClick = { c.actions.startSession(bundle.project.id) }) { Text(stringResource(R.string.session_start)) }
            }
        }
    }
}
