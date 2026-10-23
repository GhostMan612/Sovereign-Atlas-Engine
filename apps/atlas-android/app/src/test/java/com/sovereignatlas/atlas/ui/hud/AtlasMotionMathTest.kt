// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasMotionMathTest {
    @Test
    fun shortestAngleDeltaCrossesNorthClockwise() {
        assertEquals(2f, AtlasMotionMath.shortestAngleDeltaDeg(359f, 1f), 0.001f)
    }

    @Test
    fun shortestAngleDeltaCrossesNorthCounterClockwise() {
        assertEquals(-2f, AtlasMotionMath.shortestAngleDeltaDeg(1f, 359f), 0.001f)
    }

    @Test
    fun unwrapBearingTargetAccumulatesContinuouslyAcrossMultipleTurns() {
        var unwrapped = 350f
        unwrapped = AtlasMotionMath.unwrapBearingTarget(unwrapped, 10f)
        assertEquals(370f, unwrapped, 0.001f)
        unwrapped = AtlasMotionMath.unwrapBearingTarget(unwrapped, 180f)
        assertEquals(540f, unwrapped, 0.001f)
        unwrapped = AtlasMotionMath.unwrapBearingTarget(unwrapped, 355f)
        assertEquals(715f, unwrapped, 0.001f)
        unwrapped = AtlasMotionMath.unwrapBearingTarget(unwrapped, 5f)
        assertEquals(725f, unwrapped, 0.001f)
        unwrapped = AtlasMotionMath.unwrapBearingTarget(unwrapped, 355f)
        assertEquals(715f, unwrapped, 0.001f)
    }

    @Test
    fun unwrapBearingTargetGuardsNonFiniteInputs() {
        assertEquals(0f, AtlasMotionMath.unwrapBearingTarget(Float.NaN, 90f), 0.001f)
        assertEquals(0f, AtlasMotionMath.unwrapBearingTarget(45f, Float.POSITIVE_INFINITY), 0.001f)
    }

    @Test
    fun packDownloadFractionClampsAndHandlesZeroTotal() {
        assertEquals(0f, AtlasMotionMath.packDownloadFraction(10, 0), 0.001f)
        assertEquals(0f, AtlasMotionMath.packDownloadFraction(-5, 100), 0.001f)
        assertEquals(0.25f, AtlasMotionMath.packDownloadFraction(25, 100), 0.001f)
        assertEquals(1f, AtlasMotionMath.packDownloadFraction(150, 100), 0.001f)
    }

    @Test
    fun bootTelemetryStageCyclesDeterministically() {
        val s0 = AtlasMotionMath.bootTelemetryStage(0)
        val s1 = AtlasMotionMath.bootTelemetryStage(1)
        val s4 = AtlasMotionMath.bootTelemetryStage(4)
        val neg = AtlasMotionMath.bootTelemetryStage(-1)
        assertTrue(s0.isNotBlank())
        assertTrue(s0 != s1)
        assertEquals(s0, s4)
        assertEquals(AtlasMotionMath.bootTelemetryStage(3), neg)
    }
}
