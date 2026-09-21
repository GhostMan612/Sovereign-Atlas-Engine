// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import com.sovereignatlas.atlas.geo.AtlasCoordinate

final class AndroidLocationSource(
    private val activity: Activity,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : AtlasLocationSource {
    private var requestedBefore = false
    private var lastGrant: Boolean? = null
    private var fixListener: ((AtlasLocationFix) -> Unit)? = null
    private var errorListener: ((Throwable) -> Unit)? = null
    private var doneListener: (() -> Unit)? = null
    private var updatesStarted = false

    private val locationManager: LocationManager =
        activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            fixListener?.invoke(toFix(location))
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun queryStatus(): AtlasLocationQuery {
        val granted = lastGrant == true || hasFinePermission()
        val permission = when {
            granted -> AtlasLocationPermission.granted
            !requestedBefore -> AtlasLocationPermission.notRequested
            !showsRationale() -> AtlasLocationPermission.permanentlyDenied
            else -> AtlasLocationPermission.denied
        }
        return AtlasLocationQuery(
            permission = permission,
            servicesEnabled = providersEnabled(),
        )
    }

    override fun requestPermission(): AtlasLocationQuery {
        if (hasFinePermission()) {
            lastGrant = true
            return queryStatus()
        }
        requestedBefore = true
        activity.requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            REQUEST_CODE,
        )
        return queryStatus()
    }

    fun onPermissionResult(granted: Boolean) {
        lastGrant = granted
        if (granted) requestedBefore = true
    }

    override fun openAppSettings(): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null),
        )
        activity.startActivity(intent)
        return true
    }

    override fun lastKnownFix(): AtlasLocationFix? {
        if (!hasFinePermission()) return null
        try {
            val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (gps != null) return toFix(gps)
            return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?.let { toFix(it) }
        } catch (error: SecurityException) {
            return null
        }
    }

    override fun setFixListener(listener: ((AtlasLocationFix) -> Unit)?) {
        fixListener = listener
        if (listener != null) {
            startUpdates()
        } else if (errorListener == null && doneListener == null) {
            stopUpdates()
        }
    }

    override fun setErrorListener(listener: ((Throwable) -> Unit)?) {
        errorListener = listener
    }

    override fun setDoneListener(listener: (() -> Unit)?) {
        doneListener = listener
    }

    fun stopAll() {
        fixListener = null
        errorListener = null
        doneListener = null
        stopUpdates()
    }

    private fun hasFinePermission(): Boolean {
        return activity.checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showsRationale(): Boolean {
        return activity.shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    private fun providersEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (error: IllegalArgumentException) {
            false
        }
    }

    private fun startUpdates() {
        if (updatesStarted || !hasFinePermission()) return
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_MIN_MS,
                UPDATE_MIN_M,
                listener,
                Looper.getMainLooper(),
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                UPDATE_MIN_MS,
                UPDATE_MIN_M,
                listener,
                Looper.getMainLooper(),
            )
            updatesStarted = true
        } catch (error: SecurityException) {
            errorListener?.invoke(error)
        } catch (error: IllegalArgumentException) {
            errorListener?.invoke(error)
        }
    }

    private fun stopUpdates() {
        if (!updatesStarted) return
        updatesStarted = false
        try {
            locationManager.removeUpdates(listener)
        } catch (error: Exception) {
            errorListener?.invoke(error)
        }
    }

    private fun toFix(location: Location): AtlasLocationFix {
        return AtlasLocationFix(
            position = AtlasCoordinate(
                latitude = location.latitude,
                longitude = location.longitude,
            ),
            atMs = if (location.time > 0L) location.time else clockMs(),
            accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
            headingDeg = if (location.hasBearing()) location.bearing.toDouble() else null,
            source = location.provider ?: "",
        )
    }

    companion object {
        const val REQUEST_CODE = 7001
        const val UPDATE_MIN_MS = 2000L
        const val UPDATE_MIN_M = 0f
    }
}
