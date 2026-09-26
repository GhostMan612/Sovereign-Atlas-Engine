// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.routing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun chainGraph(): RoutingGraph {
    val graph = RoutingGraph()
    graph.nodes[1] = RoutingNode(1, 0.0, 0.0, 100.0)
    graph.nodes[2] = RoutingNode(2, 0.0, 0.01, 100.0)
    graph.nodes[3] = RoutingNode(3, 0.0, 0.02, 100.0)
    graph.nodes[4] = RoutingNode(4, 1.0, 1.0, 100.0)
    fun edge(
        id: Int,
        from: Int,
        to: Int,
        distance: Double,
        gain: Double = 0.0,
        descent: Double = 0.0,
        slope: Double = 2.0,
        trail: String = "ROAD",
    ): RoutingEdge {
        return RoutingEdge(id, from, to, distance, gain, descent, slope, trail)
    }
    graph.edges[1] = listOf(
        edge(1, 1, 2, 1112.0),
        edge(2, 1, 3, 2224.0, gain = 600.0, trail = "CROSS_COUNTRY"),
    )
    graph.edges[2] = listOf(edge(3, 2, 3, 1112.0, gain = 60.0, trail = "TRAIL"))
    graph.edges[3] = emptyList()
    graph.edges[4] = emptyList()
    return graph
}

final class RoutingEngineTest {
    @Test
    fun flatRoadCostScalesWithLoadProfile() {
        val engine = RoutingEngine(chainGraph())
        val edge = RoutingEdge(9, 1, 2, 5000.0, 0.0, 0.0, 0.0, "ROAD")
        assertEquals(1.0, engine.calculateEdgeCost(edge, LoadProfile.SLICK), 1e-9)
        assertEquals(1.15, engine.calculateEdgeCost(edge, LoadProfile.PATROL), 1e-9)
        assertEquals(1.35, engine.calculateEdgeCost(edge, LoadProfile.HEAVY), 1e-9)
        assertEquals(1.6, engine.calculateEdgeCost(edge, LoadProfile.OVERLOAD), 1e-9)
    }

    @Test
    fun steepAscentAppliesNaismithPenalty() {
        val engine = RoutingEngine(chainGraph())
        val edge = RoutingEdge(9, 1, 2, 0.0, 600.0, 0.0, 20.0, "ROAD")
        assertEquals(1.0, engine.calculateEdgeCost(edge, LoadProfile.SLICK), 1e-9)
        assertEquals(1.2 * 1.15, engine.calculateEdgeCost(edge, LoadProfile.PATROL), 1e-9)
        assertEquals(1.5 * 1.35, engine.calculateEdgeCost(edge, LoadProfile.HEAVY), 1e-9)
    }

    @Test
    fun steepDescentCostsTime() {
        val engine = RoutingEngine(chainGraph())
        val edge = RoutingEdge(9, 1, 2, 0.0, 0.0, 300.0, 20.0, "ROAD")
        assertEquals(300.0 / 300.0 * (10.0 / 60.0), engine.calculateEdgeCost(edge, LoadProfile.SLICK), 1e-9)
    }

    @Test
    fun moderateDescentDiscountsButNeverNegative() {
        val engine = RoutingEngine(chainGraph())
        val gentle = RoutingEdge(9, 1, 2, 5000.0, 0.0, 300.0, 3.0, "ROAD")
        val cost = engine.calculateEdgeCost(gentle, LoadProfile.SLICK)
        assertEquals(1.0 - (300.0 / 300.0) * (10.0 / 60.0), cost, 1e-9)
        val cliff = RoutingEdge(9, 1, 2, 100.0, 0.0, 600.0, 3.0, "ROAD")
        assertEquals(0.001, engine.calculateEdgeCost(cliff, LoadProfile.SLICK), 1e-9)
    }

    @Test
    fun roughTerrainSlowsTraversal() {
        val engine = RoutingEngine(chainGraph())
        val road = RoutingEdge(9, 1, 2, 5000.0, 0.0, 0.0, 0.0, "ROAD")
        val rough = road.copy(id = 10, trailType = "STEEP_OFF_TRAIL")
        val unknown = road.copy(id = 11, trailType = "MUD")
        assertTrue(engine.calculateEdgeCost(rough, LoadProfile.SLICK) > engine.calculateEdgeCost(road, LoadProfile.SLICK))
        assertEquals(
            engine.calculateEdgeCost(rough.copy(id = 12, trailType = "CROSS_COUNTRY"), LoadProfile.SLICK),
            engine.calculateEdgeCost(unknown, LoadProfile.SLICK),
            1e-9,
        )
    }

    @Test
    fun prefersCheaperIndirectPath() {
        val engine = RoutingEngine(chainGraph())
        val result = runBlocking {
            engine.route(RoutingRequest(1, 3, LoadProfile.PATROL))
        }!!
        assertEquals(listOf(1, 2, 3), result.path.map { it.id })
        assertTrue(result.totalDistanceMeters > 2220.0 && result.totalDistanceMeters < 2230.0)
        assertEquals(60.0, result.totalGainMeters, 1e-9)
    }

    @Test
    fun trivialSameNodeRoute() {
        val engine = RoutingEngine(chainGraph())
        val result = runBlocking {
            engine.route(RoutingRequest(2, 2, LoadProfile.SLICK))
        }!!
        assertEquals(listOf(2), result.path.map { it.id })
        assertEquals(0.0, result.estimatedHours, 0.0)
    }

    @Test
    fun disconnectedNodesReturnNull() {
        val engine = RoutingEngine(chainGraph())
        assertNull(
            runBlocking { engine.route(RoutingRequest(1, 4, LoadProfile.SLICK)) },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingNodeThrows() {
        val engine = RoutingEngine(chainGraph())
        runBlocking { engine.route(RoutingRequest(1, 99, LoadProfile.SLICK)) }
    }

    @Test
    fun nearestNodeFindsClosest() {
        val engine = RoutingEngine(chainGraph())
        assertEquals(1, engine.nearestNodeId(0.0, 0.001))
        assertEquals(3, engine.nearestNodeId(0.0, 0.019))
    }

    @Test
    fun heuristicNeverExceedsTrueCost() {
        val engine = RoutingEngine(chainGraph())
        for (profile in LoadProfile.values()) {
            val result = runBlocking { engine.route(RoutingRequest(1, 3, profile)) }!!
            val bound = engine.heuristic(1, 3, profile)
            assertTrue("$profile bound $bound vs ${result.estimatedHours}", bound <= result.estimatedHours + 1e-9)
        }
    }
}
