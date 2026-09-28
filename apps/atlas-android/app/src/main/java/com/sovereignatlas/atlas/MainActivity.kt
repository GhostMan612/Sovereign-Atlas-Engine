// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sovereignatlas.atlas.android.services.AtlasTacticalService
import com.sovereignatlas.atlas.goto.GoToState
import com.sovereignatlas.atlas.heading.AndroidHeadingSource
import com.sovereignatlas.atlas.heading.HeadingService
import com.sovereignatlas.atlas.location.AndroidLocationSource
import com.sovereignatlas.atlas.location.LocationService
import com.sovereignatlas.atlas.map.AtlasMapScreen
import com.sovereignatlas.atlas.map.MapBehavior
import com.sovereignatlas.atlas.measure.MeasureState
import com.sovereignatlas.atlas.track.TrackRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import org.maplibre.android.MapLibre

final class MainActivity : ComponentActivity() {
    private val appServices get() = (application as AtlasApplication).services

    private val locationSource by lazy { AndroidLocationSource(this) }
    private val locationService by lazy { LocationService(locationSource) }
    private val recorder by lazy { TrackRecorder(locationService) }
    private val goTo by lazy { GoToState() }
    private val measure by lazy { MeasureState() }
    private val behavior by lazy { MapBehavior(locationService) }
    private val headingService by lazy {
        HeadingService(
            AndroidHeadingSource(
                this,
                declinationDeg = {
                    locationService.latestFixOrNull()?.position?.let { position ->
                        GeomagneticField(
                            position.latitude.toFloat(),
                            position.longitude.toFloat(),
                            0f,
                            System.currentTimeMillis(),
                        ).declination.toDouble()
                    } ?: 0.0
                },
            ),
        )
    }

    // No ViewModel in this host: splash hold is a plain activity-owned flag.
    // Flipped once the MapLibre style is loaded; offline restore is
    // synchronous in onCreate so packs are initialized by then.
    private val mapReady = MutableStateFlow(false)

    private val services by lazy {
        AtlasServices(
            location = locationService,
            recorder = recorder,
            goTo = goTo,
            measure = measure,
            behavior = behavior,
            offline = appServices.offline,
            heading = headingService,
            tiles = appServices.tiles,
            keys = appServices.keys,
            maps = appServices.maps,
            database = appServices.database,
            waypointRepository = appServices.waypointRepository,
            trackRepository = appServices.trackRepository,
            imageDecoder = appServices.imageDecoder,
            scrubState = appServices.scrubState,
            losState = appServices.losState,
            routing = appServices.routing,
            pli = appServices.pliStore,
            messages = appServices.messageStore,
            markers = appServices.markerStore,
            settingsRepository = appServices.settingsRepository,
            locationEngine = appServices.locationEngine,
            multicastListener = appServices.multicastListener,
            atakBroadcaster = appServices.atakBroadcaster,
        )
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> requestBatteryExemptionAndStartService() }

    fun initializeTacticalMesh() {
        getSharedPreferences(
            AtlasTacticalService.PREFS_SERVICE_NAME,
            android.content.Context.MODE_PRIVATE,
        ).edit().putBoolean(AtlasTacticalService.KEY_USER_STOPPED, false).apply()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationService.requestPermission()
            return
        }
        requestNotificationStep()
    }

    private fun requestNotificationStep() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestBatteryExemptionAndStartService()
        }
    }

    private fun requestBatteryExemptionAndStartService() {
        val power = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!power.isIgnoringBatteryOptimizations(packageName)) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            ).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        try {
            val serviceIntent = android.content.Intent(
                this,
                AtlasTacticalService::class.java,
            )
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (error: Exception) {
            android.util.Log.e("MainActivity", "Failed to start tactical service", error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !mapReady.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        initializeTacticalMesh()
        setContent {
            MaterialTheme {
                AtlasMapScreen(
                    services,
                    onFirstStyle = { mapReady.value = true },
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AndroidLocationSource.REQUEST_CODE) {
            locationSource.onPermissionResult(
                grantResults.any { it == PackageManager.PERMISSION_GRANTED },
            )
            locationService.refreshStatus()
            requestNotificationStep()
        }
    }

    override fun onDestroy() {
        recorder.dispose()
        locationService.dispose()
        headingService.dispose()
        super.onDestroy()
    }
}
