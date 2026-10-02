// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

/**
 * A polygon: an outer boundary plus zero or more holes.
 *
 * OGC semantics, deliberately. A GeoJSON `Polygon` encodes a hole by ring
 * orientation and a `MultiPolygon` by which ring belongs to which part; a separate
 * [holes] list says the same thing without asking a caller to reason about winding,
 * and the winding is already load-bearing for [SphericalArea.ofSigned].
 *
 * Pure and immutable. Construction validates, so an instance cannot hold a ring
 * that is not actually closed.
 */
class Polygon private constructor(
    val exterior: PolygonRing,
    val holes: List<PolygonRing>,
) {

    /** Exterior plus holes, for algorithms that do not distinguish them. */
    val allRings: List<PolygonRing> get() = listOf(exterior) + holes

    /**
     * Whether [point] is inside the exterior and outside every hole.
     *
     * Hole-first is not a shortcut; it is the only correct order. A point inside a
     * hole is also inside the exterior ring, so testing the exterior alone would
     * report it as contained.
     */
    fun contains(point: AtlasCoordinate): Boolean {
        if (!PointInPolygon.contains(exterior, point)) return false
        return holes.none { PointInPolygon.contains(it, point) }
    }

    /** Area in square metres: exterior minus holes. */
    fun areaSquareMeters(): Double =
        SphericalArea.of(exterior) - holes.sumOf { SphericalArea.of(it) }

    /** Perimeter in metres: the exterior boundary plus every hole boundary. */
    fun perimeterMeters(): Double =
        SphericalArea.perimeterMeters(exterior) + holes.sumOf { SphericalArea.perimeterMeters(it) }

    /**
     * Area-weighted centroid, or null when the polygon has no net area.
     *
     * A polygon whose holes remove all of its interior has no centroid, and
     * returning the exterior's would point at a spot that is not in the polygon.
     */
    fun centroid(): AtlasCoordinate? {
        val exteriorCentroid = SphericalArea.centroid(exterior) ?: return null
        if (holes.isEmpty()) return exteriorCentroid

        // Subtract each hole's centroid, weighted by its area.
        var weightX = SphericalArea.of(exterior) * exteriorCentroid.longitude
        var weightY = SphericalArea.of(exterior) * exteriorCentroid.latitude
        var totalWeight = SphericalArea.of(exterior)
        for (hole in holes) {
            val holeCentroid = SphericalArea.centroid(hole) ?: continue
            val holeArea = SphericalArea.of(hole)
            weightX -= holeArea * holeCentroid.longitude
            weightY -= holeArea * holeCentroid.latitude
            totalWeight -= holeArea
        }
        if (kotlin.math.abs(totalWeight) < 1e-12) return null
        return AtlasCoordinate(
            latitude = weightY / totalWeight,
            longitude = weightX / totalWeight,
        )
    }

    /**
     * Whether this polygon and [other] share any interior ground.
     *
     * True when any vertex of either is inside the other, or any pair of edges
     * crosses. Both halves are required: two crossing "plus" shapes contain no
     * vertices of each other and still overlap, so a vertex-only test would miss
     * the most common overlap case.
     */
    fun intersects(other: Polygon): Boolean {
        if (contains(other.exterior.vertices.first()) ||
            other.contains(exterior.vertices.first())
        ) {
            return true
        }
        for (ring in allRings) {
            for (otherRing in other.allRings) {
                if (ringsCross(ring, otherRing)) return true
            }
        }
        return false
    }

    /** Whether this polygon lies entirely within [distanceMeters] of [point]. */
    fun withinDistanceOf(point: AtlasCoordinate, distanceMeters: Double): Boolean =
        PointInPolygon.withinDistanceOf(exterior, point, distanceMeters) &&
            holes.none { PointInPolygon.withinDistanceOf(it, point, distanceMeters) }

    private fun ringsCross(a: PolygonRing, b: PolygonRing): Boolean {
        val av = a.vertices
        val bv = b.vertices
        for (i in av.indices) {
            val a1 = av[i]
            val a2 = av[(i + 1) % av.size]
            for (j in bv.indices) {
                val b1 = bv[j]
                val b2 = bv[(j + 1) % bv.size]
                if (segmentsProperlyCross(a1, a2, b1, b2)) return true
            }
        }
        return false
    }

    companion object {
        /** Builds a polygon, or null when the exterior is not a usable ring. */
        fun of(exterior: List<AtlasCoordinate>, holes: List<List<AtlasCoordinate>> = emptyList()): Polygon? {
            val exteriorRing = PolygonRing.of(exterior) ?: return null
            val holeRings = holes.mapNotNull { PolygonRing.of(it) }
            // A hole that failed to parse is dropped rather than rejected: the
            // alternative is discarding a valid exterior because one hole was bad.
            return Polygon(exteriorRing, holeRings)
        }

        fun of(exterior: PolygonRing, holes: List<PolygonRing> = emptyList()): Polygon =
            Polygon(exterior, holes)
    }
}

/**
 * Whether two segments cross at a point interior to both.
 *
 * Strict inequality on the bounding-box test, so segments that merely touch at an
 * endpoint do not count as crossing. Touching is not overlapping, and reporting it
 * as such would make two adjacent parcels appear to intersect.
 */
private fun segmentsProperlyCross(
    p1: AtlasCoordinate,
    p2: AtlasCoordinate,
    p3: AtlasCoordinate,
    p4: AtlasCoordinate,
): Boolean {
    val d1 = crossProduct(p3, p4, p1)
    val d2 = crossProduct(p3, p4, p2)
    val d3 = crossProduct(p1, p2, p3)
    val d4 = crossProduct(p1, p2, p4)

    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
        ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    ) {
        return true
    }
    return false
}

private fun crossProduct(
    a: AtlasCoordinate,
    b: AtlasCoordinate,
    c: AtlasCoordinate,
): Double =
    (b.longitude - a.longitude) * (c.latitude - a.latitude) -
        (b.latitude - a.latitude) * (c.longitude - a.longitude)