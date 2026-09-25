// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.field.WaypointRepository
import java.util.Locale

@Composable
fun WaypointsDialog(
    waypointRepository: WaypointRepository,
    onCenterWaypoint: (latitude: Double, longitude: Double) -> Unit,
    onEditWaypoint: (String) -> Unit,
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
                                trailingContent = {
                                    IconButton(
                                        onClick = { onEditWaypoint(record.id) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit waypoint",
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCenterWaypoint(record.latitude, record.longitude)
                                    }
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
