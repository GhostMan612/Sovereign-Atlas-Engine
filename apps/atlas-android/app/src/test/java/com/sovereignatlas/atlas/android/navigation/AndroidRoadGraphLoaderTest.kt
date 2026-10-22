// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.navigation

import com.sovereignatlas.atlas.geo.navigation.RoadNetworkGraph
import com.sovereignatlas.atlas.geo.navigation.RoadRouteRequest
import com.sovereignatlas.atlas.geo.navigation.RoadRoutingEngine
import com.sovereignatlas.atlas.geo.navigation.TravelProfile
import org.junit.Assert.assertEquals
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
}
