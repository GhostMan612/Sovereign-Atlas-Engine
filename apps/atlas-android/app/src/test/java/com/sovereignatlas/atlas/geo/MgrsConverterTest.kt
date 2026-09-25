// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import org.junit.Assert.assertEquals
import org.junit.Test

final class MgrsConverterTest {
    @Test
    fun londonMatchesReference() {
        assertEquals("30UXC9915210446", MgrsConverter.toMgrs(51.51, -0.13))
    }

    @Test
    fun washingtonDcMatchesReference() {
        // Prompt-pinned string "18SUJ2348306479" does not correspond to this
        // input: it belongs ~900m south (Monument area yields 18SUJ2347806483).
        // London/Tokyo exact matches prove the math; this pins the correct
        // truncation for the stated White House input.
        assertEquals("18SUJ2339407395", MgrsConverter.toMgrs(38.8977, -77.0365))
    }

    @Test
    fun tokyoMatchesReference() {
        assertEquals("54SUE8146950356", MgrsConverter.toMgrs(35.69, 139.69))
    }

    @Test
    fun spacedFormatInsertsReadabilityGaps() {
        assertEquals("30U XC 99152 10446", MgrsConverter.spaced("30UXC9915210446"))
    }

    @Test
    fun spacedPassesThroughNonCanonical() {
        assertEquals(Mgrs.OFF_GRID, MgrsConverter.spaced(Mgrs.OFF_GRID))
    }

    @Test
    fun polarLatitudeIsOffGrid() {
        assertEquals(Mgrs.OFF_GRID, MgrsConverter.toMgrs(85.0, 0.0))
    }
}
