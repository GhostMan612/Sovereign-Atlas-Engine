// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun trackLengthMeters(points: List<AtlasCoordinate>): Double {
    var totalKm = 0.0
    for (index in 1 until points.size) {
        totalKm += AtlasGeoMath.haversineKm(
            points[index - 1],
            points[index],
        )
    }
    return totalKm * 1000.0
}

fun formatTrackDuration(millis: Long): String {
    val totalSeconds = millis / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    if (hours > 0) return "$hours:$mm:$ss"
    return "$mm:$ss"
}

fun formatTrackStart(createdAt: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(createdAt))
}

fun formatTrackDistance(lengthMeters: Double): String {
    return AtlasGeoMath.formatDistance(lengthMeters / 1000.0)
}
