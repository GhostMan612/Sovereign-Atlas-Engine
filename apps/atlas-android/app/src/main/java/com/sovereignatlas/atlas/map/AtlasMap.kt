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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
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
import com.sovereignatlas.atlas.geo.los.LoSStatus
import com.sovereignatlas.atlas.geo.los.TerrainProfile
import com.sovereignatlas.atlas.geo.cot.CotMarker
import com.sovereignatlas.atlas.geo.cot.CotPli
import com.sovereignatlas.atlas.core.HistoricalAsset
import com.sovereignatlas.atlas.core.HistoricalRecord
import com.sovereignatlas.atlas.geo.graphics.DrawingMode
import com.sovereignatlas.atlas.geo.graphics.OperationalGraphic
import com.sovereignatlas.atlas.geo.graphics.ZoneType
import com.sovereignatlas.atlas.map.graphics.GraphicsGeoJsonMapper
import com.sovereignatlas.atlas.ui.hud.TacticalDrawingToolbar
import com.sovereignatlas.atlas.offline.mbtiles.MbtilesPack
import com.sovereignatlas.atlas.offline.mbtiles.mbtilesLayerId
import com.sovereignatlas.atlas.offline.mbtiles.mbtilesSafeId
import com.sovereignatlas.atlas.offline.mbtiles.mbtilesSourceId
import com.sovereignatlas.atlas.offline.vectorTileTemplateUrl
import com.sovereignatlas.atlas.ui.los.TerrainProfileChart
import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.db.Waypoint
import com.sovereignatlas.atlas.field.WaypointSharingPolicy
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
import androidx.core.content.ContextCompat
import com.sovereignatlas.atlas.map.cot.CotGeoJsonMapper
import com.sovereignatlas.atlas.map.historical.HistoricalFeatureMapper
import com.sovereignatlas.atlas.map.historical.domainAssetId
import com.sovereignatlas.atlas.map.historical.toMapLibreFeatureCollection
import com.sovereignatlas.atlas.core.LandPatent
import com.sovereignatlas.atlas.core.SanbornBlueprint
import com.sovereignatlas.atlas.ui.CompassOverlay
import com.sovereignatlas.atlas.ui.hud.CompassTape
import com.sovereignatlas.atlas.ui.hud.TacNavHud
import com.sovereignatlas.atlas.ui.historical.HistoricalAssetSheet
import com.sovereignatlas.atlas.ui.historical.HistoricalRecordSheet
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
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
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
    // TacNav-X reads the same heading source the compass dial uses: the heading
    // service is listener-driven (no flow), so the tick is the recomposition key.
    // A null bearing means no sample yet - the HUD draws no readout rather than
    // inventing a heading.
    val currentBearing = remember(headingTick.value) { services.heading.displayDeg()?.toFloat() }
    val currentHeadingFrame = remember(headingTick.value) {
        if (services.heading.displayDeg() == null) null else services.heading.frameLabel()
    }
    val hudAccent = androidx.compose.ui.graphics.Color(0xFF39FF14)
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
    val losProfile by services.losState.profile.collectAsStateWithLifecycle(initialValue = null)
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
    // Refreshes every historical asset in the current viewport. Reading the
    // catalogue touches the filesystem, so the query runs on Dispatchers.IO and
    // the result is handed back to the MapLibre style, which owns the sources.
    // Bounds are clamped before use: a projection can report a viewport that wraps
    // the antimeridian, which AtlasBoundingBox encodes as west > east, and the
    // repository handles that shape, but an empty or non-finite projection during
    // a style swap must not be turned into a query that matches nothing.
    val refreshHistoricalAssets: (MapLibreMap) -> Unit = { map ->
        mapScope.launch {
            val region = map.projection.visibleRegion.latLngBounds
            val south = region.latitudeSouth
            val west = region.longitudeWest
            val north = region.latitudeNorth
            val east = region.longitudeEast
            val edges = listOf(south, west, north, east)
            if (edges.any { !it.isFinite() }) return@launch
            if (south > north || north > 90.0 || south < -90.0) return@launch
            val box = AtlasBoundingBox(
                south = south.coerceIn(-90.0, 90.0),
                west = west.coerceIn(-180.0, 180.0),
                north = north.coerceIn(-90.0, 90.0),
                east = east.coerceIn(-180.0, 180.0),
            )
            val assets = withContext(Dispatchers.IO) { services.historicalAssets.getAssets(box) }
            val patents = assets.filterIsInstance<LandPatent>()
            val blueprints = assets.filterIsInstance<SanbornBlueprint>()

            // Push an empty collection rather than skipping when nothing matched:
            // leaving the previous viewport's parcels on screen would show parcels
            // outside the visible area as if they were in it.
            val collection = patents.toMapLibreFeatureCollection()
            styleRef.value?.let { style ->
                pushFeatures(style, AtlasLayerIds.HISTORICAL_PATENTS_SOURCE, collection)
                syncHistoricalBlueprintLayers(style, blueprints)
            }
        }
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
    val activeTerrainProfile = remember { MutableStateFlow<TerrainProfile?>(null) }
    // Compass - only recompose when camera bearing changes. cameraState already
    // carries the bearing written by the existing addOnCameraMoveListener, so no
    // second listener or parallel flow is introduced.
    val bearingHud by remember {
        cameraState.map { it.bearing }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0.0)
    // State holder (not a by-delegated value): the MapView and its listeners are
    // remembered once, so long-lived callbacks must read a current .value rather
    // than capture an immutable snapshot from the first composition.
    val activeMbtilesPacks = remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(services) {
        services.settingsRepository.activeMbtilesPacks.collect { packs ->
            activeMbtilesPacks.value = packs
        }
    }
    val scannedPacks = remember { mutableStateOf<List<MbtilesPack>>(emptyList()) }
    val showLayerManager = remember { mutableStateOf(false) }
    // Drawing state is held in flows that outlive the map instance so the single
    // click listener (registered once inside getMapAsync) always reads the current
    // value instead of a snapshot from the first composition.
    val drawingModeFlow = remember { MutableStateFlow(DrawingMode.NONE) }
    val inProgressPointsFlow = remember { MutableStateFlow<List<AtlasCoordinate>>(emptyList()) }
    // Interrogated historical feature. Same rule as the drawing flows: created
    // before the remembered MapView so the long-lived click listener can write it.
    val selectedHistoricalRecord = remember { MutableStateFlow<HistoricalRecord?>(null) }
    val selectedRecord by selectedHistoricalRecord.collectAsStateWithLifecycle()
    // Resolved from the catalogue by id on tap. Kept separate from
    // selectedHistoricalRecord because that one is built from the rendered
    // feature of an offline pack, which is lossy; this one is the domain object.
    val selectedHistoricalAsset = remember { MutableStateFlow<HistoricalAsset?>(null) }
    val selectedAsset by selectedHistoricalAsset.collectAsStateWithLifecycle()
    // Bumped on every tap and on every dismissal so a catalogue lookup that
    // completes late cannot resurrect a sheet the operator already closed.
    val patentTapToken = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val drawingMode by drawingModeFlow.collectAsStateWithLifecycle()
    val inProgressPoints by inProgressPointsFlow.collectAsStateWithLifecycle()
    // Placeholder until the drawing tools UI ships: the operational-graphics
    // plumbing is live, the authored graphic list is not populated yet.
    val opsGraphics = remember { mutableStateOf<List<OperationalGraphic>>(emptyList()) }
    // Committed graphics plus a transient preview. The committed list is never
    // mutated by the preview, so the style-reload cache stays authoritative.
    val displayGraphics = remember(opsGraphics.value, drawingMode, inProgressPoints) {
        val list = opsGraphics.value.toMutableList()
        if (drawingMode != DrawingMode.NONE && inProgressPoints.isNotEmpty()) {
            val previewId = "preview-graphic"
            val preview: OperationalGraphic? = when (drawingMode) {
                DrawingMode.TACTICAL_LINE ->
                    OperationalGraphic.TacticalLine(previewId, inProgressPoints)
                DrawingMode.MEDEVAC_ZONE ->
                    OperationalGraphic.TacticalZone(previewId, inProgressPoints, ZoneType.MEDEVAC)
                DrawingMode.RESTRICTED_ZONE ->
                    OperationalGraphic.TacticalZone(previewId, inProgressPoints, ZoneType.RESTRICTED)
                DrawingMode.NONE -> null
            }
            if (preview != null) list.add(preview)
        }
        list
    }
    val layerManagerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        // A pbf offline pack cannot render through a raster source, so it takes
        // over the base slot with a VectorSource plus one fill/line layer per
        // declared source layer. Raster packs keep the untouched raster path.
        val activeVectorPack = services.maps.activeMap.value
            ?.takeIf { it.format.equals("pbf", ignoreCase = true) }
        if (activeVectorPack != null) {
            val vectorUrl = services.tiles.tileUrl(activeVectorPack.name)
                ?.let { vectorTileTemplateUrl(it) }
            if (vectorUrl != null) {
                ensureVectorBaseTemplate(
                    style = style,
                    template = vectorUrl,
                    minZoom = 0,
                    maxZoom = 16,
                    vectorLayerIds = activeVectorPack.vectorLayerIds,
                )
            }
        }
        installAtlasLayers(style)
        ensureWaypointIcon(style)
        ensureGpsPuck(style, context)
        ensureScrubIcon(style)
        ensurePliMarker(style, context)
        ensureCotTrackIcons(style, context)
        pushScrubPoint(style, services.scrubState.activePoint.value)
        pushPli(style, services.pli.observe().value)
        pushMarkers(style, services.markers.observe().value)
        pushMeshTracks(style, services.markers.observe().value)
        pushOpsGraphics(style, displayGraphics)
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
            services.losState.profile.value,
        )
        applyOverlayVisibility(style, showGraticule.value, showMgrsGrid.value, showRings.value, showWaypointsLayer.value, showTrackLayer.value, showMeasureLayer.value)
        // Patents are pushed here as well as on camera idle: a style reload
        // recreates an empty source, and a camera that never moves again would
        // otherwise leave the viewport with no parcels at all.
        refreshHistoricalAssets(map)
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
        syncMbtilesPackLayers(style, services, activeMbtilesPacks.value)
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
                // Global camera ceiling, pinned once here. The MapLibre default is
                // 22.0; 24.0 permits 4 levels of overzoom past the Carto source
                // max of 20, which is 2^4 = 16x linear magnification before tiles
                // stop being readable. The SDK hard cap is 25.5 and this stays
                // under it, so no silent clamping occurs. This is GLOBAL, so it
                // also governs offline MBTiles packs whose TileSet maxZoom is
                // unset; that path is expected to request literal z24 tiles and
                // is called out as a known follow-up in the commit message.
                map.setMaxZoomPreference(24.0)
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
                    // Drawing intercept runs first and consumes the tap while a
                    // mode is active; every other tap behavior below is unchanged.
                    if (drawingModeFlow.value != DrawingMode.NONE) {
                        inProgressPointsFlow.value = inProgressPointsFlow.value + AtlasCoordinate(
                            latitude = point.latitude,
                            longitude = point.longitude,
                        )
                        return@addOnMapClickListener true
                    }
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
                            services.losState.setProfile(null)
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
                                services.losState.profile.value,
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
                        // Historical interrogation runs before waypoint selection so
                        // a polygon hit does not also clear the waypoint selection.
                        // IDs come from the active pbf pack's vector_layers metadata,
                        // so this stays empty for raster packs and for a pbf pack
                        // whose metadata declares no layers.
                        if (screen != null) {
                            val historicalLayerIds = services.maps.activeMap.value
                                ?.takeIf { it.format.equals("pbf", ignoreCase = true) }
                                ?.vectorLayerIds
                                ?.map { AtlasLayerIds.HISTORICAL_FILL_PREFIX + it }
                                ?: emptyList()
                            for (layerId in historicalLayerIds) {
                                val hit = map.queryRenderedFeatures(screen, layerId).firstOrNull()
                                if (hit != null) {
                                    // This tap selected a different historical source,
                                    // so any open patent sheet is now describing
                                    // something the operator is no longer pointing at.
                                    patentTapToken.incrementAndGet()
                                    selectedHistoricalAsset.value = null
                                    selectedHistoricalRecord.value =
                                        HistoricalFeatureMapper.fromFeature(hit, layerId)
                                    return@addOnMapClickListener true
                                }
                            }
                            // Catalogue patents are interrogated after the offline
                            // pack layers: a pack layer that hits under the same tap
                            // is the older, more specific source.
                            val patentHit = map
                                .queryRenderedFeatures(screen, AtlasLayerIds.HISTORICAL_PATENTS_FILL)
                                .firstOrNull()
                            val patentId = patentHit?.domainAssetId()
                            if (patentId != null) {
                                // Token guards against a slow lookup resolving after
                                // the operator has already tapped something else or
                                // dismissed the sheet. Comparing the flow's own value
                                // is not enough: after a dismissal the flow is null
                                // again, and the stale result would re-open the sheet.
                                val token = patentTapToken.incrementAndGet()
                                mapScope.launch {
                                    // Reading the catalogue touches the filesystem,
                                    // so it leaves the main thread.
                                    val asset = withContext(Dispatchers.IO) {
                                        services.historicalAssets.getAssetById(patentId)
                                    }
                                    if (token == patentTapToken.get()) {
                                        selectedHistoricalAsset.value = asset
                                    }
                                }
                                return@addOnMapClickListener true
                            }
                        }
                        // A tap that reaches here hit no historical feature at all, so
                        // any open sheet is describing something off-screen. The token
                        // bump also cancels a lookup still in flight from a prior tap.
                        patentTapToken.incrementAndGet()
                        selectedHistoricalAsset.value = null
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
                    activeTerrainProfile.value = null
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
                    refreshHistoricalAssets(map)
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
                services.pli.observe().collect { plis ->
                    styleRef.value?.let { style ->
                        pushPli(style, plis)
                    }
                }
            }
            launch {
                services.markers.observe().collect { markerMap ->
                    styleRef.value?.let { style ->
                        pushMarkers(style, markerMap)
                        pushMeshTracks(style, markerMap)
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
                services.losState.profile.collect { profile ->
                    styleRef.value?.let { style ->
                        pushLosState(
                            style,
                            services.losState.observer.value,
                            services.losState.target.value,
                            profile,
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
            // Exclusivity: measurement wins the bottom-center band, so any
            // in-progress drawing draft is discarded on activation.
            if (services.measure.isActive()) {
                drawingModeFlow.value = DrawingMode.NONE
                inProgressPointsFlow.value = emptyList()
            }
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
    LaunchedEffect(activeMbtilesPacks.value) {
        val style = styleRef.value ?: return@LaunchedEffect
        syncMbtilesPackLayers(style, services, activeMbtilesPacks.value)
    }
    // The TacNav readout needs a live heading; the dial previously started the
    // sensor only when head-up mode was toggled. Start it for the HUD too.
    LaunchedEffect(services) {
        services.heading.ensureStarted()
    }
    LaunchedEffect(displayGraphics) {
        val style = styleRef.value ?: return@LaunchedEffect
        pushOpsGraphics(style, displayGraphics)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        // TacNav-X overlay: full-screen so the center reticle stays on the map
        // center. Only the reticle is drawn here; the bearing readout is stacked
        // below the compass tape in the top Column. No pointer input, so map
        // gestures pass straight through.
        TacNavHud(
            bearing = currentBearing,
            hudColor = hudAccent,
            frameLabel = currentHeadingFrame,
            showReadout = false,
        )
        Column(
            modifier = Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // No Locate FAB: tapping the compass rose locates, long-press faces
            // north. The extra button only crowded the rail.
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
                onClick = {
                    showLayerManager.value = true
                    mapScope.launch {
                        scannedPacks.value = services.mbtilesScanner.scanPacks()
                    }
                },
                modifier = Modifier.semantics {
                    stateDescription = "Open encyclopedia layer manager"
                },
            ) {
                Text("Layers ${activeMbtilesPacks.value.size}")
            }
            FloatingActionButton(
                onClick = { showTools.value = true },
                modifier = Modifier.semantics { stateDescription = "Tools" },
            ) {
                Text("Tools")
            }
        }
        if (!measureActive.value) {
            TacticalDrawingToolbar(
                currentMode = drawingMode,
                pointCount = inProgressPoints.size,
                onModeSelected = { mode ->
                    drawingModeFlow.value = mode
                    inProgressPointsFlow.value = emptyList()
                    // Exclusivity: taking the glass for drawing releases the
                    // measurement session (and vice versa via the Measure FAB).
                    if (mode != DrawingMode.NONE && services.measure.isActive()) {
                        services.measure.clear()
                    }
                },
                onUndo = {
                    val current = inProgressPointsFlow.value
                    if (current.isNotEmpty()) {
                        inProgressPointsFlow.value = current.dropLast(1)
                    }
                },
                onCommit = {
                    val points = inProgressPointsFlow.value
                    val mode = drawingModeFlow.value
                    val committed: OperationalGraphic? = when (mode) {
                        DrawingMode.TACTICAL_LINE ->
                            OperationalGraphic.TacticalLine(UUID.randomUUID().toString(), points)
                        DrawingMode.MEDEVAC_ZONE ->
                            OperationalGraphic.TacticalZone(
                                UUID.randomUUID().toString(),
                                points,
                                ZoneType.MEDEVAC,
                            )
                        DrawingMode.RESTRICTED_ZONE ->
                            OperationalGraphic.TacticalZone(
                                UUID.randomUUID().toString(),
                                points,
                                ZoneType.RESTRICTED,
                            )
                        DrawingMode.NONE -> null
                    }
                    if (committed != null) {
                        opsGraphics.value = opsGraphics.value + committed
                    }
                    drawingModeFlow.value = DrawingMode.NONE
                    inProgressPointsFlow.value = emptyList()
                },
                // The right-rail FABs own the bottom-right corner, so the toolbar
                // reserves that width (56dp FAB + 16dp rail padding + gap). The
                // mode picker is collapsed to one button, so the row no longer
                // needs to scroll.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 8.dp, end = 88.dp, bottom = 8.dp)
            )
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
        if (showLayerManager.value) {
            ModalBottomSheet(
                onDismissRequest = { showLayerManager.value = false },
                sheetState = layerManagerSheetState,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Encyclopedia Layers",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (scannedPacks.value.isEmpty()) {
                        Text(
                            "No .mbtiles packs found in the offline packs directory.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    scannedPacks.value.forEach { pack ->
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
                                checked = activeMbtilesPacks.value.contains(pack.packId),
                                onCheckedChange = { enabled ->
                                    val current = activeMbtilesPacks.value
                                    val next =
                                        if (enabled) current + pack.packId
                                        else current - pack.packId
                                    services.settingsRepository.setActiveMbtilesPacks(next)
                                },
                            )
                        }
                    }
                }
            }
        }
        val targetDropPointState by targetDropPoint.collectAsStateWithLifecycle()
        val activeProfileState by activeTerrainProfile.collectAsStateWithLifecycle()
        val currentLocationFix = services.locationEngine.currentLocation.collectAsStateWithLifecycle().value
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
                                    // Everything else was never measured. Saying
                                    // "BLOCKED" here would assert a terrain finding
                                    // the engine did not make.
                                    else -> "LINE OF SIGHT UNDETERMINED"
                                },
                                color = when (losProfile.lineOfSight.status) {
                                    LoSStatus.Clear -> androidx.compose.ui.graphics.Color.Green
                                    LoSStatus.BlockedTerrain -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                },
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }
                    } else if (currentLocationFix != null && targetDropPointState != null) {
                        Button(
                            onClick = {
                                mapScope.launch {
                                    val drop = targetDropPointState ?: return@launch
                                    val origin = currentLocationFix ?: return@launch
                                    val profile = withContext(Dispatchers.IO) {
                                        services.lineOfSightEngine.calculateProfile(
                                            start = origin,
                                            end = GeoPoint(
                                                drop.latitude,
                                                drop.longitude,
                                                null,
                                                null,
                                                null,
                                                origin.timestamp,
                                            ),
                                        )
                                    }
                                    activeTerrainProfile.value = profile
                                }
                            },
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
                            color = androidx.compose.ui.graphics.Color.Gray,
                            modifier = Modifier.padding(bottom = 16.dp),
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
                                        val result = withContext(Dispatchers.IO) {
                                            services.atakBroadcaster.sendMarker(
                                                type = type,
                                                callsign = label,
                                                lat = targetDropPointState!!.latitude,
                                                lon = targetDropPointState!!.longitude,
                                            )
                                        }
                                        // Local echo under the SAME wire uid: the
                                        // marker is visible immediately instead of
                                        // depending on multicast loopback, and a later
                                        // mesh round-trip overwrites the same key
                                        // rather than duplicating it.
                                        services.markers.upsert(
                                            CotMarker(
                                                uid = result.uid,
                                                type = type,
                                                callsign = label,
                                                latitude = targetDropPointState!!.latitude,
                                                longitude = targetDropPointState!!.longitude,
                                                altitude = null,
                                                timestampMillis = System.currentTimeMillis(),
                                            )
                                        )
                                        if (!result.transmitted) {
                                            errorMessage = "Radio offline — marker saved locally only"
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
                                                    // A waypoint dropped from a broadcast
                                                    // marker is PRIVATE by default. Phase 10
                                                    // §10.6 requires explicit opt-in, so
                                                    // creating one must not also publish it.
                                                    sharingPolicy = WaypointSharingPolicy.Private.storedValue,
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
        // Top band: opaque black so the system status bar (clock, date, battery)
        // reads on top of it instead of over raw map imagery, and so the cutout
        // never lands on the tape. Content is inset below the bar.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.Black)
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompassTape(
                bearing = currentBearing?.toDouble() ?: bearingHud,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Left column: MGRS readout plus the LoS status line, both inset below
        // the black band so they never collide with it.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars))
                .padding(start = 8.dp, top = 48.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MgrsHud(mgrsText = mgrsText)
            when (losMode) {
                LoSMode.AwaitingObserver -> Text(
                    text = "LoS: tap observer point",
                    fontSize = 12.sp,
                    color = hudAccent,
                )
                LoSMode.AwaitingTarget -> Text(
                    text = "LoS: tap target point",
                    fontSize = 12.sp,
                    color = hudAccent,
                )
                LoSMode.Inactive -> {
                    val verdict = losProfile?.lineOfSight
                    val error = losProfile?.errorMessage
                    if (verdict == null) {
                        Unit
                    } else if (error != null) {
                        // Undetermined, not blocked. Red would claim a terrain
                        // finding; amber says the question was not answered.
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color(0xFFFFA500),
                        )
                    } else {
                        if (verdict.status == LoSStatus.Clear) {
                            Text(
                                text = "CLEAR",
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF39FF14),
                            )
                        } else {
                            Text(
                                text = "OBSTRUCTED at ${verdict.blockingDistanceMeters?.toInt()}m",
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color.Red,
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars))
                .padding(end = 8.dp, top = 48.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                onLocate = onLocate,
                onFaceNorth = {
                    headingUp.value = false
                    pendingHeadingUp.value = false
                    mapRef.value?.let { map ->
                        map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 300)
                    }
                },
            )
            // Bearing readout sits directly under the rose, mirroring the TacNav
            // readout style so the right column carries heading, not the center.
            TacNavHud(
                bearing = currentBearing,
                hudColor = hudAccent,
                frameLabel = currentHeadingFrame,
                showReticle = false,
                fillScreen = false,
                topPadding = 0.dp,
                modifier = Modifier.width(160.dp).height(56.dp),
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
        if (measureActive.value && drawingMode == DrawingMode.NONE) {            Surface(modifier = Modifier.align(Alignment.BottomCenter)) {
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
            // No Surface: an opaque panel buried the lower-left glass. White text
            // with a shadow stays legible over snow, sand, and satellite imagery
            // without hiding the map behind it.
            Text(
                text = attribution.value,
                fontSize = 9.sp,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                style = TextStyle(
                    shadow = Shadow(
                        color = androidx.compose.ui.graphics.Color.Black,
                        blurRadius = 6f,
                    )
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 8.dp, bottom = 8.dp),
            )
        }
        AtlasLoadingOverlay(visible = mapLoading.value)
    }
    HistoricalRecordSheet(
        record = selectedRecord,
        onDismissRequest = { selectedHistoricalRecord.value = null }
    )
    HistoricalAssetSheet(
        asset = selectedAsset,
        onDismissRequest = {
            patentTapToken.incrementAndGet()
            selectedHistoricalAsset.value = null
        },
    )
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
    // Both names, one drawable. The LoS observer layer asks for "user-puck" and the
    // position layer asks for "gps-puck-icon"; they are the same puck seen from two
    // tools, and only the second was ever registered. The observer marker therefore
    // had features in its source and painted nothing — the standing-facts failure
    // mode (source has data, layer silently blank) with no error anywhere to
    // explain it.
    //
    // Aliased rather than given its own asset: a second drawable would be a second
    // thing to keep in sync for no visual gain. Registering both names from one
    // decode also means one bitmap object, not two.
    if (style.getImage("gps-puck-icon") == null || style.getImage(AtlasLayerIds.USER_PUCK_IMAGE) == null) {
        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            com.sovereignatlas.atlas.R.drawable.ic_gps_puck_sdf,
        ) ?: return
        if (style.getImage("gps-puck-icon") == null) {
            style.addImage("gps-puck-icon", bitmap, true)
        }
        if (style.getImage(AtlasLayerIds.USER_PUCK_IMAGE) == null) {
            style.addImage(AtlasLayerIds.USER_PUCK_IMAGE, bitmap, true)
        }
    }
}

fun ensurePliMarker(style: Style, context: Context) {
    if (style.getImage("blue-force-marker") == null) {
        // ic_blue_force_marker is a VectorDrawable; BitmapFactory cannot decode it
        // and returns null, so the Drawable overload is required here.
        val pliDrawable = ContextCompat.getDrawable(
            context,
            com.sovereignatlas.atlas.R.drawable.ic_blue_force_marker,
        )
        if (pliDrawable == null) {
            Log.e("AtlasMap", "Failed to load PLI marker drawable")
            return
        }
        style.addImage("blue-force-marker", pliDrawable)
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

fun pushOpsGraphics(style: Style, graphics: List<OperationalGraphic>) {
    val source = style.getSourceAs<GeoJsonSource>(AtlasLayerIds.OPS_GRAPHICS_SOURCE) ?: return
    source.setGeoJson(GraphicsGeoJsonMapper.toFeatureCollection(graphics))
}

fun ensureCotTrackIcons(style: Style, context: Context) {
    val icons = listOf(
        "friendly" to com.sovereignatlas.atlas.R.drawable.ic_friendly,
        "hostile" to com.sovereignatlas.atlas.R.drawable.ic_hostile,
        "neutral" to com.sovereignatlas.atlas.R.drawable.ic_neutral,
        "unknown" to com.sovereignatlas.atlas.R.drawable.ic_unknown,
    )
    for ((name, resId) in icons) {
        if (style.getImage(name) != null) continue
        val drawable = ContextCompat.getDrawable(context, resId) ?: continue
        style.addImage(name, drawable)
    }
}

fun pushMeshTracks(style: Style, markers: Map<String, CotMarker>) {
    val source = style.getSourceAs<GeoJsonSource>(AtlasLayerIds.MESH_TRACK_SOURCE) ?: return
    source.setGeoJson(CotGeoJsonMapper.toFeatureCollection(markers.values.toList()))
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
    // One engine for both the two-tap tool and the long-press analysis sheet, so
    // the map overlay and the profile chart cannot contradict each other. When no
    // DEM is active the engine reports NoTerrainData itself - a distinct verdict,
    // not a synthetic "blocked".
    services.losState.setProfile(
        services.lineOfSightEngine.calculateProfile(LoSRequest(observer, target)),
    )
}

fun pushLosState(
    style: Style,
    observer: GeoPoint?,
    target: GeoPoint?,
    profile: TerrainProfile?,
) {
    val verdict = profile?.lineOfSight
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
        if (verdict?.blockingPoint == null) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            val block = verdict.blockingPoint!!
            FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(Point.fromLngLat(block.longitude, block.latitude))),
            )
        },
    )
    val ray = style.getLayerAs<LineLayer>(AtlasLayerIds.LOS_LAYER)
    // Amber, not red, for an unmeasured verdict. Red means terrain BLOCKED the ray;
    // painting unmeasured ground red asserts a terrain finding nobody made.
    //
    // The three-arm when this replaced collapsed IncompleteTerrain, NoTerrainData,
    // DegenerateGeometry and ProviderError into one amber, undoing the Phase 9 §9.5
    // split. LosRenderStyle now carries one entry per status, and the dash pattern is
    // the second channel: a dashed line is visibly "partly an assumption" where a
    // solid one reads as a finding.
    val render = LosRenderStyle.forStatus(verdict?.status)
    ray?.setProperties(
        PropertyFactory.lineColor(render.lineColor),
        PropertyFactory.lineWidth(render.lineWidth),
    )
    // A null dash array clears the pattern, which is why the solid states pass null
    // rather than "no dash set": a CLEAR verdict after an INCOMPLETE one must return
    // to a solid line, and skipping the call would leave the previous dashes up.
    ray?.setProperties(
        if (render.lineDashArray == null) {
            PropertyFactory.lineDasharray(null as Array<Float>?)
        } else {
            PropertyFactory.lineDasharray(render.lineDashArray)
        },
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
    // {key} becomes the query parameter of the provider template, for example
    // ".../{z}/{x}/{y}.png?key={key}" for CARTO. A null key leaves the parameter
    // empty, which still resolves to a valid watermarked request rather than a
    // malformed URL, and the reactive LaunchedEffect above re-applies the source
    // when a key arrives so no app restart is needed.
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
    // minZoom stays, maxZoom is deliberately NOT set. A layer maxZoom is a hard
    // render cutoff, not a hint: with it set to the provider max the base layer
    // vanished at zoom 20 while the camera continued past it, which presented as
    // a black screen with the overlays still drawing. MapLibre overzooms the
    // source tiles above tileSet.maxZoom, so leaving the layer unconstrained
    // makes the raster stretch instead of self-culling. Only this base layer is
    // affected; MGRS, waypoint and route layers keep their own limits.
    layer.minZoom = minZoom.toFloat()
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
    // Per-source-layer vector fill/line layers from a pbf pack have no single
    // ID, so they are cleared by prefix or they would survive a pack switch.
    for (layer in style.layers) {
        val id = layer.id
        if (id.startsWith(AtlasLayerIds.HISTORICAL_FILL_PREFIX) ||
            id.startsWith(AtlasLayerIds.HISTORICAL_LINE_PREFIX)
        ) {
            style.removeLayer(id)
        }
    }
}

fun ensureVectorBaseTemplate(
    style: Style,
    template: String,
    minZoom: Int,
    maxZoom: Int,
    vectorLayerIds: List<String>,
) {
    removeBaseLayer(style)
    val tileSet = TileSet("2.2.0", template)
    tileSet.minZoom = minZoom.toFloat()
    tileSet.maxZoom = maxZoom.toFloat()
    style.addSource(VectorSource(BASE_SOURCE_ID, tileSet))
    val anchor = if (style.getLayer(AtlasLayerIds.MGRS_LINE_LAYER) != null) {
        AtlasLayerIds.MGRS_LINE_LAYER
    } else if (style.getLayer(AtlasLayerIds.WAYPOINTS_LAYER) != null) {
        AtlasLayerIds.WAYPOINTS_LAYER
    } else {
        null
    }
    for (internalLayerId in vectorLayerIds) {
        val fillLayerId = AtlasLayerIds.HISTORICAL_FILL_PREFIX + internalLayerId
        if (style.getLayer(fillLayerId) == null) {
            val fillLayer = FillLayer(fillLayerId, BASE_SOURCE_ID)
                .withSourceLayer(internalLayerId)
                .withProperties(
                    PropertyFactory.fillColor("#4A90E2"),
                    PropertyFactory.fillOpacity(0.4f),
                )
            addBelowAnchor(style, fillLayer, anchor)
        }
        val lineLayerId = AtlasLayerIds.HISTORICAL_LINE_PREFIX + internalLayerId
        if (style.getLayer(lineLayerId) == null) {
            val lineLayer = LineLayer(lineLayerId, BASE_SOURCE_ID)
                .withSourceLayer(internalLayerId)
                .withProperties(
                    PropertyFactory.lineColor("#003366"),
                    PropertyFactory.lineWidth(1.5f),
                )
            addBelowAnchor(style, lineLayer, anchor)
        }
    }
}

private fun addBelowAnchor(style: Style, layer: Layer, anchor: String?) {
    if (anchor == null) {
        style.addLayer(layer)
    } else {
        style.addLayerBelow(layer, anchor)
    }
}

/**
 * Mounts one RasterSource + RasterLayer per Sanborn blueprint in view, and hides
 * the ones that have left the viewport.
 *
 * The tile URL is the app's established MBTiles form, `mbtiles://file://<abs>`,
 * the same one `buildMbtilesStyleJson` substitutes into `offline_style.json` for
 * an offline base map. MapLibre Native resolves that scheme against the file
 * directly, so a Sanborn pack is read without going through the loopback tile
 * server and without the pack needing to live in the pack-journal directory.
 *
 * Visibility is toggled rather than layers being torn down. A pack is expensive to
 * mount (open database, build TileSet) and cheap to hide, and an operator moving
 * back and forth across a city edge would otherwise thrash the engine. A layer left
 * hidden holds memory for the rest of the style's life, which is the deliberate
 * trade here.
 *
 * A blueprint with no recorded file path is skipped rather than mounted against an
 * empty URI: the source would be created and would never resolve a tile.
 */
fun syncHistoricalBlueprintLayers(
    style: Style,
    inView: List<SanbornBlueprint>,
) {
    val visible = inView.associateBy { blueprint -> historicalRasterLayerId(blueprint.id) }

    for (blueprint in inView) {
        if (blueprint.filePath.isEmpty()) continue
        val layerId = historicalRasterLayerId(blueprint.id)
        if (style.getLayer(layerId) != null) continue
        val tileSet = TileSet("2.2.0", "mbtiles://file://${blueprint.filePath}")
        // The pack's own zoom range, not the camera ceiling. With maxzoom unset
        // MapLibre asks for literal z24 tiles of a 17-max pack and renders black
        // at high zoom.
        blueprint.maxZoom?.let { tileSet.maxZoom = it }
        blueprint.minZoom?.let { tileSet.minZoom = it }
        style.addSource(RasterSource(historicalRasterSourceId(blueprint.id), tileSet))
        // Plain addLayer: addLayerBelow has produced layers that exist in
        // style.layers and never paint.
        style.addLayer(RasterLayer(layerId, historicalRasterSourceId(blueprint.id)))
    }

    for (layer in style.layers) {
        val id = layer.id
        if (!id.startsWith(AtlasLayerIds.HISTORICAL_RASTER_LAYER_PREFIX)) continue
        val shouldShow = visible.containsKey(id)
        // Property.VISIBLE and Property.NONE are plain String constants, and
        // PropertyValue<String> is what the layer hands back, so the comparison is
        // a string compare, not an enum compare.
        val current = layer.visibility?.value
        val target = if (shouldShow) Property.VISIBLE else Property.NONE
        if (current == target) continue
        layer.setProperties(PropertyFactory.visibility(target))
    }
}

fun historicalRasterSourceId(assetId: String): String =
    AtlasLayerIds.HISTORICAL_RASTER_SOURCE_PREFIX + mbtilesSafeId(assetId)

fun historicalRasterLayerId(assetId: String): String =
    AtlasLayerIds.HISTORICAL_RASTER_LAYER_PREFIX + mbtilesSafeId(assetId)

fun ensureMbtilesPackLayer(style: Style, services: AtlasServices, packId: String) {
    val sourceId = mbtilesSourceId(packId)
    if (style.getSource(sourceId) != null) return
    val url = services.tiles.tileUrl(packId) ?: return
    // The pack's own zMax must be on the TileSet, otherwise the source maxzoom is
    // unset and MapLibre requests literal z24 tiles for this pack instead of
    // overzooming its deepest available tile, which renders black at the global
    // camera ceiling. Overlook the lookup failing and leave maxzoom unset, which
    // is the previous behaviour.
    val tileSet = TileSet("2.2.0", url)
    services.offline.lookup(packId)?.zMax?.let { tileSet.maxZoom = it.toFloat() }
    style.addSource(RasterSource(sourceId, tileSet))
    val layer = RasterLayer(mbtilesLayerId(packId), sourceId)
    if (style.getLayer(AtlasLayerIds.WAYPOINTS_LAYER) != null) {
        style.addLayerBelow(layer, AtlasLayerIds.WAYPOINTS_LAYER)
    } else {
        style.addLayer(layer)
    }
}

fun removeMbtilesPackLayer(style: Style, packId: String) {
    val layerId = mbtilesLayerId(packId)
    if (style.getLayer(layerId) != null) {
        style.removeLayer(layerId)
    }
    val sourceId = mbtilesSourceId(packId)
    if (style.getSource(sourceId) != null) {
        style.removeSource(sourceId)
    }
}

fun mountedMbtilesSafeIds(style: Style): Set<String> {
    return style.layers
        .map { it.id }
        .filter { it.startsWith(AtlasLayerIds.MBTILES_LAYER_ID_PREFIX) }
        .map { it.removePrefix(AtlasLayerIds.MBTILES_LAYER_ID_PREFIX) }
        .toSet()
}

fun syncMbtilesPackLayers(style: Style, services: AtlasServices, activePacks: Set<String>) {
    val wanted = activePacks.map { mbtilesSafeId(it) }.toSet()
    for (safeId in mountedMbtilesSafeIds(style) - wanted) {
        removeMbtilesPackLayer(style, safeId)
    }
    for (packId in activePacks) {
        ensureMbtilesPackLayer(style, services, packId)
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
