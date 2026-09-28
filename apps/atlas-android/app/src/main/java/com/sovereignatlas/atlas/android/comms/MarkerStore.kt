// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.geo.cot.CotMarker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MarkerStore(private val scope: CoroutineScope) {
    private val _markers = MutableStateFlow<Map<String, CotMarker>>(emptyMap())
    val markerStream: StateFlow<Map<String, CotMarker>> = _markers.asStateFlow()

    private val pruneJob = scope.launch {
        while (isActive) {
            delay(60_000L)
            pruneStaleMarkers()
        }
    }

    fun addMarker(marker: CotMarker) {
        _markers.update { current ->
            current + (marker.uid to marker)
        }
    }

    fun shutdown() {
        pruneJob.cancel()
    }

    private fun pruneStaleMarkers() {
        val now = System.currentTimeMillis()
        _markers.update { currentMap ->
            currentMap.filterValues { marker ->
                (now - marker.timestampMillis) < (24 * 60 * 60 * 1000L)
            }
        }
    }
}
