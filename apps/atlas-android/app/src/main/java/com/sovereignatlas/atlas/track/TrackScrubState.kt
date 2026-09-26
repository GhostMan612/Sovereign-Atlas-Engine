// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrackScrubState {
    private val _activePoint = MutableStateFlow<ProfilePoint?>(null)
    val activePoint: StateFlow<ProfilePoint?> = _activePoint.asStateFlow()

    fun setActivePoint(point: ProfilePoint?) {
        _activePoint.value = point
    }
}
