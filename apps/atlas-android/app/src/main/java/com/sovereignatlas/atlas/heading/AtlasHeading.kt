// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.heading

data class AtlasHeading(
    val degrees: Double,
    val source: String = "",
) {
    val normalized: Double
        get() {
            var value = degrees % 360.0
            if (value < 0.0) value += 360.0
            return value
        }
}

enum class HeadingAccuracy { unknown, high, medium, low, unreliable }

data class HeadingSample(
    val magnetic: AtlasHeading,
    val accuracy: HeadingAccuracy,
    val receivedAtMs: Long,
    val trueNorthDeg: Double? = null,
    val sensorTimeNanos: Long? = null,
)

interface HeadingSource {
    fun querySupported(): Boolean
    fun setSampleListener(listener: ((HeadingSample) -> Unit)?)
    fun setErrorListener(listener: ((Throwable) -> Unit)?)
    fun setDoneListener(listener: (() -> Unit)?)
}

class HeadingService(
    private val source: HeadingSource,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private var supported: Boolean? = null
    private var latest: HeadingSample? = null
    private var lastError: Throwable? = null
    private var subscribed = false
    private var disposed = false
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun supported(): Boolean? = supported

    fun isUnsupported(): Boolean = supported == false

    fun latest(): HeadingSample? = latest

    fun lastError(): Throwable? = lastError

    fun isActive(): Boolean = subscribed

    fun accuracy(): HeadingAccuracy = latest?.accuracy ?: HeadingAccuracy.unknown

    fun displayDeg(): Double? {
        val sample = latest ?: return null
        return sample.trueNorthDeg ?: sample.magnetic.degrees
    }

    fun frameLabel(): String {
        return if (latest?.trueNorthDeg != null) "TRUE" else "MAG"
    }

    fun isDimmed(): Boolean {
        return when (accuracy()) {
            HeadingAccuracy.low,
            HeadingAccuracy.unreliable,
            HeadingAccuracy.unknown,
            -> true
            HeadingAccuracy.high,
            HeadingAccuracy.medium,
            -> false
        }
    }

    fun ensureStarted() {
        try {
            supported = source.querySupported()
        } catch (error: Throwable) {
            fail(error)
            return
        }
        if (disposed || supported != true) {
            notifyListeners()
            return
        }
        start()
    }

    fun start() {
        if (subscribed || disposed) return
        try {
            supported = source.querySupported()
        } catch (error: Throwable) {
            fail(error)
            return
        }
        if (disposed || supported != true) {
            notifyListeners()
            return
        }
        subscribed = true
        source.setSampleListener { sample -> ingest(sample) }
        source.setErrorListener { error -> fail(error) }
        source.setDoneListener {
            fail(IllegalStateException("Heading stream closed"))
        }
    }

    fun stop() {
        subscribed = false
        source.setSampleListener(null)
        source.setErrorListener(null)
        source.setDoneListener(null)
    }

    fun dispose() {
        disposed = true
        stop()
        listeners.clear()
    }

    private fun ingest(sample: HeadingSample) {
        if (disposed) return
        latest = sample
        lastError = null
        notifyListeners()
    }

    private fun fail(error: Throwable) {
        if (disposed) return
        lastError = error
        notifyListeners()
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener()
        }
    }
}
