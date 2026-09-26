// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.track.ProfilePoint
import com.sovereignatlas.atlas.track.TrackProfile
import com.sovereignatlas.atlas.track.TrackScrubState

fun nearestPoint(pixelX: Float, canvasWidth: Float, profile: TrackProfile): ProfilePoint? {
    if (profile.points.isEmpty() || canvasWidth <= 0f) return null
    val total = profile.totalDistance
    if (total <= 0.0) return profile.points.first()
    val target = (pixelX / canvasWidth).coerceIn(0f, 1f) * total
    var best = profile.points.first()
    var bestGap = Double.MAX_VALUE
    for (point in profile.points) {
        val gap = kotlin.math.abs(point.distanceMeters - target)
        if (gap < bestGap) {
            bestGap = gap
            best = point
        }
    }
    return best
}

@Composable
fun TrackProfileChart(profile: TrackProfile, scrubState: TrackScrubState) {
    val range = profile.maxElevation - profile.minElevation
    val padding = if (range < 10.0) 5.0 else range * 0.10
    val yMin = profile.minElevation - padding
    val yMax = profile.maxElevation + padding
    Column {
        Text(
            text = "↑ ${profile.totalGain.toInt()} m  ↓ ${profile.totalLoss.toInt()} m",
            fontSize = 12.sp,
        )
        Canvas(
            modifier = Modifier.fillMaxWidth().height(180.dp)
                .pointerInput(profile) {
                    detectTapGestures { offset ->
                        scrubState.setActivePoint(
                            nearestPoint(offset.x, size.width.toFloat(), profile),
                        )
                    }
                }
                .pointerInput(profile) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            scrubState.setActivePoint(
                                nearestPoint(offset.x, size.width.toFloat(), profile),
                            )
                        },
                        onDrag = { change, _ ->
                            scrubState.setActivePoint(
                                nearestPoint(change.position.x, size.width.toFloat(), profile),
                            )
                        },
                        onDragEnd = { scrubState.setActivePoint(null) },
                        onDragCancel = { scrubState.setActivePoint(null) },
                    )
                },
        ) {
            if (profile.points.isEmpty() || profile.totalDistance <= 0.0) return@Canvas
            val span = (yMax - yMin).takeIf { it > 0.0 } ?: 1.0
            fun xOf(distance: Double): Float {
                return (distance / profile.totalDistance).toFloat() * size.width
            }
            fun yOf(elevation: Double): Float {
                return size.height - ((elevation - yMin) / span).toFloat() * size.height
            }
            val line = Path()
            profile.points.forEachIndexed { index, point ->
                val at = Offset(xOf(point.distanceMeters), yOf(point.elevationMeters))
                if (index == 0) line.moveTo(at.x, at.y) else line.lineTo(at.x, at.y)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Color(0xFF39FF14).copy(alpha = 0.25f))
            drawPath(line, Color(0xFF39FF14))
        }
    }
}
