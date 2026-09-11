// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
package com.sovereignatlas.atlas

import android.app.Activity
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class HeadingChannel(private val activity: Activity) :
    MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private var sink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rotationSensor: Sensor? = null
    private var active = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.size < 3) return
            val matrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(matrix, orientation)
            val magnetic = normalizeDeg(Math.toDegrees(orientation[0].toDouble()))
            val payload = mapOf(
                "magnetic" to magnetic,
                "true" to trueHeading(magnetic),
                "accuracy" to accuracyName(event.accuracy),
                "timeNanos" to event.timestamp
            )
            mainHandler.post { sink?.success(payload) }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        }
    }

    fun attach(executor: DartExecutor) {
        MethodChannel(executor.binaryMessenger, METHOD).setMethodCallHandler(this)
        EventChannel(executor.binaryMessenger, STREAM).setStreamHandler(this)
    }

    fun detach() {
        stopSensor()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "status" -> result.success(statusMap())
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        sink = events
        startSensor()
    }

    override fun onCancel(arguments: Any?) {
        sink = null
        stopSensor()
    }

    private fun sensorManager(): SensorManager? {
        return activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    private fun supported(): Boolean {
        if (rotationSensor != null) return true
        val manager = sensorManager() ?: return false
        rotationSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        return rotationSensor != null
    }

    private fun statusMap(): Map<String, Any> {
        return mapOf("supported" to supported(), "active" to active)
    }

    private fun startSensor() {
        if (active) return
        val manager = sensorManager() ?: return
        val sensor = if (supported()) rotationSensor else null ?: return
        active = manager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    private fun stopSensor() {
        try {
            sensorManager()?.unregisterListener(listener)
        } catch (e: Exception) {
            Unit
        }
        active = false
    }

    private fun trueHeading(magnetic: Double): Double? {
        return try {
            val manager = activity.getSystemService(Context.LOCATION_SERVICE)
                as? LocationManager ?: return null
            val fix = lastKnown(manager) ?: return null
            val field = GeomagneticField(
                fix.latitude.toFloat(),
                fix.longitude.toFloat(),
                0f,
                fix.time
            )
            normalizeDeg(magnetic + field.declination.toDouble())
        } catch (e: Exception) {
            null
        }
    }

    private fun lastKnown(manager: LocationManager): android.location.Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        for (provider in providers) {
            try {
                val fix = manager.getLastKnownLocation(provider)
                if (fix != null) return fix
            } catch (e: SecurityException) {
                return null
            }
        }
        return null
    }

    private fun accuracyName(accuracy: Int): String {
        return when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "high"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "low"
            else -> "unreliable"
        }
    }

    private fun normalizeDeg(degrees: Double): Double {
        return ((degrees % 360.0) + 360.0) % 360.0
    }

    companion object {
        const val METHOD = "com.sovereignatlas.atlas/heading"
        const val STREAM = "com.sovereignatlas.atlas/heading_stream"
    }
}
