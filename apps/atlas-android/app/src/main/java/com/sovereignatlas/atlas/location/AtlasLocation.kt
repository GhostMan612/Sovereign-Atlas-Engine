// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.location

import com.sovereignatlas.atlas.geo.AtlasCoordinate

data class AtlasLocationFix(
    val position: AtlasCoordinate,
    val atMs: Long,
    val accuracyM: Double? = null,
    val speedMps: Double? = null,
    val headingDeg: Double? = null,
    val altitudeM: Double? = null,
    val source: String = "",
)

enum class AtlasLocationPermission { notRequested, denied, permanentlyDenied, granted }

enum class AtlasLocationStatus {
    notRequested,
    denied,
    permanentlyDenied,
    servicesDisabled,
    acquiring,
    valid,
    stale,
    error,
}

data class AtlasLocationQuery(
    val permission: AtlasLocationPermission,
    val servicesEnabled: Boolean,
)

interface AtlasLocationSource {
    fun queryStatus(): AtlasLocationQuery
    fun requestPermission(): AtlasLocationQuery
    fun openAppSettings(): Boolean
    fun lastKnownFix(): AtlasLocationFix?
    fun setFixListener(listener: ((AtlasLocationFix) -> Unit)?)
    fun setErrorListener(listener: ((Throwable) -> Unit)?)
    fun setDoneListener(listener: (() -> Unit)?)
}
