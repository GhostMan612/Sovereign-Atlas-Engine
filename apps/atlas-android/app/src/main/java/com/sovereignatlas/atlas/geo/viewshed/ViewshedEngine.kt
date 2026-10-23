// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.viewshed

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.los.ElevationProvider

class ViewshedEngine(private val elevationProvider: ElevationProvider) {

    suspend fun computeViewshed(config: ViewshedConfig): ViewshedResult {
        if (config.maxRadiusMeters <= 0.0 || config.rayStepMeters <= 0.0 || config.angularStepDeg <= 0.0) {
            return emptyResult(config)
        }

        val bearings = ArrayList<Double>()
        var b = 0.0
        while (b < 360.0) {
            bearings.add(b)
            b += config.angularStepDeg
        }

        val distances = ArrayList<Double>()
        var d = config.rayStepMeters
        while (d < config.maxRadiusMeters) {
            distances.add(d)
            d += config.rayStepMeters
        }
        if (distances.isEmpty() || distances.last() < config.maxRadiusMeters) {
            distances.add(config.maxRadiusMeters)
        }

        val queryPoints = ArrayList<Pair<Double, Double>>()
        queryPoints.add(config.observer.latitude to config.observer.longitude)

        val rayCoordinates = ArrayList<List<AtlasCoordinate>>(bearings.size)
        for (bearing in bearings) {
            val coords = ArrayList<AtlasCoordinate>(distances.size)
            for (dist in distances) {
                val coord = AtlasGeoMath.destinationPoint(
                    origin = config.observer,
                    distanceKm = dist / 1000.0,
                    bearingDeg = bearing,
                )
                coords.add(coord)
                queryPoints.add(coord.latitude to coord.longitude)
            }
            rayCoordinates.add(coords)
        }

        val elevations = elevationProvider.getElevations(queryPoints)
        val observerElev = elevations.firstOrNull() ?: 0.0
        val observerEye = observerElev + config.observerHeightAglMeters

        var elevationIndex = 1
        val computedRays = ArrayList<ViewshedRay>(bearings.size)
        val visiblePerimeter = ArrayList<AtlasCoordinate>(bearings.size)
        val maxRangeCircle = ArrayList<AtlasCoordinate>(bearings.size)

        for (i in bearings.indices) {
            val bearing = bearings[i]
            val coords = rayCoordinates[i]
            val samples = ArrayList<ViewshedSample>(distances.size)

            var maxTangent = Double.NEGATIVE_INFINITY
            var maxVisibleDist = 0.0
            var horizonCoord = config.observer

            for (j in distances.indices) {
                val dist = distances[j]
                val coord = coords[j]
                val terrainElev = elevations.getOrNull(elevationIndex++) ?: observerElev

                val curvatureDrop = (dist * dist) / (2.0 * EFFECTIVE_EARTH_RADIUS_M)
                val apparentElev = terrainElev - curvatureDrop

                val targetElev = terrainElev + config.targetHeightAglMeters
                val targetApparent = targetElev - curvatureDrop
                val targetTangent = (targetApparent - observerEye) / dist

                val isVisible = targetTangent >= maxTangent

                val terrainTangent = (apparentElev - observerEye) / dist
                if (terrainTangent > maxTangent) {
                    maxTangent = terrainTangent
                }

                if (isVisible) {
                    maxVisibleDist = dist
                    horizonCoord = coord
                }

                samples.add(
                    ViewshedSample(
                        coordinate = coord,
                        distanceMeters = dist,
                        terrainElevationMeters = terrainElev,
                        isVisible = isVisible,
                    ),
                )
            }

            val isObscuredAtMax = samples.isNotEmpty() && !samples.last().isVisible

            computedRays.add(
                ViewshedRay(
                    bearingDeg = bearing,
                    maxVisibleDistanceMeters = maxVisibleDist,
                    horizonCoordinate = horizonCoord,
                    isObscuredAtMaxRange = isObscuredAtMax,
                    samples = samples,
                ),
            )

            visiblePerimeter.add(horizonCoord)
            maxRangeCircle.add(coords.last())
        }

        return ViewshedResult(
            config = config,
            observerElevationMeters = observerElev,
            rays = computedRays,
            visiblePerimeter = visiblePerimeter,
            maxRangeCircle = maxRangeCircle,
        )
    }

    private fun emptyResult(config: ViewshedConfig) = ViewshedResult(
        config = config,
        observerElevationMeters = 0.0,
        rays = emptyList(),
        visiblePerimeter = emptyList(),
        maxRangeCircle = emptyList(),
    )

    companion object {
        const val EFFECTIVE_EARTH_RADIUS_M = 6_371_000.0 * (4.0 / 3.0)
    }
}
