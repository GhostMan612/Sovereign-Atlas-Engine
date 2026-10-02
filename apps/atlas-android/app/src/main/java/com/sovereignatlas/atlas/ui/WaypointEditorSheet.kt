// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
                SharingPolicySelector(
                    // Read through the stored-string parse rather than comparing the
                    // raw column, so a corrupt value shows as LOCAL here instead of
                    // leaving the operator looking at an unselected list.
                    current = com.sovereignatlas.atlas.field.WaypointSharingPolicy.fromStored(
                        activeWaypoint.sharingPolicy,
                    ),
                    waypointName = name,
                    onPolicySelected = { policy ->
                        scope.launch {
                            errorMessage = null
                            try {
                                // setSharingPolicy rather than saveWaypoint: it
                                // re-reads the row and rewrites only the policy, so a
                                // half-typed name in the fields above cannot be
                                // persisted by a tap that was only meant to change
                                // sharing.
                                waypointRepository.setSharingPolicy(activeWaypoint.id, policy)
                            } catch (error: Exception) {
                                errorMessage = "Failed to change sharing: ${error.message}"
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
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
                                    // Policy is NOT part of this copy. The sharing
                                    // selector owns that column, and folding the
                                    // stale `activeWaypoint` value in here would let a
                                    // Save pressed after an unrelated edit silently
                                    // revert a sharing change.
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
