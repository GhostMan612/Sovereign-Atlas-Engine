// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MgrsTest {

    @Test
    fun eiffelTower_encodesToKnownGridReference() {
        val ref = Mgrs.gridRef(48.8583, 2.2945)
        assertEquals(31, ref.zone)
        assertEquals('U', ref.band)
        assertEquals("DQ", ref.square)
        assertTrue("easting within a metre of the reference", ref.easting in 448200..448300)
        assertTrue("northing within a metre of the reference", ref.northing in 5411900..5412000)

        val formatted = Mgrs.format(48.8583, 2.2945, digits = 5)
        assertTrue("full precision keeps zone+band+square", formatted.startsWith("31U DQ "))
    }

    @Test
    fun nullIsland_sitsInZone31BandN() {
        val ref = Mgrs.gridRef(0.0, 0.0)
        assertEquals(31, ref.zone)
        assertEquals('N', ref.band)
    }

    @Test
    fun sydney_isSouthernHemisphereZone56BandH() {
        val ref = Mgrs.gridRef(-33.8688, 151.2093)
        assertEquals(56, ref.zone)
        assertEquals('H', ref.band)
        assertTrue("southern northing stays positive (10M false)", ref.northing in 6200000..6300000)
    }

    @Test
    fun precisionDigits_truncateEastingAndNorthing() {
        val threeDigit = Mgrs.format(48.8583, 2.2945, digits = 3)
        val parts = threeDigit.split(" ")
        assertEquals("zone/band, square, easting, northing", 4, parts.size)
        assertEquals(3, parts[2].length)
        assertEquals(3, parts[3].length)
    }

    @Test
    fun polarLatitudes_returnOffGrid() {
        assertEquals(Mgrs.OFF_GRID, Mgrs.format(85.0, 10.0))
        assertEquals(Mgrs.OFF_GRID, Mgrs.format(-81.0, 10.0))
        assertTrue("just inside the band still encodes", Mgrs.format(83.9, 10.0) != Mgrs.OFF_GRID)
    }
}
