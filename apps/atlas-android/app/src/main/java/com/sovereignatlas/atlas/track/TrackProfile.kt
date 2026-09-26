// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.DemEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProfilePoint(
    val distanceMeters: Double,
    val elevationMeters: Double,
    val latitude: Double,
    val longitude: Double,
)

data class TrackProfile(
    val points: List<ProfilePoint>,
    val totalDistance: Double,
    val totalGain: Double,
    val totalLoss: Double,
    val minElevation: Double,
    val maxElevation: Double,
)

object TrackProfileGenerator {
    suspend fun generate(track: Track, demEngine: DemEngine?): TrackProfile? =
        withContext(Dispatchers.IO) {
            val raw = parsePositions3d(track.geometry) ?: return@withContext null
            val valid = ArrayList<ProfilePoint>()
            var distance = 0.0
            var previous: Pair<Double, Double>? = null
            for ((longitude, latitude, altitude) in raw) {
                val elevation = altitude ?: demEngine?.getElevation(latitude, longitude)
                    ?: continue
                val prior = previous
                if (prior != null) {
                    distance += AtlasGeoMath.haversineKm(
                        AtlasCoordinate(
                            latitude = prior.second,
                            longitude = prior.first,
                        ),
                        AtlasCoordinate(
                            latitude = latitude,
                            longitude = longitude,
                        ),
                    ) * 1000.0
                }
                previous = longitude to latitude
                valid.add(
                    ProfilePoint(
                        distanceMeters = distance,
                        elevationMeters = elevation,
                        latitude = latitude,
                        longitude = longitude,
                    ),
                )
            }
            if (valid.isEmpty()) return@withContext null
            var ref = valid.first().elevationMeters
            var gain = 0.0
            var loss = 0.0
            for (point in valid.drop(1)) {
                val delta = point.elevationMeters - ref
                if (delta >= 3.0) {
                    gain += delta
                    ref = point.elevationMeters
                } else if (delta <= -3.0) {
                    loss += -delta
                    ref = point.elevationMeters
                }
            }
            TrackProfile(
                points = valid,
                totalDistance = valid.last().distanceMeters,
                totalGain = gain,
                totalLoss = loss,
                minElevation = valid.minOf { it.elevationMeters },
                maxElevation = valid.maxOf { it.elevationMeters },
            )
        }

    private val numberPattern = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?""")

    fun parsePositions3d(json: String): List<Triple<Double, Double, Double?>>? {
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
        if (depth != 0 || pairs.isEmpty()) return null
        val positions = ArrayList<Triple<Double, Double, Double?>>(pairs.size)
        for (pair in pairs) {
            val numbers = numberPattern.findAll(pair).map { it.value.toDouble() }.toList()
            if (numbers.size < 2) return null
            positions.add(
                Triple(
                    numbers[0],
                    numbers[1],
                    numbers.getOrNull(2),
                ),
            )
        }
        return positions
    }
}
