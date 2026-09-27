// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.location

import com.sovereignatlas.atlas.geo.GeoPoint
import kotlinx.coroutines.flow.StateFlow

interface LocationEngine {
    val currentLocation: StateFlow<GeoPoint?>
    val isRecording: StateFlow<Boolean>
    val isFlushPending: Boolean
    fun startPassiveListening()
    fun stopPassiveListening()
    fun startRecording()
    fun resumeRecording(trackId: String)
    suspend fun stopRecording()
    fun shutdown()
}
