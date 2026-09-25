// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class WaypointSelection {
    private val _selectedWaypointId = MutableStateFlow<String?>(null)
    val selectedWaypointId: StateFlow<String?> = _selectedWaypointId.asStateFlow()

    fun selectWaypoint(id: String) {
        _selectedWaypointId.value = id
    }

    fun clearWaypointSelection() {
        _selectedWaypointId.value = null
    }
}

fun animateToWaypoint(map: MapLibreMap?, latitude: Double, longitude: Double) {
    if (map == null) return
    val targetZoom = maxOf(map.cameraPosition.zoom, 14.0)
    val update = CameraUpdateFactory.newLatLngZoom(
        LatLng(latitude, longitude),
        targetZoom,
    )
    map.animateCamera(update, 300)
}
