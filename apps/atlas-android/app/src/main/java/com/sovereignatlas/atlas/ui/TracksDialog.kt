// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.android.services.AtlasTacticalService
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.geo.DemEngine
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.location.LocationService
import com.sovereignatlas.atlas.track.TrackProfile
import com.sovereignatlas.atlas.track.TrackProfileGenerator
import com.sovereignatlas.atlas.track.TrackRepository
import com.sovereignatlas.atlas.track.TrackRecorder
import com.sovereignatlas.atlas.track.TrackScrubState
import com.sovereignatlas.atlas.track.exportAllGpx
import com.sovereignatlas.atlas.track.formatTrackDistance
import com.sovereignatlas.atlas.track.formatTrackStart
import com.sovereignatlas.atlas.track.trackPointCount
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun isBatteryUnrestricted(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun TracksDialog(
    trackRepository: TrackRepository,
    waypointRepository: WaypointRepository,
    recorder: TrackRecorder,
    locationService: LocationService,
    exportDir: File,
    onOpenDetail: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by trackRepository.tracks.collectAsState(initial = emptyList())
    val message = remember { mutableStateOf("") }
    val batteryRestricted = remember(recorder.isRecording()) {
        !isBatteryUnrestricted(context)
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Tracks") },
        text = {
            Column {
                if (!recorder.isRecording()) {
                    Button(
                        onClick = {
                            when (locationService.status()) {
                                AtlasLocationStatus.valid,
                                AtlasLocationStatus.acquiring,
                                AtlasLocationStatus.stale -> {
                                    if (!hasNotificationPermission(context)) {
                                        (context as? Activity)?.requestPermissions(
                                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                            7002,
                                        )
                                        message.value = "Notifications denied: " +
                                            "recording continues without a visible notification."
                                    }
                                    context.startForegroundService(
                                        Intent(context, AtlasTacticalService::class.java).apply {
                                            action = AtlasTacticalService.ACTION_START_RECORDING
                                        },
                                    )
                                    recorder.start()
                                }
                                AtlasLocationStatus.permanentlyDenied -> {
                                    message.value = "Location permanently denied: " +
                                        "enable it in system settings."
                                }
                                else -> {
                                    locationService.requestPermission()
                                    message.value = "Location permission requested: " +
                                        "tap Start again after granting."
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(16.dp)
                            .testTag("track-start"),
                    ) {
                        Text("Start recording")
                    }
                } else {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Recording • ${recorder.pointCount()} pts",
                            modifier = Modifier.testTag("track-status"),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                recorder.stop()
                                context.startService(
                                    Intent(context, AtlasTacticalService::class.java).apply {
                                        action = AtlasTacticalService.ACTION_STOP_RECORDING
                                    },
                                )
                                message.value = "Stop requested: track saves on flush."
                            },
                            modifier = Modifier.testTag("track-stop"),
                        ) {
                            Text("Stop")
                        }
                    }
                    if (batteryRestricted) {
                        TextButton(
                            onClick = {
                                (context as? Activity)?.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            },
                        ) {
                            Text("Allow unrestricted battery use")
                        }
                    }
                }
                if (records.isEmpty()) {
                    Text(
                        "No tracks yet. Start recording to capture one.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(records, key = { it.id }) { record ->
                            ListItem(
                                headlineContent = {
                                    Text("${record.name.ifEmpty { record.id }} · ${trackPointCount(record.geometry)} pts")
                                },
                                supportingContent = {
                                    Text(
                                        formatTrackDistance(record.distance_meters),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenDetail(record.id) }
                                    .testTag("track-${record.id}"),
                            )
                        }
                    }
                }
                if (message.value.isNotEmpty()) Text(message.value)
                Row {
                    Button(
                        onClick = {
                            scope.launch {
                                message.value = try {
                                    val name =
                                        "atlas-export-${System.currentTimeMillis()}.gpx"
                                    val waypoints = waypointRepository.waypoints.first()
                                    val tracks = trackRepository.tracks.first()
                                    File(exportDir, name).writeText(
                                        exportAllGpx(waypoints, tracks),
                                    )
                                    "Exported $name"
                                } catch (error: Throwable) {
                                    "Export failed: ${error.message}"
                                }
                            }
                        },
                    ) {
                        Text("Export GPX")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
    )
}

@Composable
fun TrackDetailDialog(
    trackRepository: TrackRepository,
    scrubState: TrackScrubState,
    demEngine: DemEngine?,
    id: String,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val records by trackRepository.tracks.collectAsState(initial = emptyList())
    val record = records.firstOrNull { it.id == id }
    if (record == null) {
        AlertDialog(
            onDismissRequest = onClose,
            text = { Text("Track removed.") },
            confirmButton = {
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            },
        )
        return
    }
    val points = trackPointCount(record.geometry)
    var profile by remember(id) { mutableStateOf<TrackProfile?>(null) }
    var profileMissing by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        val result = withContext(Dispatchers.IO) {
            TrackProfileGenerator.generate(record, demEngine)
        }
        profile = result
        profileMissing = result == null
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(record.name.ifEmpty { record.id }) },
        text = {
            Column {
                Text("Points: $points")
                Text(
                    "Distance: ${formatTrackDistance(record.distance_meters)}",
                )
                Text("Started: ${formatTrackStart(record.timestamp)}")
                val current = profile
                if (current != null) {
                    TrackProfileChart(profile = current, scrubState = scrubState)
                } else if (profileMissing) {
                    Text("No elevation data")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        trackRepository.deleteTrack(id)
                    }
                    onClose()
                },
                modifier = Modifier.testTag("track-delete"),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
    )
}
