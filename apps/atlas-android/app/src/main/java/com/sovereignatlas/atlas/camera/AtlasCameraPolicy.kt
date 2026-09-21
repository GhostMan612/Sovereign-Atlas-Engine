// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.camera

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.location.AtlasLocationFix
import com.sovereignatlas.atlas.location.AtlasLocationStatus

const val MY_LOCATION_ZOOM = 15.0
const val STARTUP_LOCAL_ZOOM = 13.0

enum class AtlasRotationPolicy { gated, free }

val DEFAULT_ROTATION_POLICY = AtlasRotationPolicy.gated

fun startupIntent(
    status: AtlasLocationStatus,
    fix: AtlasLocationFix?,
): AtlasCameraState? {
    if (status != AtlasLocationStatus.valid || fix == null) return null
    val state = AtlasCameraState(
        center = fix.position,
        zoom = STARTUP_LOCAL_ZOOM,
        bearing = 0.0,
        pitch = 0.0,
    )
    return if (state.validate().isValid) state else null
}

fun myLocationIntent(
    status: AtlasLocationStatus,
    fix: AtlasLocationFix?,
    currentZoom: Double,
    bearing: Double,
): AtlasCameraState? {
    if (status != AtlasLocationStatus.valid || fix == null) return null
    val state = AtlasCameraState(
        center = fix.position,
        zoom = maxOf(currentZoom, MY_LOCATION_ZOOM),
        bearing = bearing,
        pitch = 0.0,
    )
    return if (state.validate().isValid) state else null
}

fun goToIntent(
    target: AtlasCoordinate,
    currentZoom: Double,
    bearing: Double,
): AtlasCameraState? {
    val state = AtlasCameraState(
        center = target,
        zoom = currentZoom,
        bearing = bearing,
        pitch = 0.0,
    )
    return if (state.validate().isValid) state else null
}
