// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val DEG_TO_RAD = (PI / 180.0).toFloat()
private const val RAD_TO_DEG = (180.0 / PI).toFloat()

fun normalizeDegrees(degrees: Float): Float {
    val wrapped = degrees % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

fun circularDelta(a: Float, b: Float): Float {
    val diff = abs(normalizeDegrees(a) - normalizeDegrees(b)) % 360f
    return if (diff > 180f) 360f - diff else diff
}

fun applyDeclination(magneticDegrees: Float, declinationDegrees: Float): Float =
    normalizeDegrees(magneticDegrees + declinationDegrees)

class CircularSmoother(private val alpha: Float = 0.15f) {
    private var sinAcc = 0f
    private var cosAcc = 1f
    private var primed = false

    fun reset() {
        primed = false
    }

    fun smooth(rawDegrees: Float): Float {
        val rad = normalizeDegrees(rawDegrees) * DEG_TO_RAD
        val s = sin(rad)
        val c = cos(rad)
        if (!primed) {
            sinAcc = s
            cosAcc = c
            primed = true
        } else {
            sinAcc += alpha * (s - sinAcc)
            cosAcc += alpha * (c - cosAcc)
        }
        return normalizeDegrees(atan2(sinAcc, cosAcc) * RAD_TO_DEG)
    }
}

class EmitThrottle(
    private val minDeltaDegrees: Float = 0.5f,
    private val minIntervalMs: Long = 70L,
) {
    private var lastEmitted: Float? = null
    private var lastEmitTimeMs: Long = Long.MIN_VALUE

    fun shouldEmit(candidate: Float, nowMs: Long): Boolean {
        val last = lastEmitted ?: return true
        if (nowMs - lastEmitTimeMs < minIntervalMs) return false
        return circularDelta(last, candidate) >= minDeltaDegrees
    }

    fun markEmitted(value: Float, nowMs: Long) {
        lastEmitted = value
        lastEmitTimeMs = nowMs
    }
}
