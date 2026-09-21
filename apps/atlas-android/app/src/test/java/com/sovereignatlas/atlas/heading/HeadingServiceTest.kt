// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.heading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

final class FakeHeadingSource(var supported: Boolean = true) : HeadingSource {
    var subscribeCount = 0
    private var sampleListener: ((HeadingSample) -> Unit)? = null

    override fun querySupported(): Boolean = supported

    override fun setSampleListener(listener: ((HeadingSample) -> Unit)?) {
        if (listener != null) subscribeCount++
        sampleListener = listener
    }

    override fun setErrorListener(listener: ((Throwable) -> Unit)?) = Unit

    override fun setDoneListener(listener: (() -> Unit)?) = Unit

    fun emit(sample: HeadingSample) {
        sampleListener?.invoke(sample)
    }
}

private fun sample(
    magnetic: Double = 90.0,
    trueNorth: Double? = 91.0,
    accuracy: HeadingAccuracy = HeadingAccuracy.high,
): HeadingSample {
    return HeadingSample(
        magnetic = AtlasHeading(degrees = magnetic, source = "rotation-vector"),
        accuracy = accuracy,
        receivedAtMs = 1L,
        trueNorthDeg = trueNorth,
    )
}

final class HeadingServiceTest {
    @Test
    fun unsupportedNeverSubscribes() {
        val fake = FakeHeadingSource(supported = false)
        val service = HeadingService(fake)
        service.ensureStarted()
        assertTrue(service.isUnsupported())
        assertEquals(0, fake.subscribeCount)
        assertFalse(service.isActive())
    }

    @Test
    fun supportedStartsAndIngests() {
        val fake = FakeHeadingSource()
        val service = HeadingService(fake)
        service.ensureStarted()
        fake.emit(sample())
        assertEquals(91.0, service.displayDeg())
        assertEquals("TRUE", service.frameLabel())
        assertTrue(service.isActive())
    }

    @Test
    fun magneticFallbackLabelsMag() {
        val fake = FakeHeadingSource()
        val service = HeadingService(fake)
        service.start()
        fake.emit(sample(magnetic = 45.0, trueNorth = null))
        assertEquals(45.0, service.displayDeg())
        assertEquals("MAG", service.frameLabel())
    }

    @Test
    fun dimmingMatrix() {
        val fake = FakeHeadingSource()
        val service = HeadingService(fake)
        service.start()
        fake.emit(sample(accuracy = HeadingAccuracy.high))
        assertFalse(service.isDimmed())
        fake.emit(sample(accuracy = HeadingAccuracy.medium))
        assertFalse(service.isDimmed())
        fake.emit(sample(accuracy = HeadingAccuracy.low))
        assertTrue(service.isDimmed())
        fake.emit(sample(accuracy = HeadingAccuracy.unreliable))
        assertTrue(service.isDimmed())
    }

    @Test
    fun noSampleMeansUnknownAndDimmed() {
        val service = HeadingService(FakeHeadingSource())
        assertEquals(HeadingAccuracy.unknown, service.accuracy())
        assertNull(service.displayDeg())
        assertTrue(service.isDimmed())
    }

    @Test
    fun startIsIdempotentAndStopDetaches() {
        val fake = FakeHeadingSource()
        val service = HeadingService(fake)
        service.start()
        service.start()
        assertEquals(1, fake.subscribeCount)
        service.stop()
        assertFalse(service.isActive())
        fake.emit(sample())
        assertNull(service.displayDeg())
    }

    @Test
    fun headingNormalizes() {
        assertEquals(350.0, AtlasHeading(degrees = -10.0).normalized, 0.0)
        assertEquals(10.0, AtlasHeading(degrees = 370.0).normalized, 0.0)
    }
}
