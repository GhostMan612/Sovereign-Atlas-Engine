// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import java.util.PriorityQueue

data class RoadRouteRequest(
    val startNodeId: Long,
    val targetNodeId: Long,
    val profile: TravelProfile = TravelProfile.DRIVING,
    val avoidUnpaved: Boolean = false,
)

data class RoadRouteResult(
    val startNodeId: Long,
    val targetNodeId: Long,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Double,
    val coordinates: List<AtlasCoordinate>,
    val edges: List<RoadEdge>,
    val maneuvers: List<RouteManeuver>,
    val profile: TravelProfile,
)

class RoadRoutingEngine(val graph: RoadNetworkGraph) {

    private data class SearchState(
        val nodeId: Long,
        val inboundEdgeId: Long?,
        val costG: Double,
        val costF: Double,
    )

    private fun edgeTraversalCost(edge: RoadEdge, profile: TravelProfile, avoidUnpaved: Boolean): Double {
        if (avoidUnpaved && edge.surface != SurfaceType.PAVED) {
            return edge.lengthMeters * 5.0
        }

        val baseSpeed = when (profile) {
            TravelProfile.DRIVING -> edge.speedKmh
            TravelProfile.CYCLING -> minOf(profile.defaultSpeedKmh, edge.speedKmh)
            TravelProfile.WALKING -> profile.defaultSpeedKmh
        }

        val speedMps = maxOf(0.5, (baseSpeed * 1000.0) / 3600.0)
        val timeCostSeconds = edge.lengthMeters / speedMps

        val priorityMultiplier = when (profile) {
            TravelProfile.DRIVING -> edge.roadClass.defaultPriority
            TravelProfile.CYCLING -> if (edge.roadClass == RoadClass.CYCLEWAY) 0.8 else edge.roadClass.defaultPriority
            TravelProfile.WALKING -> if (edge.roadClass == RoadClass.FOOTWAY) 0.7 else 1.0
        }

        return timeCostSeconds * priorityMultiplier
    }

    private fun heuristicCost(fromNodeId: Long, targetNodeId: Long, profile: TravelProfile): Double {
        val from = graph.nodes[fromNodeId] ?: return Double.POSITIVE_INFINITY
        val to = graph.nodes[targetNodeId] ?: return Double.POSITIVE_INFINITY
        val distance = AtlasGeoMath.haversine(from.latitude, from.longitude, to.latitude, to.longitude)
        val maxSpeedMps = (profile.defaultSpeedKmh * 1000.0) / 3600.0
        return distance / maxSpeedMps
    }

    fun findNearestNode(latitude: Double, longitude: Double): RoadNode? {
        var closest: RoadNode? = null
        var minDistance = Double.POSITIVE_INFINITY

        for (node in graph.nodes.values) {
            val dist = AtlasGeoMath.haversine(latitude, longitude, node.latitude, node.longitude)
            if (dist < minDistance) {
                minDistance = dist
                closest = node
            }
        }
        return closest
    }

    fun calculateRoute(request: RoadRouteRequest): RoadRouteResult? {
        val startNode = graph.nodes[request.startNodeId] ?: return null
        val targetNode = graph.nodes[request.targetNodeId] ?: return null

        if (request.startNodeId == request.targetNodeId) {
            val coord = AtlasCoordinate(startNode.latitude, startNode.longitude)
            return RoadRouteResult(
                startNodeId = request.startNodeId,
                targetNodeId = request.targetNodeId,
                totalDistanceMeters = 0.0,
                estimatedDurationSeconds = 0.0,
                coordinates = listOf(coord),
                edges = emptyList(),
                maneuvers = listOf(
                    RouteManeuver(
                        stepIndex = 0,
                        maneuverType = ManeuverType.ARRIVE,
                        instruction = "You are already at your destination",
                        roadName = "",
                        location = coord,
                        distanceToNextMeters = 0.0,
                        estimatedDurationSeconds = 0.0,
                        bearingAfterManeuverDeg = 0.0,
                    ),
                ),
                profile = request.profile,
            )
        }

        val queue = PriorityQueue<SearchState>(compareBy { it.costF })
        val gScores = mutableMapOf<Pair<Long, Long?>, Double>()
        val cameFrom = mutableMapOf<Pair<Long, Long?>, Pair<Long, Long?>>()
        val edgeUsed = mutableMapOf<Pair<Long, Long?>, RoadEdge>()

        val initialState = SearchState(
            nodeId = request.startNodeId,
            inboundEdgeId = null,
            costG = 0.0,
            costF = heuristicCost(request.startNodeId, request.targetNodeId, request.profile),
        )

        val startKey = Pair(request.startNodeId, null as Long?)
        gScores[startKey] = 0.0
        queue.add(initialState)

        var finalKey: Pair<Long, Long?>? = null

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: break
            val currentKey = Pair(current.nodeId, current.inboundEdgeId)

            if (current.costG > (gScores[currentKey] ?: Double.POSITIVE_INFINITY)) {
                continue
            }

            if (current.nodeId == request.targetNodeId) {
                finalKey = currentKey
                break
            }

            val outbound = graph.outgoingEdgesByNode[current.nodeId] ?: emptyList()
            val inboundEdge = current.inboundEdgeId?.let { graph.edges[it] }

            for (edge in outbound) {
                if (!graph.isTurnAllowed(inboundEdge, edge)) continue

                val cost = edgeTraversalCost(edge, request.profile, request.avoidUnpaved)
                val tentativeG = current.costG + cost
                val nextKey = Pair(edge.toNodeId, edge.id)

                if (tentativeG < (gScores[nextKey] ?: Double.POSITIVE_INFINITY)) {
                    gScores[nextKey] = tentativeG
                    cameFrom[nextKey] = currentKey
                    edgeUsed[nextKey] = edge
                    val h = heuristicCost(edge.toNodeId, request.targetNodeId, request.profile)
                    queue.add(
                        SearchState(
                            nodeId = edge.toNodeId,
                            inboundEdgeId = edge.id,
                            costG = tentativeG,
                            costF = tentativeG + h,
                        ),
                    )
                }
            }
        }

        if (finalKey == null) return null

        val pathEdges = mutableListOf<RoadEdge>()
        var curr: Pair<Long, Long?>? = finalKey

        while (curr != null && curr.second != null) {
            val edge = edgeUsed[curr] ?: break
            pathEdges.add(edge)
            curr = cameFrom[curr]
        }

        pathEdges.reverse()

        var totalDist = 0.0
        var totalSec = 0.0
        val coordinates = mutableListOf<AtlasCoordinate>()

        val firstNode = graph.nodes[request.startNodeId] ?: return null
        coordinates.add(AtlasCoordinate(firstNode.latitude, firstNode.longitude))

        for (edge in pathEdges) {
            totalDist += edge.lengthMeters
            val speedKmh = when (request.profile) {
                TravelProfile.DRIVING -> edge.speedKmh
                TravelProfile.CYCLING -> minOf(request.profile.defaultSpeedKmh, edge.speedKmh)
                TravelProfile.WALKING -> request.profile.defaultSpeedKmh
            }
            val speedMps = maxOf(0.5, (speedKmh * 1000.0) / 3600.0)
            totalSec += edge.lengthMeters / speedMps
            val toNode = graph.nodes[edge.toNodeId]
            if (toNode != null) {
                coordinates.add(AtlasCoordinate(toNode.latitude, toNode.longitude))
            }
        }

        val maneuvers = ManeuverEngine.generateManeuvers(pathEdges, graph.nodes)

        return RoadRouteResult(
            startNodeId = request.startNodeId,
            targetNodeId = request.targetNodeId,
            totalDistanceMeters = totalDist,
            estimatedDurationSeconds = totalSec,
            coordinates = coordinates,
            edges = pathEdges,
            maneuvers = maneuvers,
            profile = request.profile,
        )
    }
}
