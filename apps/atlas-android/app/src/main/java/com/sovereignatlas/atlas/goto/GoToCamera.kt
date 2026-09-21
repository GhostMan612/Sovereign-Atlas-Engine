// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.goto

import com.sovereignatlas.atlas.camera.AtlasCameraState
import com.sovereignatlas.atlas.camera.goToIntent
import com.sovereignatlas.atlas.geo.AtlasCoordinate

fun goToCameraIntent(
    state: GoToState,
    currentZoom: Double,
    bearing: Double,
): AtlasCameraState? {
    val target = state.targetOrNull() ?: return null
    return goToIntent(
        target = AtlasCoordinate(
            latitude = target.latitude,
            longitude = target.longitude,
        ),
        currentZoom = currentZoom,
        bearing = bearing,
    )
}
