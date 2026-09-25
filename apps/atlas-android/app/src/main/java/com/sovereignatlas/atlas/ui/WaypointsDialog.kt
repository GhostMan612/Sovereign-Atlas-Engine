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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.geo.Mgrs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

val waypointTimestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

@Composable
fun WaypointsDialog(
    waypointRepository: WaypointRepository,
    onOpenDetail: (String) -> Unit,
    onClose: () -> Unit,
) {
    val records by waypointRepository.waypoints.collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Waypoints") },
        text = {
            Column {
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
                                        if (record.name.isEmpty()) {
                                            record.id
                                        } else {
                                            record.name
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
    waypointRepository: WaypointRepository,
    id: String,
    onGoTo: (latitude: Double, longitude: Double, label: String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val records by waypointRepository.waypoints.collectAsState(initial = emptyList())
    val record = records.firstOrNull { it.id == id }
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
    var label by remember(id) { mutableStateOf(record.name) }
    var note by remember(id) { mutableStateOf(record.notes ?: "") }
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
                Text("MGRS: ${Mgrs.format(record.latitude, record.longitude)}")
                Text("Saved: ${waypointTimestampFormat.format(Date(record.timestamp))}")
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.testTag("waypoint-edit-label"),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.testTag("waypoint-edit-note"),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        scope.launch {
                            waypointRepository.saveWaypoint(
                                record.copy(name = label, notes = note),
                            )
                        }
                        onClose()
                    },
                    modifier = Modifier.testTag("waypoint-save"),
                ) {
                    Text("Save")
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            waypointRepository.deleteWaypoint(id)
                        }
                        onClose()
                    },
                    modifier = Modifier.testTag("waypoint-delete"),
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = {
                        onGoTo(
                            record.latitude,
                            record.longitude,
                            label.ifEmpty { record.id },
                        )
                    },
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
