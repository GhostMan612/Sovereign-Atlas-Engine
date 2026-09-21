// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.goto

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasCoordinates
import com.sovereignatlas.atlas.geo.AtlasGeoMath

data class GoToTarget(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

class GoToState {
    private var target: GoToTarget? = null
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun targetOrNull(): GoToTarget? = target

    fun isActive(): Boolean = target != null

    fun activate(id: String, latitude: Double, longitude: Double, label: String) {
        val point = AtlasCoordinates.checked(latitude, longitude)
        target = GoToTarget(
            id = id,
            latitude = point.latitude,
            longitude = point.longitude,
            label = label,
        )
        notifyListeners()
    }

    fun clear() {
        if (target == null) return
        target = null
        notifyListeners()
    }

    fun distanceKmTo(fix: AtlasCoordinate?): Double? {
        val active = target ?: return null
        if (fix == null) return null
        return AtlasGeoMath.haversineKm(
            fix,
            AtlasCoordinate(latitude = active.latitude, longitude = active.longitude),
        )
    }

    fun bearingDegTo(fix: AtlasCoordinate?): Double? {
        val active = target ?: return null
        if (fix == null) return null
        return try {
            AtlasGeoMath.initialBearingDeg(
                fix,
                AtlasCoordinate(
                    latitude = active.latitude,
                    longitude = active.longitude,
                ),
            )
        } catch (error: AtlasContractException) {
            null
        }
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener()
        }
    }
}
