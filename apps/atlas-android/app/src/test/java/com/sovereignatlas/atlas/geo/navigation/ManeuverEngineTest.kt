// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverEngineTest {

    @Test
    fun turnAnglesClassifiedCorrectly() {
        assertEquals(
            ManeuverType.CONTINUE_STRAIGHT,
            ManeuverEngine.determineManeuverType(0.0, 5.0),
        )
        assertEquals(
            ManeuverType.TURN_RIGHT,
            ManeuverEngine.determineManeuverType(0.0, 90.0),
        )
        assertEquals(
            ManeuverType.TURN_LEFT,
            ManeuverEngine.determineManeuverType(0.0, 270.0),
        )
        assertEquals(
            ManeuverType.TURN_SHARP_RIGHT,
            ManeuverEngine.determineManeuverType(0.0, 140.0),
        )
        assertEquals(
            ManeuverType.TURN_SHARP_LEFT,
            ManeuverEngine.determineManeuverType(0.0, 220.0),
        )
        assertEquals(
            ManeuverType.U_TURN,
            ManeuverEngine.determineManeuverType(0.0, 180.0),
        )
    }

    @Test
    fun maneuversGeneratedFromEdgesWithArrival() {
        val nodes = mapOf(
            1L to RoadNode(1L, 45.000, -93.000),
            2L to RoadNode(2L, 45.000, -92.990),
            3L to RoadNode(3L, 45.010, -92.990),
        )
        val edges = listOf(
            RoadEdge(10L, 1L, 2L, 780.0, 50.0, "East Way", RoadClass.PRIMARY, false),
            RoadEdge(20L, 2L, 3L, 1110.0, 40.0, "North Blvd", RoadClass.SECONDARY, false),
        )

        val maneuvers = ManeuverEngine.generateManeuvers(edges, nodes)

        assertEquals(3, maneuvers.size)
        assertEquals(ManeuverType.DEPART, maneuvers[0].maneuverType)
        assertEquals("Head onto East Way", maneuvers[0].instruction)
        assertEquals(ManeuverType.TURN_LEFT, maneuvers[1].maneuverType)
        assertEquals("Turn left onto North Blvd", maneuvers[1].instruction)
        assertEquals(ManeuverType.ARRIVE, maneuvers[2].maneuverType)
        assertTrue(maneuvers[2].instruction.contains("arrived"))
    }
}
