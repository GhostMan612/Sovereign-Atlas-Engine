// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import org.junit.Assert.assertEquals
import org.junit.Test

final class TacticalMathTest {
    @Test
    fun cardinalBearingsMapToNatoMils() {
        assertEquals(0, TacticalMath.degreesToMils(0f))
        assertEquals(1600, TacticalMath.degreesToMils(90f))
        assertEquals(3200, TacticalMath.degreesToMils(180f))
        assertEquals(4800, TacticalMath.degreesToMils(270f))
    }

    @Test
    fun fullTurnNormalizesToZero() {
        assertEquals(0, TacticalMath.degreesToMils(360f))
        assertEquals(0, TacticalMath.degreesToMils(-360f))
    }

    @Test
    fun justUnderNorthRoundsTo6398() {
        assertEquals(6398, TacticalMath.degreesToMils(359.9f))
    }

    @Test
    fun negativeDegreesWrapPositively() {
        assertEquals(1600, TacticalMath.degreesToMils(-270f))
        assertEquals(6400 - 18, TacticalMath.degreesToMils(-1f))
    }

    @Test
    fun milsStayWithinNatoCircle() {
        val samples = floatArrayOf(0f, 0.4f, 17.8f, 89.9f, 123.4f, 180f, 271.1f, 359.99f, 720f)
        for (sample in samples) {
            val mils = TacticalMath.degreesToMils(sample)
            assertEquals("mils out of range for $sample", true, mils in 0..6399)
        }
    }

    @Test
    fun formatBearingPadsToThreeDigits() {
        assertEquals("000", TacticalMath.formatBearing(0f))
        assertEquals("045", TacticalMath.formatBearing(45f))
        assertEquals("090", TacticalMath.formatBearing(90f))
        assertEquals("359", TacticalMath.formatBearing(359f))
    }

    @Test
    fun formatBearingRoundsAndWraps() {
        assertEquals("090", TacticalMath.formatBearing(89.6f))
        assertEquals("000", TacticalMath.formatBearing(359.9f))
        assertEquals("045", TacticalMath.formatBearing(-315f))
    }
}
