// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SovereignGridTest {

    @Test
    fun geoToCell_cellToLatLng_roundTripsWithinGridPitch() {
        val cases = listOf(
            39.8283 to -98.5795,
            0.0 to 0.0,
            -33.8688 to 151.2093,
            89.9 to 179.9,
            -89.9 to -179.9,
        )
        for ((lat, lng) in cases) {
            val cell = SovereignGrid.geoToCell(lat, lng, SovereignGrid.RESOLUTION_STANDARD)
            val (decodedLat, decodedLng) = SovereignGrid.cellToLatLng(cell)
            val (pitchLat, pitchLng) = SovereignGrid.cellPitchDegrees(cell)
            assertTrue(
                "lat within one cell pitch for ($lat,$lng)",
                abs(decodedLat - lat) <= pitchLat,
            )
            assertTrue(
                "lng within one cell pitch for ($lat,$lng)",
                abs(decodedLng - lng) <= pitchLng,
            )
        }
    }

    @Test
    fun resolutionOf_recoversTheEncodedResolution() {
        val standard = SovereignGrid.geoToCell(45.0, -93.0, SovereignGrid.RESOLUTION_STANDARD)
        val emergency = SovereignGrid.geoToCell(45.0, -93.0, SovereignGrid.RESOLUTION_EMERGENCY)
        assertEquals(SovereignGrid.RESOLUTION_STANDARD, SovereignGrid.resolutionOf(standard))
        assertEquals(SovereignGrid.RESOLUTION_EMERGENCY, SovereignGrid.resolutionOf(emergency))
    }

    @Test
    fun higherResolution_yieldsATighterCellPitch() {
        val coarse = SovereignGrid.geoToCell(45.0, -93.0, SovereignGrid.RESOLUTION_STANDARD)
        val fine = SovereignGrid.geoToCell(45.0, -93.0, SovereignGrid.RESOLUTION_EMERGENCY)
        val (coarseLat, _) = SovereignGrid.cellPitchDegrees(coarse)
        val (fineLat, _) = SovereignGrid.cellPitchDegrees(fine)
        assertTrue("H10 pitch is tighter than H6 pitch", fineLat < coarseLat)
    }

    @Test
    fun cellBoundary_isASevenPointClosedRing() {
        val cell = SovereignGrid.geoToCell(45.0, -93.0, SovereignGrid.RESOLUTION_STANDARD)
        val ring = SovereignGrid.cellBoundary(cell)
        assertEquals("7 points, first == last (GeoJSON exterior-ring convention)", 7, ring.size)
        assertEquals(ring.first(), ring.last())
    }

    @Test
    fun cellBoundary_respectsTheDisplayFloor() {
        val cell = SovereignGrid.geoToCell(45.0, -93.0, SovereignGrid.RESOLUTION_EMERGENCY)
        val (centerLat, _) = SovereignGrid.cellToLatLng(cell)
        val tinyFloor = SovereignGrid.cellBoundary(cell, minRadiusDeg = 0.0)
        val flooredRing = SovereignGrid.cellBoundary(cell, minRadiusDeg = 1.0)
        val tinySpread = tinyFloor.maxOf { abs(it.first - centerLat) }
        val flooredSpread = flooredRing.maxOf { abs(it.first - centerLat) }
        assertTrue("a larger minRadiusDeg widens the rendered ring", flooredSpread > tinySpread)
    }

    @Test
    fun schemeId_isStableAndDistinctFromRealH3() {
        assertEquals("sovereign-grid-v1", SovereignGrid.SCHEME_ID)
        assertNotEquals("uber-h3-v4", SovereignGrid.SCHEME_ID)
    }
}
