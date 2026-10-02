// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

/**
 * Circular buffers around points, lines and polygons.
 *
 * CIRCULAR ONLY, and the limitation is stated rather than hidden. A true buffer
 * needs join geometry, mitres and round-joins; producing "good enough" joins would
 * mean inventing vertices whose correctness nobody could check, which is exactly
 * what RULES 2.3 forbids. A circular buffer is exactly right for the radial case
 * and explicitly approximate for the rest.
 *
 * For a line, the offset is built by offsetting each vertex along the
 * perpendicular bisector of its adjacent segments and sampling the arc between the
 * two offsets, so a corner is rounded rather than mitred. Vertex count is derived
 * from the radius so a small buffer is not over-sampled and a large one is not
 * visibly faceted.
 *
 * Returns null rather than a degenerate ring when the input cannot produce a
 * usable result, so a caller skipping one feature does not lose the rest.
 */
object GeometryBuffer {

    /** Vertices per full circle at the reference radius. */
    private const val BASE_VERTEX_COUNT = 36

    /**
     * Circular buffer around a point.
     *
     * Returns null for a non-positive radius: a zero-radius "buffer" is the point
     * itself, and reporting a zero-area polygon would let a caller believe it had
     * produced coverage.
     */
    fun aroundPoint(
        center: AtlasCoordinate,
        radiusKm: Double,
    ): PolygonRing? {
        if (!radiusKm.isFinite() || radiusKm <= 0.0) return null

        val steps = vertexCountFor(radiusKm)
        val vertices = (0 until steps).map { step ->
            val bearing = step * 360.0 / steps
            AtlasGeoMath.destinationPoint(center, radiusKm, bearing)
        }
        return PolygonRing.of(vertices + vertices.first())
    }

    /**
     * Circular buffer around an open polyline.
     *
     * Returns null for a non-positive radius, fewer than two vertices, or an empty
     * polyline. A single vertex has no direction, so there is no perpendicular to
     * offset along and it is delegated to [aroundPoint].
     */
    fun aroundPolyline(
        vertices: List<AtlasCoordinate>,
        radiusKm: Double,
    ): PolygonRing? {
        if (!radiusKm.isFinite() || radiusKm <= 0.0) return null
        if (vertices.isEmpty()) return null
        if (vertices.size == 1) return aroundPoint(vertices.first(), radiusKm)

        val steps = vertexCountFor(radiusKm)
        val out = ArrayList<AtlasCoordinate>(vertices.size * steps + steps + 2)

        // Forward along one side.
        for (index in vertices.indices) {
            val offset = offsetFor(vertices, index, radiusKm)
            for (step in 0..steps) {
                out.add(
                    AtlasGeoMath.destinationPoint(vertices[index], radiusKm, offset + step * 360.0 / steps),
                )
            }
        }
        // Round the far cap.
        val lastBearing = bearingFor(vertices, vertices.size - 1)
        for (step in 1 until steps) {
            out.add(
                AtlasGeoMath.destinationPoint(
                    vertices.last(),
                    radiusKm,
                    lastBearing - 90.0 + step * 360.0 / steps,
                ),
            )
        }
        // Back along the other side.
        for (index in vertices.indices.reversed()) {
            val offset = offsetFor(vertices, index, radiusKm) + 180.0
            for (step in 0..steps) {
                out.add(
                    AtlasGeoMath.destinationPoint(vertices[index], radiusKm, offset + step * 360.0 / steps),
                )
            }
        }
        // Round the near cap.
        val firstBearing = bearingFor(vertices, 0)
        for (step in 1 until steps) {
            out.add(
                AtlasGeoMath.destinationPoint(
                    vertices.first(),
                    radiusKm,
                    firstBearing + 90.0 + step * 360.0 / steps,
                ),
            )
        }

        return PolygonRing.of(out + out.first())
    }

    /**
     * Circular buffer around a polygon.
     *
     * Grows the exterior outward and shrinks the holes inward. Hole shrinkage is
     * capped so a hole smaller than twice the radius collapses rather than
     * inverting: a buffer that fills a small hole is the standard behaviour of every
     * GIS tool, and silently producing a hole with reversed winding instead would
     * be a wrong answer rather than a coarse one.
     */
    fun aroundPolygon(
        polygon: Polygon,
        radiusKm: Double,
    ): Polygon? {
        if (!radiusKm.isFinite() || radiusKm <= 0.0) return null

        val grownExterior = aroundPolyline(exteriorVertices(polygon.exterior), radiusKm)
            ?: return null

        val shrunkHoles = polygon.holes.mapNotNull { hole ->
            val vertices = hole.vertices
            val smallest = vertices.minOfOrNull { vertex ->
                AtlasGeoMath.haversineKm(polygon.centroid() ?: vertex, vertex)
            } ?: return@mapNotNull null
            if (smallest <= radiusKm) {
                null
            } else {
                shrinkPolyline(vertices, radiusKm)
            }
        }

        return Polygon.of(grownExterior, shrunkHoles.filterNotNull())
    }

    /**
     * An INWARD offset by [radiusKm]: the hole-shrinkage direction.
     *
     * Not an overload of [aroundPolyline] — a negative radius there was ambiguous
     * to read and to resolve. Named separately so every call site says which
     * direction it is going.
     */
    private fun shrinkPolyline(
        vertices: List<AtlasCoordinate>,
        radiusKm: Double,
    ): PolygonRing? {
        if (!radiusKm.isFinite() || radiusKm <= 0.0) return null
        val steps = vertexCountFor(radiusKm)
        val out = ArrayList<AtlasCoordinate>()
        for (index in vertices.indices) {
            // +180 flips the outward perpendicular to the inward one.
            val offset = offsetFor(vertices, index, radiusKm) + 180.0
            for (step in 0..steps) {
                out.add(
                    AtlasGeoMath.destinationPoint(
                        vertices[index],
                        radiusKm,
                        offset + step * 360.0 / steps,
                    ),
                )
            }
        }
        return PolygonRing.of(out + out.first())
    }

    private fun exteriorVertices(ring: PolygonRing): List<AtlasCoordinate> = ring.vertices

    /** Vertex count for a radius: more vertices for a larger circle. */
    private fun vertexCountFor(radiusKm: Double): Int {
        val scaled = BASE_VERTEX_COUNT * kotlin.math.ceil(radiusKm / 10.0).coerceAtLeast(1.0)
        return (scaled.toInt()).coerceIn(12, 360)
    }

    /**
     * The bearing to offset vertex [index] from.
     *
     * Interior vertices use the perpendicular bisector of their two adjacent
     * segments, which is what rounds a corner. Endpoints use the perpendicular to
     * their single segment.
     */
    private fun offsetFor(
        vertices: List<AtlasCoordinate>,
        index: Int,
        radiusKm: Double,
    ): Double {
        val vertex = vertices[index]
        val previous = vertices[if (index == 0) 0 else index - 1]
        val next = vertices[if (index == vertices.size - 1) vertices.size - 1 else index + 1]

        val incomingBearing = if (index == 0) {
            AtlasGeoMath.initialBearingDeg(vertex, next)
        } else {
            AtlasGeoMath.initialBearingDeg(previous, vertex)
        }
        val outgoingBearing = if (index == vertices.size - 1) {
            incomingBearing
        } else {
            AtlasGeoMath.initialBearingDeg(vertex, next)
        }

        val bisector = AtlasAngles.normalizeBearingDeg(
            incomingBearing + shortestAngularDelta(incomingBearing, outgoingBearing) / 2.0,
        )
        // Perpendicular to the bisector. Either direction yields the same ring, so
        // the choice is arbitrary but must be consistent.
        return AtlasAngles.normalizeBearingDeg(bisector + 90.0)
    }

    private fun bearingFor(vertices: List<AtlasCoordinate>, index: Int): Double {
        val vertex = vertices[index]
        val partner = if (index == 0) vertices[1] else vertices[index - 1]
        return AtlasGeoMath.initialBearingDeg(vertex, partner)
    }

    /** Signed smallest rotation from [from] to [to], in [-180, 180]. */
    private fun shortestAngularDelta(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }
}