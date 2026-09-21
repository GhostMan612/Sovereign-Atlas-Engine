// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.tactical.AtlasRadioLink
import java.util.Locale

@Composable
fun LinkDialog(
    pointA: AtlasCoordinate?,
    pointB: AtlasCoordinate?,
    aLabel: String,
    bLabel: String,
    onClose: () -> Unit,
) {
    val frequency = remember { mutableStateOf("2442.0") }
    val tx = remember { mutableStateOf("20.0") }
    val sensitivity = remember { mutableStateOf("-90.0") }
    val gain = remember { mutableStateOf("0.0") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Radio Link") },
        text = {
            Column {
                Text("A ($aLabel): ${decimalOf(pointA)}")
                Text("B ($bLabel): ${decimalOf(pointB)}")
                OutlinedTextField(
                    value = frequency.value,
                    onValueChange = { frequency.value = it },
                    label = { Text("Frequency MHz") },
                    modifier = Modifier.testTag("link-frequency"),
                )
                OutlinedTextField(
                    value = tx.value,
                    onValueChange = { tx.value = it },
                    label = { Text("TX power dBm") },
                    modifier = Modifier.testTag("link-tx"),
                )
                OutlinedTextField(
                    value = sensitivity.value,
                    onValueChange = { sensitivity.value = it },
                    label = { Text("RX sensitivity dBm") },
                    modifier = Modifier.testTag("link-sensitivity"),
                )
                OutlinedTextField(
                    value = gain.value,
                    onValueChange = { gain.value = it },
                    label = { Text("Antenna gain dBi") },
                    modifier = Modifier.testTag("link-gain"),
                )
                Text(linkVerdict(pointA, pointB, frequency.value, tx.value, sensitivity.value, gain.value))
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
    )
}

private fun decimalOf(point: AtlasCoordinate?): String {
    if (point == null) return "unavailable"
    return String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude)
}

fun linkVerdict(
    pointA: AtlasCoordinate?,
    pointB: AtlasCoordinate?,
    frequencyMHz: String,
    txDbm: String,
    sensitivityDbm: String,
    gainDbi: String,
): String {
    if (pointA == null || pointB == null) return "Endpoints unavailable."
    val distance = AtlasRadioLink.rangeMeters(pointA, pointB)
    if (distance <= 0.0) return "Endpoints coincide."
    val frequency = frequencyMHz.toDoubleOrNull()
    val tx = txDbm.toDoubleOrNull()
    val sensitivity = sensitivityDbm.toDoubleOrNull()
    val gain = gainDbi.toDoubleOrNull()
    if (frequency == null || tx == null || sensitivity == null || gain == null) {
        return "Enter numeric radio parameters."
    }
    return try {
        val loss = AtlasRadioLink.freeSpaceLossDb(distance, frequency)
        val margin = AtlasRadioLink.linkMarginDb(
            distanceMeters = distance,
            frequencyMHz = frequency,
            txPowerDbm = tx,
            rxSensitivityDbm = sensitivity,
            antennaGainDbi = gain,
        )
        val verdict = if (margin >= 0.0) "MARGIN OK" else "NO MARGIN"
        String.format(
            Locale.US,
            "Distance: %.0f m • Loss: %.1f dB • Margin: %.1f dB • %s",
            distance,
            loss,
            margin,
            verdict,
        )
    } catch (error: IllegalArgumentException) {
        "Distance and frequency must be positive."
    }
}
