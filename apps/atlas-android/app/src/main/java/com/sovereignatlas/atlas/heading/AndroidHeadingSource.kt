// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.heading

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

final class AndroidHeadingSource(
    context: Context,
    private val declinationDeg: () -> Double = { 0.0 },
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : HeadingSource {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var sampleListener: ((HeadingSample) -> Unit)? = null
    private var errorListener: ((Throwable) -> Unit)? = null
    private var updatesStarted = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            try {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val magnetic =
                    ((Math.toDegrees(orientation[0].toDouble()) % 360.0) + 360.0) % 360.0
                sampleListener?.invoke(
                    HeadingSample(
                        magnetic = AtlasHeading(
                            degrees = magnetic,
                            source = "rotation-vector",
                        ),
                        accuracy = mapAccuracy(event.accuracy),
                        receivedAtMs = clockMs(),
                        trueNorthDeg = normalize(magnetic + declinationDeg()),
                        sensorTimeNanos = event.timestamp,
                    ),
                )
            } catch (error: Exception) {
                errorListener?.invoke(error)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun querySupported(): Boolean = rotationVector != null

    override fun setSampleListener(listener: ((HeadingSample) -> Unit)?) {
        sampleListener = listener
        if (listener != null) {
            startUpdates()
        } else if (errorListener == null) {
            stopUpdates()
        }
    }

    override fun setErrorListener(listener: ((Throwable) -> Unit)?) {
        errorListener = listener
    }

    override fun setDoneListener(listener: (() -> Unit)?) = Unit

    fun stopAll() {
        sampleListener = null
        errorListener = null
        stopUpdates()
    }

    private fun startUpdates() {
        val sensor = rotationVector ?: return
        if (updatesStarted) return
        updatesStarted = sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_UI,
        )
    }

    private fun stopUpdates() {
        if (!updatesStarted) return
        updatesStarted = false
        sensorManager.unregisterListener(listener)
    }

    private fun normalize(degrees: Double): Double {
        return ((degrees % 360.0) + 360.0) % 360.0
    }

    private fun mapAccuracy(accuracy: Int): HeadingAccuracy {
        return when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> HeadingAccuracy.high
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> HeadingAccuracy.medium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> HeadingAccuracy.low
            SensorManager.SENSOR_STATUS_UNRELIABLE -> HeadingAccuracy.unreliable
            else -> HeadingAccuracy.unknown
        }
    }
}
