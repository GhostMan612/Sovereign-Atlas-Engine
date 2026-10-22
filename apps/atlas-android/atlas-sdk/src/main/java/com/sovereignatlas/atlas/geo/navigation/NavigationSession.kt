// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import kotlin.math.roundToInt

enum class NavigationStatus {
    IDLE,
    NAVIGATING,
    REROUTING,
    ARRIVED,
    STOPPED,
}

data class NavigationUpdate(
    val status: NavigationStatus,
    val snappedPosition: SnappedPosition?,
    val currentManeuver: RouteManeuver?,
    val distanceToManeuverMeters: Double,
    val remainingDistanceMeters: Double,
    val remainingDurationSeconds: Double,
    val offRoute: Boolean,
)

class NavigationSession(
    initialRoute: RoadRouteResult,
    val voice: VoiceGuidance? = null,
    val arrivalRadiusMeters: Double = 25.0,
    val onRerouteNeeded: ((AtlasCoordinate) -> Unit)? = null,
) {
    var route: RoadRouteResult = initialRoute
        private set
    private var snapper = RouteSnapper(initialRoute)

    var status: NavigationStatus = NavigationStatus.NAVIGATING
        private set

    private var currentManeuverIndex = 0
    private val announcedPromptKeys = mutableSetOf<String>()

    fun updateRoute(newRoute: RoadRouteResult) {
        route = newRoute
        snapper = RouteSnapper(newRoute)
        currentManeuverIndex = 0
        announcedPromptKeys.clear()
        status = NavigationStatus.NAVIGATING
    }

    fun stop() {
        status = NavigationStatus.STOPPED
        voice?.stop()
    }

    fun updateLocation(
        coordinate: AtlasCoordinate,
        accuracyMeters: Double = 10.0,
    ): NavigationUpdate {
        if (status == NavigationStatus.STOPPED || status == NavigationStatus.IDLE) {
            return NavigationUpdate(
                status = status,
                snappedPosition = null,
                currentManeuver = null,
                distanceToManeuverMeters = 0.0,
                remainingDistanceMeters = 0.0,
                remainingDurationSeconds = 0.0,
                offRoute = false,
            )
        }

        val snapped = snapper.snap(coordinate, accuracyMeters)

        if (snapped.isOffRoute) {
            status = NavigationStatus.REROUTING
            onRerouteNeeded?.invoke(coordinate)
            return NavigationUpdate(
                status = NavigationStatus.REROUTING,
                snappedPosition = snapped,
                currentManeuver = null,
                distanceToManeuverMeters = 0.0,
                remainingDistanceMeters = snapped.remainingDistanceMeters,
                remainingDurationSeconds = 0.0,
                offRoute = true,
            )
        }

        val destCoord = route.coordinates.lastOrNull()
        val distToDest = if (destCoord != null) {
            AtlasGeoMath.haversine(
                coordinate.latitude,
                coordinate.longitude,
                destCoord.latitude,
                destCoord.longitude,
            )
        } else {
            Double.POSITIVE_INFINITY
        }

        if (distToDest <= arrivalRadiusMeters) {
            status = NavigationStatus.ARRIVED
            voice?.speak("You have arrived at your destination")
            return NavigationUpdate(
                status = NavigationStatus.ARRIVED,
                snappedPosition = snapped,
                currentManeuver = route.maneuvers.lastOrNull(),
                distanceToManeuverMeters = 0.0,
                remainingDistanceMeters = 0.0,
                remainingDurationSeconds = 0.0,
                offRoute = false,
            )
        }

        status = NavigationStatus.NAVIGATING

        advanceManeuverIfNeeded(snapped.snappedCoordinate)

        val activeManeuver = route.maneuvers.getOrNull(currentManeuverIndex)
        val distToManeuver = if (activeManeuver != null) {
            AtlasGeoMath.haversine(
                snapped.snappedCoordinate.latitude,
                snapped.snappedCoordinate.longitude,
                activeManeuver.location.latitude,
                activeManeuver.location.longitude,
            )
        } else {
            0.0
        }

        maybeAnnounceVoice(distToManeuver, activeManeuver)

        val speedMps = maxOf(0.5, (route.profile.defaultSpeedKmh * 1000.0) / 3600.0)
        val estRemainingSec = snapped.remainingDistanceMeters / speedMps

        return NavigationUpdate(
            status = NavigationStatus.NAVIGATING,
            snappedPosition = snapped,
            currentManeuver = activeManeuver,
            distanceToManeuverMeters = distToManeuver,
            remainingDistanceMeters = snapped.remainingDistanceMeters,
            remainingDurationSeconds = estRemainingSec,
            offRoute = false,
        )
    }

    private fun advanceManeuverIfNeeded(currentCoord: AtlasCoordinate) {
        val maneuvers = route.maneuvers
        if (currentManeuverIndex == 0 && maneuvers.size > 1) {
            val departManeuver = maneuvers[0]
            val distFromDepart = AtlasGeoMath.haversine(
                currentCoord.latitude,
                currentCoord.longitude,
                departManeuver.location.latitude,
                departManeuver.location.longitude,
            )
            if (distFromDepart > 20.0) {
                currentManeuverIndex = 1
            }
        }
        while (currentManeuverIndex < maneuvers.size - 1) {
            val maneuver = maneuvers[currentManeuverIndex]
            val dist = AtlasGeoMath.haversine(
                currentCoord.latitude,
                currentCoord.longitude,
                maneuver.location.latitude,
                maneuver.location.longitude,
            )
            if (dist < 20.0) {
                currentManeuverIndex += 1
            } else {
                break
            }
        }
    }

    private fun maybeAnnounceVoice(distanceMeters: Double, maneuver: RouteManeuver?) {
        if (voice == null || maneuver == null) return

        val key500 = "${maneuver.stepIndex}_500"
        val key100 = "${maneuver.stepIndex}_100"
        val keyNow = "${maneuver.stepIndex}_now"

        when {
            distanceMeters in 400.0..600.0 && !announcedPromptKeys.contains(key500) -> {
                announcedPromptKeys.add(key500)
                val dist = distanceMeters.roundToInt()
                voice.speak("In $dist meters, ${maneuver.instruction.lowercase()}")
            }
            distanceMeters in 80.0..150.0 && !announcedPromptKeys.contains(key100) -> {
                announcedPromptKeys.add(key100)
                voice.speak("In 100 meters, ${maneuver.instruction.lowercase()}")
            }
            distanceMeters <= 30.0 && !announcedPromptKeys.contains(keyNow) -> {
                announcedPromptKeys.add(keyNow)
                voice.speak(maneuver.instruction)
            }
        }
    }
}
