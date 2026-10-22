// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import kotlin.math.cos

data class SnappedPosition(
    val rawCoordinate: AtlasCoordinate,
    val snappedCoordinate: AtlasCoordinate,
    val segmentIndex: Int,
    val distanceToSegmentMeters: Double,
    val distanceAlongRouteMeters: Double,
    val remainingDistanceMeters: Double,
    val isOffRoute: Boolean,
    val confidence: Double,
)

class RouteSnapper(
    val route: RoadRouteResult,
    val offRouteThresholdMeters: Double = 35.0,
) {
    fun snap(
        userCoord: AtlasCoordinate,
        accuracyMeters: Double = 10.0,
    ): SnappedPosition {
        val coords = route.coordinates
        if (coords.isEmpty()) {
            return SnappedPosition(
                rawCoordinate = userCoord,
                snappedCoordinate = userCoord,
                segmentIndex = 0,
                distanceToSegmentMeters = 0.0,
                distanceAlongRouteMeters = 0.0,
                remainingDistanceMeters = 0.0,
                isOffRoute = false,
                confidence = 0.0,
            )
        }

        if (coords.size == 1) {
            val dist = AtlasGeoMath.haversine(
                userCoord.latitude,
                userCoord.longitude,
                coords[0].latitude,
                coords[0].longitude,
            )
            return SnappedPosition(
                rawCoordinate = userCoord,
                snappedCoordinate = coords[0],
                segmentIndex = 0,
                distanceToSegmentMeters = dist,
                distanceAlongRouteMeters = 0.0,
                remainingDistanceMeters = 0.0,
                isOffRoute = dist > offRouteThresholdMeters,
                confidence = maxOf(0.0, 1.0 - (dist / offRouteThresholdMeters)),
            )
        }

        var minDistanceToSegment = Double.POSITIVE_INFINITY
        var bestSnappedCoord = coords[0]
        var bestSegmentIndex = 0
        var bestFraction = 0.0

        val segmentDistances = DoubleArray(coords.size - 1)
        var cumulativeDist = 0.0
        val cumulativeDistances = DoubleArray(coords.size)
        cumulativeDistances[0] = 0.0

        for (i in 0 until coords.size - 1) {
            val p1 = coords[i]
            val p2 = coords[i + 1]
            val segLen = AtlasGeoMath.haversine(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            segmentDistances[i] = segLen
            cumulativeDist += segLen
            cumulativeDistances[i + 1] = cumulativeDist

            val (projected, dist, fraction) = projectOntoSegment(userCoord, p1, p2, segLen)
            if (dist < minDistanceToSegment) {
                minDistanceToSegment = dist
                bestSnappedCoord = projected
                bestSegmentIndex = i
                bestFraction = fraction
            }
        }

        val distanceAlongRoute = cumulativeDistances[bestSegmentIndex] +
            (segmentDistances[bestSegmentIndex] * bestFraction)
        val remainingDistance = maxOf(0.0, route.totalDistanceMeters - distanceAlongRoute)

        val allowedOffRoute = maxOf(offRouteThresholdMeters, accuracyMeters * 2.5)
        val isOffRoute = minDistanceToSegment > allowedOffRoute

        val confidence = when {
            minDistanceToSegment <= 5.0 -> 1.0
            minDistanceToSegment >= allowedOffRoute -> 0.0
            else -> 1.0 - (minDistanceToSegment / allowedOffRoute)
        }

        return SnappedPosition(
            rawCoordinate = userCoord,
            snappedCoordinate = bestSnappedCoord,
            segmentIndex = bestSegmentIndex,
            distanceToSegmentMeters = minDistanceToSegment,
            distanceAlongRouteMeters = distanceAlongRoute,
            remainingDistanceMeters = remainingDistance,
            isOffRoute = isOffRoute,
            confidence = confidence,
        )
    }

    private data class ProjectionResult(
        val coordinate: AtlasCoordinate,
        val distanceMeters: Double,
        val fraction: Double,
    )

    private fun projectOntoSegment(
        p: AtlasCoordinate,
        a: AtlasCoordinate,
        b: AtlasCoordinate,
        segLengthMeters: Double,
    ): ProjectionResult {
        if (segLengthMeters < 0.1) {
            val dist = AtlasGeoMath.haversine(p.latitude, p.longitude, a.latitude, a.longitude)
            return ProjectionResult(a, dist, 0.0)
        }

        val latMeanRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val cosLat = cos(latMeanRad)

        val ax = a.longitude * cosLat
        val ay = a.latitude
        val bx = b.longitude * cosLat
        val by = b.latitude
        val px = p.longitude * cosLat
        val py = p.latitude

        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy

        val t = if (lengthSq > 0.0) {
            val dot = (px - ax) * dx + (py - ay) * dy
            (dot / lengthSq).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val projLat = a.latitude + t * (b.latitude - a.latitude)
        val projLon = a.longitude + t * (b.longitude - a.longitude)
        val projCoord = AtlasCoordinate(projLat, projLon)

        val dist = AtlasGeoMath.haversine(p.latitude, p.longitude, projLat, projLon)
        return ProjectionResult(projCoord, dist, t)
    }
}
