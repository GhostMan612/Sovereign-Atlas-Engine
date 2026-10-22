// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadNavigationStateTest {

    private fun sampleRoute(): RoadRouteResult {
        val coord1 = AtlasCoordinate(37.7749, -122.4194)
        val coord2 = AtlasCoordinate(37.7755, -122.4180)
        val coord3 = AtlasCoordinate(37.7765, -122.4165)
        return RoadRouteResult(
            startNodeId = 1L,
            targetNodeId = 3L,
            totalDistanceMeters = 330.0,
            estimatedDurationSeconds = 30.0,
            coordinates = listOf(coord1, coord2, coord3),
            edges = emptyList(),
            maneuvers = listOf(
                RouteManeuver(
                    stepIndex = 0,
                    maneuverType = ManeuverType.DEPART,
                    instruction = "Head northeast on Market St",
                    roadName = "Market St",
                    location = coord1,
                    distanceToNextMeters = 330.0,
                    estimatedDurationSeconds = 30.0,
                    bearingAfterManeuverDeg = 45.0,
                ),
                RouteManeuver(
                    stepIndex = 1,
                    maneuverType = ManeuverType.ARRIVE,
                    instruction = "Arrive at destination",
                    roadName = "Market St",
                    location = coord3,
                    distanceToNextMeters = 0.0,
                    estimatedDurationSeconds = 0.0,
                    bearingAfterManeuverDeg = 45.0,
                ),
            ),
            profile = TravelProfile.DRIVING,
        )
    }

    @Test
    fun initialStateIsIdle() {
        val state = RoadNavigationState()
        assertFalse(state.isNavigating.value)
        assertFalse(state.isMuted.value)
        assertNull(state.activeRoute.value)
        assertNull(state.session.value)
        assertNull(state.update.value)
        assertEquals(TravelProfile.DRIVING, state.profile.value)
    }

    @Test
    fun startNavigationActivatesSessionAndRoute() {
        val state = RoadNavigationState()
        val voice = FakeVoiceGuidance()
        val route = sampleRoute()

        state.startNavigation(route, voice)

        assertTrue(state.isNavigating.value)
        assertEquals(route, state.activeRoute.value)
        assertNotNull(state.session.value)
        val update = state.update.value
        assertNotNull(update)
        assertEquals(NavigationStatus.NAVIGATING, update?.status)
        assertEquals(330.0, update?.remainingDistanceMeters ?: 0.0, 0.1)
    }

    @Test
    fun updateLocationAdvancesGuidance() {
        val state = RoadNavigationState()
        val voice = FakeVoiceGuidance()
        val route = sampleRoute()

        state.startNavigation(route, voice)
        val update = state.updateLocation(AtlasCoordinate(37.7750, -122.4193))

        assertNotNull(update)
        assertEquals(NavigationStatus.NAVIGATING, update?.status)
        assertFalse(update?.offRoute ?: true)
    }

    @Test
    fun muteControlsVoiceGuidance() {
        val state = RoadNavigationState()
        val voice = FakeVoiceGuidance()
        state.startNavigation(sampleRoute(), voice)

        assertFalse(state.isMuted.value)
        assertFalse(voice.isMuted())

        state.setMuted(true)
        assertTrue(state.isMuted.value)
        assertTrue(voice.isMuted())
    }

    @Test
    fun stopNavigationClearsState() {
        val state = RoadNavigationState()
        val voice = FakeVoiceGuidance()
        state.startNavigation(sampleRoute(), voice)

        state.stopNavigation()

        assertFalse(state.isNavigating.value)
        assertNull(state.activeRoute.value)
        assertNull(state.session.value)
        assertNull(state.update.value)
    }

    @Test
    fun profileCanBeChanged() {
        val state = RoadNavigationState()
        state.setProfile(TravelProfile.WALKING)
        assertEquals(TravelProfile.WALKING, state.profile.value)
        state.setProfile(TravelProfile.CYCLING)
        assertEquals(TravelProfile.CYCLING, state.profile.value)
    }
}
