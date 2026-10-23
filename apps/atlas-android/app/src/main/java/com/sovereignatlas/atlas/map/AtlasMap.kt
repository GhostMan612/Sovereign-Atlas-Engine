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
import android.graphics.RectF
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.android.AndroidRoutingLoader
import com.sovereignatlas.atlas.android.DemSession
import com.sovereignatlas.atlas.android.SqliteDemTileStore
import com.sovereignatlas.atlas.geo.render.AtlasLayer
import com.sovereignatlas.atlas.geo.render.FeatureSink
import com.sovereignatlas.atlas.geo.render.RenderFeature
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import com.sovereignatlas.atlas.geo.routing.LoadProfile
import com.sovereignatlas.atlas.geo.routing.RoutingRequest
import com.sovereignatlas.atlas.geo.routing.RoutingResult
import com.sovereignatlas.atlas.camera.AtlasCameraState
import com.sovereignatlas.atlas.core.LngLat
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
import com.sovereignatlas.atlas.ui.hud.NavigationGuidanceHud
import com.sovereignatlas.atlas.ui.hud.TacNavHud
import com.sovereignatlas.atlas.ui.historical.HistoricalAssetSheet
import com.sovereignatlas.atlas.ui.historical.HistoricalRecordSheet
import com.sovereignatlas.atlas.ui.historical.HistoricalDossierSheet
import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier
import com.sovereignatlas.atlas.geo.navigation.ManeuverType
import com.sovereignatlas.atlas.geo.navigation.NavigationUpdate
import com.sovereignatlas.atlas.geo.gis.GisDataset
import com.sovereignatlas.atlas.geo.gis.GisFeature
import com.sovereignatlas.atlas.geo.navigation.RoadRouteRequest
import com.sovereignatlas.atlas.geo.navigation.RoadRouteResult
import com.sovereignatlas.atlas.layers.compositor.LayerCompositor
import com.sovereignatlas.atlas.layers.compositor.OverscalePolicy
import kotlin.math.roundToInt
import com.sovereignatlas.atlas.tactical.RadialFence
import com.sovereignatlas.atlas.tactical.fencePolygon
import com.sovereignatlas.atlas.ui.FenceDialog
import com.sovereignatlas.atlas.ui.GoToCard
import com.sovereignatlas.atlas.ui.HudTelemetryPill
import com.sovereignatlas.atlas.ui.LayersDialog
import com.sovereignatlas.atlas.ui.MgrsHud
import com.sovereignatlas.atlas.geo.MgrsConverter
import com.sovereignatlas.atlas.ui.AtlasActivityBanner
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
    val tilt: Double = 0.0,
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
    val sink: FeatureSink = remember { MapLibreFeatureSink { styleRef.value } }
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
    val currentBearing = remember(headingTick.value) { services.heading.displayDeg()?.toFloat() }
    val currentHeadingFrame = remember(headingTick.value) {
        if (services.heading.displayDeg() == null) null else services.heading.frameLabel()
    }
    val hudAccent = androidx.compose.ui.graphics.Color(0xFF39C5F3)
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
    val viewshedResult by services.viewshedState.result.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(viewshedResult) {
        pushViewshed(sink, viewshedResult)
    }
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
            sink.replace(AtlasLayer.MgrsLines, mgrsLinesToFeatures(grid.lines))
            sink.replace(AtlasLayer.MgrsLabels, mgrsLabelsToFeatures(grid.labels))
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
            (sink as? MapLibreFeatureSink)?.replaceRaw(AtlasLayer.AssetParcels, collection)
            styleRef.value?.let { style ->
                syncHistoricalBlueprintLayers(style, blueprints)
            }
        }
    }
    val measureActive = remember { mutableStateOf(services.measure.isActive()) }
    val measureSnapshot = remember {
        mutableStateOf<MeasureSnapshot>(services.measure.snapshot())
    }
    val mapLoading = remember { mutableStateOf(true) }
    val mapLoadingLabel = remember { mutableStateOf<String?>(null) }
    val activityBannerLabel = remember { mutableStateOf<String?>(null) }
    val showGisLayers = remember { mutableStateOf(false) }
    val selectedGisFeature = remember { mutableStateOf<Pair<GisFeature, String>?>(null) }
    val gisDatasets by services.gisRepository.datasets.collectAsStateWithLifecycle()
    val openGisLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            mapScope.launch {
                activityBannerLabel.value = "IMPORTING FIELD GIS DATASET..."
                try {
                    val result = withContext(Dispatchers.IO) {
                        com.sovereignatlas.atlas.android.gis.AndroidGisImporter.importFromUri(context, uri)
                    }
                    if (result.dataset != null) {
                        services.gisRepository.addDataset(result.dataset)
                    }
                } finally {
                    activityBannerLabel.value = null
                }
            }
        }
    }
    LaunchedEffect(gisDatasets, styleRef.value) {
        val style = styleRef.value ?: return@LaunchedEffect
        syncUserGisLayers(style, gisDatasets)
        pushUserGisDatasets(sink, gisDatasets)
    }
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
    val selectedHistoricalDossier = remember { MutableStateFlow<HistoricalDossier?>(null) }
    val selectedDossier by selectedHistoricalDossier.collectAsStateWithLifecycle()
    val selectedEpochYear = remember { mutableStateOf<Int?>(null) }
    val isNavigating by services.roadNavigation.isNavigating.collectAsStateWithLifecycle()
    val navUpdate by services.roadNavigation.update.collectAsStateWithLifecycle()
    val isMuted by services.roadNavigation.isMuted.collectAsStateWithLifecycle()
    val activeTravelProfile by services.roadNavigation.profile.collectAsStateWithLifecycle()
    val activeRoadRoute by services.roadNavigation.activeRoute.collectAsStateWithLifecycle()
    val reducedMotion by services.settingsRepository.reducedMotion.collectAsStateWithLifecycle()
    val explorerWorkspaceEnabled by services.settingsRepository.explorerWorkspaceEnabled.collectAsStateWithLifecycle()
    val tacticalWorkspaceEnabled by services.settingsRepository.tacticalWorkspaceEnabled.collectAsStateWithLifecycle()
    val studioWorkspaceEnabled by services.settingsRepository.studioWorkspaceEnabled.collectAsStateWithLifecycle()
    val navigationFeedbackMessage = remember { mutableStateOf<String?>(null) }
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
        mapLoadingLabel.value = null
        onFirstStyle()
        cameraState.value = CameraState(
            center = map.cameraPosition.target ?: LatLng(0.0, 0.0),
            zoom = map.cameraPosition.zoom,
            bearing = map.cameraPosition.bearing,
            tilt = map.cameraPosition.tilt,
            isIdle = true,
        )
        services.tiles.demTileUrl()?.let { demUrl ->
            if (services.tiles.demAvailable()) {
                ensureDemSource(style, demUrl)
                syncTerrainHillshade(style, demUrl)
            }
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
        pushScrubPoint(sink, services.scrubState.activePoint.value)
        pushPli(sink, services.pli.observe().value)
        pushMarkers(sink, services.markers.observe().value)
        pushMeshTracks(sink, services.markers.observe().value)
        pushOpsGraphics(sink, displayGraphics)
        pushRouteResult(sink, services.routing.result.value)
        if (showMgrsGrid.value) {
            val cached = mgrsCache.value
            sink.replace(AtlasLayer.MgrsLines, mgrsLinesToFeatures(cached.lines))
            sink.replace(AtlasLayer.MgrsLabels, mgrsLabelsToFeatures(cached.labels))
        }
        pushLosState(
            sink,
            services.losState.observer.value,
            services.losState.target.value,
            services.losState.profile.value,
        )
        pushHistoricalDossiers(sink, services.historicalIndex, selectedEpochYear.value)
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
        // Patents are pushed here as well as on camera idle: a style reload
        // recreates an empty source, and a camera that never moves again would
        // otherwise leave the viewport with no parcels at all.
        refreshHistoricalAssets(map)
        sink.replace(AtlasLayer.Waypoints, waypointsToFeatures(repoWaypoints.value))
        sink.replace(
            AtlasLayer.Track,
            mergedTrackFeatures(repoTracks.value, services.recorder),
        )
        pushPosition(sink, services)
        pushMeasure(sink, services)
        pushGoTo(sink, services)
        pushRings(sink, services)
        syncMbtilesPackLayers(style, services, activeMbtilesPacks.value)
        syncUserGisLayers(style, services.gisRepository.datasets.value)
        pushUserGisDatasets(sink, services.gisRepository.datasets.value)
        pushViewshed(sink, services.viewshedState.result.value)
        if (showGraticule.value) pushGraticule(map, sink)
        val baseAttr = if (applyOnlineBase) {
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
        attribution.value = combineAttribution(baseAttr, services.compositor.getAttributionRollup())
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
                    isTiltGesturesEnabled = true
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
                        tilt = map.cameraPosition.tilt,
                        isIdle = false,
                    )
                }
                map.addOnMapClickListener { point ->
                    // FIRST STATEMENT, UNCONDITIONAL. The previous diagnostic lived
                    // inside `if (hit != null)`, so a tap that hit nothing logged
                    // nothing — which is precisely the case it was written to explain.
                    // Two rounds of guessing followed, because the silence was
                    // indistinguishable from the listener never firing. This line is
                    // the answer to "did the tap even arrive", and it cannot be
                    // suppressed by any branch below.
                    Log.d(
                        "AtlasMap",
                        "map click at ${point.latitude},${point.longitude} " +
                            "drawingMode=${drawingModeFlow.value} losMode=${services.losState.mode.value} " +
                            "measureActive=${services.measure.isActive()}",
                    )
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
                                activityBannerLabel.value = "COMPUTING TERRAIN LINE-OF-SIGHT"
                                try {
                                    runLosCalculation(services)
                                } finally {
                                    activityBannerLabel.value = null
                                }
                            }
                        }
                        pushLosState(
                            sink,
                            services.losState.observer.value,
                            services.losState.target.value,
                            services.losState.profile.value,
                        )
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
                            val activeGis = services.gisRepository.datasets.value.filter { it.isVisible }
                            for (ds in activeGis) {
                                val gisLayerIds = listOf(
                                    "${AtlasLayerIds.USER_GIS_CIRCLE_PREFIX}${ds.id}",
                                    "${AtlasLayerIds.USER_GIS_LINE_PREFIX}${ds.id}",
                                    "${AtlasLayerIds.USER_GIS_FILL_PREFIX}${ds.id}",
                                )
                                for (layerId in gisLayerIds) {
                                    val hit = map.queryRenderedFeatures(screen, layerId).firstOrNull()
                                    if (hit != null) {
                                        val featId = hit.id() ?: hit.getStringProperty("id")
                                        val feat = ds.features.firstOrNull { it.id == featId }
                                        if (feat != null) {
                                            selectedGisFeature.value = feat to ds.name
                                            return@addOnMapClickListener true
                                        }
                                    }
                                }
                            }
                        }
                        // A tap that reaches here hit no historical feature at all, so
                        // any open sheet is describing something off-screen. The token
                        // bump also cancels a lookup still in flight from a prior tap.
                        patentTapToken.incrementAndGet()
                        selectedHistoricalAsset.value = null
                        val dossierHit = services.historicalIndex.query(
                            box = com.sovereignatlas.atlas.core.AtlasBoundingBox(
                                south = point.latitude - 0.005,
                                west = point.longitude - 0.005,
                                north = point.latitude + 0.005,
                                east = point.longitude + 0.005,
                            ),
                            year = selectedEpochYear.value,
                        ).firstOrNull()
                        if (dossierHit != null) {
                            selectedHistoricalDossier.value = dossierHit
                            return@addOnMapClickListener true
                        } else {
                            selectedHistoricalDossier.value = null
                        }
                        val hits = if (screen == null) {
                            emptyList()
                        } else {
                            // A RectF SLOP BOX, NOT A POINT.
                            //
                            // WHY. `queryRenderedFeatures` on a SymbolLayer returns a
                            // hit only where the icon's painted quad is under the
                            // query region. A point query asks for the exact
                            // coordinate the tap was projected to, and a fingertip
                            // reliably lands a few pixels off the icon's centre — and
                            // the wp-icon is a 24px circle, so at low zoom its
                            // rendered quad is barely larger than the tap itself. The
                            // point query returned 0 hits while the feature was plainly
                            // on screen.
                            //
                            // 32dp of slop is the standard Mapbox/MapLibre
                            // recommendation for touch targets and is comfortably
                            // inside the 48dp minimum touch target, so it cannot make
                            // a neighbouring waypoint reachable by accident at any
                            // realistic separation.
                            val slop = 32f * resources.displayMetrics.density
                            map.queryRenderedFeatures(
                                RectF(
                                    screen.x - slop,
                                    screen.y - slop,
                                    screen.x + slop,
                                    screen.y + slop,
                                ),
                                AtlasLayer.Waypoints.layerId,
                            )
                        }
                        // Unfiltered probe, diagnostic only. If the filtered query
                        // returns nothing, this distinguishes "the waypoint layer is
                        // not hit-testable" from "that layer id is wrong" from "the
                        // tap landed on nothing at all" — three different bugs that
                        // otherwise look identical from the outside.
                        val anyHits = if (screen == null) {
                            emptyList()
                        } else {
                            val slop = 32f * resources.displayMetrics.density
                            runCatching {
                                map.queryRenderedFeatures(
                                    RectF(
                                        screen.x - slop,
                                        screen.y - slop,
                                        screen.x + slop,
                                        screen.y + slop,
                                    ),
                                )
                            }.getOrDefault(emptyList())
                        }
                        // Root id first, property bag as fallback. Writing the id to the
                        // root alone was the right theory and it did not work on the
                        // device, so which channel survives setGeoJson -> tile pipeline
                        // -> queryRenderedFeatures is not something to assume. The
                        // diagnostic below exists to tell us which one does, because
                        // "tapping does nothing" is otherwise indistinguishable from
                        // "the layer is not hit-testable at all".
                        val hit = hits.firstOrNull()
                        val rootId = hit?.id()
                        val propertyId = hit?.getStringProperty(MapLibreFeatureSink.PROPERTY_ID)
                        val hitId = rootId?.takeIf { it.isNotBlank() }
                            ?: propertyId?.takeIf { it.isNotBlank() }
                        // UNCONDITIONAL, including on zero hits. A hit count of 0 means
                        // the layer is not hit-testable and no id plumbing will ever fix
                        // it; that is a completely different bug from a lost id, and
                        // conflating them is what made this take three attempts.
                        Log.d(
                            "AtlasMap",
                            "waypoint query: screen=$screen " +
                                "layer=${AtlasLayer.Waypoints.layerId} " +
                                "layerPresent=${styleRef.value?.getLayer(AtlasLayer.Waypoints.layerId) != null} " +
                                "hits=${hits.size} anyHits=${anyHits.size} " +
                                "anyIds=${anyHits.mapNotNull { it.id() }.distinct().take(5)} " +
                                "rootId=$rootId propertyId=$propertyId resolved=$hitId",
                        )
                        if (!rootId.isNullOrBlank() && !propertyId.isNullOrBlank() &&
                            rootId != propertyId
                        ) {
                            // The two channels must agree. If they ever do not, the
                            // duplication has started to drift and one of them is
                            // about to stop working silently.
                            Log.w(
                                "AtlasMap",
                                "waypoint id channels DISAGREE: root='$rootId' " +
                                    "property='$propertyId'",
                            )
                        }
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
                        tilt = map.cameraPosition.tilt,
                        isIdle = true,
                    )
                    if (showMgrsGrid.value) refreshMgrsGrid(map)
                    if (showGraticule.value) pushGraticule(map, sink)
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
            val fix = usableFixOf(services)
            if (fix != null) {
                services.roadNavigation.updateLocation(fix)
            }
            val map = mapRef.value
            val style = styleRef.value
            if (map != null && style != null) {
                pushPosition(sink, services)
                if (showRings.value) pushRings(sink, services)
                if (following.value) {
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
            pushMeasure(sink, services)
        }
        val onRecorder: () -> Unit = {
            recorderTick.value += 1
            sink.replace(
                AtlasLayer.Track,
                mergedTrackFeatures(repoTracks.value, services.recorder),
            )
        }
        val onGoTo: () -> Unit = {
            goToTick.value += 1
            pushGoTo(sink, services)
            val target = services.goTo.targetOrNull()
            val fix = usableFixOf(services)
            if (target != null && fix != null) {
                mapScope.launch {
                    refreshFootRoute(services, context, fix.latitude, fix.longitude)
                }
            } else {
                services.routing.setResult(null)
                services.roadNavigation.stopNavigation()
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
                    sink.replace(AtlasLayer.Waypoints, waypointsToFeatures(waypoints))
                }
            }
            launch {
                services.trackRepository.tracks.collect { tracks ->
                    repoTracks.value = tracks
                    sink.replace(
                        AtlasLayer.Track,
                        mergedTrackFeatures(tracks, services.recorder),
                    )
                }
            }
            launch {
                services.scrubState.activePoint.collect { point ->
                    pushScrubPoint(sink, point)
                }
            }
            launch {
                services.pli.observe().collect { plis ->
                    pushPli(sink, plis)
                }
            }
            launch {
                services.markers.observe().collect { markerMap ->
                    pushMarkers(sink, markerMap)
                    pushMeshTracks(sink, markerMap)
                }
            }
            launch {
                services.routing.result.collect { result ->
                    if (services.roadNavigation.activeRoute.value == null) {
                        pushRouteResult(sink, result)
                    }
                }
            }
            launch {
                services.roadNavigation.activeRoute.collect { roadRoute ->
                    if (roadRoute != null) {
                        pushRoadRouteResult(sink, roadRoute)
                    } else {
                        pushRouteResult(sink, services.routing.result.value)
                    }
                }
            }
            launch {
                services.losState.profile.collect { profile ->
        pushLosState(
            sink,
            services.losState.observer.value,
            services.losState.target.value,
            profile,
        )
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
            mapLoadingLabel.value = "MOUNTING OFFLINE MBTILES ARCHIVE"
            mapLoading.value = true
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
        pushOpsGraphics(sink, displayGraphics)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        if (tacticalWorkspaceEnabled) {
            TacNavHud(
                bearing = currentBearing,
                hudColor = hudAccent,
                frameLabel = currentHeadingFrame,
                showReadout = false,
            )
        }
        AtlasMapRailPills(
            following = following.value,
            measureActive = measureActive.value,
            activeMbtilesPacksCount = activeMbtilesPacks.value.size,
            showTools = showTools.value,
            studioWorkspaceEnabled = studioWorkspaceEnabled,
            explorerWorkspaceEnabled = explorerWorkspaceEnabled,
            reducedMotion = reducedMotion,
            onToggleFollow = onToggleFollow,
            onMeasure = onMeasure,
            onOpenLayers = {
                showLayerManager.value = true
                mapScope.launch {
                    activityBannerLabel.value = "SCANNING OFFLINE PACK DIRECTORY"
                    try {
                        scannedPacks.value = services.mbtilesScanner.scanPacks()
                    } finally {
                        activityBannerLabel.value = null
                    }
                }
            },
            onOpenTools = { showTools.value = true },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
        if (!measureActive.value && tacticalWorkspaceEnabled) {
            TacticalDrawingToolbar(
                currentMode = drawingMode,
                pointCount = inProgressPoints.size,
                onModeSelected = { mode ->
                    drawingModeFlow.value = mode
                    inProgressPointsFlow.value = emptyList()
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
                reducedMotion = reducedMotion,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 30.dp),
            )
        }
        AtlasCommandCenterSheet(
            visible = showTools.value,
            sheetState = toolsSheetState,
            onDismissRequest = { showTools.value = false },
            services = services,
            cartoKey = cartoKey,
            losMode = losMode,
            headingUp = headingUp.value,
            fenceArmed = fence.value?.armed == true,
            reducedMotion = reducedMotion,
            explorerWorkspaceEnabled = explorerWorkspaceEnabled,
            tacticalWorkspaceEnabled = tacticalWorkspaceEnabled,
            studioWorkspaceEnabled = studioWorkspaceEnabled,
            onOpenWaypoints = openTool(showWaypoints),
            onOpenTracks = openTool(showTracks),
            onOpenOffline = openTool(showOffline),
            onOpenLayers = openTool(showLayers),
            onOpenGisLayers = openTool(showGisLayers),
            onOpenLink = openTool(showLink),
            onOpenFence = openTool(showFence),
            onOpenSettings = openTool(showSettings),
            onOpenNodeSettings = openTool(showNodeSettings),
            onOpenChat = {
                toolsScope.launch {
                    try {
                        toolsSheetState.hide()
                    } finally {
                        showTools.value = false
                        showChat.value = true
                    }
                }
            },
            viewshedActive = viewshedResult != null,
            onToggleViewshed = {
                toolsScope.launch {
                    try {
                        toolsSheetState.hide()
                    } finally {
                        showTools.value = false
                        if (viewshedResult != null) {
                            services.viewshedState.clear()
                        } else {
                            val observerCoord = usableFixOf(services) ?: mapRef.value?.cameraPosition?.target?.let {
                                AtlasCoordinate(latitude = it.latitude, longitude = it.longitude)
                            }
                            if (observerCoord != null) {
                                activityBannerLabel.value = "COMPUTING 360° RADIAL VIEWSHED"
                                try {
                                    val config = com.sovereignatlas.atlas.geo.viewshed.ViewshedConfig(
                                        observer = observerCoord,
                                        maxRadiusMeters = 10000.0,
                                        angularStepDeg = 2.0,
                                    )
                                    val result = withContext(Dispatchers.IO) {
                                        services.viewshedEngine.computeViewshed(config)
                                    }
                                    services.viewshedState.setResult(result)
                                } finally {
                                    activityBannerLabel.value = null
                                }
                            }
                        }
                    }
                }
            },
            onToggleLoS = {
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
            onToggleHeadingUp = {
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
        AtlasLayerManagerSheet(
            visible = showLayerManager.value,
            sheetState = layerManagerSheetState,
            scannedPacks = scannedPacks.value,
            activePacks = activeMbtilesPacks.value,
            onTogglePack = { packId, enabled ->
                val current = activeMbtilesPacks.value
                val next = if (enabled) current + packId else current - packId
                services.settingsRepository.setActiveMbtilesPacks(next)
            },
            onDismissRequest = { showLayerManager.value = false },
        )
        val targetDropPointState by targetDropPoint.collectAsStateWithLifecycle()
        val activeProfileState by activeTerrainProfile.collectAsStateWithLifecycle()
        val currentLocationFix = services.locationEngine.currentLocation.collectAsStateWithLifecycle().value
        AtlasTargetDropSheet(
            targetDropPoint = targetDropPointState,
            activeProfileState = activeProfileState,
            currentLocationFix = currentLocationFix,
            onDismissRequest = { targetDropPoint.value = null },
            onAnalyzeLoS = {
                mapScope.launch {
                    val drop = targetDropPointState ?: return@launch
                    val origin = currentLocationFix ?: return@launch
                    activityBannerLabel.value = "ANALYZING TERRAIN ELEVATION PROFILE"
                    try {
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
                    } finally {
                        activityBannerLabel.value = null
                    }
                }
            },
            onComputeViewshed = {
                val drop = targetDropPointState
                if (drop != null) {
                    targetDropPoint.value = null
                    mapScope.launch {
                        activityBannerLabel.value = "COMPUTING 360° RADIAL VIEWSHED"
                        try {
                            val config = com.sovereignatlas.atlas.geo.viewshed.ViewshedConfig(
                                observer = AtlasCoordinate(drop.latitude, drop.longitude),
                                maxRadiusMeters = 10000.0,
                                angularStepDeg = 2.0,
                            )
                            val result = withContext(Dispatchers.IO) {
                                services.viewshedEngine.computeViewshed(config)
                            }
                            services.viewshedState.setResult(result)
                        } finally {
                            activityBannerLabel.value = null
                        }
                    }
                }
            },
            onSendMarker = { label, type, color ->
                mapScope.launch {
                    try {
                        val drop = targetDropPointState ?: return@launch
                        val result = withContext(Dispatchers.IO) {
                            services.atakBroadcaster.sendMarker(
                                type = type,
                                callsign = label,
                                lat = drop.latitude,
                                lon = drop.longitude,
                            )
                        }
                        services.markers.upsert(
                            CotMarker(
                                uid = result.uid,
                                type = type,
                                callsign = label,
                                latitude = drop.latitude,
                                longitude = drop.longitude,
                                altitude = null,
                                timestampMillis = System.currentTimeMillis(),
                            )
                        )
                        if (type == "b-m-p-w") {
                            val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
                            services.waypointRepository.saveWaypoint(
                                Waypoint(
                                    id = UUID.randomUUID().toString(),
                                    name = "WP-$stamp",
                                    latitude = drop.latitude,
                                    longitude = drop.longitude,
                                    timestamp = System.currentTimeMillis(),
                                    notes = "",
                                    sharingPolicy = WaypointSharingPolicy.Private.storedValue,
                                ),
                            )
                        }
                        targetDropPoint.value = null
                    } catch (e: Exception) {
                        Unit
                    }
                }
            },
        )
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
        val activeTargetBearing = if (isNavigating) {
            navUpdate?.currentManeuver?.bearingAfterManeuverDeg
        } else if (services.goTo.isActive()) {
            services.goTo.bearingDegTo(usableFixOf(services))
        } else {
            null
        }
        val activeTargetLabel = if (isNavigating) {
            navUpdate?.currentManeuver?.maneuverType?.name
        } else if (services.goTo.isActive()) {
            services.goTo.targetOrNull()?.label ?: "GOTO"
        } else {
            null
        }
        AtlasTopHud(
            bearing = currentBearing?.toDouble() ?: bearingHud,
            targetBearing = activeTargetBearing,
            targetLabel = activeTargetLabel,
            courseDeviationDeg = navUpdate?.snappedPosition?.let {
                val desired = navUpdate?.currentManeuver?.bearingAfterManeuverDeg
                val current = currentBearing?.toDouble() ?: bearingHud
                if (desired != null) (current - desired + 540.0) % 360.0 - 180.0 else null
            },
            isNavigating = isNavigating,
            navUpdate = navUpdate,
            isMuted = isMuted,
            activeRoadRoute = activeRoadRoute,
            onToggleMute = { services.roadNavigation.setMuted(!isMuted) },
            onStopNavigation = { services.roadNavigation.stopNavigation() },
            activityBannerLabel = activityBannerLabel.value,
            reducedMotion = reducedMotion,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        val statusVal = services.location.status()
        val locStatus = statusVal.name.uppercase(Locale.US)
        val speedText = currentLocationFix?.speed?.let { s ->
            String.format(Locale.US, " • %.1f km/h", s * 3.6f)
        } ?: ""
        val packCount = services.offline.packs().size
        val activeSourceMaxZoom = basePackId.value?.let { services.offline.lookup(it)?.zMax }
            ?: OfflineBuiltinProviders.lookup(baseProviderId.value)?.maxZoom
            ?: 19
        AtlasTelemetryHud(
            tacticalWorkspaceEnabled = tacticalWorkspaceEnabled,
            mgrsText = mgrsText,
            locStatus = locStatus,
            speedText = speedText,
            packCount = packCount,
            statusVal = statusVal,
            zoom = scaleInput.first,
            activeSourceMaxZoom = activeSourceMaxZoom,
            losMode = losMode,
            losProfile = losProfile,
            hudAccent = hudAccent,
            reducedMotion = reducedMotion,
            tilt = cameraState.value.tilt,
            onResetTilt = {
                mapRef.value?.animateCamera(CameraUpdateFactory.tiltTo(0.0), 300)
            },
            modifier = Modifier.align(Alignment.TopStart),
        )
        AtlasNavInstrumentHud(
            isRecording = recorderTick.value >= 0 && services.recorder.isRecording(),
            pointCount = services.recorder.pointCount(),
            bearingHud = bearingHud,
            currentBearing = currentBearing,
            currentHeadingFrame = currentHeadingFrame,
            tacticalWorkspaceEnabled = tacticalWorkspaceEnabled,
            hudAccent = hudAccent,
            reducedMotion = reducedMotion,
            onLocate = onLocate,
            onFaceNorth = {
                headingUp.value = false
                pendingHeadingUp.value = false
                mapRef.value?.let { map ->
                    map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 300)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, bottom = 30.dp),
        ) {
            ScaleBar(metersPerPixel = metersPerPixel)
        }
        AtlasGoToCardHost(
            services = services,
            usableFix = if (services.location.status() == AtlasLocationStatus.valid) {
                services.location.latestFixOrNull()?.position
            } else {
                null
            },
            activeTravelProfile = activeTravelProfile,
            navigationFeedbackMessage = navigationFeedbackMessage,
            activityBannerLabel = activityBannerLabel,
            mapScope = mapScope,
            context = context,
            modifier = Modifier.align(Alignment.TopStart),
        )
        if (measureActive.value && drawingMode == DrawingMode.NONE) {
            AtlasMeasureCardHost(
                measureSnapshot = measureSnapshot.value,
                onUnitSelected = { services.measure.setUnit(it) },
                onClose = { services.measure.clear() },
                onClear = { services.measure.clear() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        AtlasBottomAttribution(
            attribution = attribution.value,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        AtlasLoadingOverlay(
            visible = mapLoading.value,
            statusLabel = mapLoadingLabel.value,
            reducedMotion = reducedMotion,
        )
    }
    AtlasMapDialogsHost(
        showWaypoints = showWaypoints,
        showTracks = showTracks,
        trackDetailId = trackDetailId,
        showOffline = showOffline,
        showLayers = showLayers,
        showLink = showLink,
        showFence = showFence,
        showSettings = showSettings,
        showChat = showChat,
        showNodeSettings = showNodeSettings,
        chatSheetState = chatSheetState,
        nodeSettingsSheetState = nodeSettingsSheetState,
        chatViewModel = chatViewModel,
        nodeSettingsViewModel = nodeSettingsViewModel,
        waypointSelection = waypointSelection,
        fence = fence,
        baseProviderId = baseProviderId,
        basePackId = basePackId,
        showGraticule = showGraticule,
        showMgrsGrid = showMgrsGrid,
        showRings = showRings,
        showWaypointsLayer = showWaypointsLayer,
        showTrackLayer = showTrackLayer,
        showMeasureLayer = showMeasureLayer,
        selectedEpochYear = selectedEpochYear,
        selectedHistoricalDossier = selectedHistoricalDossier,
        services = services,
        sink = sink,
        styleRef = styleRef,
        mapRef = mapRef,
        attribution = attribution,
        context = context,
        refreshMgrsGrid = refreshMgrsGrid,
        showGisLayers = showGisLayers,
        gisDatasets = gisDatasets,
        onImportGis = { openGisLauncher.launch(arrayOf("*/*")) },
        selectedGisFeature = selectedGisFeature,
    )
    AtlasHistoricalSheetsHost(
        selectedRecord = selectedRecord,
        onDismissRecord = { selectedHistoricalRecord.value = null },
        selectedAsset = selectedAsset,
        onDismissAsset = {
            patentTapToken.incrementAndGet()
            selectedHistoricalAsset.value = null
        },
        selectedDossier = selectedDossier,
        onDismissDossier = { selectedHistoricalDossier.value = null },
    )
}


