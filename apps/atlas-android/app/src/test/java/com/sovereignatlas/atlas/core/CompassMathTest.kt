// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import org.junit.Assert.assertEquals
import org.junit.Test

final class CompassMathTest {
    @Test
    fun normalizeWrapsNegativeAndFullTurns() {
        assertEquals(315, CompassMath.normalize(-45))
        assertEquals(0, CompassMath.normalize(360))
        assertEquals(0, CompassMath.normalize(0))
        assertEquals(0, CompassMath.normalize(720))
        assertEquals(315, CompassMath.normalize(-405))
    }

    @Test
    fun normalizeLeavesInRangeValuesAlone() {
        assertEquals(90, CompassMath.normalize(90))
        assertEquals(359, CompassMath.normalize(359))
    }

    @Test
    fun labelsCoverTheCardinalsAndIntercardinals() {
        assertEquals("N", CompassMath.getLabel(0))
        assertEquals("NE", CompassMath.getLabel(45))
        assertEquals("E", CompassMath.getLabel(90))
        assertEquals("SE", CompassMath.getLabel(135))
        assertEquals("S", CompassMath.getLabel(180))
        assertEquals("SW", CompassMath.getLabel(225))
        assertEquals("W", CompassMath.getLabel(270))
        assertEquals("NW", CompassMath.getLabel(315))
    }

    @Test
    fun labelsWrapForOutOfRangeInput() {
        assertEquals("N", CompassMath.getLabel(720))
        assertEquals("N", CompassMath.getLabel(-360))
        assertEquals("E", CompassMath.getLabel(450))
        assertEquals("NW", CompassMath.getLabel(-45))
    }

    @Test
    fun nonCardinalDegreesRenderNumerically() {
        assertEquals("30", CompassMath.getLabel(30))
        assertEquals("30", CompassMath.getLabel(390))
        assertEquals("12", CompassMath.getLabel(12))
    }
}
