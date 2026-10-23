// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.AtlasServices
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.android.DemSession
import com.sovereignatlas.atlas.core.HistoricalAsset
import com.sovereignatlas.atlas.core.HistoricalRecord
import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.GeoPoint
import com.sovereignatlas.atlas.geo.LoSMode
import com.sovereignatlas.atlas.geo.los.LoSStatus
import com.sovereignatlas.atlas.geo.los.TerrainProfile
import com.sovereignatlas.atlas.geo.render.FeatureSink
import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders
import com.sovereignatlas.atlas.offline.mbtiles.MbtilesPack
import com.sovereignatlas.atlas.tactical.RadialFence
import com.sovereignatlas.atlas.geo.gis.GisDataset
import com.sovereignatlas.atlas.geo.gis.GisFeature
import com.sovereignatlas.atlas.ui.FenceDialog
import com.sovereignatlas.atlas.ui.gis.GisFeatureInspectorSheet
import com.sovereignatlas.atlas.ui.gis.GisLayersSheet
import com.sovereignatlas.atlas.ui.LayersDialog
import com.sovereignatlas.atlas.ui.LinkDialog
import com.sovereignatlas.atlas.ui.OfflineDialog
import com.sovereignatlas.atlas.ui.SettingsDialog
import com.sovereignatlas.atlas.ui.TrackDetailDialog
import com.sovereignatlas.atlas.ui.TracksDialog
import com.sovereignatlas.atlas.ui.WaypointEditorSheet
import com.sovereignatlas.atlas.ui.WaypointsDialog
import com.sovereignatlas.atlas.ui.chat.GeoChatScreen
import com.sovereignatlas.atlas.ui.chat.GeoChatViewModel
import com.sovereignatlas.atlas.ui.historical.HistoricalAssetSheet
import com.sovereignatlas.atlas.ui.historical.HistoricalDossierSheet
import com.sovereignatlas.atlas.ui.historical.HistoricalRecordSheet
import com.sovereignatlas.atlas.ui.los.TerrainProfileChart
import com.sovereignatlas.atlas.ui.settings.SettingsScreen
import com.sovereignatlas.atlas.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasCommandCenterSheet(
    visible: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    services: AtlasServices,
    cartoKey: String?,
    losMode: LoSMode,
    headingUp: Boolean,
    fenceArmed: Boolean,
    reducedMotion: Boolean,
    explorerWorkspaceEnabled: Boolean,
    tacticalWorkspaceEnabled: Boolean,
    studioWorkspaceEnabled: Boolean,
    onOpenWaypoints: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenLink: () -> Unit,
    onOpenFence: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNodeSettings: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenGisLayers: () -> Unit = {},
    onToggleLoS: () -> Unit,
    onToggleHeadingUp: () -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF141C24),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Sovereign Command Center",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WorkspaceQuickChip(
                    label = "All",
                    active = explorerWorkspaceEnabled && tacticalWorkspaceEnabled && studioWorkspaceEnabled,
                    reducedMotion = reducedMotion,
                    onClick = {
                        services.settingsRepository.setExplorerWorkspaceEnabled(true)
                        services.settingsRepository.setTacticalWorkspaceEnabled(true)
                        services.settingsRepository.setStudioWorkspaceEnabled(true)
                    },
                )
                WorkspaceQuickChip(
                    label = "Clean Nav",
                    active = !explorerWorkspaceEnabled && !tacticalWorkspaceEnabled && !studioWorkspaceEnabled,
                    reducedMotion = reducedMotion,
                    onClick = {
                        services.settingsRepository.setExplorerWorkspaceEnabled(false)
                        services.settingsRepository.setTacticalWorkspaceEnabled(false)
                        services.settingsRepository.setStudioWorkspaceEnabled(false)
                    },
                )
                WorkspaceQuickChip(
                    label = "Explorer",
                    active = explorerWorkspaceEnabled,
                    reducedMotion = reducedMotion,
                    onClick = {
                        services.settingsRepository.setExplorerWorkspaceEnabled(!explorerWorkspaceEnabled)
                    },
                )
                WorkspaceQuickChip(
                    label = "Tactical",
                    active = tacticalWorkspaceEnabled,
                    reducedMotion = reducedMotion,
                    onClick = {
                        services.settingsRepository.setTacticalWorkspaceEnabled(!tacticalWorkspaceEnabled)
                    },
                )
                WorkspaceQuickChip(
                    label = "Studio",
                    active = studioWorkspaceEnabled,
                    reducedMotion = reducedMotion,
                    onClick = {
                        services.settingsRepository.setStudioWorkspaceEnabled(!studioWorkspaceEnabled)
                    },
                )
            }
            ToolRow(
                label = "Waypoints",
                description = "Open waypoints",
                iconRes = R.drawable.ic_tool_waypoints,
                onClick = onOpenWaypoints,
            )
            ToolRow(
                label = "Tracks",
                description = "Open tracks",
                iconRes = R.drawable.ic_tool_tracks,
                active = services.recorder.isRecording(),
                onClick = onOpenTracks,
            )
            ToolRow(
                label = "Offline",
                description = "Open offline packs",
                iconRes = R.drawable.ic_tool_offline,
                onClick = onOpenOffline,
            )
            ToolRow(
                label = "Layers",
                description = "Open layers",
                iconRes = R.drawable.ic_tool_compositor,
                onClick = onOpenLayers,
            )
            ToolRow(
                label = "Field GIS",
                description = "Open Field GIS layers",
                iconRes = R.drawable.ic_tool_compositor,
                onClick = onOpenGisLayers,
            )
            ToolRow(
                label = "Link",
                description = "Open radio link",
                iconRes = R.drawable.ic_tool_link,
                onClick = onOpenLink,
            )
            ToolRow(
                label = "Fence",
                description = "Open geofence",
                iconRes = R.drawable.ic_tool_fence,
                active = fenceArmed,
                onClick = onOpenFence,
            )
            ToolRow(
                label = "API Keys",
                description = "Open API keys",
                iconRes = R.drawable.ic_tool_keys,
                active = cartoKey != null,
                onClick = onOpenSettings,
            )
            ToolRow(
                label = "Settings",
                description = "Open node settings",
                iconRes = R.drawable.ic_tool_settings,
                onClick = onOpenNodeSettings,
            )
            ToolRow(
                label = "Chat",
                description = "Open tactical chat",
                iconRes = R.drawable.ic_tool_chat,
                onClick = onOpenChat,
            )
            ToolRow(
                label = if (losMode != LoSMode.Inactive) "LoS off" else "LoS",
                description = if (losMode != LoSMode.Inactive) {
                    "Cancel line-of-sight"
                } else {
                    "Start line-of-sight"
                },
                iconRes = R.drawable.ic_tool_los,
                active = losMode != LoSMode.Inactive,
                onClick = onToggleLoS,
            )
            ToolRow(
                label = if (headingUp) "North-up" else "Head-up",
                description = if (headingUp) "Head-up on" else "Head-up off",
                iconRes = R.drawable.ic_tool_heading,
                active = headingUp,
                onClick = onToggleHeadingUp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasLayerManagerSheet(
    visible: Boolean,
    sheetState: SheetState,
    scannedPacks: List<MbtilesPack>,
    activePacks: Set<String>,
    onTogglePack: (String, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Encyclopedia Layers",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (scannedPacks.isEmpty()) {
                Text(
                    "No .mbtiles packs found in the offline packs directory.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            scannedPacks.forEach { pack ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pack.name, style = MaterialTheme.typography.bodyLarge)
                        if (pack.description.isNotEmpty()) {
                            Text(
                                pack.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = activePacks.contains(pack.packId),
                        onCheckedChange = { enabled ->
                            onTogglePack(pack.packId, enabled)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasTargetDropSheet(
    targetDropPoint: LatLng?,
    activeProfileState: TerrainProfile?,
    currentLocationFix: GeoPoint?,
    onDismissRequest: () -> Unit,
    onAnalyzeLoS: () -> Unit,
    onSendMarker: (label: String, type: String, color: Color) -> Unit,
) {
    if (targetDropPoint == null) return
    var errorMessage by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Drop Tactical Marker",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val losProfile = activeProfileState
            if (losProfile != null) {
                Text(
                    "Line of Sight Analysis",
                    style = MaterialTheme.typography.titleMedium,
                )
                val losError = losProfile.errorMessage
                if (losError != null) {
                    Text(
                        text = losError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                } else {
                    TerrainProfileChart(profile = losProfile)
                    Text(
                        text = when (losProfile.lineOfSight.status) {
                            LoSStatus.Clear -> "CLEAR LINE OF SIGHT"
                            LoSStatus.BlockedTerrain -> "LINE OF SIGHT BLOCKED"
                            else -> "LINE OF SIGHT UNDETERMINED"
                        },
                        color = when (losProfile.lineOfSight.status) {
                            LoSStatus.Clear -> Color.Green
                            LoSStatus.BlockedTerrain -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            } else if (currentLocationFix != null) {
                Button(
                    onClick = onAnalyzeLoS,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Text("Analyze Line of Sight")
                }
            } else {
                Text(
                    "Waiting for GPS to enable LoS...",
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            val types = listOf(
                Triple("Hostile", "a-h-G", MaterialTheme.colorScheme.error),
                Triple("Neutral", "a-n-G", Color.Green),
                Triple("Unknown", "a-u-G", Color.Yellow),
                Triple("Waypoint", "b-m-p-w", Color.White),
            )
            types.forEach { (label, type, color) ->
                Button(
                    onClick = { onSendMarker(label, type, color) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                ) {
                    Text(label, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun AtlasHistoricalSheetsHost(
    selectedRecord: HistoricalRecord?,
    onDismissRecord: () -> Unit,
    selectedAsset: HistoricalAsset?,
    onDismissAsset: () -> Unit,
    selectedDossier: HistoricalDossier?,
    onDismissDossier: () -> Unit,
) {
    HistoricalRecordSheet(
        record = selectedRecord,
        onDismissRequest = onDismissRecord,
    )
    HistoricalAssetSheet(
        asset = selectedAsset,
        onDismissRequest = onDismissAsset,
    )
    HistoricalDossierSheet(
        dossier = selectedDossier,
        onDismissRequest = onDismissDossier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasMapDialogsHost(
    showWaypoints: MutableState<Boolean>,
    showTracks: MutableState<Boolean>,
    trackDetailId: MutableState<String?>,
    showOffline: MutableState<Boolean>,
    showLayers: MutableState<Boolean>,
    showLink: MutableState<Boolean>,
    showFence: MutableState<Boolean>,
    showSettings: MutableState<Boolean>,
    showChat: MutableState<Boolean>,
    showNodeSettings: MutableState<Boolean>,
    chatSheetState: SheetState,
    nodeSettingsSheetState: SheetState,
    chatViewModel: GeoChatViewModel,
    nodeSettingsViewModel: SettingsViewModel,
    waypointSelection: WaypointSelection,
    fence: MutableState<RadialFence?>,
    baseProviderId: MutableState<String>,
    basePackId: MutableState<String?>,
    showGraticule: MutableState<Boolean>,
    showMgrsGrid: MutableState<Boolean>,
    showRings: MutableState<Boolean>,
    showWaypointsLayer: MutableState<Boolean>,
    showTrackLayer: MutableState<Boolean>,
    showMeasureLayer: MutableState<Boolean>,
    selectedEpochYear: MutableState<Int?>,
    selectedHistoricalDossier: MutableStateFlow<HistoricalDossier?>,
    services: AtlasServices,
    sink: FeatureSink,
    styleRef: MutableState<Style?>,
    mapRef: MutableState<MapLibreMap?>,
    attribution: MutableState<String>,
    context: Context,
    refreshMgrsGrid: (MapLibreMap) -> Unit,
    showGisLayers: MutableState<Boolean> = remember { mutableStateOf(false) },
    gisDatasets: List<GisDataset> = emptyList(),
    onImportGis: () -> Unit = {},
    selectedGisFeature: MutableState<Pair<GisFeature, String>?> = remember { mutableStateOf(null) },
) {
    if (showChat.value) {
        ModalBottomSheet(
            onDismissRequest = { showChat.value = false },
            sheetState = chatSheetState,
        ) {
            GeoChatScreen(viewModel = chatViewModel)
        }
    }
    if (showNodeSettings.value) {
        ModalBottomSheet(
            onDismissRequest = { showNodeSettings.value = false },
            sheetState = nodeSettingsSheetState,
        ) {
            SettingsScreen(viewModel = nodeSettingsViewModel)
        }
    }
    if (showWaypoints.value) {
        WaypointsDialog(
            waypointRepository = services.waypointRepository,
            onCenterWaypoint = { latitude, longitude ->
                mapRef.value?.let { map -> animateToWaypoint(map, latitude, longitude) }
            },
            onEditWaypoint = { id -> waypointSelection.selectWaypoint(id) },
            onClose = { showWaypoints.value = false },
        )
    }
    WaypointEditorSheet(
        selection = waypointSelection,
        waypointRepository = services.waypointRepository,
        onGoTo = { latitude, longitude, label ->
            waypointSelection.selectedWaypointId.value?.let { id ->
                services.goTo.activate(
                    id = id,
                    latitude = latitude,
                    longitude = longitude,
                    label = label,
                )
            }
            mapRef.value?.let { map ->
                com.sovereignatlas.atlas.goto.goToCameraIntent(
                    services.goTo,
                    map.cameraPosition.zoom,
                    map.cameraPosition.bearing,
                )?.let { intent ->
                    applyCameraIntent(map, intent)
                }
            }
            showWaypoints.value = false
        },
    )
    if (showTracks.value) {
        TracksDialog(
            trackRepository = services.trackRepository,
            waypointRepository = services.waypointRepository,
            recorder = services.recorder,
            locationService = services.location,
            exportDir = context.filesDir,
            onOpenDetail = { id -> trackDetailId.value = id },
            onClose = { showTracks.value = false },
        )
    }
    trackDetailId.value?.let { id ->
        TrackDetailDialog(
            trackRepository = services.trackRepository,
            scrubState = services.scrubState,
            demEngine = DemSession.engine,
            id = id,
            onClose = { trackDetailId.value = null },
        )
    }
    if (showOffline.value) {
        OfflineDialog(
            store = services.offline,
            maps = services.maps,
            tileHits = { services.tiles.tileHits() },
            basemapHits = { services.tiles.basemapHits() },
            demHits = { services.tiles.demHits() },
            keyProvider = services.keys,
            onUsePack = { packId ->
                basePackId.value = packId
                styleRef.value?.let { style ->
                    attribution.value = applyBaseSource(
                        style,
                        services,
                        baseProviderId.value,
                        packId,
                    )
                }
                showOffline.value = false
            },
            onClose = { showOffline.value = false },
        )
    }
    if (showLayers.value) {
        LayersDialog(
            providerId = baseProviderId.value,
            onProviderSelected = { id ->
                baseProviderId.value = id
                basePackId.value = null
                styleRef.value?.let { style ->
                    val baseAttr = applyBaseSource(
                        style,
                        services,
                        id,
                        null,
                        services.keys.cartoKey.value,
                    )
                    attribution.value = combineAttribution(baseAttr, services.compositor.getAttributionRollup())
                }
            },
            showGraticule = showGraticule.value,
            onGraticuleChanged = { visible ->
                showGraticule.value = visible
                styleRef.value?.let { style ->
                    setAtlasLayerVisible(style, AtlasLayerIds.GRATICULE_LAYER, visible)
                }
                val map = mapRef.value
                if (visible && map != null) pushGraticule(map, sink)
            },
            showMgrsGrid = showMgrsGrid.value,
            onMgrsGridChanged = { visible ->
                showMgrsGrid.value = visible
                styleRef.value?.let { style ->
                    setAtlasLayerVisible(style, AtlasLayerIds.MGRS_LINE_LAYER, visible)
                    setAtlasLayerVisible(style, AtlasLayerIds.MGRS_LABEL_LAYER, visible)
                    if (visible) {
                        mapRef.value?.let { map -> refreshMgrsGrid(map) }
                    }
                }
            },
            showRings = showRings.value,
            onRingsChanged = { visible ->
                showRings.value = visible
                styleRef.value?.let { style ->
                    setAtlasLayerVisible(style, AtlasLayerIds.RINGS_LAYER, visible)
                }
                if (visible) pushRings(sink, services)
            },
            showWaypoints = showWaypointsLayer.value,
            onWaypointsChanged = { visible ->
                showWaypointsLayer.value = visible
                styleRef.value?.let { style ->
                    setAtlasLayerVisible(style, AtlasLayerIds.WAYPOINTS_LAYER, visible)
                }
            },
            showTrack = showTrackLayer.value,
            onTrackChanged = { visible ->
                showTrackLayer.value = visible
                styleRef.value?.let { style ->
                    setAtlasLayerVisible(style, AtlasLayerIds.TRACK_LAYER, visible)
                }
            },
            showMeasure = showMeasureLayer.value,
            onMeasureChanged = { visible ->
                showMeasureLayer.value = visible
                styleRef.value?.let { style ->
                    setAtlasLayerVisible(style, AtlasLayerIds.MEASURE_LAYER, visible)
                    setAtlasLayerVisible(style, AtlasLayerIds.MEASURE_DOTS_LAYER, visible)
                }
            },
            compositor = services.compositor,
            onCompositorChanged = {
                services.settingsRepository.setCompositorSnapshot(services.compositor.exportStateSnapshot())
                styleRef.value?.let { style ->
                    applyOverlayVisibility(
                        style = style,
                        compositor = services.compositor,
                        showGraticule = showGraticule.value,
                        showMgrsGrid = showMgrsGrid.value,
                        showRings = showRings.value,
                        showWaypoints = showWaypointsLayer.value,
                        showTrack = showTrackLayer.value,
                        showMeasure = showMeasureLayer.value,
                        cartoKey = services.keys.cartoKey.value,
                    )
                    val baseAttr = OfflineBuiltinProviders.lookup(baseProviderId.value)?.attribution ?: ""
                    attribution.value = combineAttribution(baseAttr, services.compositor.getAttributionRollup())
                }
            },
            historicalIndex = services.historicalIndex,
            selectedEpochYear = selectedEpochYear.value,
            onEpochYearChanged = { year ->
                selectedEpochYear.value = year
                pushHistoricalDossiers(sink, services.historicalIndex, year)
            },
            onSelectDossier = { dossier ->
                selectedHistoricalDossier.value = dossier
                showLayers.value = false
                mapRef.value?.let { map -> animateToWaypoint(map, dossier.latitude, dossier.longitude) }
            },
            onClose = { showLayers.value = false },
        )
    }
    if (showLink.value) {
        val map = mapRef.value
        val center = map?.cameraPosition?.target?.let {
            AtlasCoordinate(latitude = it.latitude, longitude = it.longitude)
        }
        val fix = usableFixOf(services)
        val target = services.goTo.targetOrNull()?.let {
            AtlasCoordinate(latitude = it.latitude, longitude = it.longitude)
        }
        LinkDialog(
            pointA = fix ?: center,
            pointB = target ?: center,
            aLabel = if (fix != null) "GPS" else "map center",
            bLabel = if (target != null) "go-to" else "map center",
            onClose = { showLink.value = false },
        )
    }
    if (showFence.value) {
        val map = mapRef.value
        val center = map?.cameraPosition?.target?.let {
            AtlasCoordinate(latitude = it.latitude, longitude = it.longitude)
        }
        FenceDialog(
            center = center,
            fix = usableFixOf(services),
            fence = fence.value,
            onSet = { next ->
                fence.value = next
                pushFence(sink, next)
            },
            onClear = {
                fence.value = null
                pushFence(sink, null)
            },
            onClose = { showFence.value = false },
        )
    }
    if (showSettings.value) {
        SettingsDialog(
            keyProvider = services.keys,
            onClose = { showSettings.value = false },
        )
    }
    if (showGisLayers.value) {
        GisLayersSheet(
            datasets = gisDatasets,
            onDismiss = { showGisLayers.value = false },
            onImportClick = onImportGis,
            onToggleVisibility = { id, vis -> services.gisRepository.setDatasetVisibility(id, vis) },
            onDeleteDataset = { id -> services.gisRepository.removeDataset(id) },
            onUpdateStyle = { id, style -> services.gisRepository.updateDatasetStyle(id, style) },
        )
    }
    selectedGisFeature.value?.let { (feature, datasetName) ->
        GisFeatureInspectorSheet(
            feature = feature,
            datasetName = datasetName,
            onDismiss = { selectedGisFeature.value = null },
            onNavigateTo = { target ->
                services.goTo.activate(
                    id = feature.id,
                    latitude = target.latitude,
                    longitude = target.longitude,
                    label = feature.id,
                )
                mapRef.value?.let { map ->
                    com.sovereignatlas.atlas.goto.goToCameraIntent(
                        services.goTo,
                        map.cameraPosition.zoom,
                        map.cameraPosition.bearing,
                    )?.let { intent ->
                        applyCameraIntent(map, intent)
                    }
                }
            },
        )
    }
}
