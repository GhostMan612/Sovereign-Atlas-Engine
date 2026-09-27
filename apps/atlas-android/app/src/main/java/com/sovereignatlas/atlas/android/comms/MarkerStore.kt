// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.geo.cot.CotMarker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MarkerStore {
    private val _markers = MutableStateFlow<Map<String, CotMarker>>(emptyMap())
    val markerStream: StateFlow<Map<String, CotMarker>> = _markers.asStateFlow()

    fun addMarker(marker: CotMarker) {
        _markers.update { current ->
            current + (marker.uid to marker)
        }
    }
}
