// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sovereignatlas.atlas.ui.chat.GeoChatScreen
import com.sovereignatlas.atlas.ui.chat.GeoChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.JsonObject
import com.sovereignatlas.atlas.AtlasServices
import com.sovereignatlas.atlas.android.AndroidRoutingLoader
import com.sovereignatlas.atlas.android.DemSession
import com.sovereignatlas.atlas.android.SqliteDemTileStore
import com.sovereignatlas.atlas.geo.routing.LoadProfile
import com.sovereignatlas.atlas.geo.routing.RoutingRequest
import com.sovereignatlas.atlas.geo.routing.RoutingResult
import com.sovereignatlas.atlas.camera.AtlasCameraState
import com.sovereignatlas.atlas.geo.DemEngine
import com.sovereignatlas.atlas.geo.GeoPoint
import com.sovereignatlas.atlas.geo.LoSMode
import com.sovereignatlas.atlas.geo.LoSRequest
import com.sovereignatlas.atlas.geo.LoSResult
import com.sovereignatlas.atlas.geo.LineOfSightEngine
import com.sovereignatlas.atlas.geo.cot.CotMarker
import com.sovereignatlas.atlas.geo.cot.CotPli
import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.db.Waypoint
import com.sovereignatlas.atlas.geo.AtlasAngles
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGrids
import com.sovereignatlas.atlas.geo.MgrsEngine
import com.sovereignatlas.atlas.geo.MgrsGrid
import com.sovereignatlas.atlas.goto.goToCameraIntent
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.measure.MeasureSnapshot
import com.sovereignatlas.atlas.measure.MeasureUnit
import com.sovereignatlas.atlas.offline.DemTileStore
import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders
import com.sovereignatlas.atlas.ui.CompassOverlay
import com.sovereignatlas.atlas.tactical.RadialFence
import com.sovereignatlas.atlas.tactical.fencePolygon
import com.sovereignatlas.atlas.ui.FenceDialog
import com.sovereignatlas.atlas.ui.GoToCard
import com.sovereignatlas.atlas.ui.LayersDialog
import com.sovereignatlas.atlas.ui.MgrsHud
import com.sovereignatlas.atlas.geo.MgrsConverter
import com.sovereignatlas.atlas.ui.AtlasLoadingOverlay
import com.sovereignatlas.atlas.ui.LinkDialog
import com.sovereignatlas.atlas.ui.MeasurePanel
import com.sovereignatlas.atlas.ui.OfflineDialog
import com.sovereignatlas.atlas.ui.ScaleBar
import com.sovereignatlas.atlas.ui.SettingsDialog
import com.sovereignatlas.atlas.ui.settings.SettingsScreen
import com.sovereignatlas.atlas.ui.settings.SettingsViewModel
import com.sovereignatlas.atlas.ui.TacticalCrosshair
import com.sovereignatlas.atlas.ui.TrackDetailDialog
import com.sovereignatlas.atlas.ui.TracksDialog
import com.sovereignatlas.atlas.track.TrackRecorder
import com.sovereignatlas.atlas.track.ProfilePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.sovereignatlas.atlas.ui.WaypointEditorSheet
import com.sovereignatlas.atlas.ui.WaypointsDialog
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import kotlin.math.roundToInt
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

data class CameraState(
    val center: LatLng,
    val zoom: Double,
    val bearing: Double,
    val isIdle: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasMapScreen(
    services: AtlasServices,
    onFirstStyle: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }
    val repoWaypoints = remember { mutableStateOf<List<Waypoint>>(emptyList()) }
    val repoTracks = remember { mutableStateOf<List<Track>>(emptyList()) }
    val showWaypoints = remember { mutableStateOf(false) }
    val showTracks = remember { mutableStateOf(false) }
    val trackDetailId = remember { mutableStateOf<String?>(null) }
    val showOffline = remember { mutableStateOf(false) }
    val offlineTick = remember { mutableStateOf(0) }
    val headingUp = remember { mutableStateOf(false) }
    val pendingHeadingUp = remember { mutableStateOf(false) }
    val headingTick = remember { mutableStateOf(0) }
    val following = remember { mutableStateOf(false) }
    val showLayers = remember { mutableStateOf(false) }
    val showLink = remember { mutableStateOf(false) }
    val showFence = remember { mutableStateOf(false) }
    val showSettings = remember { mutableStateOf(false) }
    val showNodeSettings = remember { mutableStateOf(false) }
    val nodeSettingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(services.settingsRepository)
    }
    val cartoKey by services.keys.cartoKey.collectAsState()
    val losMode by services.losState.mode.collectAsStateWithLifecycle(initialValue = LoSMode.Inactive)
    val losResult by services.losState.result.collectAsStateWithLifecycle(initialValue = null)
    val activeOfflineMap by services.maps.activeMap.collectAsState()
    val activeDem by services.maps.activeDem.collectAsState()
    val demStoreState = remember { mutableStateOf<DemTileStore?>(null) }
    val demDbState = remember { mutableStateOf<SQLiteDatabase?>(null) }
    LaunchedEffect(activeDem) {
        demDbState.value?.let { db ->
            try {
                db.close()
            } catch (error: Exception) {
                Unit
            }
            demDbState.value = null
        }
        DemSession.engine = null
        DemSession.store = null
        services.tiles.demStore = null
        demStoreState.value = null
        val dem = activeDem
        if (dem != null) {
            try {
                val db = SQLiteDatabase.openDatabase(
                    dem.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                )
                val store = SqliteDemTileStore(db)
                demDbState.value = db
                demStoreState.value = store
                DemSession.store = store
                DemSession.engine = DemEngine(store, services.imageDecoder)
                services.tiles.demStore = store
            } catch (error: SQLiteException) {
                Log.w("AtlasMap", "DEM open failed: ${dem.name}", error)
            }
        }
    }
    val fence = remember { mutableStateOf<RadialFence?>(null) }
    val baseProviderId = remember { mutableStateOf("osm-standard") }
    val basePackId = remember { mutableStateOf<String?>(null) }
    val showGraticule = remember { mutableStateOf(false) }
    val showMgrsGrid = remember { mutableStateOf(false) }
    val mgrsCache = remember { mutableStateOf(MgrsGrid(emptyList(), emptyList())) }
    val showRings = remember { mutableStateOf(false) }
    val showWaypointsLayer = remember { mutableStateOf(true) }
    val showTrackLayer = remember { mutableStateOf(true) }
    val showMeasureLayer = remember { mutableStateOf(true) }
    val attribution = remember { mutableStateOf("") }
    val recorderTick = remember { mutableStateOf(0) }
    val goToTick = remember { mutableStateOf(0) }
    val positionTick = remember { mutableStateOf(0) }
    val view = LocalView.current
    val mapScope = rememberCoroutineScope()
    val refreshMgrsGrid: (MapLibreMap) -> Unit = { map ->
        mapScope.launch {
            val bounds = map.projection.visibleRegion.latLngBounds
            val box = AtlasBoundingBox(
                south = bounds.latitudeSouth,
                west = bounds.longitudeWest,
                north = bounds.latitudeNorth,
                east = bounds.longitudeEast,
            )
            val grid = withContext(Dispatchers.IO) {
                MgrsEngine.generate(box, map.cameraPosition.zoom)
            }
            mgrsCache.value = grid
            styleRef.value?.let { style ->
                pushFeatures(style, AtlasLayerIds.MGRS_LINE_SOURCE, mgrsLinesToFeatures(grid.lines))
                pushFeatures(style, AtlasLayerIds.MGRS_LABEL_SOURCE, mgrsLabelsToFeatures(grid.labels))
            }
        }
    }
    val onHaptic: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    val measureActive = remember { mutableStateOf(services.measure.isActive()) }
    val measureSnapshot = remember {
        mutableStateOf<MeasureSnapshot>(services.measure.snapshot())
    }
    val mapLoading = remember { mutableStateOf(true) }
    val hadOfflineMap = remember { mutableStateOf(false) }
    val cameraState = remember {
        MutableStateFlow(
            CameraState(
                center = LatLng(0.0, 0.0),
                zoom = 0.0,
                bearing = 0.0,
                isIdle = true,
            ),
        )
    }
    val targetDropPoint = remember { MutableStateFlow<LatLng?>(null) }
    // Shared post-style content: re-installs the atlas layer stack after any
    // full setStyle (initial load or MBTiles swap). applyOnlineBase=false
    // skips the online/pack base so the MBTiles source survives.
    val onStyleLoaded: (Style, MapLibreMap, Boolean) -> Unit = { style, map, applyOnlineBase ->
        styleRef.value = style
        mapLoading.value = false
        onFirstStyle()
        cameraState.value = CameraState(
            center = map.cameraPosition.target ?: LatLng(0.0, 0.0),
            zoom = map.cameraPosition.zoom,
            bearing = map.cameraPosition.bearing,
            isIdle = true,
        )
        services.tiles.demTileUrl()?.let { demUrl ->
            if (services.tiles.demAvailable()) ensureDemSource(style, demUrl)
        }
        if (applyOnlineBase) {
            applyBaseSource(style, services, baseProviderId.value, basePackId.value)
        }
        installAtlasLayers(style)
        ensureWaypointIcon(style)
        ensureGpsPuck(style, context)
        ensureScrubIcon(style)
        ensurePliMarker(style, context)
        pushScrubPoint(style, services.scrubState.activePoint.value)
        pushPli(style, services.pli.activePlis.value)
        pushMarkers(style, services.markers.markerStream.value)
        pushRouteResult(style, services.routing.result.value)
        if (showMgrsGrid.value) {
            val cached = mgrsCache.value
            pushFeatures(style, AtlasLayerIds.MGRS_LINE_SOURCE, mgrsLinesToFeatures(cached.lines))
            pushFeatures(style, AtlasLayerIds.MGRS_LABEL_SOURCE, mgrsLabelsToFeatures(cached.labels))
        }
        pushLosState(
            style,
            services.losState.observer.value,
            services.losState.target.value,
            services.losState.result.value,
        )
        applyOverlayVisibility(style, showGraticule.value, showMgrsGrid.value, showRings.value, showWaypointsLayer.value, showTrackLayer.value, showMeasureLayer.value)
        pushFeatures(style, AtlasLayerIds.WAYPOINTS_SOURCE, waypointsToFeatures(repoWaypoints.value))
        pushFeatures(
            style,
            AtlasLayerIds.TRACK_SOURCE,
            mergedTrackFeatures(repoTracks.value, services.recorder),
        )
        pushPosition(style, services)
        pushMeasure(style, services)
        pushGoTo(style, services)
        pushRings(style, services)
        if (showGraticule.value) pushGraticule(map, style)
        attribution.value = if (applyOnlineBase) {
            applyBaseSource(
                style,
                services,
                baseProviderId.value,
                basePackId.value,
                services.keys.cartoKey.value,
            )
        } else {
            "Offline map (MBTiles)"
        }
        services.behavior.startupCamera()?.let { intent ->
            applyCameraIntent(map, intent)
        }
    }
    val waypointSelection = remember { WaypointSelection() }
    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                mapRef.value = map
                map.uiSettings.apply {
                    // Compose HUD owns compass + scale: native widgets stay off.
                    isCompassEnabled = false
                }
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        services.behavior.markUserInteracted()
                        following.value = false
                    }
                }
                map.addOnCameraMoveListener {
                    val current = cameraState.value
                    cameraState.value = current.copy(
                        zoom = map.cameraPosition.zoom,
                        bearing = map.cameraPosition.bearing,
                        isIdle = false,
                    )
                }
                map.addOnMapClickListener { point ->
                    val losMode = services.losState.mode.value
                    if (losMode != LoSMode.Inactive) {
                        val tapped = GeoPoint(
                            point.latitude,
                            point.longitude,
                            null,
                            null,
                            null,
                            System.currentTimeMillis(),
                        )
                        if (losMode == LoSMode.AwaitingObserver) {
                            services.losState.setObserver(tapped)
                            services.losState.setResult(null)
                            services.losState.setMode(LoSMode.AwaitingTarget)
                        } else {
                            services.losState.setTarget(tapped)
                            services.losState.setMode(LoSMode.Inactive)
                            mapScope.launch {
                                runLosCalculation(services)
                            }
                        }
                        styleRef.value?.let { style ->
                            pushLosState(
                                style,
                                services.losState.observer.value,
                                services.losState.target.value,
                                services.losState.result.value,
                            )
                        }
                    } else if (services.measure.isActive()) {
                        services.measure.setB(
                            AtlasCoordinate(
                                latitude = point.latitude,
                                longitude = point.longitude,
                            ),
                        )
                    } else {
                        // addOnMapClickListener replaces the prior listener, so this
                        // single registration is swap-safe: the map outlives styles.
                        val screen = map.projection.toScreenLocation(point)
                        val hits = if (screen == null) {
                            emptyList()
                        } else {
                            map.queryRenderedFeatures(screen, AtlasLayerIds.WAYPOINTS_LAYER)
                        }
                        val hitId = hits.firstOrNull()?.getStringProperty("id")
                        if (hitId == null) {
                            waypointSelection.clearWaypointSelection()
                        } else {
                            waypointSelection.selectWaypoint(hitId)
                        }
                    }
                    true
                }
                map.addOnMapLongClickListener { point ->
                    onHaptic()
                    targetDropPoint.value = point
                    true
                }
                map.addOnCameraIdleListener {
                    cameraState.value = CameraState(
                        center = map.cameraPosition.target ?: LatLng(0.0, 0.0),
                        zoom = map.cameraPosition.zoom,
                        bearing = map.cameraPosition.bearing,
                        isIdle = true,
                    )
                    if (showMgrsGrid.value) refreshMgrsGrid(map)
                    styleRef.value?.let { style ->
                        if (showGraticule.value) pushGraticule(map, style)
                    }
                }
                val initialJson = services.maps.activeMap.value?.let { offline ->
                    buildMbtilesStyleJson(context, offline.absolutePath)
                } ?: if (services.tiles.demAvailable()) BLANK_STYLE_TERRAIN else BLANK_STYLE
                map.setStyle(Style.Builder().fromJson(initialJson)) { style ->
                    onStyleLoaded(
                        style,
                        map,
                        services.maps.activeMap.value == null,
                    )
                }
            }
        }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
    DisposableEffect(services) {
        val onLocation = {
            positionTick.value += 1
            val map = mapRef.value
            val style = styleRef.value
            if (map != null && style != null) {
                pushPosition(style, services)
                if (showRings.value) pushRings(style, services)
                if (following.value) {
                    val fix = usableFixOf(services)
                    if (fix != null) {
                        val followed = AtlasCameraState(
                            center = fix,
                            zoom = map.cameraPosition.zoom,
                            bearing = map.cameraPosition.bearing,
                            pitch = 0.0,
                        )
                        if (followed.validate().isValid) {
                            applyCameraIntent(map, followed)
                        }
                    }
                }
                services.behavior.onLocationUpdate(
                    map.cameraPosition.zoom,
                    map.cameraPosition.bearing,
                )?.let { intent ->
                    applyCameraIntent(map, intent)
                }
            }
        }
        val onMeasure: () -> Unit = {
            measureActive.value = services.measure.isActive()
            measureSnapshot.value = services.measure.snapshot()
            styleRef.value?.let { style -> pushMeasure(style, services) }
        }
        val onRecorder: () -> Unit = {
            recorderTick.value += 1
            styleRef.value?.let { style ->
                pushFeatures(
                    style,
                    AtlasLayerIds.TRACK_SOURCE,
                    mergedTrackFeatures(repoTracks.value, services.recorder),
                )
            }
        }
        val onGoTo: () -> Unit = {
            goToTick.value += 1
            styleRef.value?.let { style -> pushGoTo(style, services) }
            val target = services.goTo.targetOrNull()
            val fix = usableFixOf(services)
            if (target != null && fix != null) {
                mapScope.launch {
                    refreshFootRoute(services, context, fix.latitude, fix.longitude)
                }
            } else {
                services.routing.setResult(null)
            }
        }
        val onOffline: () -> Unit = {
            offlineTick.value += 1
        }
        val onHeading: () -> Unit = {
            headingTick.value += 1
            val degrees = services.heading.displayDeg()
            if (pendingHeadingUp.value && degrees != null) {
                pendingHeadingUp.value = false
                headingUp.value = true
                mapRef.value?.let { map -> rotateMap(map, -degrees) }
            } else if (headingUp.value && degrees != null) {
                mapRef.value?.let { map -> rotateMap(map, -degrees) }
            }
        }
        services.location.addListener(onLocation)
        services.measure.addListener(onMeasure)
        services.recorder.addListener(onRecorder)
        services.goTo.addListener(onGoTo)
        services.offline.addListener(onOffline)
        services.heading.addListener(onHeading)
        onMeasure()
        onDispose {
            services.location.removeListener(onLocation)
            services.measure.removeListener(onMeasure)
            services.recorder.removeListener(onRecorder)
            services.goTo.removeListener(onGoTo)
            services.offline.removeListener(onOffline)
            services.heading.removeListener(onHeading)
        }
    }
    val showTools = remember { mutableStateOf(false) }
    val showChat = remember { mutableStateOf(false) }
    val chatViewModel: GeoChatViewModel = viewModel { GeoChatViewModel(services) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                services.waypointRepository.waypoints.collect { waypoints ->
                    repoWaypoints.value = waypoints
                    styleRef.value?.let { style ->
                        pushFeatures(
                            style,
                            AtlasLayerIds.WAYPOINTS_SOURCE,
                            waypointsToFeatures(waypoints),
                        )
                    }
                }
            }
            launch {
                services.trackRepository.tracks.collect { tracks ->
                    repoTracks.value = tracks
                    styleRef.value?.let { style ->
                        pushFeatures(
                            style,
                            AtlasLayerIds.TRACK_SOURCE,
                            mergedTrackFeatures(tracks, services.recorder),
                        )
                    }
                }
            }
            launch {
                services.scrubState.activePoint.collect { point ->
                    styleRef.value?.let { style ->
                        pushScrubPoint(style, point)
                    }
                }
            }
            launch {
                services.pli.activePlis.collect { plis ->
                    styleRef.value?.let { style ->
                        pushPli(style, plis)
                    }
                }
            }
            launch {
                services.markers.markerStream.collect { markerMap ->
                    styleRef.value?.let { style ->
                        pushMarkers(style, markerMap)
                    }
                }
            }
            launch {
                services.routing.result.collect { result ->
                    styleRef.value?.let { style ->
                        pushRouteResult(style, result)
                    }
                }
            }
            launch {
                services.losState.result.collect { result ->
                    styleRef.value?.let { style ->
                        pushLosState(
                            style,
                            services.losState.observer.value,
                            services.losState.target.value,
                            result,
                        )
                    }
                }
            }
        }
    }
    val toolsScope = rememberCoroutineScope()
    val toolsSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chatSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nodeSettingsSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val onToggleFollow: () -> Unit = {
        following.value = !following.value
    }
    val onToggleHeadingUp: () -> Unit = {
        if (headingUp.value) {
            headingUp.value = false
            pendingHeadingUp.value = false
            mapRef.value?.let { map -> rotateMap(map, 0.0) }
        } else {
            services.heading.ensureStarted()
            val degrees = services.heading.displayDeg()
            if (degrees != null) {
                pendingHeadingUp.value = false
                headingUp.value = true
                mapRef.value?.let { map -> rotateMap(map, -degrees) }
            } else if (!services.heading.isUnsupported()) {
                pendingHeadingUp.value = true
            }
        }
    }
    val onLocate: () -> Unit = {
        val map = mapRef.value
        if (map != null) {
            when (
                val outcome = services.behavior.locate(
                    map.cameraPosition.zoom,
                    map.cameraPosition.bearing,
                )
            ) {
                is LocateOutcome.Applied -> applyCameraIntent(map, outcome.intent)
                LocateOutcome.Pending, LocateOutcome.Ignored -> Unit
            }
        }
    }
    val onMeasure: () -> Unit = {
        val map = mapRef.value
        val target = map?.cameraPosition?.target
        if (map != null && target != null) {
            val validFix =
                if (services.location.status() == AtlasLocationStatus.valid) {
                    services.location.latestFixOrNull()?.position
                } else {
                    null
                }
            services.measure.begin(
                validFix,
                AtlasCoordinate(
                    latitude = target.latitude,
                    longitude = target.longitude,
                ),
            )
        }
    }
    val openTool: (androidx.compose.runtime.MutableState<Boolean>) -> () -> Unit = { flag ->
        {
            showTools.value = false
            flag.value = true
        }
    }
    // Reactive CARTO refresh: when a key arrives while a CARTO base is
    // active, re-add the source through the same live-mutation path as a
    // manual provider switch. Camera is preserved; ui/ never calls setStyle.
    LaunchedEffect(cartoKey, baseProviderId.value, basePackId.value) {
        val style = styleRef.value
        if (cartoKey != null && style != null &&
            isCartoProvider(baseProviderId.value) && basePackId.value == null
        ) {
            attribution.value = applyBaseSource(
                style,
                services,
                baseProviderId.value,
                null,
                cartoKey,
            )
        }
    }
    // MBTiles swap: selecting an offline map reloads the style from the
    // bundled template over the native mbtiles:// protocol, then re-installs
    // the atlas layers. Deselecting falls back to the online base WITHOUT a
    // full setStyle, preserving camera and overlays.
    LaunchedEffect(activeOfflineMap, demStoreState.value, mapRef.value) {
        val map = mapRef.value
        if (activeOfflineMap != null && map != null) {
            hadOfflineMap.value = true
            val demUrl = if (demStoreState.value != null) {
                services.tiles.demTileUrl()
            } else {
                null
            }
            val json = buildMbtilesStyleJson(
                context,
                activeOfflineMap!!.absolutePath,
                demUrl,
            )
            map.setStyle(Style.Builder().fromJson(json)) { style ->
                onStyleLoaded(style, map, false)
            }
        } else if (activeOfflineMap == null && hadOfflineMap.value) {
            hadOfflineMap.value = false
            styleRef.value?.let { style ->
                attribution.value = applyBaseSource(
                    style,
                    services,
                    baseProviderId.value,
                    basePackId.value,
                    services.keys.cartoKey.value,
                )
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FloatingActionButton(
                onClick = onLocate,
                modifier = Modifier.semantics { stateDescription = "Locate" },
            ) {
                Text("Locate")
            }
            FloatingActionButton(
                onClick = onToggleFollow,
                modifier = Modifier.semantics {
                    stateDescription = if (following.value) "Follow active" else "Follow inactive"
                },
            ) {
                Text(if (following.value) "Unfollow" else "Follow")
            }
            FloatingActionButton(
                onClick = onMeasure,
                modifier = Modifier.semantics {
                    stateDescription = if (measureActive.value) "Measure active" else "Measure inactive"
                },
            ) {
                Text("Measure")
            }
            FloatingActionButton(
                onClick = { showTools.value = true },
                modifier = Modifier.semantics { stateDescription = "Tools" },
            ) {
                Text("Tools")
            }
        }
        if (showTools.value) {
            ModalBottomSheet(
                onDismissRequest = { showTools.value = false },
                sheetState = toolsSheetState,
            ) {
                ToolRow("Waypoints", "Open waypoints", openTool(showWaypoints))
                ToolRow("Tracks", "Open tracks", openTool(showTracks))
                ToolRow("Offline", "Open offline packs", openTool(showOffline))
                ToolRow("Layers", "Open layers", openTool(showLayers))
                ToolRow("Link", "Open radio link", openTool(showLink))
                ToolRow("Fence", "Open geofence", openTool(showFence))
                ToolRow("API Keys", "Open API keys", openTool(showSettings))
                ToolRow("Settings", "Open node settings", openTool(showNodeSettings))
                ToolRow(
                    "Chat",
                    "Open tactical chat",
                    {
                        toolsScope.launch {
                            try {
                                toolsSheetState.hide()
                            } finally {
                                showTools.value = false
                                showChat.value = true
                            }
                        }
                    },
                )
                ToolRow(
                    if (losMode != LoSMode.Inactive) "LoS off" else "LoS",
                    if (losMode != LoSMode.Inactive) {
                        "Cancel line-of-sight"
                    } else {
                        "Start line-of-sight"
                    },
                    {
                        toolsScope.launch {
                            try {
                                toolsSheetState.hide()
                            } finally {
                                showTools.value = false
                                if (losMode != LoSMode.Inactive) {
                                    services.losState.reset()
                                } else {
                                    services.losState.reset()
                                    services.losState.setMode(LoSMode.AwaitingObserver)
                                }
                            }
                        }
                    },
                )
                ToolRow(
                    if (headingUp.value) "North-up" else "Head-up",
                    if (headingUp.value) "Head-up on" else "Head-up off",
                    {
                        toolsScope.launch {
                            try {
                                toolsSheetState.hide()
                            } finally {
                                showTools.value = false
                                onToggleHeadingUp()
                            }
                        }
                    },
                )
            }
        }
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
        val targetDropPointState by targetDropPoint.collectAsStateWithLifecycle()
        if (targetDropPointState != null) {
            var errorMessage by remember { mutableStateOf<String?>(null) }
            ModalBottomSheet(onDismissRequest = { targetDropPoint.value = null }) {
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
                    val types = listOf(
                        Triple("Hostile", "a-h-G", MaterialTheme.colorScheme.error),
                        Triple("Neutral", "a-n-G", androidx.compose.ui.graphics.Color.Green),
                        Triple("Unknown", "a-u-G", androidx.compose.ui.graphics.Color.Yellow),
                        Triple("Waypoint", "b-m-p-w", androidx.compose.ui.graphics.Color.White),
                    )
                    types.forEach { (label, type, color) ->
                        Button(
                            onClick = {
                                mapScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            services.atakBroadcaster.sendMarker(
                                                type = type,
                                                callsign = label,
                                                lat = targetDropPointState!!.latitude,
                                                lon = targetDropPointState!!.longitude,
                                            )
                                        }
                                        if (type == "b-m-p-w") {
                                            val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
                                            services.waypointRepository.saveWaypoint(
                                                Waypoint(
                                                    id = UUID.randomUUID().toString(),
                                                    name = "WP-$stamp",
                                                    latitude = targetDropPointState!!.latitude,
                                                    longitude = targetDropPointState!!.longitude,
                                                    timestamp = System.currentTimeMillis(),
                                                    notes = "",
                                                ),
                                            )
                                        }
                                        targetDropPoint.value = null
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Failed to drop marker"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                        ) {
                            Text(label, color = androidx.compose.ui.graphics.Color.Black)
                        }
                    }
                }
            }
        }
        // MGRS HUD - only recompose when center changes AND isIdle is true
        val mgrsInput by remember {
            cameraState
                .filter { it.isIdle }
                .map { it.center }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null)
        // Compass - only recompose when bearing changes
        val bearingHud by remember {
            cameraState.map { it.bearing }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = 0.0)
        // Scale Bar - only recompose when zoom or latitude changes
        val scaleInput by remember {
            cameraState.map { it.zoom to it.center.latitude }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = 0.0 to 0.0)
        val mgrsText = remember(mgrsInput) {
            mgrsInput?.let {
                MgrsConverter.spaced(MgrsConverter.toMgrs(it.latitude, it.longitude))
            } ?: ""
        }
        val metersPerPixel = mapRef.value?.projection
            ?.getMetersPerPixelAtLatitude(scaleInput.second) ?: 0.0
        TacticalCrosshair(modifier = Modifier.align(Alignment.Center))
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MgrsHud(mgrsText = mgrsText)
            when (losMode) {
                LoSMode.AwaitingObserver -> Text(
                    text = "LoS: tap observer point",
                    fontSize = 12.sp,
                )
                LoSMode.AwaitingTarget -> Text(
                    text = "LoS: tap target point",
                    fontSize = 12.sp,
                )
                LoSMode.Inactive -> {
                    val error = losResult?.errorMessage
                    if (error != null) {
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color.Red,
                        )
                    } else if (losResult != null) {
                        val result = losResult!!
                        if (result.isVisible) {
                            Text(
                                text = "CLEAR",
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF39FF14),
                            )
                        } else {
                            Text(
                                text = "OBSTRUCTED at ${result.blockingDistanceMeters?.toInt()}m",
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color.Red,
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (recorderTick.value >= 0 && services.recorder.isRecording()) {
                Surface {
                    Text(
                        text = "REC • ${services.recorder.pointCount()} pts",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            CompassOverlay(
                bearing = bearingHud,
                onFaceNorth = {
                    headingUp.value = false
                    pendingHeadingUp.value = false
                    mapRef.value?.let { map ->
                        map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 300)
                    }
                },
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            ScaleBar(metersPerPixel = metersPerPixel)
        }
        goToTick.value.let {
            positionTick.value.let {
                if (services.goTo.isActive()) {
                    Surface(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                        GoToCard(
                            goTo = services.goTo,
                            usableFix =
                            if (services.location.status() == AtlasLocationStatus.valid) {
                                services.location.latestFixOrNull()?.position
                            } else {
                                null
                            },
                            onClear = { services.goTo.clear() },
                        )
                    }
                }
            }
        }
        if (measureActive.value) {            Surface(modifier = Modifier.align(Alignment.BottomCenter)) {
                MeasurePanel(
                    snapshot = measureSnapshot.value,
                    units = MeasureUnit.values().toList(),
                    onUnitSelected = { services.measure.setUnit(it) },
                    onClose = { services.measure.clear() },
                    onClear = { services.measure.clear() },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    goToCameraIntent(
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
            recorderTick.value.let {
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
            offlineTick.value.let {
                OfflineDialog(
                    store = services.offline,
                    maps = services.maps,
                    tileHits = { services.tiles.tileHits() },
                    basemapHits = { services.tiles.basemapHits() },
                    demHits = { services.tiles.demHits() },
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
        }
        if (showLayers.value) {
            LayersDialog(
                providerId = baseProviderId.value,
                onProviderSelected = { id ->
                    baseProviderId.value = id
                    basePackId.value = null
                    styleRef.value?.let { style ->
                        attribution.value = applyBaseSource(
                            style,
                            services,
                            id,
                            null,
                            services.keys.cartoKey.value,
                        )
                    }
                },
                showGraticule = showGraticule.value,
                onGraticuleChanged = { visible ->
                    showGraticule.value = visible
                    styleRef.value?.let { style ->
                        setAtlasLayerVisible(style, AtlasLayerIds.GRATICULE_LAYER, visible)
                        val map = mapRef.value
                        if (visible && map != null) pushGraticule(map, style)
                    }
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
                        if (visible) pushRings(style, services)
                    }
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
                onClose = { showLayers.value = false },
            )
        }
        if (showLink.value) {            val map = mapRef.value
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
        if (showFence.value) {            val map = mapRef.value
            val center = map?.cameraPosition?.target?.let {
                AtlasCoordinate(latitude = it.latitude, longitude = it.longitude)
            }
            FenceDialog(
                center = center,
                fix = usableFixOf(services),
                fence = fence.value,
                onSet = { next ->
                    fence.value = next
                    styleRef.value?.let { style -> pushFence(style, next) }
                },
                onClear = {
                    fence.value = null
                    styleRef.value?.let { style -> pushFence(style, null) }
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
        if (attribution.value.isNotEmpty()) {
            Surface(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(
                    text = attribution.value,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
        AtlasLoadingOverlay(visible = mapLoading.value)
    }
}

@Composable
private fun ToolRow(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .semantics { stateDescription = description },
    ) {
        Text(label)
    }
}

fun buildMbtilesStyleJson(context: Context, absolutePath: String): String {
    return buildMbtilesStyleJson(context, absolutePath, null)
}

fun buildMbtilesStyleJson(
    context: Context,
    absolutePath: String,
    demTileUrl: String?,
): String {
    val template = context.assets.open("offline_style.json").bufferedReader().use { it.readText() }
    var json = template.replace("___FILE_URI___", "mbtiles://file://$absolutePath")
    if (demTileUrl != null) {
        json = json.replace(
            "\"atlas-offline\": {",
            "\"dem-source\": {\n" +
                "      \"type\": \"raster-dem\",\n" +
                "      \"encoding\": \"mapbox\",\n" +
                "      \"tiles\": [\"$demTileUrl\"],\n" +
                "      \"tileSize\": 256\n" +
                "    },\n" +
                "    \"atlas-offline\": {",
        )
        json = json.replace(
            "\"id\": \"water\",",
            "\"id\": \"hillshade-layer\",\n" +
                "      \"type\": \"hillshade\",\n" +
                "      \"source\": \"dem-source\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"water\",",
        )
    }
    return json
}

fun applyCameraIntent(map: MapLibreMap, intent: AtlasCameraState) {    map.moveCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(intent.center.latitude, intent.center.longitude))
                .zoom(intent.zoom)
                .bearing(intent.bearing)
                .build(),
        ),
    )
}

fun rotateMap(map: MapLibreMap, bearing: Double) {
    map.moveCamera(
        CameraUpdateFactory.bearingTo(AtlasAngles.normalizeBearingDeg(bearing)),
    )
}

private val wpIconBitmap: Bitmap by lazy {
    val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
    canvas.drawCircle(12f, 12f, 12f, paint)
    bitmap
}

fun ensureWaypointIcon(style: Style) {
    if (style.getImage("wp-icon") == null) {
        style.addImage("wp-icon", wpIconBitmap)
    }
}

fun ensureGpsPuck(style: Style, context: Context) {
    if (style.getImage("gps-puck-icon") == null) {
        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            com.sovereignatlas.atlas.R.drawable.ic_gps_puck_sdf,
        ) ?: return
        style.addImage("gps-puck-icon", bitmap, true)
    }
}

fun ensurePliMarker(style: Style, context: Context) {
    if (style.getImage("blue-force-marker") == null) {
        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            com.sovereignatlas.atlas.R.drawable.ic_blue_force_marker,
        ) ?: return
        style.addImage("blue-force-marker", bitmap)
    }
}

fun markerColorHex(type: String): String {
    return when {
        type.startsWith("a-h-") -> "#ff0000"
        type.startsWith("a-n-") -> "#00ff00"
        type.startsWith("a-u-") -> "#ffff00"
        else -> "#ffffff"
    }
}

fun markersToFeatures(markers: Map<String, CotMarker>): FeatureCollection {
    return FeatureCollection.fromFeatures(
        markers.values.map { marker ->
            Feature.fromGeometry(
                Point.fromLngLat(marker.longitude, marker.latitude),
            ).apply {
                addStringProperty("color", markerColorHex(marker.type))
                addStringProperty("callsign", marker.callsign)
            }
        },
    )
}

fun pushMarkers(style: Style, markers: Map<String, CotMarker>) {
    pushFeatures(style, AtlasLayerIds.MARKER_SOURCE, markersToFeatures(markers))
}

fun pushPli(style: Style, plis: Map<String, CotPli>) {
    pushFeatures(
        style,
        AtlasLayerIds.PLI_SOURCE,
        FeatureCollection.fromFeatures(
            plis.values.map { pli ->
                val properties = JsonObject()
                properties.addProperty("callsign", pli.callsign)
                properties.addProperty("uid", pli.uid)
                Feature.fromGeometry(
                    Point.fromLngLat(pli.longitude, pli.latitude),
                    properties,
                )
            },
        ),
    )
}

private val scrubIconBitmap: Bitmap by lazy {
    val bitmap = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(14f, 14f, 11f, paint)
    bitmap
}

fun ensureScrubIcon(style: Style) {
    if (style.getImage("scrub-icon") == null) {
        style.addImage("scrub-icon", scrubIconBitmap)
    }
}

fun pushScrubPoint(style: Style, point: ProfilePoint?) {
    val features = if (point == null) {
        FeatureCollection.fromFeatures(emptyList())
    } else {
        FeatureCollection.fromFeatures(
            listOf(
                Feature.fromGeometry(
                    Point.fromLngLat(point.longitude, point.latitude),
                ),
            ),
        )
    }
    pushFeatures(style, AtlasLayerIds.SCRUB_SOURCE, features)
}

suspend fun refreshFootRoute(
    services: AtlasServices,
    context: Context,
    fromLat: Double,
    fromLng: Double,
) {
    val target = services.goTo.targetOrNull()
    if (target == null) {
        services.routing.setResult(null)
        return
    }
    val engine = services.routing.ensureEngine {
        AndroidRoutingLoader.load(context.applicationContext)
    } ?: return
    val startId = engine.nearestNodeId(fromLat, fromLng) ?: return
    val endId = engine.nearestNodeId(target.latitude, target.longitude) ?: return
    // PATROL is the default foot profile; a selector is follow-up UI.
    val result = engine.route(RoutingRequest(startId, endId, LoadProfile.PATROL))
    services.routing.setResult(result)
}

fun pushRouteResult(style: Style, result: RoutingResult?) {
    pushFeatures(
        style,
        AtlasLayerIds.ROUTE_SOURCE,
        if (result == null || result.path.size < 2) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeatures(
                listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(
                            result.path.map { node ->
                                Point.fromLngLat(node.longitude, node.latitude)
                            },
                        ),
                    ),
                ),
            )
        },
    )
}

suspend fun runLosCalculation(services: AtlasServices) {
    val observer = services.losState.observer.value
    val target = services.losState.target.value
    if (observer == null || target == null) return
    val engine = DemSession.engine
    if (engine == null) {
        services.losState.setResult(
            LoSResult(false, null, null, emptyList(), "DEM unavailable: activate a relief map."),
        )
        return
    }
    services.losState.setResult(
        LineOfSightEngine.calculate(LoSRequest(observer, target), engine),
    )
}

fun pushLosState(    style: Style,
    observer: GeoPoint?,
    target: GeoPoint?,
    result: LoSResult?,
) {
    pushFeatures(
        style,
        AtlasLayerIds.LOS_OBSERVER_SOURCE,
        if (observer == null) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(Point.fromLngLat(observer.longitude, observer.latitude))),
            )
        },
    )
    pushFeatures(
        style,
        AtlasLayerIds.LOS_TARGET_SOURCE,
        if (target == null) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(Point.fromLngLat(target.longitude, target.latitude))),
            )
        },
    )
    pushFeatures(
        style,
        AtlasLayerIds.LOS_SOURCE,
        if (observer == null || target == null) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeatures(
                listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(
                            listOf(
                                Point.fromLngLat(observer.longitude, observer.latitude),
                                Point.fromLngLat(target.longitude, target.latitude),
                            ),
                        ),
                    ),
                ),
            )
        },
    )
    pushFeatures(
        style,
        AtlasLayerIds.LOS_BLOCK_SOURCE,
        if (result?.blockingPoint == null) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            val block = result.blockingPoint
            FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(Point.fromLngLat(block.longitude, block.latitude))),
            )
        },
    )
    val ray = style.getLayerAs<LineLayer>(AtlasLayerIds.LOS_LAYER)
    ray?.setProperties(
        PropertyFactory.lineColor(if (result != null && !result.isVisible) "#FF0000" else "#39FF14"),
    )
}

fun mergedTrackFeatures(
    stored: List<Track>,
    recorder: TrackRecorder,
): FeatureCollection {
    val features = ArrayList<Feature>()
    tracksToFeatures(stored).features()?.let { features.addAll(it) }
    val active = recorder.points()
    if (active.size >= 2) {
        features.add(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    active.map { fix ->
                        Point.fromLngLat(
                            fix.position.longitude,
                            fix.position.latitude,
                        )
                    },
                ),
            ),
        )
    }
    return FeatureCollection.fromFeatures(features)
}

fun pushPosition(style: Style, services: AtlasServices) {
    val fix = services.location.latestFixOrNull() ?: return
    pushFeatures(
        style,
        AtlasLayerIds.POSITION_SOURCE,
        FeatureCollection.fromFeatures(
            listOf(positionToFeature(fix.position, fix.headingDeg)),
        ),
    )
}

fun pushMeasure(style: Style, services: AtlasServices) {    val measure = services.measure
    val a = measure.pointAOrNull()
    val b = measure.pointBOrNull()
    pushFeatures(
        style,
        AtlasLayerIds.MEASURE_SOURCE,
        if (a != null && b != null) {
            measureToFeatures(a, b)
        } else {
            FeatureCollection.fromFeatures(emptyList())
        },
    )
    val dots = ArrayList<Feature>()
    if (a != null) {
        dots.add(
            Feature.fromGeometry(Point.fromLngLat(a.longitude, a.latitude)),
        )
    }
    if (b != null) {
        dots.add(
            Feature.fromGeometry(Point.fromLngLat(b.longitude, b.latitude)),
        )
    }
    pushFeatures(
        style,
        AtlasLayerIds.MEASURE_DOTS_SOURCE,
        FeatureCollection.fromFeatures(dots),
    )
}

fun pushFence(style: Style, fence: RadialFence?) {
    pushFeatures(
        style,
        AtlasLayerIds.FENCE_SOURCE,
        if (fence == null || !fence.armed) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeatures(
                listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(
                            fencePolygon(fence.center, fence.radiusMeters).map { point ->
                                Point.fromLngLat(point.longitude, point.latitude)
                            },
                        ),
                    ),
                ),
            )
        },
    )
}

fun pushGoTo(style: Style, services: AtlasServices) {    val target = services.goTo.targetOrNull()
    pushFeatures(
        style,
        AtlasLayerIds.GOTO_SOURCE,
        if (target == null) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeatures(
                listOf(
                    goToToFeature(target.latitude, target.longitude, target.label),
                ),
            )
        },
    )
}

fun usableFixOf(services: AtlasServices): AtlasCoordinate? {
    return if (services.location.status() == AtlasLocationStatus.valid) {
        services.location.latestFixOrNull()?.position
    } else {
        null
    }
}

fun pushRings(style: Style, services: AtlasServices) {
    pushFeatures(
        style,
        AtlasLayerIds.RINGS_SOURCE,
        ringsToFeatures(usableFixOf(services), RING_STEP_INDEX),
    )
}

fun pushGraticule(map: MapLibreMap, style: Style) {
    val region = map.projection.visibleRegion.latLngBounds
    val box = AtlasBoundingBox(
        south = region.latitudeSouth,
        west = region.longitudeWest,
        north = region.latitudeNorth,
        east = region.longitudeEast,
    )
    if (!box.validate().isValid) {
        pushFeatures(style, AtlasLayerIds.GRATICULE_SOURCE, FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val grid = AtlasGrids.graticuleFor(
        box,
        AtlasGrids.intervalForZoom(map.cameraPosition.zoom.roundToInt()),
    )
    if (grid.meridians.size + grid.parallels.size > MAX_GRATICULE_LINES) {
        pushFeatures(style, AtlasLayerIds.GRATICULE_SOURCE, FeatureCollection.fromFeatures(emptyList()))
        return
    }
    pushFeatures(style, AtlasLayerIds.GRATICULE_SOURCE, graticuleToFeatures(box, AtlasGrids.intervalForZoom(map.cameraPosition.zoom.roundToInt())))
}

fun applyOverlayVisibility(
    style: Style,
    showGraticule: Boolean,
    showMgrsGrid: Boolean,
    showRings: Boolean,
    showWaypoints: Boolean,
    showTrack: Boolean,
    showMeasure: Boolean,
) {
    setAtlasLayerVisible(style, AtlasLayerIds.GRATICULE_LAYER, showGraticule)
    setAtlasLayerVisible(style, AtlasLayerIds.MGRS_LINE_LAYER, showMgrsGrid)
    setAtlasLayerVisible(style, AtlasLayerIds.MGRS_LABEL_LAYER, showMgrsGrid)
    setAtlasLayerVisible(style, AtlasLayerIds.RINGS_LAYER, showRings)
    setAtlasLayerVisible(style, AtlasLayerIds.WAYPOINTS_LAYER, showWaypoints)
    setAtlasLayerVisible(style, AtlasLayerIds.TRACK_LAYER, showTrack)
    setAtlasLayerVisible(style, AtlasLayerIds.MEASURE_LAYER, showMeasure)
    setAtlasLayerVisible(style, AtlasLayerIds.MEASURE_DOTS_LAYER, showMeasure)
}

fun ensureBaseLayer(style: Style, providerId: String, key: String? = null) {
    val descriptor = OfflineBuiltinProviders.lookup(providerId) ?: return
    var template = descriptor.urlTemplate ?: return
    for ((param, value) in descriptor.params) {
        template = template.replace("{$param}", value)
    }
    // Key-aware templates substitute {key}; current CARTO basemaps are
    // keyless, so a key arrival re-applies the identical source — the
    // reactive path exists so key rotation takes effect without camera loss.
    template = template.replace("{key}", key ?: "")
    ensureBaseTemplate(style, template, descriptor.minZoom, descriptor.maxZoom)
}

fun ensureBaseTemplate(style: Style, template: String, minZoom: Int, maxZoom: Int) {
    removeBaseLayer(style)
    val tileSet = TileSet("2.2.0", template)
    tileSet.minZoom = minZoom.toFloat()
    tileSet.maxZoom = maxZoom.toFloat()
    style.addSource(RasterSource(BASE_SOURCE_ID, tileSet, 256))
    val layer = RasterLayer(BASE_LAYER_ID, BASE_SOURCE_ID)
    layer.minZoom = minZoom.toFloat()
    layer.maxZoom = maxZoom.toFloat()
    if (style.getLayer(AtlasLayerIds.WAYPOINTS_LAYER) != null) {
        style.addLayerBelow(layer, AtlasLayerIds.WAYPOINTS_LAYER)
    } else {
        style.addLayer(layer)
    }
}

fun removeBaseLayer(style: Style) {
    if (style.getLayer(BASE_LAYER_ID) != null) {
        style.removeLayer(BASE_LAYER_ID)
    }
    if (style.getSource(BASE_SOURCE_ID) != null) {
        style.removeSource(BASE_SOURCE_ID)
    }
}

fun ensureDemSource(style: Style, demUrl: String) {
    if (style.getSource(DEM_SOURCE_ID) != null) {
        style.removeSource(DEM_SOURCE_ID)
    }
    val tileSet = TileSet("2.2.0", demUrl)
    tileSet.minZoom = 0f
    tileSet.maxZoom = 15f
    tileSet.encoding = "mapbox"
    style.addSource(RasterDemSource(DEM_SOURCE_ID, tileSet, 256))
}

fun removeDemSource(style: Style) {
    if (style.getSource(DEM_SOURCE_ID) != null) {
        style.removeSource(DEM_SOURCE_ID)
    }
}

fun applyBaseSource(
    style: Style,
    services: AtlasServices,
    providerId: String,
    packId: String?,
    key: String? = null,
): String {
    if (packId != null) {
        val pack = services.offline.lookup(packId)
        val url = services.tiles.tileUrl(packId)
        if (pack != null && url != null) {
            ensureBaseTemplate(style, url, pack.zMin, pack.zMax)
            return "${pack.providerTitle} (offline)"
        }
    }
    ensureBaseLayer(style, providerId, key)
    return OfflineBuiltinProviders.lookup(providerId)?.attribution ?: ""
}

fun isCartoProvider(providerId: String): Boolean {
    return providerId == "carto-positron" || providerId == "carto-dark-matter"
}

const val BASE_SOURCE_ID = "atlas-base"
const val BASE_LAYER_ID = "atlas-base-layer"
const val DEM_SOURCE_ID = "atlas-dem"
const val RING_STEP_INDEX = 3
const val MAX_GRATICULE_LINES = 240

private const val OVERVIEW_LAT = 0.0
private const val OVERVIEW_LNG = 0.0
private const val OVERVIEW_ZOOM = 2.0

private const val BLANK_STYLE = """
{
  "version": 8,
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#111111" }
    }
  ]
}
"""

private const val BLANK_STYLE_TERRAIN = """
{
  "version": 8,
  "sources": {},
  "terrain": { "source": "atlas-dem", "exaggeration": 1.0 },
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#111111" }
    }
  ]
}
"""
