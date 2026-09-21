// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sovereignatlas.atlas.AtlasServices
import com.sovereignatlas.atlas.camera.AtlasCameraState
import com.sovereignatlas.atlas.geo.AtlasAngles
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.goto.goToCameraIntent
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.measure.MeasureSnapshot
import com.sovereignatlas.atlas.measure.MeasureUnit
import com.sovereignatlas.atlas.ui.CompassDial
import com.sovereignatlas.atlas.ui.GoToCard
import com.sovereignatlas.atlas.ui.MeasurePanel
import com.sovereignatlas.atlas.ui.OfflineDialog
import com.sovereignatlas.atlas.ui.TrackDetailDialog
import com.sovereignatlas.atlas.ui.TracksDialog
import com.sovereignatlas.atlas.ui.WaypointCreateDialog
import com.sovereignatlas.atlas.ui.WaypointDetailDialog
import com.sovereignatlas.atlas.ui.WaypointsDialog
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun AtlasMapScreen(services: AtlasServices) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }
    val pendingWaypoint = remember { mutableStateOf<AtlasCoordinate?>(null) }
    val showWaypoints = remember { mutableStateOf(false) }
    val waypointDetailId = remember { mutableStateOf<String?>(null) }
    val showTracks = remember { mutableStateOf(false) }
    val trackDetailId = remember { mutableStateOf<String?>(null) }
    val showOffline = remember { mutableStateOf(false) }
    val offlineTick = remember { mutableStateOf(0) }
    val headingUp = remember { mutableStateOf(false) }
    val pendingHeadingUp = remember { mutableStateOf(false) }
    val headingTick = remember { mutableStateOf(0) }
    val recorderTick = remember { mutableStateOf(0) }
    val goToTick = remember { mutableStateOf(0) }
    val positionTick = remember { mutableStateOf(0) }
    val journalTick = remember { mutableStateOf(0) }
    val measureActive = remember { mutableStateOf(services.measure.isActive()) }
    val measureSnapshot = remember {
        mutableStateOf<MeasureSnapshot>(services.measure.snapshot())
    }
    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                mapRef.value = map
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        services.behavior.markUserInteracted()
                    }
                }
                map.addOnMapClickListener { point ->
                    if (services.measure.isActive()) {
                        services.measure.setB(
                            AtlasCoordinate(
                                latitude = point.latitude,
                                longitude = point.longitude,
                            ),
                        )
                    }
                    true
                }
                map.addOnMapLongClickListener { point ->
                    pendingWaypoint.value = AtlasCoordinate(
                        latitude = point.latitude,
                        longitude = point.longitude,
                    )
                    true
                }
                map.setStyle(Style.Builder().fromJson(BLANK_STYLE)) { style ->
                    styleRef.value = style
                    installAtlasLayers(style)
                    pushJournal(style, services)
                    pushPosition(style, services)
                    pushMeasure(style, services)
                    pushGoTo(style, services)
                    services.behavior.startupCamera()?.let { intent ->
                        applyCameraIntent(map, intent)
                    }
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
                services.behavior.onLocationUpdate(
                    map.cameraPosition.zoom,
                    map.cameraPosition.bearing,
                )?.let { intent ->
                    applyCameraIntent(map, intent)
                }
            }
        }
        val onJournal: () -> Unit = {
            journalTick.value += 1
            styleRef.value?.let { style -> pushJournal(style, services) }
        }
        val onMeasure: () -> Unit = {
            measureActive.value = services.measure.isActive()
            measureSnapshot.value = services.measure.snapshot()
            styleRef.value?.let { style -> pushMeasure(style, services) }
        }
        val onRecorder: () -> Unit = {
            recorderTick.value += 1
            styleRef.value?.let { style -> pushTracks(style, services) }
        }
        val onGoTo: () -> Unit = {
            goToTick.value += 1
            styleRef.value?.let { style -> pushGoTo(style, services) }
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
        services.journal.addListener(onJournal)
        services.measure.addListener(onMeasure)
        services.recorder.addListener(onRecorder)
        services.goTo.addListener(onGoTo)
        services.offline.addListener(onOffline)
        services.heading.addListener(onHeading)
        onMeasure()
        onDispose {
            services.location.removeListener(onLocation)
            services.journal.removeListener(onJournal)
            services.measure.removeListener(onMeasure)
            services.recorder.removeListener(onRecorder)
            services.goTo.removeListener(onGoTo)
            services.offline.removeListener(onOffline)
            services.heading.removeListener(onHeading)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Button(
                onClick = { showWaypoints.value = true },
            ) {
                Text("Waypoints")
            }
            Button(
                onClick = { showTracks.value = true },
            ) {
                Text("Tracks")
            }
            Button(
                onClick = { showOffline.value = true },
            ) {
                Text("Offline")
            }
            Button(
                onClick = {
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
                },
            ) {
                Text(if (headingUp.value) "North-up" else "Head-up")
            }
            Button(
                onClick = {
                    val map = mapRef.value ?: return@Button
                    val validFix =
                        if (services.location.status() == AtlasLocationStatus.valid) {
                            services.location.latestFixOrNull()?.position
                        } else {
                            null
                        }
                    val target = map.cameraPosition.target ?: return@Button
                    services.measure.begin(
                        validFix,
                        AtlasCoordinate(
                            latitude = target.latitude,
                            longitude = target.longitude,
                        ),
                    )
                },
            ) {
                Text("Measure")
            }
            Button(
                onClick = {
                    val map = mapRef.value ?: return@Button
                    when (
                        val outcome = services.behavior.locate(
                            map.cameraPosition.zoom,
                            map.cameraPosition.bearing,
                        )
                    ) {
                        is LocateOutcome.Applied -> applyCameraIntent(map, outcome.intent)
                        LocateOutcome.Pending, LocateOutcome.Ignored -> Unit
                    }
                },
            ) {
                Text("Locate")
            }
        }
        headingTick.value.let {
            CompassDial(
                heading = services.heading,
                orientToken = orientToken(services, headingUp.value),
                onFaceNorth = {
                    headingUp.value = false
                    pendingHeadingUp.value = false
                    mapRef.value?.let { map -> rotateMap(map, 0.0) }
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )
        }
        if (recorderTick.value >= 0 && services.recorder.isRecording()) {
            Surface(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Text(
                    text = "REC • ${services.recorder.pointCount()} pts",
                    modifier = Modifier.padding(8.dp),
                )
            }
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
        pendingWaypoint.value?.let { point ->
            WaypointCreateDialog(
                point = point,
                onSave = { label, note ->
                    services.journal.create(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        label = label,
                        note = note,
                    )
                    pendingWaypoint.value = null
                },
                onCancel = { pendingWaypoint.value = null },
            )
        }
        if (showWaypoints.value) {
            journalTick.value.let {
                WaypointsDialog(
                    journal = services.journal,
                    onOpenDetail = { id -> waypointDetailId.value = id },
                    onClose = { showWaypoints.value = false },
                )
            }
        }
        waypointDetailId.value?.let { id ->
            journalTick.value.let {
                WaypointDetailDialog(
                    journal = services.journal,
                    id = id,
                    onGoTo = { targetId ->
                        services.journal.lookup(targetId)?.let { record ->
                            services.goTo.activate(
                                id = record.id,
                                latitude = record.latitude,
                                longitude = record.longitude,
                                label = record.label.ifEmpty { record.id },
                            )
                            mapRef.value?.let { map ->
                                goToCameraIntent(
                                    services.goTo,
                                    map.cameraPosition.zoom,
                                    map.cameraPosition.bearing,
                                )?.let { intent ->
                                    applyCameraIntent(map, intent)
                                }
                            }
                        }
                        waypointDetailId.value = null
                        showWaypoints.value = false
                    },
                    onClose = { waypointDetailId.value = null },
                )
            }
        }
        if (showTracks.value) {
            journalTick.value.let {
                recorderTick.value.let {
                    TracksDialog(
                        journal = services.journal,
                        recorder = services.recorder,
                        onOpenDetail = { id -> trackDetailId.value = id },
                        onClose = { showTracks.value = false },
                    )
                }
            }
        }
        trackDetailId.value?.let { id ->
            journalTick.value.let {
                TrackDetailDialog(
                    journal = services.journal,
                    id = id,
                    onClose = { trackDetailId.value = null },
                )
            }
        }
        if (showOffline.value) {
            offlineTick.value.let {
                OfflineDialog(
                    store = services.offline,
                    onClose = { showOffline.value = false },
                )
            }
        }
    }
}

fun applyCameraIntent(map: MapLibreMap, intent: AtlasCameraState) {
    map.moveCamera(
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

fun orientToken(services: AtlasServices, headingUp: Boolean): String {
    if (services.heading.isUnsupported()) return "orient unsupported"
    if (services.heading.latest() == null) return "orient off"
    return if (headingUp) "orient heading-up" else "orient north-up"
}

fun pushJournal(style: Style, services: AtlasServices) {
    pushFeatures(
        style,
        AtlasLayerIds.WAYPOINTS_SOURCE,
        waypointsToFeatures(services.journal.waypoints()),
    )
    pushTracks(style, services)
}

fun pushTracks(style: Style, services: AtlasServices) {
    val trackFeatures = ArrayList<Feature>()
    for (track in services.journal.tracks()) {
        trackToFeatures(track).features()?.let { trackFeatures.addAll(it) }
    }
    val active = services.recorder.points()
    if (active.size >= 2) {
        trackFeatures.add(
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
    pushFeatures(
        style,
        AtlasLayerIds.TRACK_SOURCE,
        FeatureCollection.fromFeatures(trackFeatures),
    )
}

fun pushPosition(style: Style, services: AtlasServices) {
    val fix = services.location.latestFixOrNull() ?: return
    pushFeatures(
        style,
        AtlasLayerIds.POSITION_SOURCE,
        FeatureCollection.fromFeatures(
            listOf(positionToFeature(fix.position)),
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

fun pushGoTo(style: Style, services: AtlasServices) {
    val target = services.goTo.targetOrNull()
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
