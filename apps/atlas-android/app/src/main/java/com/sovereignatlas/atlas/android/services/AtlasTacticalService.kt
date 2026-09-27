// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sovereignatlas.atlas.AtlasApplication
import com.sovereignatlas.atlas.MainActivity
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.android.location.AndroidLocationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class AtlasTacticalService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val services by lazy { (application as AtlasApplication).services }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var serviceRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (!startForegroundSafely()) return

        val prefs = getSharedPreferences(AndroidLocationEngine.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(AndroidLocationEngine.KEY_WAS_RECORDING, false)) {
            val trackId = prefs.getString(AndroidLocationEngine.KEY_ACTIVE_TRACK_ID, null)
            if (trackId != null) {
                runCatching { services.locationEngine.resumeRecording(trackId) }
                    .onFailure { error ->
                        Log.e("AtlasTacticalService", "Failed to resume recording", error)
                        prefs.edit().putBoolean(AndroidLocationEngine.KEY_WAS_RECORDING, false).apply()
                    }
            } else {
                prefs.edit().putBoolean(AndroidLocationEngine.KEY_WAS_RECORDING, false).apply()
            }
        }

        runCatching { services.locationEngine.startPassiveListening() }
        runCatching { services.multicastListener.startListening(serviceScope) }

        serviceScope.launch {
            services.locationEngine.currentLocation.collect { geoPoint ->
                if (geoPoint != null) {
                    services.atakBroadcaster.broadcastPli(
                        lat = geoPoint.latitude,
                        lon = geoPoint.longitude,
                        hae = geoPoint.altitude,
                        ce = 10.0,
                        fixTimeMillis = geoPoint.timestamp,
                    )
                }
            }
        }

        serviceScope.launch {
            services.pliStore.activePlis
                .map { it.size }
                .distinctUntilChanged()
                .drop(1)
                .collect { allyCount ->
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(allyCount))
                }
        }

        serviceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                getSharedPreferences(AndroidLocationEngine.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(AndroidLocationEngine.KEY_WAS_RECORDING, false).apply()

                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val restartIntent = Intent(applicationContext, AtlasTacticalService::class.java)
                val pendingIntent = PendingIntent.getService(
                    applicationContext,
                    2001,
                    restartIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pendingIntent != null) alarmManager.cancel(pendingIntent)

                val prefs = getSharedPreferences(PREFS_SERVICE_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_USER_STOPPED, true).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_RECORDING -> {
                runCatching { services.locationEngine.startRecording() }
                    .onFailure { Log.e("AtlasTacticalService", "startRecording failed", it) }
            }
            ACTION_STOP_RECORDING -> {
                serviceScope.launch {
                    runCatching { services.locationEngine.stopRecording() }
                        .onFailure { Log.e("AtlasTacticalService", "stopRecording failed", it) }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val prefs = getSharedPreferences(PREFS_SERVICE_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_USER_STOPPED, false)) {
            stopSelf()
            return
        }

        val restartIntent = Intent(applicationContext, AtlasTacticalService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            2001,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent,
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (serviceRunning) {
            val userStopped = getSharedPreferences(PREFS_SERVICE_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_STOPPED, false)

            if ((services.locationEngine.isRecording.value || services.locationEngine.isFlushPending) && userStopped) {
                runBlocking {
                    withTimeoutOrNull(5000L) {
                        runCatching { services.locationEngine.stopRecording() }
                    }
                }
            }

            runCatching { services.locationEngine.stopPassiveListening() }
            runCatching { services.multicastListener.stopListening() }
            runCatching { services.locationEngine.shutdown() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(allyCount: Int = 0): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, AtlasTacticalService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1002,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val contentText = if (allyCount > 0) {
            "$allyCount allies tracked · Transmitting"
        } else {
            "Tactical Mesh Active · Transmitting"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sovereign Atlas")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_blue_force_marker)
            .setContentIntent(pendingIntent)
            .addAction(NotificationCompat.Action.Builder(0, "Stop", stopPendingIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun startForegroundSafely(): Boolean {
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            true
        } catch (error: Exception) {
            Log.e("AtlasTacticalService", "Failed to start foreground", error)
            stopSelf()
            false
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tactical Mesh",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "atlas_tactical_channel"
        private const val NOTIFICATION_ID = 1001

        const val PREFS_SERVICE_NAME = "atlas_service_prefs"
        const val KEY_USER_STOPPED = "user_stopped_service"

        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
    }
}
