// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

/**
 * A closed ring on the WGS84 sphere.
 *
 * [coordinates] is expected to repeat its first vertex last, per RFC 7946. That is
 * a real requirement rather than a convention: a ring whose last vertex differs
 * from its first has an implicit closing segment, and every area and
 * point-in-polygon result shifts by whatever that gap encloses.
 *
 * Pure Kotlin and immutable. Construction goes through [of] rather than the primary
 * constructor so a non-closed or too-short ring cannot exist — every downstream
 * algorithm would otherwise have to re-check what this already guarantees.
 */
class PolygonRing private constructor(val coordinates: List<AtlasCoordinate>) {

    /** Vertices excluding the duplicated closing vertex. */
    val vertices: List<AtlasCoordinate> = coordinates.dropLast(1)

    val size: Int get() = vertices.size

    companion object {
        /**
         * Builds a ring, or returns null when the input cannot describe one.
         *
         * Null rather than a throw: a caller reading a dataset of unknown quality
         * wants to skip the bad ring and keep the good ones, and an exception forces
         * it to choose between a try/catch and discarding the whole feature.
         *
         * Rejects fewer than three distinct vertices (no area), a closed ring whose
         * first and last differ (implicit segment), and non-finite coordinates.
         */
        fun of(coordinates: List<AtlasCoordinate>): PolygonRing? {
            if (coordinates.size < 4) return null
            if (coordinates.any { !it.latitude.isFinite() || !it.longitude.isFinite() }) {
                return null
            }
            val first = coordinates.first()
            val last = coordinates.last()
            if (first.latitude != last.latitude || first.longitude != last.longitude) {
                return null
            }
            if (coordinates.dropLast(1).distinct().size < 3) return null
            return PolygonRing(coordinates.toList())
        }
    }
}

/**
 * Spherical excess area of a ring, in square metres.
 *
 * The spherical-excess formula, not the planar shoelace. Shoelace on longitude and
 * latitude is wrong by a factor that grows with latitude: a "square" drawn in
 * degrees at 60 degrees north is roughly half its planar area, because a degree of
 * longitude is half as wide as a degree of latitude there. For a tactical map that
 * is not a rounding error, it is a wrong number reported confidently.
 *
 * Rings are interpreted counter-clockwise as seen from above the surface. A ring
 * wound the other way encloses the same ground but computes negative; [ofSigned]
 * preserves the sign so a caller can detect and correct reversed winding rather
 * than silently taking an absolute value.
 */
object SphericalArea {

    /**
     * Signed area in square metres. Positive for counter-clockwise winding.
     *
     * Uses [AtlasGeoMath.SPHERE_RADIUS_M]. An ellipsoidal area would be more
     * accurate by ~0.3% and needs a full geodesic solver; that is recorded as a
     * known approximation rather than hidden.
     */
    fun ofSigned(ring: PolygonRing): Double {
        val vertices = ring.vertices
        if (vertices.size < 3) return 0.0

        val radius = AtlasGeoMath.SPHERE_RADIUS_M
        var total = 0.0
        for (index in vertices.indices) {
            val current = vertices[index]
            val next = vertices[(index + 1) % vertices.size]
            val lon1 = Math.toRadians(current.longitude)
            val lon2 = Math.toRadians(next.longitude)
            val lat1 = Math.toRadians(current.latitude)
            val lat2 = Math.toRadians(next.latitude)
            total += (lon2 - lon1) * (2 + Math.sin(lat1) + Math.sin(lat2))
        }
        // NEGATED from the textbook summation, deliberately. That summation is
        // negative for a counter-clockwise ring because the north edge carries a
        // larger sin-latitude weight than the south edge of equal longitude span:
        // for a 1-degree box from 44N to 45N it yields (2+2sin44) - (2+2sin45),
        // which is negative. Flipping here makes the sign match the convention
        // every GeoJSON and OGC caller already assumes - counter-clockwise
        // exterior ring, positive area - rather than making each caller discover
        // the inversion. Verified by windingDirectionChangesTheSignButNotTheMagnitude.
        return -radius * radius * total / 2.0
    }

    /**
     * Area magnitude in square metres.
     *
     * Takes the absolute value, so a reversed ring yields the same positive answer.
     * Use this when the winding is not meaningful; use [ofSigned] when it is.
     */
    fun of(ring: PolygonRing): Double = kotlin.math.abs(ofSigned(ring))

    /** Perimeter in metres, summing great-circle edges. */
    fun perimeterMeters(ring: PolygonRing): Double {
        val vertices = ring.vertices
        if (vertices.size < 3) return 0.0
        var total = 0.0
        for (index in vertices.indices) {
            val current = vertices[index]
            val next = vertices[(index + 1) % vertices.size]
            total += AtlasGeoMath.haversineKm(current, next) * 1000.0
        }
        return total
    }

    /**
     * Area-weighted centroid of a ring, or null when it has no area.
     *
     * Weighted by triangle area about the ring's first vertex, so a larger lobe
     * pulls the centroid toward itself. A degenerate ring — all vertices on one
     * line — has no interior and returns null rather than the arithmetic mean,
     * which for such a ring is an arbitrary point on the line.
     */
    fun centroid(ring: PolygonRing): AtlasCoordinate? {
        val vertices = ring.vertices
        if (vertices.size < 3) return null

        val origin = vertices.first()
        var sumX = 0.0
        var sumY = 0.0
        var sumWeight = 0.0

        for (index in 1 until vertices.size - 1) {
            val a = vertices[index]
            val b = vertices[index + 1]
            val weight = triangleAreaAbout(origin, a, b)
            sumX += weight * ((origin.longitude + a.longitude + b.longitude) / 3.0)
            sumY += weight * ((origin.latitude + a.latitude + b.latitude) / 3.0)
            sumWeight += weight
        }

        if (kotlin.math.abs(sumWeight) < 1e-12) return null
        return AtlasCoordinate(
            latitude = sumY / sumWeight,
            longitude = sumX / sumWeight,
        )
    }

    /**
     * Signed planar area of a triangle in DEGREES-squared, used only as a weight.
     *
     * Deliberately not the spherical area: this feeds a weighted average of
     * coordinates, where what matters is that the weights are proportional to the
     * triangles' relative size, not that they are metrically exact.
     */
    private fun triangleAreaAbout(
        origin: AtlasCoordinate,
        a: AtlasCoordinate,
        b: AtlasCoordinate,
    ): Double = 0.5 * (
        (a.longitude - origin.longitude) * (b.latitude - origin.latitude) -
            (b.longitude - origin.longitude) * (a.latitude - origin.latitude)
        )
}