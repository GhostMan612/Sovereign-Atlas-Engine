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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.field.FieldJournal
import com.sovereignatlas.atlas.track.TrackRecorder
import com.sovereignatlas.atlas.track.formatTrackDistance
import com.sovereignatlas.atlas.track.formatTrackDuration
import com.sovereignatlas.atlas.track.formatTrackStart
import com.sovereignatlas.atlas.track.trackLengthMeters

@Composable
fun TracksDialog(
    journal: FieldJournal,
    recorder: TrackRecorder,
    onOpenDetail: (String) -> Unit,
    onClose: () -> Unit,
) {
    val error = journal.lastErrorOrNull()
    val records = journal.tracks()
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
                                if (fixes.isNotEmpty()) {
                                    journal.saveTrack(fixes)
                                }
                            },
                            modifier = Modifier.testTag("track-stop"),
                        ) {
                            Text("Stop")
                        }
                    }
                }
                if (error != null) {
                    Text("Journal unavailable: $error")
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
                                    Text("${record.id} · ${record.pointCount} pts")
                                },
                                supportingContent = {
                                    Text(
                                        formatTrackDistance(
                                            trackLengthMeters(record.points),
                                        ),
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
    journal: FieldJournal,
    id: String,
    onClose: () -> Unit,
) {
    val record = journal.lookupTrack(id)
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
    val points = record.points
    val durationMs = if (points.isEmpty()) {
        0L
    } else {
        points.last().createdAt - points.first().createdAt
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(record.id) },
        text = {
            Column {
                Text("Points: ${record.pointCount}")
                Text(
                    "Distance: ${formatTrackDistance(trackLengthMeters(points))}",
                )
                Text("Started: ${formatTrackStart(record.createdAt)}")
                Text("Duration: ${formatTrackDuration(durationMs)}")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    journal.removeTrack(id)
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
