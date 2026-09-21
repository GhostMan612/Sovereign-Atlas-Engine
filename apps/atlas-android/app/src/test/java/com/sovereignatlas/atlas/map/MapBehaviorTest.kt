// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.location.AtlasLocationFix
import com.sovereignatlas.atlas.location.AtlasLocationPermission
import com.sovereignatlas.atlas.location.AtlasLocationQuery
import com.sovereignatlas.atlas.location.AtlasLocationSource
import com.sovereignatlas.atlas.location.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val GRANTED = AtlasLocationQuery(
    permission = AtlasLocationPermission.granted,
    servicesEnabled = true,
)

private final class FakeBehaviorSource(
    var query: AtlasLocationQuery = GRANTED,
    var seed: AtlasLocationFix? = null,
) : AtlasLocationSource {
    private var fixListener: ((AtlasLocationFix) -> Unit)? = null

    override fun queryStatus(): AtlasLocationQuery = query

    override fun requestPermission(): AtlasLocationQuery = query

    override fun openAppSettings(): Boolean = true

    override fun lastKnownFix(): AtlasLocationFix? = seed

    override fun setFixListener(listener: ((AtlasLocationFix) -> Unit)?) {
        fixListener = listener
    }

    override fun setErrorListener(listener: ((Throwable) -> Unit)?) = Unit

    override fun setDoneListener(listener: (() -> Unit)?) = Unit

    fun emit(value: AtlasLocationFix) {
        fixListener?.invoke(value)
    }
}

private fun fix(): AtlasLocationFix {
    return AtlasLocationFix(
        position = AtlasCoordinate(latitude = 45.0, longitude = -93.0),
        atMs = 1000L,
        source = "gps",
    )
}

final class MapBehaviorTest {
    private fun behavior(fake: FakeBehaviorSource): MapBehavior {
        return MapBehavior(LocationService(source = fake))
    }

    @Test
    fun startupAppliesValidSeedOnce() {
        val behavior = behavior(FakeBehaviorSource(seed = fix()))
        val intent = behavior.startupCamera()
        assertEquals(45.0, intent?.center?.latitude)
        assertEquals(-93.0, intent?.center?.longitude)
        assertEquals(13.0, intent?.zoom)
        assertEquals(0.0, intent?.bearing)
        assertNull(behavior.startupCamera())
    }

    @Test
    fun startupWithoutFixRetriesLater() {
        val fake = FakeBehaviorSource()
        val service = LocationService(source = fake)
        val behavior = MapBehavior(service)
        assertNull(behavior.startupCamera())
        service.start()
        fake.emit(fix())
        val intent = behavior.startupCamera()
        assertEquals(13.0, intent?.zoom)
        assertNull(behavior.startupCamera())
    }

    @Test
    fun userInteractionCancelsStartupForever() {
        val behavior = behavior(FakeBehaviorSource(seed = fix()))
        behavior.markUserInteracted()
        assertNull(behavior.startupCamera())
    }

    @Test
    fun locateAppliesValidFixWithZoomFloor() {
        val behavior = behavior(FakeBehaviorSource(seed = fix()))
        val outcome = behavior.locate(11.5, 30.0)
        assertTrue(outcome is LocateOutcome.Applied)
        val intent = (outcome as LocateOutcome.Applied).intent
        assertEquals(45.0, intent.center.latitude, 0.0)
        assertEquals(15.0, intent.zoom, 0.0)
        assertEquals(30.0, intent.bearing, 0.0)
        assertFalse(behavior.isPendingRecenter())
    }

    @Test
    fun locateWhileAcquiringPendsThenRecovers() {
        val fake = FakeBehaviorSource()
        val service = LocationService(source = fake)
        service.start()
        val behavior = MapBehavior(service)
        assertEquals(LocateOutcome.Pending, behavior.locate(11.5, 0.0))
        assertTrue(behavior.isPendingRecenter())
        assertNull(behavior.onLocationUpdate(11.5, 0.0))
        assertTrue(behavior.isPendingRecenter())
        fake.emit(fix())
        val intent = behavior.onLocationUpdate(11.5, 0.0)
        assertEquals(45.0, intent?.center?.latitude)
        assertEquals(15.0, intent?.zoom)
        assertFalse(behavior.isPendingRecenter())
        assertNull(behavior.onLocationUpdate(11.5, 0.0))
    }

    @Test
    fun locateWhileDeniedIsIgnoredWithoutPending() {
        val behavior = behavior(
            FakeBehaviorSource(
                query = AtlasLocationQuery(
                    permission = AtlasLocationPermission.denied,
                    servicesEnabled = true,
                ),
            ),
        )
        assertEquals(LocateOutcome.Ignored, behavior.locate(11.5, 0.0))
        assertFalse(behavior.isPendingRecenter())
    }

    @Test
    fun updateWithoutPendingDoesNothing() {
        val behavior = behavior(FakeBehaviorSource(seed = fix()))
        assertNull(behavior.onLocationUpdate(11.5, 0.0))
    }
}
