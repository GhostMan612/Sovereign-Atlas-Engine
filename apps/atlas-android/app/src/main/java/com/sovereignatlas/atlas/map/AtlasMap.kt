// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

@Composable
fun AtlasMapScreen(services: AtlasServices) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }
    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                mapRef.value = map
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        services.behavior.markUserInteracted()
                    }
                }
                map.setStyle(Style.Builder().fromJson(BLANK_STYLE)) { style ->
                    styleRef.value = style
                    installAtlasLayers(style)
                    pushJournal(style, services)
                    pushPosition(style, services)
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
            styleRef.value?.let { style -> pushJournal(style, services) }
        }
        services.location.addListener(onLocation)
        services.journal.addListener(onJournal)
        onDispose {
            services.location.removeListener(onLocation)
            services.journal.removeListener(onJournal)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Text("Locate")
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

fun pushJournal(style: Style, services: AtlasServices) {
    pushFeatures(
        style,
        AtlasLayerIds.WAYPOINTS_SOURCE,
        waypointsToFeatures(services.journal.waypoints()),
    )
    val trackFeatures = ArrayList<Feature>()
    for (track in services.journal.tracks()) {
        trackToFeatures(track).features()?.let { trackFeatures.addAll(it) }
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
