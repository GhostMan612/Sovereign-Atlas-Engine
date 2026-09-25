// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.tactical.RadialFence
import java.util.Locale

@Composable
fun FenceDialog(
    center: AtlasCoordinate?,
    fix: AtlasCoordinate?,
    fence: RadialFence?,
    onSet: (RadialFence) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val radius = remember(fence) { mutableStateOf(fence?.radiusMeters?.toString() ?: "1000") }
    val message = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Geofence (session)") },
        text = {
            Column {
                Text("Center (map): ${decimalOf(center)}")
                Text("Status: ${fenceStatus(fence, fix)}")
                OutlinedTextField(
                    value = radius.value,
                    onValueChange = { radius.value = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Radius meters") },
                    modifier = Modifier.testTag("fence-radius"),
                )
                if (message.value.isNotEmpty()) Text(message.value)
                Row {
                    Button(
                        onClick = {
                            val at = center
                            val parsed = radius.value.toDoubleOrNull()
                            if (at == null || parsed == null || parsed <= 0.0) {
                                message.value = "Need map center and positive radius."
                                return@Button
                            }
                            onSet(RadialFence(center = at, radiusMeters = parsed))
                            message.value = ""
                        },
                        modifier = Modifier.testTag("fence-set"),
                    ) {
                        Text(if (fence == null) "Set" else "Move")
                    }
                    TextButton(
                        onClick = {
                            val current = fence
                            if (current != null) {
                                onSet(current.copy(armed = !current.armed))
                            }
                        },
                        modifier = Modifier.testTag("fence-arm"),
                    ) {
                        Text(if (fence?.armed == true) "Disarm" else "Arm")
                    }
                    TextButton(
                        onClick = {
                            onClear()
                            message.value = ""
                        },
                        modifier = Modifier.testTag("fence-clear"),
                    ) {
                        Text("Clear")
                    }
                }
                Text("Session fence — not persisted.")
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

fun fenceStatus(fence: RadialFence?, fix: AtlasCoordinate?): String {
    if (fence == null) return "none"
    if (!fence.armed) return "DISARMED"
    if (fix == null) return "NO FIX"
    return if (fence.breachedBy(fix)) "INSIDE" else "OUTSIDE"
}
