// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import com.sovereignatlas.atlas.geo.AtlasAngles
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath

enum class ManeuverType {
    DEPART,
    CONTINUE_STRAIGHT,
    TURN_SLIGHT_LEFT,
    TURN_LEFT,
    TURN_SHARP_LEFT,
    TURN_SLIGHT_RIGHT,
    TURN_RIGHT,
    TURN_SHARP_RIGHT,
    U_TURN,
    ARRIVE,
}

data class RouteManeuver(
    val stepIndex: Int,
    val maneuverType: ManeuverType,
    val instruction: String,
    val roadName: String,
    val location: AtlasCoordinate,
    val distanceToNextMeters: Double,
    val estimatedDurationSeconds: Double,
    val bearingAfterManeuverDeg: Double,
)

object ManeuverEngine {

    fun determineManeuverType(inboundBearingDeg: Double, outboundBearingDeg: Double): ManeuverType {
        val diff = AtlasAngles.normalizeSignedDeg(outboundBearingDeg - inboundBearingDeg)

        return when {
            diff in -20.0..20.0 -> ManeuverType.CONTINUE_STRAIGHT
            diff in 20.0..55.0 -> ManeuverType.TURN_SLIGHT_RIGHT
            diff in 55.0..125.0 -> ManeuverType.TURN_RIGHT
            diff in 125.0..165.0 -> ManeuverType.TURN_SHARP_RIGHT
            diff in -55.0..-20.0 -> ManeuverType.TURN_SLIGHT_LEFT
            diff in -125.0..-55.0 -> ManeuverType.TURN_LEFT
            diff in -165.0..-125.0 -> ManeuverType.TURN_SHARP_LEFT
            else -> ManeuverType.U_TURN
        }
    }

    fun buildInstruction(type: ManeuverType, roadName: String): String {
        val destination = if (roadName.isBlank()) "the road" else roadName
        return when (type) {
            ManeuverType.DEPART -> "Head onto $destination"
            ManeuverType.CONTINUE_STRAIGHT -> "Continue straight on $destination"
            ManeuverType.TURN_SLIGHT_RIGHT -> "Slight right onto $destination"
            ManeuverType.TURN_RIGHT -> "Turn right onto $destination"
            ManeuverType.TURN_SHARP_RIGHT -> "Sharp right onto $destination"
            ManeuverType.TURN_SLIGHT_LEFT -> "Slight left onto $destination"
            ManeuverType.TURN_LEFT -> "Turn left onto $destination"
            ManeuverType.TURN_SHARP_LEFT -> "Sharp left onto $destination"
            ManeuverType.U_TURN -> "Make a U-turn onto $destination"
            ManeuverType.ARRIVE -> "You have arrived at your destination"
        }
    }

    fun generateManeuvers(edges: List<RoadEdge>, nodes: Map<Long, RoadNode>): List<RouteManeuver> {
        if (edges.isEmpty()) return emptyList()

        val maneuvers = mutableListOf<RouteManeuver>()
        var previousBearing: Double? = null

        for (i in edges.indices) {
            val edge = edges[i]
            val fromNode = nodes[edge.fromNodeId] ?: continue
            val toNode = nodes[edge.toNodeId] ?: continue

            val fromCoord = AtlasCoordinate(fromNode.latitude, fromNode.longitude)
            val toCoord = AtlasCoordinate(toNode.latitude, toNode.longitude)
            val currentBearing = if (fromCoord.latitude == toCoord.latitude && fromCoord.longitude == toCoord.longitude) {
                previousBearing ?: 0.0
            } else {
                AtlasGeoMath.initialBearingDeg(fromCoord, toCoord)
            }

            val type = if (previousBearing == null) {
                ManeuverType.DEPART
            } else {
                val turn = determineManeuverType(previousBearing, currentBearing)
                if (turn == ManeuverType.CONTINUE_STRAIGHT && edge.name == edges[i - 1].name) {
                    previousBearing = currentBearing
                    continue
                }
                turn
            }

            val speedMps = (edge.speedKmh * 1000.0) / 3600.0
            val durationSec = if (speedMps > 0.0) edge.lengthMeters / speedMps else 0.0

            maneuvers.add(
                RouteManeuver(
                    stepIndex = maneuvers.size,
                    maneuverType = type,
                    instruction = buildInstruction(type, edge.name),
                    roadName = edge.name,
                    location = AtlasCoordinate(fromNode.latitude, fromNode.longitude),
                    distanceToNextMeters = edge.lengthMeters,
                    estimatedDurationSeconds = durationSec,
                    bearingAfterManeuverDeg = currentBearing,
                ),
            )

            previousBearing = currentBearing
        }

        val lastEdge = edges.last()
        val destNode = nodes[lastEdge.toNodeId]
        if (destNode != null) {
            maneuvers.add(
                RouteManeuver(
                    stepIndex = maneuvers.size,
                    maneuverType = ManeuverType.ARRIVE,
                    instruction = buildInstruction(ManeuverType.ARRIVE, lastEdge.name),
                    roadName = lastEdge.name,
                    location = AtlasCoordinate(destNode.latitude, destNode.longitude),
                    distanceToNextMeters = 0.0,
                    estimatedDurationSeconds = 0.0,
                    bearingAfterManeuverDeg = previousBearing ?: 0.0,
                ),
            )
        }

        return maneuvers
    }
}
