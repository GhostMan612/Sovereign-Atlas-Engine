// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.camera.AtlasCameraState
import com.sovereignatlas.atlas.camera.myLocationIntent
import com.sovereignatlas.atlas.camera.startupIntent
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.location.LocationService

sealed interface LocateOutcome {
    data class Applied(val intent: AtlasCameraState) : LocateOutcome
    data object Pending : LocateOutcome
    data object Ignored : LocateOutcome
}

class MapBehavior(private val location: LocationService) {
    private var startupDone = false
    private var userInteracted = false
    private var pendingRecenter = false

    fun markUserInteracted() {
        userInteracted = true
    }

    fun startupCamera(): AtlasCameraState? {
        if (startupDone || userInteracted) return null
        location.start()
        if (startupDone || userInteracted) return null
        val intent = startupIntent(
            location.status(),
            location.latestFixOrNull(),
        ) ?: return null
        startupDone = true
        return intent
    }

    fun locate(currentZoom: Double, bearing: Double): LocateOutcome {
        location.ensureActive()
        val intent = myLocationIntent(
            location.status(),
            location.latestFixOrNull(),
            currentZoom,
            bearing,
        )
        if (intent == null) {
            if (location.status() == AtlasLocationStatus.acquiring) {
                pendingRecenter = true
                return LocateOutcome.Pending
            }
            return LocateOutcome.Ignored
        }
        pendingRecenter = false
        return LocateOutcome.Applied(intent)
    }

    fun onLocationUpdate(currentZoom: Double, bearing: Double): AtlasCameraState? {
        if (!pendingRecenter) return null
        val intent = myLocationIntent(
            location.status(),
            location.latestFixOrNull(),
            currentZoom,
            bearing,
        ) ?: return null
        pendingRecenter = false
        return intent
    }

    fun isPendingRecenter(): Boolean = pendingRecenter
}
