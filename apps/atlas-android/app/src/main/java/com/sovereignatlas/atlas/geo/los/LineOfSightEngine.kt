// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.los

import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.GeoPoint
import kotlin.math.max

class LineOfSightEngine(private val elevationProvider: ElevationProvider) {

    companion object {
        private const val EFFECTIVE_EARTH_RADIUS_M = 6371000.0 * (4.0 / 3.0)
        private const val SAMPLES = 100
    }

    suspend fun calculateProfile(
        start: GeoPoint,
        end: GeoPoint,
        observerHeightM: Double = 2.0,
        targetHeightM: Double = 2.0
    ): TerrainProfile {
        val distanceTotal = AtlasGeoMath.haversine(
            start.latitude, start.longitude, end.latitude, end.longitude
        )

        if (distanceTotal <= 0.0) {
            return TerrainProfile(start, end, 0.0, 0.0, emptyList(), false, "Observer and target are at the same location")
        }

        val coords = (0..SAMPLES).map { i ->
            val fraction = i.toDouble() / SAMPLES
            val interp = AtlasGeoMath.interpolateGreatCircle(
                start.latitude, start.longitude, end.latitude, end.longitude, fraction, distanceTotal
            )
            Pair(interp.first, interp.second)
        }

        val elevations = elevationProvider.getElevations(coords)
        if (elevations.size != coords.size) {
            return TerrainProfile(start, end, 0.0, 0.0, emptyList(), false, "Elevation batch size mismatch")
        }

        val startTerrain = elevations.first() ?: start.altitude
        val endTerrain = elevations.last() ?: end.altitude

        if (startTerrain == null || endTerrain == null) {
            return TerrainProfile(start, end, 0.0, 0.0, emptyList(), false, "Missing DEM data at endpoints")
        }

        val startElev = startTerrain + observerHeightM
        val endElev = endTerrain + targetHeightM

        val points = mutableListOf<ProfilePoint>()
        var highestSlope = Double.NEGATIVE_INFINITY
        var hasLineOfSight = true
        val ts = start.timestamp

        for (i in 0..SAMPLES) {
            val fraction = i.toDouble() / SAMPLES
            val lat = coords[i].first
            val lon = coords[i].second
            val dist = distanceTotal * fraction

            val terrainElev = if (i == 0) startTerrain
                              else if (i == SAMPLES) endTerrain
                              else elevations[i]

            if (terrainElev == null) {
                return TerrainProfile(
                    start, end, startElev, endElev, emptyList(), false,
                    "Missing DEM data along path"
                )
            }

            val curvatureDrop = (dist * dist) / (2 * EFFECTIVE_EARTH_RADIUS_M)
            val effectiveElev = terrainElev - curvatureDrop

            var isVisible = true
            if (i > 0) {
                val slopeToPoint = (effectiveElev - startElev) / dist
                if (slopeToPoint < highestSlope) {
                    isVisible = false
                    if (i == SAMPLES) hasLineOfSight = false
                }
                highestSlope = max(highestSlope, slopeToPoint)
            }

            points.add(
                ProfilePoint(
                    location = GeoPoint(lat, lon, terrainElev, null, null, ts),
                    distanceFromStartMeters = dist,
                    terrainElevationMeters = terrainElev,
                    isVisible = isVisible
                )
            )
        }

        return TerrainProfile(start, end, startElev, endElev, points, hasLineOfSight)
    }
}
