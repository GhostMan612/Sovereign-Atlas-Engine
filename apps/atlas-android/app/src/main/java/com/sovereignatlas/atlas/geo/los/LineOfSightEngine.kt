// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.los

import com.sovereignatlas.atlas.geo.GeoPoint
import com.sovereignatlas.atlas.geo.LoSRequest

/**
 * Casts a ray between two points and reports whether terrain blocks it.
 *
 * This is the single line-of-sight engine. It replaces two coexisting engines that
 * disagreed: an `object` in `geo/` that found the maximum obstruction but SILENTLY
 * SKIPPED unmeasured samples, and a class in `geo/los/` that aborted on the same
 * gap but could not name where the obstruction was. Both behaviours are here —
 * abort on incomplete terrain, report the maximum obstruction — so the map overlay
 * and the profile chart cannot contradict each other.
 *
 * Pure Kotlin: no Android, no MapLibre, no I/O. The elevation source is injected.
 */
class LineOfSightEngine(private val elevationProvider: ElevationProvider) {

    /**
     * Samples along the ray.
     *
     * Adaptive, not fixed: 5 m under 1 km, 10 m under 5 km, 20 m beyond. A fixed
     * 100-point sample spacing means resolution that varies with distance, so a
     * 20 km ray would be inspected every ~200 m and could walk straight over a
     * short ridge.
     */
    private fun sampleCountFor(distanceMeters: Double): Int {
        val intervalMeters = when {
            distanceMeters < 1_000.0 -> 5.0
            distanceMeters < 5_000.0 -> 10.0
            else -> 20.0
        }
        // Endpoints are excluded: they define the ray rather than block it. At
        // least two interior samples even on a very short ray.
        return maxOf(2, (distanceMeters / intervalMeters).toInt() - 1)
    }

    suspend fun calculateProfile(
        start: GeoPoint,
        end: GeoPoint,
        observerHeightM: Double = 2.0,
        targetHeightM: Double = 2.0,
    ): TerrainProfile = calculateProfile(LoSRequest(start, end, observerHeightM, targetHeightM))

    suspend fun calculateProfile(request: LoSRequest): TerrainProfile {
        val observer = request.observer
        val target = request.target

        val totalDistance = GeoDistance.groundDistance(observer, target)
        if (totalDistance <= 0.0) {
            return failure(observer, target, LineOfSight(LoSStatus.DegenerateGeometry))
        }

        val sampleCount = sampleCountFor(totalDistance)
        // Endpoints ARE queried. They define the ray's heights, so they must come
        // from the terrain source rather than be assumed; a GeoPoint.altitude
        // overrides the source where a caller supplied one.
        val coordinates = (0..sampleCount).map { index ->
            val distance = totalDistance * index / (sampleCount + 1)
            val (latitude, longitude) = GeoDistance.interpolate(observer, target, distance, totalDistance)
            latitude to longitude
        }

        val sampled = elevationProvider.getElevations(coordinates)

        // A provider returning the wrong count cannot be trusted for any of them.
        if (sampled.size != coordinates.size) {
            return failure(observer, target, LineOfSight(LoSStatus.ProviderError))
        }

        // An explicit altitude on a GeoPoint wins: a caller that supplied a height
        // meant it. Otherwise the terrain source answers.
        val observerTerrain = observer.altitude ?: sampled.first()
        val targetTerrain = target.altitude ?: sampled.last()

        if (observerTerrain == null || targetTerrain == null) {
            return failure(observer, target, LineOfSight(LoSStatus.NoTerrainData))
        }

        // Any interior gap aborts. Skipping it would let an unmeasured stretch read
        // as clear ground, which is the false-certainty failure this engine exists
        // to prevent. The endpoints are excluded from this check because their
        // absence is already reported as NoTerrainData above.
        if (sampled.drop(1).dropLast(1).any { it == null }) {
            return failure(observer, target, LineOfSight(LoSStatus.IncompleteTerrain))
        }

        val observerEye = observerTerrain + request.observerHeightMeters
        val targetEye = targetTerrain + request.targetHeightMeters

        var maxObstruction = 0.0
        var blockingIndex = -1
        // Indices 1..sampleCount, i.e. EVERY interior sample that was requested.
        // An earlier draft used `1 until sampleCount`, which silently discarded the
        // reading nearest the target - the one most likely to matter - and left the
        // sample set asymmetric about the midpoint.
        val interior = (1..sampleCount).toList()
        val points = ArrayList<ProfilePoint>(interior.size)

        for (index in interior) {
            val distance = totalDistance * index / (sampleCount + 1)
            val terrainElevation = sampled[index]!!
            val (latitude, longitude) = coordinates[index]

            // Symmetric bulge: zero at both endpoints, greatest at the midpoint.
            // The replaced class engine used distance-from-observer squared, which
            // is zero at the observer and largest at the target, so its correction
            // never vanished at the far end and it drifted from this one.
            val earthBulge = (distance * (totalDistance - distance)) /
                (2.0 * EFFECTIVE_EARTH_RADIUS_M)
            val correctedTerrain = terrainElevation + earthBulge
            val rayElevation = observerEye + (targetEye - observerEye) * (distance / totalDistance)
            val obstruction = correctedTerrain - rayElevation

            if (obstruction > maxObstruction) {
                maxObstruction = obstruction
                blockingIndex = index
            }

            points.add(
                ProfilePoint(
                    location = GeoPoint(
                        latitude,
                        longitude,
                        terrainElevation,
                        null,
                        null,
                        observer.timestamp,
                    ),
                    distanceFromStartMeters = distance,
                    terrainElevationMeters = terrainElevation,
                    rayElevationMeters = rayElevation,
                    correctedTerrainElevationMeters = correctedTerrain,
                    obstructionMeters = obstruction,
                    isVisible = maxObstruction <= 0.0,
                ),
            )
        }

        val verdict = if (maxObstruction <= 0.0) {
            LineOfSight(LoSStatus.Clear)
        } else {
            val blocker = points[blockingIndex.coerceIn(0, points.lastIndex)]
            LineOfSight(
                status = LoSStatus.BlockedTerrain,
                blockingPoint = blocker.location,
                blockingDistanceMeters = blocker.distanceFromStartMeters,
            )
        }

        return TerrainProfile(
            observerLocation = observer,
            targetLocation = target,
            observerElevationMeters = observerTerrain,
            targetElevationMeters = targetTerrain,
            points = points,
            lineOfSight = verdict,
        )
    }

    private fun failure(observer: GeoPoint, target: GeoPoint, verdict: LineOfSight) = TerrainProfile(
        observerLocation = observer,
        targetLocation = target,
        observerElevationMeters = 0.0,
        targetElevationMeters = 0.0,
        points = emptyList(),
        lineOfSight = verdict,
    )

    private companion object {
        /**
         * Effective earth radius under standard atmospheric refraction (4/3).
         * Hoisted here so no second engine can quietly pick a different value.
         */
        const val EFFECTIVE_EARTH_RADIUS_M = 6_371_000.0 * (4.0 / 3.0)
    }
}