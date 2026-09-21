// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.sovereignatlas.atlas.field.FieldJournal
import com.sovereignatlas.atlas.goto.GoToState
import com.sovereignatlas.atlas.location.AndroidLocationSource
import com.sovereignatlas.atlas.location.LocationService
import com.sovereignatlas.atlas.map.AtlasMapScreen
import com.sovereignatlas.atlas.map.MapBehavior
import com.sovereignatlas.atlas.measure.MeasureState
import com.sovereignatlas.atlas.track.TrackRecorder
import org.maplibre.android.MapLibre

final class MainActivity : ComponentActivity() {
    private val locationSource by lazy { AndroidLocationSource(this) }
    private val locationService by lazy { LocationService(locationSource) }
    private val journal by lazy {
        FieldJournal(directoryProvider = { filesDir })
    }
    private val recorder by lazy { TrackRecorder(locationService) }
    private val goTo by lazy { GoToState() }
    private val measure by lazy { MeasureState() }
    private val behavior by lazy { MapBehavior(locationService) }

    private val services by lazy {
        AtlasServices(
            location = locationService,
            journal = journal,
            recorder = recorder,
            goTo = goTo,
            measure = measure,
            behavior = behavior,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        journal.restore()
        setContent {
            MaterialTheme {
                AtlasMapScreen(services)
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
        super.onDestroy()
    }
}
