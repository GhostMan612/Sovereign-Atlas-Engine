// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.los

import com.sovereignatlas.atlas.geo.GeoPoint

data class ProfilePoint(
    val location: GeoPoint,
    val distanceFromStartMeters: Double,
    val terrainElevationMeters: Double,
    val isVisible: Boolean
)

data class TerrainProfile(
    val observerLocation: GeoPoint,
    val targetLocation: GeoPoint,
    val observerElevationMeters: Double,
    val targetElevationMeters: Double,
    val points: List<ProfilePoint>,
    val hasLineOfSight: Boolean,
    val errorMessage: String? = null
)

interface ElevationProvider {
    suspend fun getElevation(latitude: Double, longitude: Double): Double?
}
