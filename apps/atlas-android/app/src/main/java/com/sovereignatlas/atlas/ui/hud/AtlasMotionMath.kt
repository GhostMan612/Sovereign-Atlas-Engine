// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

object AtlasMotionMath {
    fun shortestAngleDeltaDeg(fromDeg: Float, toDeg: Float): Float {
        var delta = (toDeg - fromDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta <= -180f) delta += 360f
        return delta
    }

    fun unwrapBearingTarget(currentUnwrappedDeg: Float, targetDeg: Float): Float {
        if (!currentUnwrappedDeg.isFinite() || !targetDeg.isFinite()) return 0f
        val normalizedCurrent = ((currentUnwrappedDeg % 360f) + 360f) % 360f
        val normalizedTarget = ((targetDeg % 360f) + 360f) % 360f
        return currentUnwrappedDeg + shortestAngleDeltaDeg(normalizedCurrent, normalizedTarget)
    }

    fun packDownloadFraction(receivedTiles: Int, totalTiles: Int): Float {
        if (totalTiles <= 0) return 0f
        return (receivedTiles.toFloat() / totalTiles.toFloat()).coerceIn(0f, 1f)
    }

    fun bootTelemetryStage(elapsedStep: Int): String {
        val stages = listOf(
            "INITIALIZING GEODETIC KERNEL",
            "CALIBRATING SPATIAL COMPOSITOR",
            "MOUNTING OFFLINE PACK ARCHIVES",
            "SYNCHRONIZING TACTICAL HUD",
        )
        val idx = ((elapsedStep % stages.size) + stages.size) % stages.size
        return stages[idx]
    }
}
