// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.location

import java.util.Timer
import java.util.TimerTask

class LocationService(
    private val source: AtlasLocationSource,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val staleAfterMs: Long = STALE_AFTER_MS,
) {
    private var permission = AtlasLocationPermission.notRequested
    private var servicesEnabled = false
    private var latestFix: AtlasLocationFix? = null
    private var fixReceivedAtMs: Long? = null
    private var lastError: Throwable? = null
    private var subscribed = false
    private var staleTimer: Timer? = null
    private var disposed = false
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun status(): AtlasLocationStatus {
        if (lastError != null) return AtlasLocationStatus.error
        when (permission) {
            AtlasLocationPermission.notRequested -> return AtlasLocationStatus.notRequested
            AtlasLocationPermission.denied -> return AtlasLocationStatus.denied
            AtlasLocationPermission.permanentlyDenied -> return AtlasLocationStatus.permanentlyDenied
            AtlasLocationPermission.granted -> Unit
        }
        if (!servicesEnabled) return AtlasLocationStatus.servicesDisabled
        if (latestFix == null) return AtlasLocationStatus.acquiring
        return if (isStale()) AtlasLocationStatus.stale else AtlasLocationStatus.valid
    }

    fun isStale(): Boolean {
        val received = fixReceivedAtMs ?: return false
        if (latestFix == null) return false
        return clockMs() - received >= staleAfterMs
    }

    fun latestFixOrNull(): AtlasLocationFix? = latestFix

    fun refreshStatus() {
        try {
            applyQuery(source.queryStatus())
        } catch (error: Throwable) {
            fail(error)
        }
    }

    fun requestPermission() {
        try {
            applyQuery(source.requestPermission())
        } catch (error: Throwable) {
            fail(error)
        }
    }

    fun openAppSettings(): Boolean {
        return try {
            source.openAppSettings()
        } catch (error: Throwable) {
            fail(error)
            false
        }
    }

    fun ensureActive() {
        refreshStatus()
        if (disposed) return
        if (permission == AtlasLocationPermission.notRequested) {
            requestPermission()
            if (disposed) return
        }
        if (permission == AtlasLocationPermission.granted) {
            start()
        }
    }

    fun start() {
        if (subscribed || disposed) return
        refreshStatus()
        if (disposed || permission != AtlasLocationPermission.granted) return
        try {
            val seed = source.lastKnownFix()
            if (disposed) return
            if (seed != null) ingest(seed)
            subscribed = true
            source.setFixListener { fix -> ingest(fix) }
            source.setErrorListener { error -> fail(error) }
            source.setDoneListener {
                fail(IllegalStateException("Location stream closed"))
            }
        } catch (error: Throwable) {
            fail(error)
        }
    }

    fun stop() {
        subscribed = false
        source.setFixListener(null)
        source.setErrorListener(null)
        source.setDoneListener(null)
        staleTimer?.cancel()
        staleTimer = null
    }

    fun dispose() {
        disposed = true
        stop()
        listeners.clear()
    }

    private fun applyQuery(query: AtlasLocationQuery) {
        if (disposed) return
        permission = query.permission
        servicesEnabled = query.servicesEnabled
        lastError = null
        notifyListeners()
    }

    private fun ingest(fix: AtlasLocationFix) {
        if (disposed) return
        latestFix = fix
        fixReceivedAtMs = clockMs()
        servicesEnabled = true
        lastError = null
        notifyListeners()
        armStaleTimer(fix)
    }

    private fun fail(error: Throwable) {
        if (disposed) return
        lastError = error
        notifyListeners()
    }

    private fun armStaleTimer(fix: AtlasLocationFix) {
        staleTimer?.cancel()
        staleTimer = null
        val received = fixReceivedAtMs ?: return
        val remaining = staleAfterMs - (clockMs() - received)
        if (remaining <= 0L) return
        val timer = Timer("atlas-stale", true)
        staleTimer = timer
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    if (!disposed && latestFix === fix) notifyListeners()
                }
            },
            remaining,
        )
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener()
        }
    }

    companion object {
        const val STALE_AFTER_MS = 30_000L
    }
}
