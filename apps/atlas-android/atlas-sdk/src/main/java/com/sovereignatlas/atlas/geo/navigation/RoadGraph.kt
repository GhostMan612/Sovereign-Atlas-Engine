// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

enum class RoadClass(val baseSpeedKmh: Double, val defaultPriority: Double) {
    MOTORWAY(110.0, 1.0),
    TRUNK(90.0, 1.1),
    PRIMARY(70.0, 1.2),
    SECONDARY(50.0, 1.3),
    TERTIARY(40.0, 1.4),
    RESIDENTIAL(30.0, 1.5),
    SERVICE(20.0, 1.8),
    TRACK(15.0, 2.0),
    FOOTWAY(5.0, 3.0),
    CYCLEWAY(18.0, 1.2),
}

enum class SurfaceType {
    PAVED,
    UNPAVED,
    GRAVEL,
    DIRT,
}

enum class RestrictionType {
    NO_LEFT_TURN,
    NO_RIGHT_TURN,
    NO_U_TURN,
    ONLY_STRAIGHT_ON,
}

enum class TravelProfile(val defaultSpeedKmh: Double) {
    DRIVING(50.0),
    CYCLING(15.0),
    WALKING(4.5),
}

data class RoadNode(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
)

data class RoadEdge(
    val id: Long,
    val fromNodeId: Long,
    val toNodeId: Long,
    val lengthMeters: Double,
    val speedKmh: Double,
    val name: String,
    val roadClass: RoadClass,
    val isOneWay: Boolean,
    val surface: SurfaceType = SurfaceType.PAVED,
)

data class TurnRestriction(
    val fromEdgeId: Long,
    val viaNodeId: Long,
    val toEdgeId: Long,
    val type: RestrictionType,
)

class RoadNetworkGraph(
    val nodes: Map<Long, RoadNode>,
    val edges: Map<Long, RoadEdge>,
    val outgoingEdgesByNode: Map<Long, List<RoadEdge>>,
    val turnRestrictions: List<TurnRestriction> = emptyList(),
) {
    private val restrictionsByFromEdge: Map<Long, List<TurnRestriction>> =
        turnRestrictions.groupBy { it.fromEdgeId }

    fun isTurnAllowed(fromEdge: RoadEdge?, toEdge: RoadEdge): Boolean {
        if (fromEdge == null) return true
        if (fromEdge.toNodeId != toEdge.fromNodeId) return false
        val restrictions = restrictionsByFromEdge[fromEdge.id] ?: return true
        for (restriction in restrictions) {
            if (restriction.viaNodeId == fromEdge.toNodeId) {
                when (restriction.type) {
                    RestrictionType.NO_LEFT_TURN,
                    RestrictionType.NO_RIGHT_TURN,
                    RestrictionType.NO_U_TURN -> {
                        if (restriction.toEdgeId == toEdge.id) return false
                    }
                    RestrictionType.ONLY_STRAIGHT_ON -> {
                        if (restriction.toEdgeId != toEdge.id) return false
                    }
                }
            }
        }
        return true
    }
    companion object {
        val EMPTY = RoadNetworkGraph(emptyMap(), emptyMap(), emptyMap(), emptyList())
    }

    class Builder {
        private val nodes = mutableMapOf<Long, RoadNode>()
        private val edges = mutableMapOf<Long, RoadEdge>()
        private val outgoing = mutableMapOf<Long, MutableList<RoadEdge>>()
        private val restrictions = mutableListOf<TurnRestriction>()

        fun addNode(id: Long, lat: Double, lon: Double): Builder {
            nodes[id] = RoadNode(id, lat, lon)
            return this
        }

        fun addEdge(
            id: Long,
            fromNodeId: Long,
            toNodeId: Long,
            lengthMeters: Double,
            speedKmh: Double,
            name: String,
            roadClass: RoadClass,
            isOneWay: Boolean = false,
            surface: SurfaceType = SurfaceType.PAVED,
        ): Builder {
            val forwardEdge = RoadEdge(
                id = id,
                fromNodeId = fromNodeId,
                toNodeId = toNodeId,
                lengthMeters = lengthMeters,
                speedKmh = speedKmh,
                name = name,
                roadClass = roadClass,
                isOneWay = isOneWay,
                surface = surface,
            )
            edges[id] = forwardEdge
            outgoing.getOrPut(fromNodeId) { mutableListOf() }.add(forwardEdge)

            if (!isOneWay) {
                val reverseId = -id
                val reverseEdge = RoadEdge(
                    id = reverseId,
                    fromNodeId = toNodeId,
                    toNodeId = fromNodeId,
                    lengthMeters = lengthMeters,
                    speedKmh = speedKmh,
                    name = name,
                    roadClass = roadClass,
                    isOneWay = false,
                    surface = surface,
                )
                edges[reverseId] = reverseEdge
                outgoing.getOrPut(toNodeId) { mutableListOf() }.add(reverseEdge)
            }
            return this
        }

        fun addTurnRestriction(
            fromEdgeId: Long,
            viaNodeId: Long,
            toEdgeId: Long,
            type: RestrictionType,
        ): Builder {
            restrictions.add(TurnRestriction(fromEdgeId, viaNodeId, toEdgeId, type))
            return this
        }

        fun build(): RoadNetworkGraph {
            return RoadNetworkGraph(
                nodes = nodes.toMap(),
                edges = edges.toMap(),
                outgoingEdgesByNode = outgoing.mapValues { it.value.toList() },
                turnRestrictions = restrictions.toList(),
            )
        }
    }
}
