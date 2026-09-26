// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val bearing: Float?,
    val speed: Float?,
    val timestamp: Long,
)

data class LoSRequest(
    val observer: GeoPoint,
    val target: GeoPoint,
    val observerHeightMeters: Double = 2.0,
    val targetHeightMeters: Double = 2.0,
)

data class LoSProfilePoint(
    val distance: Double,
    val terrainElevation: Double,
    val rayElevation: Double,
    val geoPoint: GeoPoint,
)

data class LoSResult(
    val isVisible: Boolean,
    val blockingPoint: GeoPoint?,
    val blockingDistanceMeters: Double?,
    val profile: List<LoSProfilePoint>,
    val errorMessage: String? = null,
)

enum class LoSMode {
    Inactive,
    AwaitingObserver,
    AwaitingTarget,
}

class LoSState {
    private val _mode = MutableStateFlow(LoSMode.Inactive)
    val mode: StateFlow<LoSMode> = _mode.asStateFlow()

    private val _result = MutableStateFlow<LoSResult?>(null)
    val result: StateFlow<LoSResult?> = _result.asStateFlow()

    private val _observer = MutableStateFlow<GeoPoint?>(null)
    val observer: StateFlow<GeoPoint?> = _observer.asStateFlow()

    private val _target = MutableStateFlow<GeoPoint?>(null)
    val target: StateFlow<GeoPoint?> = _target.asStateFlow()

    fun setMode(mode: LoSMode) {
        _mode.value = mode
    }

    fun setObserver(point: GeoPoint?) {
        _observer.value = point
    }

    fun setTarget(point: GeoPoint?) {
        _target.value = point
    }

    fun setResult(result: LoSResult?) {
        _result.value = result
    }

    fun reset() {
        _mode.value = LoSMode.Inactive
        _result.value = null
        _observer.value = null
        _target.value = null
    }
}
