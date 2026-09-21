// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders

@Composable
fun LayersDialog(
    providerId: String,
    onProviderSelected: (String) -> Unit,
    showGraticule: Boolean,
    onGraticuleChanged: (Boolean) -> Unit,
    showRings: Boolean,
    onRingsChanged: (Boolean) -> Unit,
    showWaypoints: Boolean,
    onWaypointsChanged: (Boolean) -> Unit,
    showTrack: Boolean,
    onTrackChanged: (Boolean) -> Unit,
    showMeasure: Boolean,
    onMeasureChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Layers") },
        text = {
            Column {
                LazyColumn {
                    items(OfflineBuiltinProviders.all, key = { it.id }) { provider ->
                        ListItem(
                            headlineContent = { Text(provider.title) },
                            leadingContent = {
                                RadioButton(
                                    selected = provider.id == providerId,
                                    onClick = { onProviderSelected(provider.id) },
                                )
                            },
                            modifier = Modifier.clickable {
                                onProviderSelected(provider.id)
                            },
                        )
                    }
                }
                for (row in listOf(
                    "Graticule" to (showGraticule to onGraticuleChanged),
                    "Range rings" to (showRings to onRingsChanged),
                    "Waypoints" to (showWaypoints to onWaypointsChanged),
                    "Track" to (showTrack to onTrackChanged),
                    "Measure" to (showMeasure to onMeasureChanged),
                )) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = row.second.first,
                            onCheckedChange = row.second.second,
                        )
                        Text(row.first)
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
