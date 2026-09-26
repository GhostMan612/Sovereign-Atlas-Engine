// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

object LineOfSightEngine {
    private const val EARTH_RADIUS = 6371000.0
    private const val REFRACTION_K = 4.0 / 3.0

    private data class Sample(val distance: Double, val latitude: Double, val longitude: Double)

    internal fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return AtlasGeoMath.haversine(lat1, lon1, lat2, lon2)
    }

    internal fun interpolateGreatCircle(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        fraction: Double,
        totalMeters: Double,
    ): Pair<Double, Double> {
        return AtlasGeoMath.interpolateGreatCircle(
            lat1,
            lon1,
            lat2,
            lon2,
            fraction,
            totalMeters,
        )
    }

    suspend fun calculate(request: LoSRequest, demEngine: DemEngine?): LoSResult {
        if (demEngine == null) {
            return LoSResult(false, null, null, emptyList(), "DEM unavailable.")
        }
        val totalDistance = haversine(
            request.observer.latitude,
            request.observer.longitude,
            request.target.latitude,
            request.target.longitude,
        )
        if (totalDistance <= 0.0) {
            return LoSResult(false, null, null, emptyList(), "Observer and target coincide.")
        }
        val obsElev = request.observer.altitude
            ?: demEngine.getElevation(request.observer.latitude, request.observer.longitude)
        val tgtElev = request.target.altitude
            ?: demEngine.getElevation(request.target.latitude, request.target.longitude)
        if (obsElev == null || tgtElev == null) {
            return LoSResult(
                false,
                null,
                null,
                emptyList(),
                "Missing DEM data at Observer or Target.",
            )
        }
        val obsTotalHeight = obsElev + request.observerHeightMeters
        val tgtTotalHeight = tgtElev + request.targetHeightMeters

        val intervalMeters = when {
            totalDistance < 1000 -> 5.0
            totalDistance < 5000 -> 10.0
            else -> 20.0
        }
        val numSamples = (totalDistance / intervalMeters).toInt()
        val samples = (1 until numSamples).map { index ->
            val distance = index * intervalMeters
            val (latitude, longitude) = interpolateGreatCircle(
                request.observer.latitude,
                request.observer.longitude,
                request.target.latitude,
                request.target.longitude,
                distance / totalDistance,
                totalDistance,
            )
            Sample(distance, latitude, longitude)
        }
        val elevations = demEngine.getElevationsBatch(
            samples.map { it.latitude to it.longitude },
        )
        val effectiveRadius = EARTH_RADIUS * REFRACTION_K
        var maxObstruction = 0.0
        var blockingPoint: GeoPoint? = null
        var blockingDistance: Double? = null
        val now = System.currentTimeMillis()
        val profile = samples.mapIndexedNotNull { index, sample ->
            val terrainElev = elevations[index] ?: return@mapIndexedNotNull null
            val x = sample.distance
            val total = totalDistance
            val earthBulge = (x * (total - x)) / (2.0 * effectiveRadius)
            val correctedTerrain = terrainElev + earthBulge
            val rayElevation = obsTotalHeight + (tgtTotalHeight - obsTotalHeight) * (x / total)
            val obstruction = correctedTerrain - rayElevation
            if (obstruction > maxObstruction) {
                maxObstruction = obstruction
                blockingDistance = x
                blockingPoint = GeoPoint(
                    sample.latitude,
                    sample.longitude,
                    terrainElev,
                    null,
                    null,
                    now,
                )
            }
            LoSProfilePoint(
                x,
                correctedTerrain,
                rayElevation,
                GeoPoint(sample.latitude, sample.longitude, terrainElev, null, null, now),
            )
        }
        return LoSResult(maxObstruction <= 0.0, blockingPoint, blockingDistance, profile)
    }
}
