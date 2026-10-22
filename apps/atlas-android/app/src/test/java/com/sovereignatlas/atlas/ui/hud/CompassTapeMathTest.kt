// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import com.sovereignatlas.atlas.core.CompassMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassTapeMathTest {

    private fun computeRelativeDiff(targetBearing: Double, currentBearing: Double): Double {
        return (targetBearing - currentBearing + 540.0) % 360.0 - 180.0
    }

    @Test
    fun targetAheadIsCenteredOnTape() {
        val diff = computeRelativeDiff(45.0, 45.0)
        assertEquals(0.0, diff, 0.001)
        assertTrue(diff in -45.0..45.0)
    }

    @Test
    fun targetWithinFovIsPositionedOnTape() {
        val diffRight = computeRelativeDiff(75.0, 45.0)
        assertEquals(30.0, diffRight, 0.001)
        assertTrue(diffRight in -45.0..45.0)

        val diffLeft = computeRelativeDiff(15.0, 45.0)
        assertEquals(-30.0, diffLeft, 0.001)
        assertTrue(diffLeft in -45.0..45.0)
    }

    @Test
    fun targetOutsideFovTriggersPinnedIndicators() {
        val diffRight = computeRelativeDiff(120.0, 45.0)
        assertEquals(75.0, diffRight, 0.001)
        assertFalse(diffRight in -45.0..45.0)
        assertTrue(diffRight > 45.0)

        val diffLeft = computeRelativeDiff(330.0, 45.0)
        assertEquals(-75.0, diffLeft, 0.001)
        assertFalse(diffLeft in -45.0..45.0)
        assertTrue(diffLeft < -45.0)
    }

    @Test
    fun targetAcrossNorthBoundaryCalculatesShortestTurn() {
        val diffRight = computeRelativeDiff(10.0, 350.0)
        assertEquals(20.0, diffRight, 0.001)
        assertTrue(diffRight in -45.0..45.0)

        val diffLeft = computeRelativeDiff(350.0, 10.0)
        assertEquals(-20.0, diffLeft, 0.001)
        assertTrue(diffLeft in -45.0..45.0)
    }

    @Test
    fun cardinalLabelsAreFormattedAccurately() {
        assertEquals("N", CompassMath.getLabel(0))
        assertEquals("NE", CompassMath.getLabel(45))
        assertEquals("E", CompassMath.getLabel(90))
        assertEquals("SE", CompassMath.getLabel(135))
        assertEquals("S", CompassMath.getLabel(180))
        assertEquals("SW", CompassMath.getLabel(225))
        assertEquals("W", CompassMath.getLabel(270))
        assertEquals("NW", CompassMath.getLabel(315))
        assertEquals("N", CompassMath.getLabel(360))
    }
}
