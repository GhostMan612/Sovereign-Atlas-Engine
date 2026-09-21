// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.field.FieldJournal
import com.sovereignatlas.atlas.field.waypointSourceName
import com.sovereignatlas.atlas.geo.Mgrs
import java.util.Locale

@Composable
fun WaypointsDialog(
    journal: FieldJournal,
    onOpenDetail: (String) -> Unit,
    onClose: () -> Unit,
) {
    val error = journal.lastErrorOrNull()
    val records = journal.waypoints()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Waypoints") },
        text = {
            Column {
                if (error != null) {
                    Text("Journal unavailable: $error")
                }
                if (records.isEmpty()) {
                    Text(
                        "No waypoints yet. Long-press the map to add one.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(records, key = { it.id }) { record ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        if (record.label.isEmpty()) {
                                            record.id
                                        } else {
                                            record.label
                                        },
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        String.format(
                                            Locale.US,
                                            "%.4f, %.4f",
                                            record.latitude,
                                            record.longitude,
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenDetail(record.id) }
                                    .testTag("waypoint-${record.id}"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
    )
}

@Composable
fun WaypointDetailDialog(
    journal: FieldJournal,
    id: String,
    onGoTo: (String) -> Unit,
    onClose: () -> Unit,
) {
    val record = journal.lookup(id)
    if (record == null) {
        AlertDialog(
            onDismissRequest = onClose,
            text = { Text("Waypoint removed.") },
            confirmButton = {
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            },
        )
        return
    }
    val label = remember(id) { mutableStateOf(record.label) }
    val note = remember(id) { mutableStateOf(record.note) }
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                String.format(
                    Locale.US,
                    "%.4f, %.4f",
                    record.latitude,
                    record.longitude,
                ),
            )
        },
        text = {
            Column {
                Text("Source: ${waypointSourceName(record.source)}")
                Text("MGRS: ${Mgrs.format(record.latitude, record.longitude)}")
                OutlinedTextField(
                    value = label.value,
                    onValueChange = { label.value = it },
                    label = { Text("Label") },
                    modifier = Modifier.testTag("waypoint-edit-label"),
                )
                OutlinedTextField(
                    value = note.value,
                    onValueChange = { note.value = it },
                    label = { Text("Note") },
                    modifier = Modifier.testTag("waypoint-edit-note"),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        journal.updateLabel(id, label.value)
                        journal.updateNote(id, note.value)
                        onClose()
                    },
                    modifier = Modifier.testTag("waypoint-save"),
                ) {
                    Text("Save")
                }
                TextButton(
                    onClick = {
                        journal.remove(id)
                        onClose()
                    },
                    modifier = Modifier.testTag("waypoint-delete"),
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = { onGoTo(id) },
                    modifier = Modifier.testTag("waypoint-go-to"),
                ) {
                    Text("Go-To")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
    )
}
