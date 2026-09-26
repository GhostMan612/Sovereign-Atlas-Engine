// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.routing

import com.sovereignatlas.atlas.geo.AtlasGeoMath
import java.util.PriorityQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RoutingEngine(private val graph: RoutingGraph) {

    internal fun calculateEdgeCost(edge: RoutingEdge, profile: LoadProfile): Double {
        val forwardHours = edge.distanceMeters / 5000.0
        val ascentHours = (edge.gainMeters / 600.0) * profile.ascentPenaltyMultiplier

        val descentHours = when {
            edge.descentMeters <= 0.0 -> 0.0
            edge.slopeDegrees > 12.0 -> (edge.descentMeters / 300.0) * (10.0 / 60.0)
            edge.slopeDegrees < 5.0 -> -(edge.descentMeters / 300.0) * (10.0 / 60.0)
            else -> 0.0
        }

        val terrainFactor = runCatching { TrailType.valueOf(edge.trailType).speedFactor }
            .getOrDefault(TrailType.CROSS_COUNTRY.speedFactor)

        val baseHours = maxOf(0.001, (forwardHours + ascentHours + descentHours) / terrainFactor)
        return baseHours * profile.speedMultiplier
    }

    internal fun heuristic(fromId: Int, toId: Int, profile: LoadProfile): Double {
        val from = graph.nodes[fromId] ?: return Double.POSITIVE_INFINITY
        val to = graph.nodes[toId] ?: return Double.POSITIVE_INFINITY
        val straightLineMeters = AtlasGeoMath.haversine(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
        )

        val forwardHoursRaw = straightLineMeters / 5000.0
        val maxDescentBonusRaw = straightLineMeters * 0.0872 / 1800.0
        val baseHoursRaw = maxOf(0.001, forwardHoursRaw - maxDescentBonusRaw)

        val result = baseHoursRaw * profile.speedMultiplier

        return if (result.isNaN()) Double.POSITIVE_INFINITY else result
    }

    fun nearestNodeId(latitude: Double, longitude: Double): Int? {
        var bestId: Int? = null
        var bestMeters = Double.POSITIVE_INFINITY
        for ((id, node) in graph.nodes) {
            val meters = AtlasGeoMath.haversine(latitude, longitude, node.latitude, node.longitude)
            if (meters < bestMeters) {
                bestMeters = meters
                bestId = id
            }
        }
        return bestId
    }

    suspend fun route(request: RoutingRequest): RoutingResult? = withContext(Dispatchers.Default) {
        require(graph.nodes.containsKey(request.startNodeId)) { "Start node not in graph" }
        require(graph.nodes.containsKey(request.endNodeId)) { "End node not in graph" }

        if (request.startNodeId == request.endNodeId) {
            val node = graph.nodes[request.startNodeId] ?: return@withContext null
            return@withContext RoutingResult(listOf(node), 0.0, 0.0, 0.0, 0.0)
        }

        val openSet = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        openSet.add(request.startNodeId to heuristic(request.startNodeId, request.endNodeId, request.loadProfile))

        val gScore = HashMap<Int, Double>().apply { put(request.startNodeId, 0.0) }
        val cameFrom = HashMap<Int, RoutingEdge>()

        while (openSet.isNotEmpty()) {
            ensureActive()
            val (currentId, currentF) = openSet.poll()
            if (currentId == request.endNodeId) break

            val optimalF = gScore[currentId]!! + heuristic(currentId, request.endNodeId, request.loadProfile)
            if (currentF > optimalF) continue

            for (edge in graph.edges[currentId] ?: emptyList()) {
                val tentativeG = gScore[currentId]!! + calculateEdgeCost(edge, request.loadProfile)
                if (tentativeG < (gScore[edge.toNodeId] ?: Double.POSITIVE_INFINITY)) {
                    gScore[edge.toNodeId] = tentativeG
                    cameFrom[edge.toNodeId] = edge
                    val fScore = tentativeG + heuristic(edge.toNodeId, request.endNodeId, request.loadProfile)
                    openSet.add(edge.toNodeId to fScore)
                }
            }
        }

        if (!cameFrom.containsKey(request.endNodeId)) return@withContext null

        var totalHours = 0.0
        var totalDist = 0.0
        var totalGain = 0.0
        var totalDescent = 0.0
        var currentId = request.endNodeId
        val path = mutableListOf<RoutingNode>()

        while (currentId != request.startNodeId) {
            val node = graph.nodes[currentId] ?: return@withContext null
            path.add(node)
            val edge = cameFrom[currentId] ?: return@withContext null

            totalHours += calculateEdgeCost(edge, request.loadProfile)
            totalDist += edge.distanceMeters
            totalGain += edge.gainMeters
            totalDescent += edge.descentMeters
            currentId = edge.fromNodeId
        }

        val startNode = graph.nodes[request.startNodeId] ?: return@withContext null
        path.add(startNode)
        path.reverse()

        RoutingResult(path, totalHours, totalDist, totalGain, totalDescent)
    }
}

class RoutingState {
    @Volatile
    private var engine: RoutingEngine? = null
    private val loadMutex = Mutex()

    private val _result = MutableStateFlow<RoutingResult?>(null)
    val result: StateFlow<RoutingResult?> = _result.asStateFlow()

    suspend fun ensureEngine(loader: suspend () -> RoutingEngine?): RoutingEngine? {
        engine?.let { return it }
        return loadMutex.withLock {
            engine ?: loader()?.also { engine = it }
        }
    }

    fun setResult(result: RoutingResult?) {
        _result.value = result
    }
}
