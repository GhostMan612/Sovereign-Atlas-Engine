// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.navigation

import com.sovereignatlas.atlas.geo.navigation.RoadGraphBundleSerializer
import com.sovereignatlas.atlas.geo.navigation.RoadNetworkGraph
import com.sovereignatlas.atlas.geo.navigation.RoadRouteRequest
import com.sovereignatlas.atlas.geo.navigation.RoadRoutingEngine
import com.sovereignatlas.atlas.geo.navigation.TravelProfile
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRoadGraphLoaderTest {

    @Test
    fun emptyGraphHasNoNodesOrEdges() {
        val empty = RoadNetworkGraph.EMPTY
        assertTrue(empty.nodes.isEmpty())
        assertTrue(empty.edges.isEmpty())
        assertTrue(empty.outgoingEdgesByNode.isEmpty())
    }

    @Test
    fun demoSimulationGraphBuildsExpectedTopology() {
        val demo = AndroidRoadGraphLoader.buildDemoSimulationGraph()
        assertEquals(7, demo.nodes.size)
        assertTrue(demo.edges.isNotEmpty())
        assertTrue(demo.outgoingEdgesByNode.containsKey(1L))
        assertTrue(demo.outgoingEdgesByNode.containsKey(2L))
    }

    @Test
    fun routingEngineWithEmptyGraphSafelyReturnsNull() {
        val engine = RoadRoutingEngine(RoadNetworkGraph.EMPTY)
        assertNull(engine.findNearestNode(37.7749, -122.4194))
        val route = engine.calculateRoute(
            RoadRouteRequest(
                startNodeId = 1L,
                targetNodeId = 5L,
                profile = TravelProfile.DRIVING,
            ),
        )
        assertNull(route)
    }

    @Test
    fun demoSimulationGraphProducesRouteWhenExplicitlyInvoked() {
        val demo = AndroidRoadGraphLoader.buildDemoSimulationGraph()
        val engine = RoadRoutingEngine(demo)
        val startNode = engine.findNearestNode(37.7749, -122.4194)
        val targetNode = engine.findNearestNode(37.7795, -122.4135)
        assertNotNull(startNode)
        assertNotNull(targetNode)
        val route = engine.calculateRoute(
            RoadRouteRequest(
                startNodeId = startNode!!.id,
                targetNodeId = targetNode!!.id,
                profile = TravelProfile.DRIVING,
            ),
        )
        assertNotNull(route)
        assertTrue(route!!.coordinates.size >= 2)
    }

    @Test
    fun loadFromDirectoryIngestsGoldenPresidioPackAndEnforcesTurnRestrictions() {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden/road_network_presidio.pack")
        assertNotNull(stream)
        val packBytes = stream!!.readBytes()
        val sha256 = RoadGraphBundleSerializer.computeSha256(packBytes)

        val dir = Files.createTempDirectory("atlas-road-pack").toFile()
        val packFile = File(dir, AndroidRoadGraphLoader.ROADS_PACK_FILE)
        val sidecarFile = File(dir, "${AndroidRoadGraphLoader.ROADS_PACK_FILE}.sha256")
        packFile.writeBytes(packBytes)
        sidecarFile.writeText("$sha256  roads.pack\n", Charsets.UTF_8)

        assertTrue(AndroidRoadGraphLoader.isRoutableDirectoryAvailable(dir))
        val graph = AndroidRoadGraphLoader.loadFromDirectory(dir)
        assertNotNull(graph)
        assertEquals(5, graph!!.nodes.size)
        assertEquals(1, graph.turnRestrictions.size)

        val engine = RoadRoutingEngine(graph)
        val route = engine.calculateRoute(
            RoadRouteRequest(
                startNodeId = 1001L,
                targetNodeId = 1003L,
                profile = TravelProfile.DRIVING,
            ),
        )
        assertNotNull(route)
        assertEquals(listOf(5001L, 5003L, 5004L), route!!.edges.map { it.id })
        assertEquals(785.0, route.totalDistanceMeters, 0.01)
    }

    @Test
    fun loadFromDirectoryRejectsTamperedPackWhenSidecarFails() {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden/road_network_presidio.pack")
        val packBytes = stream!!.readBytes()

        val dir = Files.createTempDirectory("atlas-road-tamper").toFile()
        val packFile = File(dir, AndroidRoadGraphLoader.ROADS_PACK_FILE)
        val sidecarFile = File(dir, "${AndroidRoadGraphLoader.ROADS_PACK_FILE}.sha256")
        packFile.writeBytes(packBytes)
        sidecarFile.writeText("0000000000000000000000000000000000000000000000000000000000000000", Charsets.UTF_8)

        assertFalse(AndroidRoadGraphLoader.isRoutableDirectoryAvailable(dir))
        assertNull(AndroidRoadGraphLoader.loadFromDirectory(dir))
    }
}
