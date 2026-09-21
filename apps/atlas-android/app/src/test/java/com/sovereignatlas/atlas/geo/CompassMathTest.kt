// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassMathTest {

    @Test
    fun normalizeDegreesWrapsIntoZeroTo360() {
        assertEquals(0f, normalizeDegrees(0f), 1e-4f)
        assertEquals(350f, normalizeDegrees(-10f), 1e-4f)
        assertEquals(10f, normalizeDegrees(370f), 1e-4f)
        assertEquals(0f, normalizeDegrees(360f), 1e-4f)
        assertEquals(180f, normalizeDegrees(-180f), 1e-4f)
    }

    @Test
    fun circularDeltaIsShortestPathAcrossTheWrap() {
        assertEquals(2f, circularDelta(359f, 1f), 1e-4f)
        assertEquals(2f, circularDelta(1f, 359f), 1e-4f)
        assertEquals(180f, circularDelta(0f, 180f), 1e-4f)
        assertEquals(0f, circularDelta(45f, 45f), 1e-4f)
    }

    @Test
    fun applyDeclinationAddsAndNormalizes() {
        assertEquals(50f, applyDeclination(40f, 10f), 1e-4f)
        assertEquals(5f, applyDeclination(355f, 10f), 1e-4f)
        assertEquals(350f, applyDeclination(10f, -20f), 1e-4f)
    }

    @Test
    fun smootherConvergesTowardASteadyHeading() {
        val smoother = CircularSmoother(alpha = 0.5f)
        var last = smoother.smooth(90f)
        repeat(20) { last = smoother.smooth(90f) }
        assertEquals(90f, last, 0.5f)
    }

    @Test
    fun smootherCrossesThe360SeamTheShortWay() {
        val smoother = CircularSmoother(alpha = 0.5f)
        smoother.smooth(350f)
        val nearSeam = smoother.smooth(355f)
        val pastSeam = smoother.smooth(5f)
        assertTrue("expected $nearSeam close to 355", circularDelta(nearSeam, 355f) < 30f)
        assertTrue("expected $pastSeam close to 5 (short way, not via 180)", circularDelta(pastSeam, 5f) < 30f)
    }

    @Test
    fun smootherFirstSampleReturnsThatSampleUnchanged() {
        val smoother = CircularSmoother(alpha = 0.15f)
        assertEquals(123f, smoother.smooth(123f), 1e-3f)
    }

    @Test
    fun smootherResetDropsHistory() {
        val smoother = CircularSmoother(alpha = 0.5f)
        repeat(10) { smoother.smooth(90f) }
        smoother.reset()
        assertEquals(10f, smoother.smooth(10f), 1e-3f)
    }

    @Test
    fun throttleAlwaysEmitsTheFirstCandidate() {
        val throttle = EmitThrottle(minDeltaDegrees = 0.5f, minIntervalMs = 70L)
        assertTrue(throttle.shouldEmit(10f, nowMs = 0L))
    }

    @Test
    fun throttleSuppressesWithinTheMinInterval() {
        val throttle = EmitThrottle(minDeltaDegrees = 0.5f, minIntervalMs = 70L)
        throttle.markEmitted(10f, nowMs = 1_000L)
        assertFalse(throttle.shouldEmit(20f, nowMs = 1_030L))
    }

    @Test
    fun throttleSuppressesBelowTheMinDelta() {
        val throttle = EmitThrottle(minDeltaDegrees = 0.5f, minIntervalMs = 70L)
        throttle.markEmitted(10f, nowMs = 1_000L)
        assertFalse(throttle.shouldEmit(10.2f, nowMs = 1_200L))
    }

    @Test
    fun throttleEmitsOnceBothConditionsAreMet() {
        val throttle = EmitThrottle(minDeltaDegrees = 0.5f, minIntervalMs = 70L)
        throttle.markEmitted(10f, nowMs = 1_000L)
        assertTrue(throttle.shouldEmit(11f, nowMs = 1_100L))
    }

    @Test
    fun throttleDeltaCheckIsCircular() {
        val throttle = EmitThrottle(minDeltaDegrees = 1f, minIntervalMs = 0L)
        throttle.markEmitted(359f, nowMs = 0L)
        assertTrue(throttle.shouldEmit(1f, nowMs = 100L))
    }
}
