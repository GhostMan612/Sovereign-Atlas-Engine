// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Press is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

/**
 * Point-in-ring and point-in-multipolygon tests, plus proximity queries.
 *
 * The ray-crossing rule, not a winding number, and the difference matters at the
 * boundary. A winding-number test classifies a point exactly on an edge as inside
 * or outside depending on which side it approaches from; the crossing test below
 * is explicitly half-open on longitude, so a vertex is counted once.
 *
 * All methods take WGS84 coordinates and return plain values. Nothing here throws:
 * a spatial query over a dataset of unknown quality should return "not contained"
 * for an unanswerable shape rather than abort the query.
 */
object PointInPolygon {

    /**
     * Whether [point] lies inside [ring], boundary included.
     *
     * The `>` comparison on the longitude crossing is the half-open fix: an edge
     * that runs exactly through the point's latitude is counted when it extends
     * eastward and skipped when westward, so a shared vertex is never counted
     * twice. Using `>=` would double-count every vertex and misclassify shapes
     * whose vertices sit exactly on the query latitude.
     */
    fun contains(ring: PolygonRing, point: AtlasCoordinate): Boolean {
        val vertices = ring.vertices
        if (vertices.size < 3) return false

        var inside = false
        var index = vertices.size - 1
        for (current in vertices.indices) {
            val a = vertices[index]
            val b = vertices[current]

            val straddles = (a.latitude > point.latitude) != (b.latitude > point.latitude)
            if (straddles) {
                val crossingLongitude =
                    (b.longitude - a.longitude) * (point.latitude - a.latitude) /
                    (b.latitude - a.latitude) + a.longitude
                if (point.longitude < crossingLongitude) inside = !inside
            }
            index = current
        }
        return inside
    }

    /**
     * Whether [point] lies inside any ring of [polygon].
     *
     * Even-odd semantics: a point inside two overlapping rings of one polygon is
     * reported inside, because the winding parity is what a filled polygon means
     * visually. Rings are evaluated in order and the first hit wins, so a
     * polygon cannot be used to express a hole; that is what [Polygon] is for.
     */
    fun containsAny(polygon: List<PolygonRing>, point: AtlasCoordinate): Boolean =
        polygon.any { contains(it, point) }

    /**
     * Whether [point] is within [distanceMeters] of [ring], boundary included.
     *
     * Cheaper than buffering when the question is "is this contact near this
     * boundary", which is the common tactical question, and it costs one distance
     * computation per vertex instead of constructing a buffer polygon.
     */
    fun withinDistanceOf(
        ring: PolygonRing,
        point: AtlasCoordinate,
        distanceMeters: Double,
    ): Boolean {
        if (contains(ring, point)) return true
        val vertices = ring.vertices
        if (vertices.size < 2) return false
        for (index in vertices.indices) {
            val current = vertices[index]
            val next = vertices[(index + 1) % vertices.size]
            if (distanceToEdgeKm(point, current, next) * 1000.0 <= distanceMeters) {
                return true
            }
        }
        return false
    }

    /**
     * Great-circle distance from [point] to the segment [from]-[to], in km.
     *
     * Sampled at 1 km steps and then refined by bisection. Exact spherical
     * point-to-arc distance needs a numerical solve; sampling is within a few
     * metres over the ranges a tactical map deals in, and a bisection pass brings
     * that to well under a metre.
     */
    fun distanceToEdgeKm(
        point: AtlasCoordinate,
        from: AtlasCoordinate,
        to: AtlasCoordinate,
    ): Double {
        val segmentKm = AtlasGeoMath.haversineKm(from, to)
        if (segmentKm <= 1e-9) return AtlasGeoMath.haversineKm(point, from)

        // 1 km steps, capped so a very long edge cannot blow up the sample count.
        val steps = kotlin.math.min(MAX_SAMPLES, kotlin.math.max(2, (segmentKm / 1.0).toInt() + 1))
        var bestDistance = Double.MAX_VALUE
        var bestFraction = 0.0

        for (step in 0..steps) {
            val fraction = step.toDouble() / steps
            val candidate = AtlasGeoMath.interpolateAlongSegment(from, to, fraction)
            val distance = AtlasGeoMath.haversineKm(point, candidate)
            if (distance < bestDistance) {
                bestDistance = distance
                bestFraction = fraction
            }
        }

        // Refine around the best coarse sample.
        val window = 1.0 / steps
        var low = (bestFraction - window).coerceIn(0.0, 1.0)
        var high = (bestFraction + window).coerceIn(0.0, 1.0)
        repeat(BISECTION_STEPS) {
            val middle = (low + high) / 2.0
            val left = (low + middle) / 2.0
            val right = (middle + high) / 2.0
            val dLeft = AtlasGeoMath.haversineKm(point, AtlasGeoMath.interpolateAlongSegment(from, to, left))
            val dRight = AtlasGeoMath.haversineKm(point, AtlasGeoMath.interpolateAlongSegment(from, to, right))
            if (dLeft < dRight) high = middle else low = middle
        }
        return minOf(
            bestDistance,
            AtlasGeoMath.haversineKm(
                point,
                AtlasGeoMath.interpolateAlongSegment(from, to, (low + high) / 2.0),
            ),
        )
    }

    private const val MAX_SAMPLES = 512
    private const val BISECTION_STEPS = 24
}