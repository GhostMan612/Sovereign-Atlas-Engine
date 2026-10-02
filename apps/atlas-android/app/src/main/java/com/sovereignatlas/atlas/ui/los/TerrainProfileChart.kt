// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.los

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.geo.los.LoSStatus
import com.sovereignatlas.atlas.geo.los.TerrainProfile
import kotlin.math.max
import kotlin.math.min

@Composable
fun TerrainProfileChart(profile: TerrainProfile, modifier: Modifier = Modifier) {
    if (profile.points.isEmpty()) return

    val visibleColor = Color.Green
    val blockedColor = Color.Red
    val undeterminedColor = Color(0xFFFFA500)

    val chartData = remember(profile) {
        val terrainMax = profile.points.maxOf { it.terrainElevationMeters }
        val terrainMin = profile.points.minOf { it.terrainElevationMeters }
        val maxElev = max(terrainMax, max(profile.observerElevationMeters, profile.targetElevationMeters))
        val minElev = min(terrainMin, min(profile.observerElevationMeters, profile.targetElevationMeters))
        val elevRange = (maxElev - minElev).coerceAtLeast(1.0)
        val maxDist = profile.points.last().distanceFromStartMeters
        Triple(minElev, elevRange, maxDist)
    }
    val (minElev, elevRange, maxDist) = chartData

    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        val width = size.width
        val height = size.height

        var lastPoint: Offset? = null
        var currentIsVisible = true

        profile.points.forEach { point ->
            val x = (point.distanceFromStartMeters / maxDist) * width
            val y = height - (((point.terrainElevationMeters - minElev) / elevRange) * height * 0.8) - (height * 0.1)
            val currentPoint = Offset(x.toFloat(), y.toFloat())

            if (lastPoint != null) {
                drawLine(
                    color = if (currentIsVisible) visibleColor else blockedColor,
                    start = lastPoint!!,
                    end = currentPoint,
                    strokeWidth = 4.dp.toPx()
                )
            }
            lastPoint = currentPoint
            currentIsVisible = point.isVisible
        }

        val startY = height - (((profile.observerElevationMeters - minElev) / elevRange) * height * 0.8) - (height * 0.1)
        val endY = height - (((profile.targetElevationMeters - minElev) / elevRange) * height * 0.8) - (height * 0.1)

        drawLine(
            // The chord is drawn in the same colour as the verdict, so the chart and the map
    // ray agree. An undetermined verdict is drawn amber rather than blocked-red.
    color = when (profile.lineOfSight.status) {
        LoSStatus.Clear -> visibleColor.copy(alpha = 0.5f)
        LoSStatus.BlockedTerrain -> blockedColor.copy(alpha = 0.5f)
        else -> undeterminedColor.copy(alpha = 0.5f)
    },
            start = Offset(0f, startY.toFloat()),
            end = Offset(width, endY.toFloat()),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    }
}
