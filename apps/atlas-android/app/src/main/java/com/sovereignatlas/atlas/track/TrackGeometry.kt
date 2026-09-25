// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.geo.AtlasCoordinate

fun trackGeometryJson(points: List<AtlasCoordinate>): String {
    val coords = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"LineString","coordinates":[$coords]}"""
}

private val NUMBER = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")

fun parseTrackGeometry(json: String): List<AtlasCoordinate>? {
    if (!json.contains("\"LineString\"")) return null
    val keyAt = json.indexOf("\"coordinates\"")
    if (keyAt < 0) return null
    val open = json.indexOf('[', keyAt)
    if (open < 0) return null
    var depth = 0
    var pairStart = -1
    val pairs = ArrayList<String>()
    var index = open
    while (index < json.length) {
        when (json[index]) {
            '[' -> {
                depth += 1
                if (depth == 2) pairStart = index + 1
            }
            ']' -> {
                if (depth == 2 && pairStart >= 0) {
                    pairs.add(json.substring(pairStart, index))
                    pairStart = -1
                }
                depth -= 1
                if (depth <= 0) break
            }
        }
        index += 1
    }
    if (depth != 0) return null
    val points = ArrayList<AtlasCoordinate>(pairs.size)
    for (pair in pairs) {
        val numbers = NUMBER.findAll(pair).map { it.value.toDouble() }.toList()
        if (numbers.size != 2) return null
        points.add(AtlasCoordinate(latitude = numbers[1], longitude = numbers[0]))
    }
    return points
}

fun trackPointCount(geometry: String): Int {
    return try {
        parseTrackGeometry(geometry)?.size ?: 0
    } catch (error: Throwable) {
        0
    }
}
