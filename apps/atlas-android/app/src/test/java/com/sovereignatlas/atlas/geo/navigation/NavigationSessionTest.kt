// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSessionTest {

    private fun createSampleRoute(): RoadRouteResult {
        val start = AtlasCoordinate(45.000, -93.000)
        val turn = AtlasCoordinate(45.000, -92.990)
        val dest = AtlasCoordinate(45.010, -92.990)

        val coords = listOf(start, turn, dest)
        val maneuvers = listOf(
            RouteManeuver(0, ManeuverType.DEPART, "Head onto East Way", "East Way", start, 780.0, 50.0, 90.0),
            RouteManeuver(1, ManeuverType.TURN_LEFT, "Turn left onto North Blvd", "North Blvd", turn, 1110.0, 70.0, 0.0),
            RouteManeuver(2, ManeuverType.ARRIVE, "You have arrived at your destination", "North Blvd", dest, 0.0, 0.0, 0.0),
        )

        return RoadRouteResult(
            startNodeId = 1L,
            targetNodeId = 3L,
            totalDistanceMeters = 1890.0,
            estimatedDurationSeconds = 120.0,
            coordinates = coords,
            edges = emptyList(),
            maneuvers = maneuvers,
            profile = TravelProfile.DRIVING,
        )
    }

    @Test
    fun voiceAnnouncementsTriggerAtDistanceThresholds() {
        val route = createSampleRoute()
        val voice = FakeVoiceGuidance()
        val session = NavigationSession(route, voice = voice)

        session.updateLocation(AtlasCoordinate(45.000, -92.996))
        assertTrue(voice.utterances.any { it.contains("meters") })

        val previousCount = voice.utterances.size
        session.updateLocation(AtlasCoordinate(45.000, -92.996))
        assertEquals(previousCount, voice.utterances.size)
    }

    @Test
    fun mutedVoiceGuidanceDoesNotSpeak() {
        val route = createSampleRoute()
        val voice = FakeVoiceGuidance()
        voice.setMuted(true)
        val session = NavigationSession(route, voice = voice)

        session.updateLocation(AtlasCoordinate(45.000, -92.996))
        assertTrue(voice.utterances.isEmpty())
    }

    @Test
    fun arrivalWithinRadiusChangesStatusToArrived() {
        val route = createSampleRoute()
        val voice = FakeVoiceGuidance()
        val session = NavigationSession(route, voice = voice, arrivalRadiusMeters = 30.0)

        val nearDest = AtlasCoordinate(45.010, -92.990)
        val update = session.updateLocation(nearDest)

        assertEquals(NavigationStatus.ARRIVED, update.status)
        assertEquals(NavigationStatus.ARRIVED, session.status)
        assertTrue(voice.utterances.any { it.contains("arrived") })
    }

    @Test
    fun offRouteTriggersRerouteCallback() {
        val route = createSampleRoute()
        var rerouteCalledWith: AtlasCoordinate? = null
        val session = NavigationSession(
            initialRoute = route,
            onRerouteNeeded = { rerouteCalledWith = it },
        )

        val farCoord = AtlasCoordinate(45.050, -92.950)
        val update = session.updateLocation(farCoord)

        assertEquals(NavigationStatus.REROUTING, update.status)
        assertTrue(update.offRoute)
        assertEquals(farCoord, rerouteCalledWith)
    }
}
