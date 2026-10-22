// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RoadNavigationState {
    private val _activeRoute = MutableStateFlow<RoadRouteResult?>(null)
    val activeRoute: StateFlow<RoadRouteResult?> = _activeRoute.asStateFlow()

    private val _session = MutableStateFlow<NavigationSession?>(null)
    val session: StateFlow<NavigationSession?> = _session.asStateFlow()

    private val _update = MutableStateFlow<NavigationUpdate?>(null)
    val update: StateFlow<NavigationUpdate?> = _update.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _profile = MutableStateFlow(TravelProfile.DRIVING)
    val profile: StateFlow<TravelProfile> = _profile.asStateFlow()

    fun startNavigation(
        route: RoadRouteResult,
        voice: VoiceGuidance? = null,
        arrivalRadiusMeters: Double = 25.0,
        onRerouteNeeded: ((AtlasCoordinate) -> Unit)? = null,
    ) {
        voice?.setMuted(_isMuted.value)
        val newSession = NavigationSession(
            initialRoute = route,
            voice = voice,
            arrivalRadiusMeters = arrivalRadiusMeters,
            onRerouteNeeded = onRerouteNeeded,
        )
        _activeRoute.value = route
        _session.value = newSession
        _isNavigating.value = true
        _update.value = NavigationUpdate(
            status = NavigationStatus.NAVIGATING,
            snappedPosition = null,
            currentManeuver = route.maneuvers.firstOrNull(),
            distanceToManeuverMeters = 0.0,
            remainingDistanceMeters = route.totalDistanceMeters,
            remainingDurationSeconds = route.estimatedDurationSeconds,
            offRoute = false,
        )
    }

    fun updateLocation(
        coordinate: AtlasCoordinate,
        accuracyMeters: Double = 10.0,
    ): NavigationUpdate? {
        val currentSession = _session.value ?: return null
        val navUpdate = currentSession.updateLocation(coordinate, accuracyMeters)
        _update.value = navUpdate
        if (navUpdate.status == NavigationStatus.ARRIVED || navUpdate.status == NavigationStatus.STOPPED) {
            _isNavigating.value = false
        }
        return navUpdate
    }

    fun updateRoute(newRoute: RoadRouteResult) {
        _activeRoute.value = newRoute
        _session.value?.updateRoute(newRoute)
    }

    fun stopNavigation() {
        _session.value?.stop()
        _session.value = null
        _activeRoute.value = null
        _update.value = null
        _isNavigating.value = false
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        _session.value?.voice?.setMuted(muted)
    }

    fun setProfile(profile: TravelProfile) {
        _profile.value = profile
    }
}
