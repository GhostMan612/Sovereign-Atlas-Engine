// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.viewshed

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViewshedState {
    private val _result = MutableStateFlow<ViewshedResult?>(null)
    val result: StateFlow<ViewshedResult?> = _result.asStateFlow()

    private val _isCalculating = MutableStateFlow(false)
    val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

    private val _observer = MutableStateFlow<AtlasCoordinate?>(null)
    val observer: StateFlow<AtlasCoordinate?> = _observer.asStateFlow()

    fun setResult(result: ViewshedResult?) {
        _result.value = result
    }

    fun setCalculating(calculating: Boolean) {
        _isCalculating.value = calculating
    }

    fun setObserver(coord: AtlasCoordinate?) {
        _observer.value = coord
    }

    fun clear() {
        _result.value = null
        _isCalculating.value = false
        _observer.value = null
    }
}
