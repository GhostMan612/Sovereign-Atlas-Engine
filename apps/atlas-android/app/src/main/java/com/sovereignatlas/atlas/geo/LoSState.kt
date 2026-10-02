// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.geo.los.TerrainProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LoSMode {
    Inactive,
    AwaitingObserver,
    AwaitingTarget,
}

/**
 * UI state for the two-tap line-of-sight tool.
 *
 * Holds the request in progress and the [TerrainProfile] that came back. The
 * profile — not a bare boolean — is what the map draws, so the overlay and the HUD
 * read from one calculation and cannot disagree.
 */
class LoSState {
    private val _mode = MutableStateFlow(LoSMode.Inactive)
    val mode: StateFlow<LoSMode> = _mode.asStateFlow()

    private val _profile = MutableStateFlow<TerrainProfile?>(null)
    val profile: StateFlow<TerrainProfile?> = _profile.asStateFlow()

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

    fun setProfile(profile: TerrainProfile?) {
        _profile.value = profile
    }

    fun reset() {
        _mode.value = LoSMode.Inactive
        _profile.value = null
        _observer.value = null
        _target.value = null
    }
}