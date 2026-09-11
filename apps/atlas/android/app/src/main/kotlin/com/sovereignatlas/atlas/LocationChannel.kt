// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
package com.sovereignatlas.atlas

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class LocationChannel(private val activity: Activity) :
    MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private var permissionResult: MethodChannel.Result? = null
    private var fixSink: EventChannel.EventSink? = null
    private var manager: LocationManager? = null
    private var requestedBefore = false

    private val listener = LocationListener { location ->
        fixSink?.success(fixMap(location))
    }

    fun attach(executor: DartExecutor) {
        MethodChannel(executor.binaryMessenger, METHOD).setMethodCallHandler(this)
        EventChannel(executor.binaryMessenger, STREAM).setStreamHandler(this)
    }

    fun detach() {
        stopUpdates()
        permissionResult = null
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val pending = permissionResult
        permissionResult = null
        pending?.success(statusMap())
        return true
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getStatus" -> result.success(statusMap())
            "requestPermission" -> requestPermission(result)
            "openAppSettings" -> result.success(openSettings())
            "getLastKnownFix" -> result.success(lastKnownMap())
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        fixSink = events
        startUpdates()
    }

    override fun onCancel(arguments: Any?) {
        fixSink = null
        stopUpdates()
    }

    private fun serviceManager(): LocationManager? {
        return activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private fun statusMap(): Map<String, Any> {
        return mapOf(
            "permission" to permissionName(),
            "servicesEnabled" to servicesEnabled()
        )
    }

    private fun permissionName(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "granted"
        val fine = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine || coarse) return "granted"
        if (!requestedBefore) return "notRequested"
        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return "denied"
        }
        return "permanentlyDenied"
    }

    private fun servicesEnabled(): Boolean {
        val mgr = serviceManager() ?: return false
        return try {
            mgr.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    private fun requestPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || permissionName() == "granted") {
            result.success(statusMap())
            return
        }
        if (permissionResult != null) {
            result.success(statusMap())
            return
        }
        permissionResult = result
        requestedBefore = true
        activity.requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_CODE
        )
    }

    private fun openSettings(): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun startUpdates() {
        if (permissionName() != "granted") {
            fixSink?.error("permission_required", "Location permission not granted", null)
            return
        }
        val mgr = serviceManager() ?: return
        manager = mgr
        try {
            mgr.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, listener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Unit
        }
        try {
            mgr.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, UPDATE_INTERVAL_MS, 0f, listener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Unit
        }
    }

    private fun stopUpdates() {
        try {
            manager?.removeUpdates(listener)
        } catch (e: Exception) {
            Unit
        }
        manager = null
    }

    private fun lastKnownMap(): Map<String, Any?>? {
        if (permissionName() != "granted") return null
        val mgr = serviceManager() ?: return null
        val fixes = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).mapNotNull { provider ->
            try {
                mgr.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            }
        }
        return fixes.maxByOrNull { it.time }?.let { fixMap(it) }
    }

    private fun fixMap(location: Location): Map<String, Any?> {
        return mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to if (location.hasAccuracy()) location.accuracy else null,
            "speed" to if (location.hasSpeed()) location.speed else null,
            "bearing" to if (location.hasBearing()) location.bearing else null,
            "time" to location.time,
            "provider" to location.provider
        )
    }

    companion object {
        const val METHOD = "com.sovereignatlas.atlas/location"
        const val STREAM = "com.sovereignatlas.atlas/location_stream"
        const val REQUEST_CODE = 2101
        const val UPDATE_INTERVAL_MS = 2000L
    }
}
