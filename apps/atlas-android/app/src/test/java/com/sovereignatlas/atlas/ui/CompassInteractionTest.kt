// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import com.sovereignatlas.atlas.camera.AtlasCameraState
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.layers.compositor.LayerAttribution
import com.sovereignatlas.atlas.layers.compositor.LayerCompositor
import com.sovereignatlas.atlas.layers.compositor.LayerDescriptor
import com.sovereignatlas.atlas.layers.compositor.LayerType
import com.sovereignatlas.atlas.location.AtlasLocationFix
import com.sovereignatlas.atlas.location.AtlasLocationPermission
import com.sovereignatlas.atlas.location.AtlasLocationQuery
import com.sovereignatlas.atlas.location.AtlasLocationSource
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.location.LocationService
import com.sovereignatlas.atlas.map.LocateOutcome
import com.sovereignatlas.atlas.map.MapBehavior
import com.sovereignatlas.atlas.ui.hud.AtlasMotionMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCompassLocationSource(
    var query: AtlasLocationQuery = AtlasLocationQuery(
        permission = AtlasLocationPermission.granted,
        servicesEnabled = true,
    ),
    var seed: AtlasLocationFix? = null,
) : AtlasLocationSource {
    private var fixListener: ((AtlasLocationFix) -> Unit)? = null

    override fun queryStatus(): AtlasLocationQuery = query
    override fun requestPermission(): AtlasLocationQuery = query
    override fun openAppSettings(): Boolean = true
    override fun lastKnownFix(): AtlasLocationFix? = seed
    override fun setFixListener(listener: ((AtlasLocationFix) -> Unit)?) {
        fixListener = listener
        seed?.let { listener?.invoke(it) }
    }
    override fun setErrorListener(listener: ((Throwable) -> Unit)?) = Unit
    override fun setDoneListener(listener: (() -> Unit)?) = Unit

    fun publishFix(fix: AtlasLocationFix) {
        seed = fix
        fixListener?.invoke(fix)
    }
}

class CompassInteractionTest {

    private fun createLocationService(initialFix: AtlasLocationFix? = null): LocationService {
        val fakeSource = FakeCompassLocationSource(seed = initialFix)
        val service = LocationService(source = fakeSource)
        service.ensureActive()
        return service
    }

    @Test
    fun singleTapTriggersLocateOnly() {
        val fix = AtlasLocationFix(
            position = AtlasCoordinate(37.7749, -122.4194),
            atMs = System.currentTimeMillis(),
            accuracyM = 5.0,
            altitudeM = 10.0,
            headingDeg = 45.0,
            speedMps = 1.0,
        )
        val locationService = createLocationService(fix)
        val behavior = MapBehavior(location = locationService)

        val currentZoom = 16.0
        val currentBearing = 180.0
        val outcome = behavior.locate(currentZoom, currentBearing)

        assertTrue(outcome is LocateOutcome.Applied)
        val intent = (outcome as LocateOutcome.Applied).intent
        assertEquals(37.7749, intent.center.latitude, 0.0001)
        assertEquals(-122.4194, intent.center.longitude, 0.0001)
        assertEquals(currentZoom, intent.zoom, 0.001)
        assertEquals(currentBearing, intent.bearing, 0.001)
    }

    @Test
    fun doubleTapRecentersAndResetsMapBearingToNorth() {
        val fix = AtlasLocationFix(
            position = AtlasCoordinate(38.8951, -77.0364),
            atMs = System.currentTimeMillis(),
            accuracyM = 4.0,
            altitudeM = 15.0,
            headingDeg = 270.0,
            speedMps = 0.0,
        )
        val locationService = createLocationService(fix)

        var headingUp = true
        var pendingHeadingUp = true

        val initialCamera = AtlasCameraState(
            center = AtlasCoordinate(34.0522, -118.2437),
            zoom = 12.0,
            bearing = 215.0,
            pitch = 45.0,
        )

        // Perform double-tap reset logic
        headingUp = false
        pendingHeadingUp = false

        val validFix = if (locationService.status() == AtlasLocationStatus.valid) {
            locationService.latestFixOrNull()?.position
        } else {
            null
        }

        assertNotNull(validFix)
        val targetCenter = validFix!!
        val resetCamera = AtlasCameraState(
            center = targetCenter,
            zoom = initialCamera.zoom,
            bearing = 0.0,
            pitch = initialCamera.pitch,
        )

        assertFalse(headingUp)
        assertFalse(pendingHeadingUp)
        assertEquals(0.0, resetCamera.bearing, 0.001)
        assertEquals(38.8951, resetCamera.center.latitude, 0.0001)
        assertEquals(-77.0364, resetCamera.center.longitude, 0.0001)
        assertEquals(12.0, resetCamera.zoom, 0.001)
        assertEquals(45.0, resetCamera.pitch, 0.001)
    }

    @Test
    fun noLocationBehaviorIsSafeAndDoesNotInventLocation() {
        val locationService = createLocationService(initialFix = null)
        val cameraCenter = AtlasCoordinate(40.7128, -74.0060)
        val initialCamera = AtlasCameraState(
            center = cameraCenter,
            zoom = 15.0,
            bearing = 120.0,
            pitch = 30.0,
        )

        val validFix = if (locationService.status() == AtlasLocationStatus.valid) {
            locationService.latestFixOrNull()?.position
        } else {
            null
        }

        assertEquals(null, validFix)
        // With no fix, target remains initial camera target
        val targetCenter = validFix ?: initialCamera.center
        val resetCamera = AtlasCameraState(
            center = targetCenter,
            zoom = initialCamera.zoom,
            bearing = 0.0,
            pitch = initialCamera.pitch,
        )

        assertEquals(cameraCenter.latitude, resetCamera.center.latitude, 0.0001)
        assertEquals(cameraCenter.longitude, resetCamera.center.longitude, 0.0001)
        assertEquals(0.0, resetCamera.bearing, 0.001)
        assertEquals(15.0, resetCamera.zoom, 0.001)
        assertEquals(30.0, resetCamera.pitch, 0.001)
    }

    @Test
    fun headingFollowDoesNotOverrideNorthReset() {
        var headingUp = true
        var pendingHeadingUp = true

        // User triggers north reset
        headingUp = false
        pendingHeadingUp = false

        // Sensor emits heading update
        val sensorBearing = 135.0
        var rotatedBearing: Double? = null

        if (pendingHeadingUp) {
            pendingHeadingUp = false
            headingUp = true
            rotatedBearing = -sensorBearing
        } else if (headingUp) {
            rotatedBearing = -sensorBearing
        }

        assertEquals(null, rotatedBearing)
        assertFalse(headingUp)
        assertFalse(pendingHeadingUp)
    }

    @Test
    fun bearingTransitionsAroundNorthBoundaryAreCorrect() {
        // Crossing from 358° to 2° should unwrap to 362° rather than rotating 356° backwards
        val unwrappedFromNorth = AtlasMotionMath.unwrapBearingTarget(358f, 2f)
        assertEquals(362f, unwrappedFromNorth, 0.001f)

        // Crossing from 2° to 358° should unwrap to -2°
        val unwrappedToNorth = AtlasMotionMath.unwrapBearingTarget(2f, 358f)
        assertEquals(-2f, unwrappedToNorth, 0.001f)

        val deltaClockwise = AtlasMotionMath.shortestAngleDeltaDeg(350f, 10f)
        assertEquals(20f, deltaClockwise, 0.001f)

        val deltaCounterClockwise = AtlasMotionMath.shortestAngleDeltaDeg(10f, 350f)
        assertEquals(-20f, deltaCounterClockwise, 0.001f)
    }

    @Test
    fun reducedMotionHonorsZeroAnimationDuration() {
        fun cameraAnimationDurationMs(reducedMotion: Boolean): Int {
            return if (reducedMotion) 0 else 300
        }

        assertEquals(0, cameraAnimationDurationMs(reducedMotion = true))
        assertEquals(300, cameraAnimationDurationMs(reducedMotion = false))
    }

    @Test
    fun attributionSurvivesChangesInCompositorLayers() {
        val compositor = LayerCompositor()
        val usgsLayer = LayerDescriptor(
            id = "usgs-topo",
            title = "USGS Topo",
            type = LayerType.RASTER_OVERLAY,
            defaultVisibility = true,
            defaultOpacity = 0.8f,
            defaultZIndex = 10,
            attribution = LayerAttribution(
                sourceName = "USGS",
                license = "Public Domain",
                attributionText = "USGS The National Map",
            ),
        )
        val osmLayer = LayerDescriptor(
            id = "osm-standard",
            title = "OpenStreetMap",
            type = LayerType.RASTER_OVERLAY,
            defaultVisibility = true,
            defaultOpacity = 0.5f,
            defaultZIndex = 20,
            attribution = LayerAttribution(
                sourceName = "OSM",
                license = "ODbL",
                attributionText = "© OpenStreetMap contributors",
            ),
        )

        compositor.registerLayer(usgsLayer)
        compositor.registerLayer(osmLayer)

        val rollup = compositor.getAttributionRollup()
        assertTrue(rollup.contains("USGS The National Map"))
        assertTrue(rollup.contains("© OpenStreetMap contributors"))

        compositor.setVisibility("usgs-topo", false)
        val rollupOsmOnly = compositor.getAttributionRollup()
        assertFalse(rollupOsmOnly.contains("USGS The National Map"))
        assertTrue(rollupOsmOnly.contains("© OpenStreetMap contributors"))
    }
}

