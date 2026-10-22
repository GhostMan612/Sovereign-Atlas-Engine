// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadRoutingEngineTest {

    private fun createTestGridGraph(): RoadNetworkGraph {
        return RoadNetworkGraph.Builder()
            .addNode(1L, 45.000, -93.000)
            .addNode(2L, 45.000, -92.990)
            .addNode(3L, 45.000, -92.980)
            .addNode(4L, 45.010, -93.000)
            .addNode(5L, 45.010, -92.990)
            .addNode(6L, 45.010, -92.980)
            .addEdge(101L, 1L, 2L, 780.0, 50.0, "Main St", RoadClass.PRIMARY, isOneWay = false)
            .addEdge(102L, 2L, 3L, 780.0, 50.0, "Main St", RoadClass.PRIMARY, isOneWay = false)
            .addEdge(103L, 4L, 5L, 780.0, 50.0, "North Ave", RoadClass.SECONDARY, isOneWay = false)
            .addEdge(104L, 5L, 6L, 780.0, 50.0, "North Ave", RoadClass.SECONDARY, isOneWay = false)
            .addEdge(105L, 1L, 4L, 1110.0, 40.0, "1st St", RoadClass.RESIDENTIAL, isOneWay = false)
            .addEdge(106L, 2L, 5L, 1110.0, 40.0, "2nd St", RoadClass.RESIDENTIAL, isOneWay = false)
            .addEdge(107L, 3L, 6L, 1110.0, 40.0, "3rd St", RoadClass.RESIDENTIAL, isOneWay = false)
            .build()
    }

    @Test
    fun straightRouteCalculatesAccurately() {
        val graph = createTestGridGraph()
        val engine = RoadRoutingEngine(graph)

        val result = engine.calculateRoute(
            RoadRouteRequest(startNodeId = 1L, targetNodeId = 3L, profile = TravelProfile.DRIVING),
        )

        assertNotNull(result)
        assertEquals(1L, result!!.startNodeId)
        assertEquals(3L, result.targetNodeId)
        assertEquals(1560.0, result.totalDistanceMeters, 0.001)
        assertEquals(2, result.edges.size)
        assertEquals(3, result.coordinates.size)
    }

    @Test
    fun oneWayEnforcementBlocksReverseTravel() {
        val graph = RoadNetworkGraph.Builder()
            .addNode(1L, 45.0, -93.0)
            .addNode(2L, 45.0, -92.99)
            .addEdge(10L, 1L, 2L, 800.0, 50.0, "OneWay Way", RoadClass.PRIMARY, isOneWay = true)
            .build()

        val engine = RoadRoutingEngine(graph)

        val forward = engine.calculateRoute(RoadRouteRequest(1L, 2L))
        assertNotNull(forward)

        val reverse = engine.calculateRoute(RoadRouteRequest(2L, 1L))
        assertNull(reverse)
    }

    @Test
    fun turnRestrictionForcesAlternativePath() {
        val builder = RoadNetworkGraph.Builder()
            .addNode(1L, 45.000, -93.000)
            .addNode(2L, 45.000, -92.990)
            .addNode(3L, 45.000, -92.980)
            .addNode(4L, 45.010, -93.000)
            .addNode(5L, 45.010, -92.990)
            .addEdge(101L, 1L, 2L, 780.0, 50.0, "Main St", RoadClass.PRIMARY, isOneWay = true)
            .addEdge(102L, 2L, 3L, 780.0, 50.0, "Main St", RoadClass.PRIMARY, isOneWay = true)
            .addEdge(105L, 1L, 4L, 1110.0, 40.0, "1st St", RoadClass.RESIDENTIAL, isOneWay = false)
            .addEdge(103L, 4L, 5L, 780.0, 50.0, "North Ave", RoadClass.SECONDARY, isOneWay = false)
            .addEdge(106L, 5L, 2L, 1110.0, 40.0, "2nd St", RoadClass.RESIDENTIAL, isOneWay = false)
            .addTurnRestriction(101L, 2L, 102L, RestrictionType.NO_LEFT_TURN)

        val graph = builder.build()
        val engine = RoadRoutingEngine(graph)

        val direct = engine.calculateRoute(RoadRouteRequest(1L, 3L))
        assertNotNull(direct)
        assertFalse(direct!!.edges.any { it.id == 101L && direct.edges.indexOf(it) < direct.edges.size - 1 && direct.edges[direct.edges.indexOf(it) + 1].id == 102L })
    }

    @Test
    fun travelProfilesCalculateDifferentDurations() {
        val graph = createTestGridGraph()
        val engine = RoadRoutingEngine(graph)

        val driving = engine.calculateRoute(RoadRouteRequest(1L, 3L, TravelProfile.DRIVING))
        val walking = engine.calculateRoute(RoadRouteRequest(1L, 3L, TravelProfile.WALKING))
        val cycling = engine.calculateRoute(RoadRouteRequest(1L, 3L, TravelProfile.CYCLING))

        assertNotNull(driving)
        assertNotNull(walking)
        assertNotNull(cycling)

        assertTrue(walking!!.estimatedDurationSeconds > driving!!.estimatedDurationSeconds)
        assertTrue(walking.estimatedDurationSeconds > cycling!!.estimatedDurationSeconds)
        assertTrue(cycling.estimatedDurationSeconds > driving.estimatedDurationSeconds)
    }

    @Test
    fun sameStartAndTargetReturnsZeroDistance() {
        val graph = createTestGridGraph()
        val engine = RoadRoutingEngine(graph)

        val result = engine.calculateRoute(RoadRouteRequest(1L, 1L))
        assertNotNull(result)
        assertEquals(0.0, result!!.totalDistanceMeters, 0.001)
        assertEquals(0.0, result.estimatedDurationSeconds, 0.001)
        assertEquals(1, result.coordinates.size)
    }

    @Test
    fun unreachableDestinationReturnsNull() {
        val graph = RoadNetworkGraph.Builder()
            .addNode(1L, 45.0, -93.0)
            .addNode(2L, 45.0, -92.99)
            .addNode(99L, 46.0, -90.0)
            .addEdge(10L, 1L, 2L, 800.0, 50.0, "Local Way", RoadClass.RESIDENTIAL)
            .build()

        val engine = RoadRoutingEngine(graph)
        val result = engine.calculateRoute(RoadRouteRequest(1L, 99L))
        assertNull(result)
    }

    @Test
    fun routeAlternativesDiscoverDistinctPaths() {
        val graph = createTestGridGraph()
        val engine = RoadRoutingEngine(graph)

        val alternatives = engine.calculateRouteAlternatives(
            request = RoadRouteRequest(startNodeId = 1L, targetNodeId = 6L, profile = TravelProfile.DRIVING),
            maxAlternatives = 2,
        )

        assertTrue(alternatives.size >= 2)
        val primaryEdges = alternatives[0].edges.map { it.id }
        val altEdges = alternatives[1].edges.map { it.id }
        assertFalse(primaryEdges == altEdges)
        assertEquals("Offline driving speed model", alternatives[0].etaProvenance)
    }
}
