// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.location.AtlasLocationFix
import com.sovereignatlas.atlas.location.AtlasLocationPermission
import com.sovereignatlas.atlas.location.AtlasLocationQuery
import com.sovereignatlas.atlas.location.AtlasLocationSource
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.location.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val GRANTED = AtlasLocationQuery(
    permission = AtlasLocationPermission.granted,
    servicesEnabled = true,
)

private fun fix(latitude: Double, longitude: Double, atMs: Long): AtlasLocationFix {
    return AtlasLocationFix(
        position = AtlasCoordinate(latitude = latitude, longitude = longitude),
        atMs = atMs,
        source = "gps",
    )
}

private final class FakeTrackSource(
    var query: AtlasLocationQuery = GRANTED,
    var seed: AtlasLocationFix? = null,
) : AtlasLocationSource {
    private var fixListener: ((AtlasLocationFix) -> Unit)? = null
    private var errorListener: ((Throwable) -> Unit)? = null

    override fun queryStatus(): AtlasLocationQuery = query

    override fun requestPermission(): AtlasLocationQuery = query

    override fun openAppSettings(): Boolean = true

    override fun lastKnownFix(): AtlasLocationFix? = seed

    override fun setFixListener(listener: ((AtlasLocationFix) -> Unit)?) {
        fixListener = listener
    }

    override fun setErrorListener(listener: ((Throwable) -> Unit)?) {
        errorListener = listener
    }

    override fun setDoneListener(listener: (() -> Unit)?) = Unit

    fun emit(value: AtlasLocationFix) {
        fixListener?.invoke(value)
    }

    fun emitError(error: Throwable) {
        errorListener?.invoke(error)
    }
}

final class TrackRecorderTest {
    private var nowMs = 1_000L

    private fun service(fake: FakeTrackSource, staleAfterMs: Long = 30_000L): LocationService {
        return LocationService(
            source = fake,
            clockMs = { nowMs },
            staleAfterMs = staleAfterMs,
        )
    }

    private fun started(fake: FakeTrackSource): LocationService {
        val target = service(fake)
        target.start()
        return target
    }

    @Test
    fun initialStateIsIdleWithNoPoints() {
        val fake = FakeTrackSource()
        val target = service(fake)
        val recorder = TrackRecorder(target)
        assertEquals(TrackRecorderState.idle, recorder.state())
        assertFalse(recorder.isRecording())
        assertEquals(0, recorder.pointCount())
        assertTrue(recorder.points().isEmpty())
        assertTrue(recorder.stop().isEmpty())
        assertFalse(recorder.isRecording())
    }

    @Test
    fun startEntersRecordingAndDoubleStartAttachesOnce() {
        val fake = FakeTrackSource()
        val target = started(fake)
        val recorder = TrackRecorder(target)
        var notifications = 0
        recorder.addListener { notifications++ }
        recorder.start()
        recorder.start()
        assertTrue(recorder.isRecording())
        assertEquals(TrackRecorderState.recording, recorder.state())
        fake.emit(fix(45.0, -93.0, 1000L))
        assertEquals(1, recorder.pointCount())
        assertTrue(notifications >= 2)
    }

    @Test
    fun startSeedsTheCurrentValidFix() {
        val fake = FakeTrackSource()
        val target = started(fake)
        fake.emit(fix(45.0, -93.0, 1000L))
        val recorder = TrackRecorder(target)
        recorder.start()
        assertEquals(1, recorder.pointCount())
        assertEquals(45.0, recorder.points().single().position.latitude, 0.0)
    }

    @Test
    fun validEventsAppendInOrderWithFullFidelity() {
        val fake = FakeTrackSource()
        val target = started(fake)
        val recorder = TrackRecorder(target)
        recorder.start()
        fake.emit(fix(45.0, -93.0, 1000L))
        fake.emit(fix(45.1, -93.1, 2000L))
        fake.emit(fix(45.1, -93.1, 3000L))
        assertEquals(3, recorder.pointCount())
        assertEquals(1000L, recorder.points()[0].atMs)
        assertEquals(2000L, recorder.points()[1].atMs)
        assertEquals(3000L, recorder.points()[2].atMs)
    }

    @Test
    fun identicalRenotificationIsNotANewPoint() {
        val fake = FakeTrackSource()
        val target = started(fake)
        val recorder = TrackRecorder(target)
        recorder.start()
        val current = fix(45.0, -93.0, 1000L)
        fake.emit(current)
        fake.emit(current)
        assertEquals(1, recorder.pointCount())
    }

    @Test
    fun staleFlipWithoutNewFixAppendsNothing() {
        val fake = FakeTrackSource()
        val target = service(fake, staleAfterMs = 50L)
        target.start()
        val recorder = TrackRecorder(target)
        recorder.start()
        fake.emit(fix(45.0, -93.0, 1000L))
        assertEquals(1, recorder.pointCount())
        nowMs += 120L
        assertEquals(AtlasLocationStatus.stale, target.status())
        assertEquals(1, recorder.pointCount())
    }

    @Test
    fun nonValidStatesRecordNothing() {
        val fake = FakeTrackSource(
            query = AtlasLocationQuery(
                permission = AtlasLocationPermission.denied,
                servicesEnabled = true,
            ),
        )
        val target = service(fake)
        val recorder = TrackRecorder(target)
        recorder.start()
        target.refreshStatus()
        assertEquals(AtlasLocationStatus.denied, target.status())
        assertEquals(0, recorder.pointCount())
        assertTrue(recorder.points().isEmpty())
    }

    @Test
    fun acquiringServiceYieldsNoFabricatedPoints() {
        val fake = FakeTrackSource()
        val target = service(fake)
        val recorder = TrackRecorder(target)
        recorder.start()
        target.refreshStatus()
        assertEquals(AtlasLocationStatus.acquiring, target.status())
        assertEquals(0, recorder.pointCount())
    }

    @Test
    fun stopReturnsOrderedFixesAndDetaches() {
        val fake = FakeTrackSource()
        val target = started(fake)
        val recorder = TrackRecorder(target)
        recorder.start()
        fake.emit(fix(45.0, -93.0, 1000L))
        fake.emit(fix(45.1, -93.1, 2000L))
        val fixes = recorder.stop()
        assertFalse(recorder.isRecording())
        assertEquals(2, fixes.size)
        assertEquals(1000L, fixes[0].atMs)
        assertEquals(2000L, fixes[1].atMs)
        assertEquals(0, recorder.pointCount())
        fake.emit(fix(45.2, -93.2, 3000L))
        assertEquals(0, recorder.pointCount())
        assertEquals(45.2, target.latestFixOrNull()?.position?.latitude)
    }

    @Test
    fun zeroPointStopReturnsEmpty() {
        val fake = FakeTrackSource()
        val target = started(fake)
        val recorder = TrackRecorder(target)
        recorder.start()
        assertTrue(recorder.stop().isEmpty())
        assertFalse(recorder.isRecording())
    }

    @Test
    fun disposeDetachesWithoutCrashing() {
        val fake = FakeTrackSource()
        val target = started(fake)
        val recorder = TrackRecorder(target)
        recorder.start()
        recorder.dispose()
        fake.emit(fix(45.0, -93.0, 1000L))
        assertEquals(0, recorder.pointCount())
    }

    @Test
    fun streamErrorSurfacesOnServiceWhileRecorderStaysSafe() {
        val fake = FakeTrackSource()
        val target = started(fake)
        val recorder = TrackRecorder(target)
        recorder.start()
        fake.emitError(IllegalStateException("sensor lost"))
        assertEquals(AtlasLocationStatus.error, target.status())
        assertEquals(0, recorder.pointCount())
        fake.emit(fix(45.0, -93.0, 1000L))
        assertEquals(1, recorder.pointCount())
    }
}
