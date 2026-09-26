// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object LineOfSightEngine {
    private const val EARTH_RADIUS = 6371000.0
    private const val REFRACTION_K = 4.0 / 3.0

    private data class Sample(val distance: Double, val latitude: Double, val longitude: Double)

    internal fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = EARTH_RADIUS
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * radius * asin(sqrt(a))
    }

    internal fun interpolateGreatCircle(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        fraction: Double,
        totalMeters: Double,
    ): Pair<Double, Double> {
        if (totalMeters == 0.0) return lat1 to lon1
        val p1 = Math.toRadians(lat1)
        val l1 = Math.toRadians(lon1)
        val p2 = Math.toRadians(lat2)
        val l2 = Math.toRadians(lon2)
        val delta = totalMeters / EARTH_RADIUS
        val a = sin((1 - fraction) * delta) / sin(delta)
        val b = sin(fraction * delta) / sin(delta)
        val x = a * cos(p1) * cos(l1) + b * cos(p2) * cos(l2)
        val y = a * cos(p1) * sin(l1) + b * cos(p2) * sin(l2)
        val z = a * sin(p1) + b * sin(p2)
        return Math.toDegrees(atan2(z, sqrt(x * x + y * y))) to
            Math.toDegrees(atan2(y, x))
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
