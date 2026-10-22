// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.layers.compositor.LayerCompositor
import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders

@Composable
fun LayersDialog(
    providerId: String,
    onProviderSelected: (String) -> Unit,
    showGraticule: Boolean,
    onGraticuleChanged: (Boolean) -> Unit,
    showMgrsGrid: Boolean,
    onMgrsGridChanged: (Boolean) -> Unit,
    showRings: Boolean,
    onRingsChanged: (Boolean) -> Unit,
    showWaypoints: Boolean,
    onWaypointsChanged: (Boolean) -> Unit,
    showTrack: Boolean,
    onTrackChanged: (Boolean) -> Unit,
    showMeasure: Boolean,
    onMeasureChanged: (Boolean) -> Unit,
    compositor: LayerCompositor? = null,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Layers") },
        text = {
            // One scroll container for the whole dialog. The provider list was a
            // LazyColumn sitting directly in the dialog's unconstrained Column,
            // so the six overlay checkboxes below it were pushed past the bottom
            // of the dialog. A LazyColumn nested inside a vertically-scrolling
            // parent would additionally throw on infinite height constraints, so
            // the inner list is a plain Column and the outer Column owns the
            // single scroll.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (provider in OfflineBuiltinProviders.all) {
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
                for (row in listOf(
                    "Graticule" to (showGraticule to onGraticuleChanged),
                    "MGRS grid" to (showMgrsGrid to onMgrsGridChanged),
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
                if (compositor != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Compositor Stack",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    for (layer in compositor.getAllLayers()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = "\u2022 ${layer.descriptor.title} (z:${layer.zIndex}, ${(layer.opacity * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (layer.isVisible) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
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
