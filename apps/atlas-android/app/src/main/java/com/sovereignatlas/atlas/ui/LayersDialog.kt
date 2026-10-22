// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier
import com.sovereignatlas.atlas.core.knowledge.HistoricalKnowledgeIndex
import com.sovereignatlas.atlas.layers.compositor.AtlasLayerPresets
import com.sovereignatlas.atlas.layers.compositor.LayerCompositor
import com.sovereignatlas.atlas.layers.compositor.LayerRuntimeState
import com.sovereignatlas.atlas.layers.compositor.LayerType
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
    onCompositorChanged: () -> Unit = {},
    historicalIndex: HistoricalKnowledgeIndex? = null,
    selectedEpochYear: Int? = null,
    onEpochYearChanged: (Int?) -> Unit = {},
    onSelectDossier: (HistoricalDossier) -> Unit = {},
    onClose: () -> Unit,
) {
    val recomposeTick = remember { mutableIntStateOf(0) }
    val dossierQuery = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Map & Overlay Compositor") },
        text = {
            // One scroll container for the whole dialog. The provider list was a
            // LazyColumn sitting directly in the dialog's unconstrained Column,
            // so the six overlay checkboxes below it were pushed past the bottom
            // of the dialog. A LazyColumn nested inside a vertically-scrolling
            // parent would additionally throw on infinite height constraints, so
            // the inner list is a plain Column and the outer Column owns the
            // single scroll.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                recomposeTick.intValue.let { _ ->
                    if (compositor != null) {
                        Text(
                            text = "Multi-Layer Combination Presets",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        for (preset in AtlasLayerPresets.all) {
                            OutlinedButton(
                                onClick = {
                                    compositor.applyCombinationPreset(preset)
                                    onProviderSelected(preset.baseProviderId)
                                    compositor.getLayer("graticule")?.let { onGraticuleChanged(it.isVisible) }
                                    compositor.getLayer("mgrs")?.let { onMgrsGridChanged(it.isVisible) }
                                    compositor.getLayer("rings")?.let { onRingsChanged(it.isVisible) }
                                    compositor.getLayer("waypoints")?.let { onWaypointsChanged(it.isVisible) }
                                    compositor.getLayer("track")?.let { onTrackChanged(it.isVisible) }
                                    compositor.getLayer("measure")?.let { onMeasureChanged(it.isVisible) }
                                    onCompositorChanged()
                                    recomposeTick.intValue += 1
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Text(
                        text = "Primary Basemap",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
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

                    if (compositor != null) {
                        val rasterOverlays = compositor.getAllLayers()
                            .filter { it.descriptor.type == LayerType.RASTER_OVERLAY }
                        if (rasterOverlays.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "Simultaneous Map Overlays (Blended)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            for (layer in rasterOverlays) {
                                CompositorLayerControlRow(
                                    layer = layer,
                                    onVisibilityChanged = { visible ->
                                        compositor.setVisibility(layer.descriptor.id, visible)
                                        onCompositorChanged()
                                        recomposeTick.intValue += 1
                                    },
                                    onOpacityChanged = { opacity ->
                                        compositor.setOpacity(layer.descriptor.id, opacity)
                                        onCompositorChanged()
                                        recomposeTick.intValue += 1
                                    },
                                )
                            }
                        }

                        val historicalOverlays = compositor.getAllLayers()
                            .filter { it.descriptor.type == LayerType.HISTORICAL_OVERLAY }
                        if (historicalOverlays.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "Historical & Encyclopedic Overlays",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            for (layer in historicalOverlays) {
                                CompositorLayerControlRow(
                                    layer = layer,
                                    onVisibilityChanged = { visible ->
                                        compositor.setVisibility(layer.descriptor.id, visible)
                                        onCompositorChanged()
                                        recomposeTick.intValue += 1
                                    },
                                    onOpacityChanged = { opacity ->
                                        compositor.setOpacity(layer.descriptor.id, opacity)
                                        onCompositorChanged()
                                        recomposeTick.intValue += 1
                                    },
                                )
                            }
                        }
                    }

                    if (historicalIndex != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Historical Epoch Timeline & Dossier Index",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selectedEpochYear == null,
                                    onCheckedChange = { showAll ->
                                        onEpochYearChanged(if (showAll) null else 1880)
                                        recomposeTick.intValue += 1
                                    },
                                )
                                Text(
                                    text = if (selectedEpochYear == null) {
                                        "All Historical Epochs (${historicalIndex.count()} dossiers)"
                                    } else {
                                        "Epoch Year: $selectedEpochYear (${historicalIndex.queryTemporal(selectedEpochYear).size} active)"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (selectedEpochYear != null) {
                            Slider(
                                value = selectedEpochYear.toFloat(),
                                onValueChange = { yearFloat ->
                                    onEpochYearChanged(yearFloat.toInt())
                                    recomposeTick.intValue += 1
                                },
                                valueRange = 1750f..2025f,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        OutlinedTextField(
                            value = dossierQuery.value,
                            onValueChange = { dossierQuery.value = it },
                            label = { Text("Search Dossiers (name, archive, tag)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                        val matchingDossiers = if (dossierQuery.value.isNotBlank()) {
                            historicalIndex.search(dossierQuery.value)
                        } else if (selectedEpochYear != null) {
                            historicalIndex.queryTemporal(selectedEpochYear)
                        } else {
                            historicalIndex.getAll()
                        }
                        for (dossier in matchingDossiers) {
                            val fromYr = dossier.temporal.validFromYear?.toString() ?: "?"
                            val toYr = dossier.temporal.validToYear?.toString() ?: "present"
                            OutlinedButton(
                                onClick = { onSelectDossier(dossier) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "${dossier.name} ($fromYr–$toYr)",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "${dossier.entityType.name} • ${dossier.primaryCitation.authorOrArchive}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Grids, Tactical & Field Overlays",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    for (row in listOf(
                        Triple("graticule", "Graticule", showGraticule to onGraticuleChanged),
                        Triple("mgrs", "MGRS grid", showMgrsGrid to onMgrsGridChanged),
                        Triple("rings", "Range rings", showRings to onRingsChanged),
                        Triple("waypoints", "Waypoints", showWaypoints to onWaypointsChanged),
                        Triple("track", "Track", showTrack to onTrackChanged),
                        Triple("measure", "Measure", showMeasure to onMeasureChanged),
                    )) {
                        val compLayer = compositor?.getLayer(row.first)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = row.third.first,
                                        onCheckedChange = { checked ->
                                            compositor?.setVisibility(row.first, checked)
                                            row.third.second(checked)
                                            onCompositorChanged()
                                            recomposeTick.intValue += 1
                                        },
                                    )
                                    Text(row.second)
                                }
                                if (compLayer != null) {
                                    Text(
                                        text = "${(compLayer.opacity * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (compLayer != null && row.third.first) {
                                Slider(
                                    value = compLayer.opacity,
                                    onValueChange = { opacity ->
                                        compositor.setOpacity(row.first, opacity)
                                        onCompositorChanged()
                                        recomposeTick.intValue += 1
                                    },
                                    valueRange = 0.1f..1.0f,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }

                    if (compositor != null) {
                        val extraTacticalIds = setOf("cot-markers", "pli-mesh", "ops-graphics", "fence", "route")
                        val extraTacticalLayers = compositor.getAllLayers()
                            .filter { it.descriptor.id in extraTacticalIds }
                        for (layer in extraTacticalLayers) {
                            CompositorLayerControlRow(
                                layer = layer,
                                onVisibilityChanged = { visible ->
                                    compositor.setVisibility(layer.descriptor.id, visible)
                                    onCompositorChanged()
                                    recomposeTick.intValue += 1
                                },
                                onOpacityChanged = { opacity ->
                                    compositor.setOpacity(layer.descriptor.id, opacity)
                                    onCompositorChanged()
                                    recomposeTick.intValue += 1
                                },
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Active Compositor Render Stack (${compositor.getRenderStack().size} visible)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        for (layer in compositor.getRenderStack()) {
                            Text(
                                text = "\u2022 ${layer.descriptor.title} (z:${layer.zIndex}, ${(layer.opacity * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 1.dp),
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
private fun CompositorLayerControlRow(
    layer: LayerRuntimeState,
    onVisibilityChanged: (Boolean) -> Unit,
    onOpacityChanged: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Checkbox(
                    checked = layer.isVisible,
                    onCheckedChange = onVisibilityChanged,
                )
                Text(
                    text = layer.descriptor.title,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "${(layer.opacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (layer.isVisible) {
            Slider(
                value = layer.opacity,
                onValueChange = onOpacityChanged,
                valueRange = 0.1f..1.0f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
