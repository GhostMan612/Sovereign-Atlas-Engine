// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sovereignatlas.atlas.MainActivity
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.track.TrackRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TrackRecordingService : Service() {
    companion object {
        const val NOTIFICATION_ID = 7001
        const val CHANNEL_ID = "atlas-track-recording"
        const val EXTRA_TRACK_ID = "com.sovereignatlas.atlas.TRACK_ID"

        // In-process session handle: the UI reads this on stop to flush the
        // buffer. The service runs in the app process (no :remote).
        @Volatile
        var currentTrackId: String? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationManager: LocationManager? = null
    private var repository: TrackRepository? = null
    private var trackId: String? = null
    private val sequence = AtomicLong(0L)

    private val listener = LocationListener { location ->
        onLocation(location)
    }

    override fun onCreate() {
        super.onCreate()
        // Notification first: startForeground has a ~5s deadline.
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Track recording",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording track")
            .setContentText("Sovereign Atlas is capturing GPS fixes.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(content)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // START_STICKY restart without an explicit start: never record a
            // ghost session.
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent.getStringExtra(EXTRA_TRACK_ID) ?: UUID.randomUUID().toString()
        trackId = id
        currentTrackId = id
        sequence.set(0L)
        ensureRepository()
        locationManager =
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager).also { manager ->
                try {
                    manager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                } catch (error: SecurityException) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            locationManager?.removeUpdates(listener)
        } catch (error: Exception) {
            Unit
        }
        locationManager = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureRepository() {
        if (repository != null) return
        val driver = AndroidSqliteDriver(AtlasDatabase.Schema, applicationContext, "atlas.db")
        repository = TrackRepository(AtlasDatabase(driver))
    }

    private fun onLocation(location: Location) {
        val id = trackId ?: return
        val repo = repository ?: return
        val seq = sequence.getAndIncrement()
        val time = if (location.time > 0L) location.time else System.currentTimeMillis()
        scope.launch {
            repo.insertBufferedPoint(
                trackId = id,
                sequence = seq,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                timestamp = time,
            )
        }
    }
}
