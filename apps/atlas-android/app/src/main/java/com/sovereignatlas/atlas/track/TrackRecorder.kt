// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.location.AtlasLocationFix
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.location.LocationService

enum class TrackRecorderState { idle, recording }

class TrackRecorder(private val location: LocationService) {
    private var state = TrackRecorderState.idle
    private val log = ArrayList<AtlasLocationFix>()
    private var lastAppended: AtlasLocationFix? = null
    private var listening = false
    private var disposed = false
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun state(): TrackRecorderState = state

    fun isRecording(): Boolean = state == TrackRecorderState.recording

    fun pointCount(): Int = log.size

    fun points(): List<AtlasLocationFix> = log.toList()

    fun start() {
        if (disposed || state == TrackRecorderState.recording) return
        log.clear()
        lastAppended = null
        state = TrackRecorderState.recording
        if (!listening) {
            location.addListener(::onLocation)
            listening = true
        }
        ingestCurrent()
        notifyListeners()
    }

    fun stop(): List<AtlasLocationFix> {
        if (state == TrackRecorderState.idle) return emptyList()
        detach()
        val fixes = log.toList()
        log.clear()
        lastAppended = null
        state = TrackRecorderState.idle
        notifyListeners()
        return fixes
    }

    fun dispose() {
        disposed = true
        detach()
    }

    private fun detach() {
        if (listening) {
            location.removeListener(::onLocation)
            listening = false
        }
    }

    private fun onLocation() {
        if (disposed || state != TrackRecorderState.recording) return
        ingestCurrent()
        notifyListeners()
    }

    private fun ingestCurrent() {
        if (location.status() != AtlasLocationStatus.valid) return
        val fix = location.latestFixOrNull() ?: return
        if (fix === lastAppended) return
        log.add(fix)
        lastAppended = fix
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener()
        }
    }
}
