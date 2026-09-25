// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.track.TrackRepository
import com.sovereignatlas.atlas.goto.GoToState
import com.sovereignatlas.atlas.heading.AndroidHeadingSource
import com.sovereignatlas.atlas.heading.HeadingService
import com.sovereignatlas.atlas.location.AndroidLocationSource
import com.sovereignatlas.atlas.location.LocationService
import com.sovereignatlas.atlas.map.AtlasMapScreen
import com.sovereignatlas.atlas.map.MapBehavior
import com.sovereignatlas.atlas.measure.MeasureState
import com.sovereignatlas.atlas.offline.OfflineStore
import com.sovereignatlas.atlas.offline.PACK_JOURNAL_DIR
import com.sovereignatlas.atlas.offline.PackTileServer
import com.sovereignatlas.atlas.track.TrackRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import org.maplibre.android.MapLibre

final class MainActivity : ComponentActivity() {
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
    private val offline by lazy {
        OfflineStore(directoryProvider = { filesDir })
    }
    private val tileServer by lazy {
        PackTileServer(packsDir = { File(filesDir, PACK_JOURNAL_DIR) })
    }
    private val keyProvider by lazy {
        AndroidKeyProvider(this)
    }
    private val mapRepository by lazy {
        AndroidOfflineMapRepository(applicationContext)
    }
    private val sqlDriver by lazy {
        AndroidSqliteDriver(AtlasDatabase.Schema, applicationContext, "atlas.db")
    }
    private val atlasDatabase by lazy {
        AtlasDatabase(sqlDriver)
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
            offline = offline,
            heading = headingService,
            tiles = tileServer,
            keys = keyProvider,
            maps = mapRepository,
            database = atlasDatabase,
            waypointRepository = WaypointRepository(atlasDatabase),
            trackRepository = TrackRepository(atlasDatabase),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !mapReady.value }
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        offline.restore()
        tileServer.start()
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
        }
    }

    override fun onDestroy() {
        recorder.dispose()
        locationService.dispose()
        headingService.dispose()
        tileServer.stop()
        super.onDestroy()
    }
}
