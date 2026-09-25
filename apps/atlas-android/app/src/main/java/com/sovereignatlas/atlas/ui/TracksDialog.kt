// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.track.TrackRepository
import com.sovereignatlas.atlas.track.TrackRecorder
import com.sovereignatlas.atlas.track.exportAllGpx
import com.sovereignatlas.atlas.track.formatTrackDistance
import com.sovereignatlas.atlas.track.formatTrackStart
import com.sovereignatlas.atlas.track.trackGeometryJson
import com.sovereignatlas.atlas.track.trackLengthMeters
import com.sovereignatlas.atlas.track.trackPointCount
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TracksDialog(
    trackRepository: TrackRepository,
    waypointRepository: WaypointRepository,
    recorder: TrackRecorder,
    exportDir: File,
    onOpenDetail: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val records by trackRepository.tracks.collectAsState(initial = emptyList())
    val message = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Tracks") },
        text = {
            Column {
                if (!recorder.isRecording()) {
                    Button(
                        onClick = { recorder.start() },
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
                                val fixes = recorder.stop()
                                if (fixes.size >= 2) {
                                    val coords = fixes.map { fix ->
                                        AtlasCoordinate(
                                            latitude = fix.position.latitude,
                                            longitude = fix.position.longitude,
                                        )
                                    }
                                    val stamp = SimpleDateFormat("HHmmss", Locale.US)
                                        .format(Date())
                                    scope.launch {
                                        trackRepository.saveTrack(
                                            Track(
                                                id = UUID.randomUUID().toString(),
                                                name = "TR-$stamp",
                                                timestamp = System.currentTimeMillis(),
                                                distance_meters = trackLengthMeters(coords),
                                                geometry = trackGeometryJson(coords),
                                            ),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("track-stop"),
                        ) {
                            Text("Stop")
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
