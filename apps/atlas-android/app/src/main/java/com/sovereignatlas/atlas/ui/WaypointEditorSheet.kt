// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.geo.MgrsConverter
import com.sovereignatlas.atlas.map.WaypointSelection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointEditorSheet(
    selection: WaypointSelection,
    waypointRepository: WaypointRepository,
    onGoTo: (latitude: Double, longitude: Double, label: String) -> Unit,
) {
    val selectedId by selection.selectedWaypointId.collectAsStateWithLifecycle()
    val waypoints by waypointRepository.waypoints.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
    val activeWaypoint = waypoints.find { it.id == selectedId }
    if (activeWaypoint != null) {
        ModalBottomSheet(
            onDismissRequest = { selection.clearWaypointSelection() },
        ) {
            val scope = rememberCoroutineScope()
            var name by remember(activeWaypoint.id) { mutableStateOf(activeWaypoint.name) }
            var notes by remember(activeWaypoint.id) {
                mutableStateOf(activeWaypoint.notes ?: "")
            }
            var errorMessage by remember(activeWaypoint.id) {
                mutableStateOf<String?>(null)
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "MGRS: ${remember(activeWaypoint.id) {
                        MgrsConverter.spaced(
                            MgrsConverter.toMgrs(
                                activeWaypoint.latitude,
                                activeWaypoint.longitude,
                            ),
                        )
                    }}",
                )
                Text("Lat/Lng: ${activeWaypoint.latitude}, ${activeWaypoint.longitude}")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red)
                }
                Row {
                    Button(
                        onClick = {
                            scope.launch {
                                errorMessage = null
                                try {
                                    val updated = activeWaypoint.copy(
                                        name = name,
                                        notes = notes.takeIf { it.isNotBlank() },
                                    )
                                    waypointRepository.saveWaypoint(updated)
                                    selection.clearWaypointSelection()
                                } catch (error: Exception) {
                                    errorMessage = "Failed to save: ${error.message}"
                                }
                            }
                        },
                    ) {
                        Text("Save")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                errorMessage = null
                                try {
                                    waypointRepository.deleteWaypoint(activeWaypoint.id)
                                    selection.clearWaypointSelection()
                                } catch (error: Exception) {
                                    errorMessage = "Failed to delete: ${error.message}"
                                }
                            }
                        },
                    ) {
                        Text("Delete")
                    }
                    Button(
                        onClick = {
                            onGoTo(
                                activeWaypoint.latitude,
                                activeWaypoint.longitude,
                                name.ifEmpty { activeWaypoint.id },
                            )
                            selection.clearWaypointSelection()
                        },
                    ) {
                        Text("Go-To")
                    }
                }
            }
        }
    }
}
