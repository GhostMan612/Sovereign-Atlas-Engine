// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.geo.GeoPoint
import com.sovereignatlas.atlas.geo.location.LocationEngine
import com.sovereignatlas.atlas.track.trackGeometryJson
import com.sovereignatlas.atlas.track.trackLengthMeters
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidLocationEngine(
    private val context: Context,
    private val database: AtlasDatabase,
) : LocationEngine {

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    override val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isFlushPending = AtomicBoolean(false)
    override val isFlushPending: Boolean get() = _isFlushPending.get()

    private var hasPassiveRequest = false
    private val nextSequence = AtomicLong(0L)
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var updatesActive = false

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val locationListener = LocationListener { location ->
        val geoPoint = GeoPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            speed = if (location.hasSpeed()) location.speed else null,
            timestamp = location.time,
        )
        _currentLocation.value = geoPoint

        if (_isRecording.value) {
            val trackId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_TRACK_ID, null) ?: return@LocationListener

            val seq = nextSequence.getAndIncrement()
            writeScope.launch {
                runCatching {
                    database.atlasQueries.insertTrackPoint(
                        track_id = trackId,
                        sequence = seq,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        timestamp = location.time,
                    )
                }.onFailure { Log.e("AndroidLocationEngine", "Buffer insert failed", it) }
            }
        }
    }

    override fun startPassiveListening() {
        if (hasPassiveRequest || isRecording.value) return
        hasPassiveRequest = true
        requestUpdates(5000L, 5f)
    }

    override fun stopPassiveListening() {
        hasPassiveRequest = false
        if (!isRecording.value) {
            removeUpdates()
        }
    }

    override fun startRecording() {
        _startRecordingInternal(UUID.randomUUID().toString())
    }

    override fun resumeRecording(trackId: String) {
        _startRecordingInternal(trackId)
    }

    private fun _startRecordingInternal(trackId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WAS_RECORDING, true)
            .putString(KEY_ACTIVE_TRACK_ID, trackId)
            .apply()

        val maxVal = runCatching {
            database.atlasQueries.getMaxSequence(trackId).executeAsOne().maxSequence
        }.getOrNull() ?: -1L
        nextSequence.set(maxVal + 1L)

        _isRecording.value = true
        requestUpdates(1000L, 0f)
    }

    override suspend fun stopRecording() {
        if (!_isRecording.value) return

        _isRecording.value = false

        withContext(NonCancellable) {
            _isFlushPending.set(true)
            try {
                writeScope.coroutineContext[Job]?.children?.forEach { it.join() }

                val trackId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_ACTIVE_TRACK_ID, null)

                if (trackId != null) {
                    val points = database.atlasQueries.getTrackPoints(trackId).executeAsList()
                    if (points.size >= 2) {
                        val coords = points.map { point ->
                            AtlasCoordinate(
                                latitude = point.latitude,
                                longitude = point.longitude,
                            )
                        }
                        val stamp = java.text.SimpleDateFormat("HHmmss", java.util.Locale.US)
                            .format(java.util.Date())
                        database.atlasQueries.insertTrack(
                            id = UUID.randomUUID().toString(),
                            name = "TR-$stamp",
                            timestamp = System.currentTimeMillis(),
                            distance_meters = trackLengthMeters(coords),
                            geometry = trackGeometryJson(coords),
                        )
                    }
                    database.atlasQueries.clearTrackPoints(trackId)
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_WAS_RECORDING, false)
                        .remove(KEY_ACTIVE_TRACK_ID)
                        .apply()
                }
            } finally {
                _isFlushPending.set(false)
            }
        }

        if (hasPassiveRequest) {
            requestUpdates(5000L, 5f)
        } else {
            removeUpdates()
        }
    }

    override fun shutdown() {
        writeScope.cancel()
    }

    private fun hasFinePermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestUpdates(minTimeMs: Long, minDistanceM: Float) {
        if (!hasFinePermission()) return
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minTimeMs,
                minDistanceM,
                locationListener,
                Looper.getMainLooper(),
            )
            updatesActive = true
        } catch (error: SecurityException) {
            Log.w("AndroidLocationEngine", "Location permission revoked", error)
        } catch (error: IllegalArgumentException) {
            Log.w("AndroidLocationEngine", "Location provider unavailable", error)
        }
    }

    private fun removeUpdates() {
        if (!updatesActive) return
        updatesActive = false
        try {
            locationManager.removeUpdates(locationListener)
        } catch (error: Exception) {
            Log.w("AndroidLocationEngine", "removeUpdates failed", error)
        }
    }

    companion object {
        const val PREFS_NAME = "atlas_location_prefs"
        const val KEY_WAS_RECORDING = "was_recording"
        const val KEY_ACTIVE_TRACK_ID = "active_track_id"
    }
}
