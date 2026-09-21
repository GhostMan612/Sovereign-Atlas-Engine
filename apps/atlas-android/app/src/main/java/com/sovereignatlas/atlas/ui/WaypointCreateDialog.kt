// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import java.util.Locale

@Composable
fun WaypointCreateDialog(
    point: AtlasCoordinate,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    val label = remember { mutableStateOf("") }
    val note = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = String.format(
                    Locale.US,
                    "Waypoint %.4f, %.4f",
                    point.latitude,
                    point.longitude,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = label.value,
                    onValueChange = { label.value = it },
                    label = { Text("Label") },
                    modifier = Modifier.testTag("waypoint-label"),
                )
                OutlinedTextField(
                    value = note.value,
                    onValueChange = { note.value = it },
                    label = { Text("Note") },
                    modifier = Modifier.testTag("waypoint-note"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label.value, note.value) },
                modifier = Modifier.testTag("waypoint-save"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}
