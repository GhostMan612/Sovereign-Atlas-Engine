// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.core.GeoJsonGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the geometry kernel.
 *
 * Two failure modes are guarded specifically, because both produce a confidently
 * wrong number rather than an error:
 *
 * - Planar shoelace on lat/lon is wrong by a factor that grows with latitude. A
 *   square at 60 N computed planarly is roughly half its true area, so
 *   [areaScalesWithLatitude] pins that the same square at the equator and at high
 *   latitude does NOT return the same area.
 * - A point-in-ring test that mishandles vertices exactly on the query latitude
 *   misclassifies shapes whose edges run through it. [vertexOnTheQueryLatitude]
 *   pins that.
 */
final class PolygonGeometryTest {

    private fun coord(lat: Double, lon: Double) = AtlasCoordinate(lat, lon)

    /** A closed ring from a list of vertices. */
    private fun ringOf(vararg vertices: Pair<Double, Double>): PolygonRing {
        val coords = vertices.map { coord(it.first, it.second) }
        return PolygonRing.of(coords + coords.first())!!
    }

    /**
     * A closed square of [sideKm] per side around (lat, lon).
     *
     * Counter-clockwise as seen from above the surface, which makes
     * [SphericalArea.ofSigned] positive. Uses the same 111.32 km/degree constant as
     * [AtlasGeoMath]'s reference radius to within 0.1%, which is inside every
     * tolerance below and keeps the expected numbers readable.
     */
    private fun squareAt(
        lat: Double,
        lon: Double,
        sideKm: Double,
    ): PolygonRing {
        val half = sideKm / 2.0
        val latDelta = half / 111.32
        val lonDelta = half / (111.32 * kotlin.math.cos(Math.toRadians(lat)))
        return ringOf(
            lat - latDelta to lon - lonDelta,
            lat + latDelta to lon - lonDelta,
            lat + latDelta to lon + lonDelta,
            lat - latDelta to lon + lonDelta,
        )
    }

    /** Degrees of longitude spanned by [km] at [lat]. */
    private fun lonDegreesFor(km: Double, lat: Double): Double =
        km / (111.32 * kotlin.math.cos(Math.toRadians(lat)))

    // ---- ring construction ---------------------------------------------------

    @Test
    fun aRingMustRepeatItsFirstVertex() {
        val unclosed = listOf(
            coord(0.0, 0.0), coord(0.0, 1.0), coord(1.0, 1.0), coord(1.0, 0.0),
        )
        assertNull("a ring whose last vertex differs from its first is not closed", PolygonRing.of(unclosed))

        val closed = unclosed + coord(0.0, 0.0)
        assertNotNull(PolygonRing.of(closed))
    }

    @Test
    fun aRingNeedsThreeDistinctVertices() {
        assertNull(PolygonRing.of(emptyList()))
        assertNull(PolygonRing.of(listOf(coord(0.0, 0.0))))
        assertNull(PolygonRing.of(listOf(coord(0.0, 0.0), coord(0.0, 1.0))))
    }

    @Test
    fun aCollinearRingIsZeroAreaRatherThanRejected() {
        // A closed ring of collinear points satisfies every structural rule, so it
        // constructs. It must then report zero area rather than a spurious positive
        // one from accumulated floating-point noise.
        val collinear = PolygonRing.of(
            listOf(coord(0.0, 0.0), coord(0.0, 1.0), coord(0.0, 2.0), coord(0.0, 0.0)),
        )
        assertNotNull("collinear but structurally closed", collinear)
        assertEquals(
            0.0,
            SphericalArea.of(collinear!!),
            1.0,
        )
    }

    @Test
    fun aRingRejectsNonFiniteCoordinates() {
        val broken = listOf(
            coord(0.0, 0.0), coord(0.0, Double.NaN), coord(1.0, 1.0), coord(0.0, 0.0),
        )
        assertNull(PolygonRing.of(broken))
    }

    // ---- area ----------------------------------------------------------------

    @Test
    fun aOneDegreeSquareAtTheEquatorIsAboutTwelveThousandSquareKm() {
        // Known value: 1 degree of latitude is ~110.57 km, and at the equator a
        // degree of longitude is the same.
        val ring = ringOf(
            0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0, 1.0 to 0.0,
        )
        val area = SphericalArea.of(ring) / 1_000_000.0
        assertEquals(12_363.0, area, 60.0)
    }

    @Test
    fun areaScalesWithLatitude() {
        // THE guard against planar shoelace. The same longitude span covers half
        // the ground at 60 N as at the equator, so a planar computation would
        // report the equator area unchanged. Spherical excess must not.
        val equator = ringOf(0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0, 1.0 to 0.0)
        val north = ringOf(
            59.0 to 0.0, 59.0 to 1.0, 60.0 to 1.0, 60.0 to 0.0,
        )
        val equatorArea = SphericalArea.of(equator)
        val northArea = SphericalArea.of(north)

        assertTrue(
            "a degree of longitude is narrower at 60 N, so the area must shrink",
            northArea < equatorArea,
        )
        // cos(60) = 0.5, so roughly half the equatorial area.
        assertEquals(equatorArea * 0.5, northArea, equatorArea * 0.02)
    }

    @Test
    fun areaIsIndependentOfVertexCount() {
        // The same square described with 4 vertices and with 36 must agree. A
        // formula that silently depends on sampling density is not an area.
        val four = squareAt(44.9, -93.1, 2.0)
        val expected = SphericalArea.of(four)

        // Re-describe the identical square with vertices inserted along each edge.
        val latDelta = 1.0 / 111.32
        val lonDelta = lonDegreesFor(1.0, 44.9)
        val lat = 44.9
        val lon = -93.1
        val dense = ArrayList<Pair<Double, Double>>()
        val edges = listOf(
            Triple(lat - latDelta, lon - lonDelta, lat + latDelta to lon - lonDelta),
            Triple(lat + latDelta, lon - lonDelta, lat + latDelta to lon + lonDelta),
            Triple(lat + latDelta, lon + lonDelta, lat - latDelta to lon + lonDelta),
            Triple(lat - latDelta, lon + lonDelta, lat - latDelta to lon - lonDelta),
        )
        for ((startLat, startLon, end) in edges) {
            for (step in 0 until 8) {
                val fraction = step / 8.0
                dense.add(
                    (startLat + (end.first - startLat) * fraction) to
                        (startLon + (end.second - startLon) * fraction),
                )
            }
        }
        val many = ringOf(*dense.toTypedArray())

        assertEquals(expected, SphericalArea.of(many), expected * 0.001)
    }

    @Test
    fun windingDirectionChangesTheSignButNotTheMagnitude() {
        // South-west, then east, then north: counter-clockwise from above.
        val counterClockwise = ringOf(
            44.0 to -94.0, 44.0 to -93.0, 45.0 to -93.0, 45.0 to -94.0,
        )
        val reversedVertices = counterClockwise.vertices.reversed().toList()
        val clockwise = PolygonRing.of(reversedVertices + reversedVertices.first())!!

        assertTrue(
            "counter-clockwise winding must be positive (was ${SphericalArea.ofSigned(counterClockwise)})",
            SphericalArea.ofSigned(counterClockwise) > 0,
        )
        assertTrue(
            "clockwise winding must be negative (was ${SphericalArea.ofSigned(clockwise)})",
            SphericalArea.ofSigned(clockwise) < 0,
        )
        assertEquals(
            SphericalArea.of(counterClockwise),
            SphericalArea.of(clockwise),
            1.0,
        )
    }

    @Test
    fun perimeterSumsGreatCircleEdges() {
        // A square of side s has perimeter 4s.
        val side = 2.0
        val ring = squareAt(0.0, 0.0, side)
        val perimeterKm = SphericalArea.perimeterMeters(ring) / 1000.0
        assertEquals(4 * side, perimeterKm, side * 0.01)
    }

    // ---- centroid ------------------------------------------------------------

    @Test
    fun theCentroidOfASquareIsItsCentre() {
        val ring = squareAt(44.9, -93.1, 1.0)
        val centre = SphericalArea.centroid(ring)

        assertNotNull(centre)
        assertEquals(44.9, centre!!.latitude, 1e-6)
        assertEquals(-93.1, centre.longitude, 1e-6)
    }

    @Test
    fun theCentroidIsPulledTowardTheLargerLobe() {
        // An L-shape: a wide northern lobe and a narrow southern tail. The centroid
        // must sit north of the bounding midpoint, not at the arithmetic mean of
        // vertices, which would ignore how much ground each vertex encloses.
        val ring = ringOf(
            45.0 to -94.0, 45.0 to -92.0, 45.4 to -92.0, 45.4 to -94.0,
            44.6 to -94.0, 44.6 to -93.9, 44.0 to -93.9, 44.0 to -94.0,
        )
        val centre = SphericalArea.centroid(ring)

        assertNotNull(centre)
        assertTrue(
            "centroid must favour the wide lobe (was ${centre!!.latitude})",
            centre.latitude > 44.8,
        )
    }

    @Test
    fun aDegenerateRingHasNoCentroid() {
        val line = PolygonRing.of(
            listOf(coord(44.0, -94.0), coord(44.0, -93.0), coord(44.0, -92.0), coord(44.0, -94.0)),
        )!!
        // Collinear, but closed and three-distinct, so it constructs. It has no
        // interior, so it must not report a centroid.
        assertNull(SphericalArea.centroid(line))
    }

    // ---- point in polygon ----------------------------------------------------

    @Test
    fun aPointInsideIsContainedAndOneOutsideIsNot() {
        val ring = squareAt(44.9, -93.1, 5.0)

        assertTrue(PointInPolygon.contains(ring, coord(44.9, -93.1)))
        assertFalse(PointInPolygon.contains(ring, coord(50.0, -93.1)))
        assertFalse(PointInPolygon.contains(ring, coord(44.9, -80.0)))
    }

    @Test
    fun aPointOnTheBoundaryIsContained() {
        val side = 2.0
        val ring = squareAt(0.0, 0.0, side)
        // Exactly on the western edge's midpoint: the square spans +/- half a side
        // in longitude, so its western edge sits at -lonDegreesFor(1.0, 0.0).
        val westEdgeLon = -lonDegreesFor(side / 2.0, 0.0)
        assertTrue(
            "a point exactly on the western edge must count as contained",
            PointInPolygon.contains(ring, coord(0.0, westEdgeLon)),
        )
    }

    @Test
    fun vertexOnTheQueryLatitudeIsCountedOnce() {
        // THE half-open guard. The square's vertices sit exactly on latitudes 0 and
        // 1. A query at exactly 0.0 must not double-count the two vertices there,
        // which would flip the parity and report the centre as OUTSIDE.
        val ring = ringOf(
            0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0, 1.0 to 0.0,
        )
        assertTrue(
            "a query on the latitude of two vertices must still be inside",
            PointInPolygon.contains(ring, coord(0.0, 0.5)),
        )
    }

    @Test
    fun aConcaveNotchExcludesItsOwnInterior() {
        // An L-shape. The point sits in the notch, which is outside the polygon
        // even though it is inside the bounding box. A bounding-box test would get
        // this wrong.
        val ring = ringOf(
            45.0 to -94.0, 45.0 to -92.0, 44.7 to -92.0, 44.7 to -93.0,
            44.0 to -93.0, 44.0 to -94.0,
        )
        assertTrue(PointInPolygon.contains(ring, coord(44.9, -92.5)))
        assertFalse(
            "the notch is outside the polygon",
            PointInPolygon.contains(ring, coord(44.5, -92.5)),
        )
    }

    @Test
    fun aHoleIsExcludedFromItsPolygon() {
        val polygon = Polygon.of(
            exterior = listOf(
                coord(44.0, -94.0), coord(44.0, -92.0), coord(45.0, -92.0),
                coord(45.0, -94.0), coord(44.0, -94.0),
            ),
            holes = listOf(
                listOf(
                    coord(44.4, -93.5), coord(44.4, -92.5), coord(44.6, -92.5),
                    coord(44.6, -93.5), coord(44.4, -93.5),
                ),
            ),
        )!!

        assertTrue(polygon.contains(coord(44.2, -93.0)))
        assertFalse(
            "a point inside a hole is NOT inside the polygon",
            polygon.contains(coord(44.5, -93.0)),
        )
    }

    @Test
    fun polygonAreaSubtractsItsHoles() {
        val solid = Polygon.of(
            listOf(
                coord(44.0, -94.0), coord(44.0, -92.0), coord(45.0, -92.0),
                coord(45.0, -94.0), coord(44.0, -94.0),
            ),
        )!!
        val withHole = Polygon.of(
            exterior = solid.exterior.coordinates,
            holes = listOf(
                listOf(
                    coord(44.4, -93.5), coord(44.4, -92.5), coord(44.6, -92.5),
                    coord(44.6, -93.5), coord(44.4, -93.5),
                ),
            ),
        )!!

        assertTrue(
            "a hole must reduce the area",
            withHole.areaSquareMeters() < solid.areaSquareMeters(),
        )
        assertTrue(
            "the hole is a real fraction of the solid",
            solid.areaSquareMeters() - withHole.areaSquareMeters() > 0,
        )
    }

    // ---- proximity -----------------------------------------------------------

    @Test
    fun withinDistanceUsesTheNearestEdgeNotTheBoundingBox() {
        val side = 2.0
        val half = side / 2.0
        val ring = squareAt(44.9, -93.1, side)
        val centre = coord(44.9, -93.1)
        assertTrue("the centre is inside, so any distance matches", PointInPolygon.withinDistanceOf(ring, centre, 1.0))

        // 3 km due east of the eastern edge. The nearest edge is therefore ~3 km
        // away, even though the point is well inside the bounding box.
        val outside = coord(44.9, -93.1 + lonDegreesFor(half + 3.0, 44.9))
        assertFalse(
            "3 km outside the edge must not match a 2 km radius",
            PointInPolygon.withinDistanceOf(ring, outside, 2_000.0),
        )
        assertTrue(
            "3 km outside the edge must match a 4 km radius",
            PointInPolygon.withinDistanceOf(ring, outside, 4_000.0),
        )
    }

    @Test
    fun distanceToEdgeIsZeroWhenThePointIsOnIt() {
        val from = coord(0.0, 0.0)
        val to = coord(0.0, 1.0)
        val onEdge = coord(0.0, 0.5)
        val distance = PointInPolygon.distanceToEdgeKm(onEdge, from, to)
        assertEquals(0.0, distance, 0.5)
    }

    // ---- overlap -------------------------------------------------------------

    @Test
    fun crossingShapesIntersectEvenWithNoContainedVertex() {
        // Two thin bars forming a plus sign. Neither contains a vertex of the
        // other, so a vertex-only overlap test reports no overlap and is wrong.
        val horizontal = Polygon.of(
            listOf(
                coord(44.0, -94.0), coord(44.0, -92.0), coord(44.2, -92.0),
                coord(44.2, -94.0), coord(44.0, -94.0),
            ),
        )!!
        val vertical = Polygon.of(
            listOf(
                coord(43.9, -93.1), coord(43.9, -92.9), coord(44.3, -92.9),
                coord(44.3, -93.1), coord(43.9, -93.1),
            ),
        )!!

        assertTrue("crossing bars overlap", horizontal.intersects(vertical))
    }

    @Test
    fun disjointShapesDoNotIntersect() {
        val west = Polygon.of(
            listOf(
                coord(44.0, -94.0), coord(44.0, -93.0), coord(45.0, -93.0),
                coord(45.0, -94.0), coord(44.0, -94.0),
            ),
        )!!
        val east = Polygon.of(
            listOf(
                coord(44.0, -91.0), coord(44.0, -90.0), coord(45.0, -90.0),
                coord(45.0, -91.0), coord(44.0, -91.0),
            ),
        )!!

        assertFalse(west.intersects(east))
    }

    @Test
    fun aContainedPolygonIntersectsItsContainer() {
        val outer = squareAt(44.9, -93.1, 10.0)
        val outerPolygon = Polygon.of(outer)
        val inner = squareAt(44.9, -93.1, 1.0)
        val innerPolygon = Polygon.of(inner)

        assertTrue(outerPolygon.intersects(innerPolygon))
    }
}