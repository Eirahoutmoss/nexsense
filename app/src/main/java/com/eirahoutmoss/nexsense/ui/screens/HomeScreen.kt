package com.eirahoutmoss.nexsense.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nexsense.AppContainer
import com.eirahoutmoss.nexsense.R
import com.eirahoutmoss.nexsense.core.model.ProjectBundle
import com.eirahoutmoss.nexsense.core.store.ProjectSummary
import com.eirahoutmoss.nexsense.ui.Fmt
import com.eirahoutmoss.nexsense.ui.Tab
import com.eirahoutmoss.nexsense.ui.components.ConfirmDialog
import com.eirahoutmoss.nexsense.ui.components.DemoBadge
import com.eirahoutmoss.nexsense.ui.components.EmptyState
import com.eirahoutmoss.nexsense.ui.components.Pill
import com.eirahoutmoss.nexsense.ui.components.ProjectDialog

@Composable
fun HomeScreen(c: AppContainer, active: ProjectBundle?, modifier: Modifier, onGo: (Tab) -> Unit) {
    val projects by c.repository.summaries.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }

    if (projects.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.FolderOpen,
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
            modifier = modifier,
        ) {
            Button(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.project_new))
            }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = { c.actions.addDemoProject() }) {
                Icon(Icons.Outlined.Science, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.demo_add))
            }
        }
    } else {
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.slogan), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.home_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { creating = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.project_new))
                    }
                    OutlinedButton(onClick = { onGo(Tab.LIVE) }, enabled = active != null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Sensors, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.measure_start))
                    }
                }
            }
            item { Text(stringResource(R.string.home_projects), style = MaterialTheme.typography.titleMedium) }
            items(projects, key = { it.project.id }) { s ->
                ProjectCard(
                    s = s,
                    isActive = s.project.id == active?.project?.id,
                    onOpen = { c.actions.openProject(s.project.id); onGo(Tab.SPACE) },
                    onRename = { renaming = s },
                    onDelete = { deleting = s },
                )
            }
        }
    }

    if (creating) {
        ProjectDialog(
            title = stringResource(R.string.project_new),
            initialName = "", initialDescription = "",
            onSave = { n, d -> c.actions.createProject(n, d) },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { s ->
        ProjectDialog(
            title = stringResource(R.string.project_rename),
            initialName = s.project.name, initialDescription = s.project.description,
            onSave = { n, d -> c.actions.renameProject(s.project.id, n, d) },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.project_delete),
            text = stringResource(R.string.project_delete_confirm, s.project.name, s.pointCount),
            confirm = stringResource(R.string.delete),
            onConfirm = { c.actions.deleteProject(s.project.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ProjectCard(s: ProjectSummary, isActive: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.hasDemoData) DemoBadge()
                }
                if (s.project.description.isNotBlank()) {
                    Text(s.project.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    stringResource(R.string.project_stats, s.pointCount, s.sessionCount, Fmt.dateTime(s.project.updatedAt)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isActive) Pill(stringResource(R.string.project_active))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.project_rename)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
