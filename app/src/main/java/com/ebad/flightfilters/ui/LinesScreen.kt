package com.ebad.flightfilters.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ebad.flightfilters.data.Preset
import com.ebad.flightfilters.ui.theme.LocalLineInks

/** Saved searches, each drawn as its own short line: origin roundels running into one terminus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinesScreen(vm: AppViewModel, onRun: () -> Unit, onOpenPlan: () -> Unit, onOpenSettings: () -> Unit) {
    val presets by vm.presets.collectAsState()
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lines", style = MaterialTheme.typography.titleLarge) },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") } },
            )
        },
    ) { pad ->
        if (presets.isEmpty()) {
            Column(Modifier.padding(pad)) {
                EmptyLine(
                    "No saved lines yet",
                    "Set up airports, windows and filters in Plan, then save them as a line to run again with one tap.",
                ) { Button(onClick = onOpenPlan) { Text("Open Plan") } }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp)) {
            items(presets, key = { it.id }) { p ->
                PresetRow(
                    p,
                    onRun = { vm.applyPreset(p); onRun() },
                    onRename = { renaming = p.id },
                    onDelete = { deleting = p.id },
                )
                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    renaming?.let { id ->
        val p = presets.firstOrNull { it.id == id } ?: return@let
        var name by remember(id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename line") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { vm.renamePreset(id, name); renaming = null }, enabled = name.isNotBlank()) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { id ->
        val p = presets.firstOrNull { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${p.name}”?") },
            text = { Text("The saved airports, windows and filters for this line will be removed. Your current plan stays as it is.") },
            confirmButton = {
                TextButton(onClick = { vm.deletePreset(id); deleting = null }) { Text("Delete line", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun PresetRow(p: Preset, onRun: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val inks = LocalLineInks.current
    val surface = MaterialTheme.colorScheme.surface
    val porcelain = MaterialTheme.colorScheme.onSurface
    var menu by remember { mutableStateOf(false) }
    val origins = p.query.origins.filter { it.enabled }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRun).padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                origins.forEachIndexed { i, o ->
                    LineRoundel(o.iata, inks.forIndex(p.query.origins.indexOf(o)))
                    if (i < origins.lastIndex) Spacer(Modifier.width(4.dp))
                }
                Canvas(Modifier.weight(1f).height(16.dp).padding(horizontal = 6.dp)) {
                    val y = size.height / 2
                    // The line runs in its first origin's own ink, as it does on the network.
                    val ink = origins.firstOrNull()?.let { inks.forIndex(p.query.origins.indexOf(it)) } ?: porcelain
                    drawLine(ink, Offset(0f, y), Offset(size.width - 8.dp.toPx(), y), 4.dp.toPx(), StrokeCap.Round)
                    drawCircle(surface, 7.dp.toPx(), Offset(size.width - 7.dp.toPx(), y))
                    drawCircle(porcelain, 7.dp.toPx(), Offset(size.width - 7.dp.toPx(), y), style = Stroke(2.5.dp.toPx()))
                }
                Text(p.query.dests.joinToString(" · ").ifEmpty { "ANY" }, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(6.dp))
            Text(routeSubtitle(p.query), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onRun) { Icon(Icons.Outlined.PlayArrow, "Run ${p.name}") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More for ${p.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                    onClick = { menu = false; onRename() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}
