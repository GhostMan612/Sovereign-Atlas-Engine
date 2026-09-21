// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.location

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val GRANTED = AtlasLocationQuery(
    permission = AtlasLocationPermission.granted,
    servicesEnabled = true,
)

private fun fix(
    latitude: Double = 10.0,
    longitude: Double = 20.0,
): AtlasLocationFix {
    return AtlasLocationFix(
        position = AtlasCoordinate(latitude = latitude, longitude = longitude),
        atMs = 1L,
        source = "gps",
    )
}

final class FakeLocationSource(
    var query: AtlasLocationQuery = AtlasLocationQuery(
        permission = AtlasLocationPermission.notRequested,
        servicesEnabled = false,
    ),
    var requestResult: AtlasLocationQuery = GRANTED,
    var seed: AtlasLocationFix? = null,
    var queryThrows: Boolean = false,
) : AtlasLocationSource {
    var subscribeCount = 0
    private var fixListener: ((AtlasLocationFix) -> Unit)? = null
    private var errorListener: ((Throwable) -> Unit)? = null
    private var doneListener: (() -> Unit)? = null

    override fun queryStatus(): AtlasLocationQuery {
        if (queryThrows) throw IllegalStateException("query failed")
        return query
    }

    override fun requestPermission(): AtlasLocationQuery {
        query = requestResult
        return requestResult
    }

    override fun openAppSettings(): Boolean = true

    override fun lastKnownFix(): AtlasLocationFix? = seed

    override fun setFixListener(listener: ((AtlasLocationFix) -> Unit)?) {
        if (listener != null) subscribeCount++
        fixListener = listener
    }

    override fun setErrorListener(listener: ((Throwable) -> Unit)?) {
        errorListener = listener
    }

    override fun setDoneListener(listener: (() -> Unit)?) {
        doneListener = listener
    }

    fun emit(value: AtlasLocationFix) {
        fixListener?.invoke(value)
    }
}

final class LocationServiceTest {
    private var nowMs = 1_000L

    private fun service(fake: FakeLocationSource): LocationService {
        return LocationService(
            source = fake,
            clockMs = { nowMs },
        )
    }

    @Test
    fun initialStatusIsNotRequested() {
        val fake = FakeLocationSource()
        assertEquals(AtlasLocationStatus.notRequested, service(fake).status())
    }

    @Test
    fun deniedMapsToDenied() {
        val fake = FakeLocationSource(
            query = AtlasLocationQuery(AtlasLocationPermission.denied, true),
        )
        val location = service(fake)
        location.refreshStatus()
        assertEquals(AtlasLocationStatus.denied, location.status())
    }

    @Test
    fun permanentlyDeniedMaps() {
        val fake = FakeLocationSource(
            query = AtlasLocationQuery(AtlasLocationPermission.permanentlyDenied, true),
        )
        val location = service(fake)
        location.refreshStatus()
        assertEquals(AtlasLocationStatus.permanentlyDenied, location.status())
    }

    @Test
    fun grantedWithoutServicesIsDisabled() {
        val fake = FakeLocationSource(
            query = AtlasLocationQuery(AtlasLocationPermission.granted, false),
        )
        val location = service(fake)
        location.refreshStatus()
        assertEquals(AtlasLocationStatus.servicesDisabled, location.status())
    }

    @Test
    fun grantedWithoutFixIsAcquiring() {
        val fake = FakeLocationSource(query = GRANTED)
        val location = service(fake)
        location.refreshStatus()
        assertEquals(AtlasLocationStatus.acquiring, location.status())
    }

    @Test
    fun ingestedFixBecomesValid() {
        val fake = FakeLocationSource(query = GRANTED)
        val location = service(fake)
        location.start()
        fake.emit(fix())
        assertEquals(AtlasLocationStatus.valid, location.status())
        assertEquals(10.0, location.latestFixOrNull()?.position?.latitude)
    }

    @Test
    fun fixAgesIntoStale() {
        val fake = FakeLocationSource(query = GRANTED)
        val location = service(fake)
        location.start()
        fake.emit(fix())
        nowMs += 29_000L
        assertEquals(AtlasLocationStatus.valid, location.status())
        nowMs += 1_000L
        assertEquals(AtlasLocationStatus.stale, location.status())
        assertTrue(location.isStale())
    }

    @Test
    fun queryFailureSurfacesError() {
        val fake = FakeLocationSource(queryThrows = true)
        val location = service(fake)
        location.refreshStatus()
        assertEquals(AtlasLocationStatus.error, location.status())
    }

    @Test
    fun ensureActiveRequestsThenStarts() {
        val fake = FakeLocationSource(
            query = AtlasLocationQuery(AtlasLocationPermission.notRequested, true),
            seed = fix(),
        )
        val location = service(fake)
        location.ensureActive()
        assertEquals(AtlasLocationStatus.valid, location.status())
        assertEquals(1, fake.subscribeCount)
    }

    @Test
    fun startSeedsLastKnown() {
        val fake = FakeLocationSource(query = GRANTED, seed = fix(11.0, 21.0))
        val location = service(fake)
        location.start()
        assertEquals(11.0, location.latestFixOrNull()?.position?.latitude)
        assertEquals(AtlasLocationStatus.valid, location.status())
    }

    @Test
    fun startIsIdempotent() {
        val fake = FakeLocationSource(query = GRANTED)
        val location = service(fake)
        location.start()
        location.start()
        assertEquals(1, fake.subscribeCount)
    }

    @Test
    fun stopDetachesStream() {
        val fake = FakeLocationSource(query = GRANTED)
        val location = service(fake)
        location.start()
        location.stop()
        fake.emit(fix(12.0, 22.0))
        assertNull(location.latestFixOrNull())
        assertEquals(AtlasLocationStatus.acquiring, location.status())
    }

    @Test
    fun startWithoutFixStaysAcquiring() {
        val fake = FakeLocationSource(query = GRANTED)
        val location = service(fake)
        location.start()
        assertEquals(AtlasLocationStatus.acquiring, location.status())
        assertEquals(1, fake.subscribeCount)
    }
}
