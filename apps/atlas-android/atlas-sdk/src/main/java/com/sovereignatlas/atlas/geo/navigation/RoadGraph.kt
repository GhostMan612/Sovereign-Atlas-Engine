// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import java.io.File
import java.security.MessageDigest

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

data class RoadGraphBundle(
    val bundleId: String,
    val license: String,
    val attribution: String,
    val graph: RoadNetworkGraph,
)

object RoadGraphBundleSerializer {

    fun computeSha256(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(content).joinToString("") { "%02x".format(it) }
    }

    fun verifyFileWithSidecar(file: File): Boolean {
        if (!file.isFile) return false
        val sidecar = File(file.parentFile, "${file.name}.sha256")
        if (!sidecar.isFile) return true
        val expected = sidecar.readText(Charsets.UTF_8).trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (expected.length != 64) return false
        val actual = computeSha256(file.readBytes())
        return actual.equals(expected, ignoreCase = true)
    }

    fun renderPack(bundle: RoadGraphBundle): String {
        val sb = StringBuilder()
        sb.append("META|")
            .append(bundle.bundleId.replace("|", " "))
            .append("|")
            .append(bundle.license.replace("|", " "))
            .append("|")
            .append(bundle.attribution.replace("|", " "))
            .append("\n")
        for (node in bundle.graph.nodes.values.sortedBy { it.id }) {
            sb.append("NODE|")
                .append(node.id).append("|")
                .append(node.latitude).append("|")
                .append(node.longitude).append("\n")
        }
        for (edge in bundle.graph.edges.values.filter { it.id > 0L }.sortedBy { it.id }) {
            sb.append("EDGE|")
                .append(edge.id).append("|")
                .append(edge.fromNodeId).append("|")
                .append(edge.toNodeId).append("|")
                .append(edge.lengthMeters).append("|")
                .append(edge.speedKmh).append("|")
                .append(edge.name.replace("|", " ")).append("|")
                .append(edge.roadClass.name).append("|")
                .append(if (edge.isOneWay) "1" else "0").append("|")
                .append(edge.surface.name).append("\n")
        }
        for (tr in bundle.graph.turnRestrictions) {
            sb.append("TURN|")
                .append(tr.fromEdgeId).append("|")
                .append(tr.viaNodeId).append("|")
                .append(tr.toEdgeId).append("|")
                .append(tr.type.name).append("\n")
        }
        return sb.toString()
    }

    fun parsePack(raw: String): RoadGraphBundle? {
        var bundleId = "unknown"
        var license = "unknown"
        var attribution = "unknown"
        var hasMeta = false
        val builder = RoadNetworkGraph.Builder()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("|")
            when (parts[0]) {
                "META" -> {
                    if (parts.size < 4) return null
                    bundleId = parts[1]
                    license = parts[2]
                    attribution = parts[3]
                    hasMeta = true
                }
                "NODE" -> {
                    if (parts.size < 4) return null
                    val id = parts[1].toLongOrNull() ?: return null
                    val lat = parts[2].toDoubleOrNull() ?: return null
                    val lon = parts[3].toDoubleOrNull() ?: return null
                    builder.addNode(id, lat, lon)
                }
                "EDGE" -> {
                    if (parts.size < 10) return null
                    val id = parts[1].toLongOrNull() ?: return null
                    val fromId = parts[2].toLongOrNull() ?: return null
                    val toId = parts[3].toLongOrNull() ?: return null
                    val len = parts[4].toDoubleOrNull() ?: return null
                    val speed = parts[5].toDoubleOrNull() ?: return null
                    val name = parts[6]
                    val roadClass = runCatching { RoadClass.valueOf(parts[7]) }.getOrNull() ?: return null
                    val oneWay = parts[8] == "1" || parts[8].equals("true", ignoreCase = true)
                    val surface = runCatching { SurfaceType.valueOf(parts[9]) }.getOrNull() ?: return null
                    builder.addEdge(id, fromId, toId, len, speed, name, roadClass, oneWay, surface)
                }
                "TURN" -> {
                    if (parts.size < 5) return null
                    val fromEdge = parts[1].toLongOrNull() ?: return null
                    val viaNode = parts[2].toLongOrNull() ?: return null
                    val toEdge = parts[3].toLongOrNull() ?: return null
                    val rType = runCatching { RestrictionType.valueOf(parts[4]) }.getOrNull() ?: return null
                    builder.addTurnRestriction(fromEdge, viaNode, toEdge, rType)
                }
                else -> return null
            }
        }
        if (!hasMeta) return null
        val graph = builder.build()
        if (graph.nodes.isEmpty()) return null
        return RoadGraphBundle(bundleId, license, attribution, graph)
    }

    fun loadVerifiedFile(file: File): RoadGraphBundle? {
        if (!verifyFileWithSidecar(file)) return null
        return try {
            parsePack(file.readText(Charsets.UTF_8))
        } catch (error: Throwable) {
            null
        }
    }
}

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
