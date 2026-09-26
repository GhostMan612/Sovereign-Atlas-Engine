// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.routing

data class RoutingNode(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
)

data class RoutingEdge(
    val id: Int,
    val fromNodeId: Int,
    val toNodeId: Int,
    val distanceMeters: Double,
    val gainMeters: Double,
    val descentMeters: Double,
    val slopeDegrees: Double,
    val trailType: String,
)

interface RoutingGraphReader {
    fun readNodes(): Sequence<RoutingNode>
    fun readEdges(): Sequence<RoutingEdge>
}

class RoutingGraph {
    val nodes = HashMap<Int, RoutingNode>()
    val edges = HashMap<Int, List<RoutingEdge>>()

    fun loadFrom(reader: RoutingGraphReader) {
        reader.readNodes().forEach { nodes[it.id] = it }
        reader.readEdges().groupBy { it.fromNodeId }.forEach { (from, group) ->
            edges[from] = group
        }
    }
}
