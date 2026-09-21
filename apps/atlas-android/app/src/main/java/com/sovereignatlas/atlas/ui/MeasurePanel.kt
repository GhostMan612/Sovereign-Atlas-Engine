// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.measure.MeasureSnapshot
import com.sovereignatlas.atlas.measure.MeasureUnit
import com.sovereignatlas.atlas.measure.labelOf

@Composable
fun MeasurePanel(
    snapshot: MeasureSnapshot,
    units: List<MeasureUnit>,
    onUnitSelected: (MeasureUnit) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag("measure-panel")
            .padding(16.dp),
    ) {
        Row {
            Text(
                text = "Measure",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onClose,
                modifier = Modifier.testTag("measure-close"),
            ) {
                Text(text = "Close")
            }
        }
        Text(text = snapshot.pointALabel ?: "A: waiting")
        Text(text = snapshot.pointBLabel ?: "B: tap the map to set point B")
        Row {
            Text(text = snapshot.distanceText ?: "Distance: —")
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    val next = units[(units.indexOfFirst {
                        snapshot.unitLabel == labelOf(it)
                    } + 1) % units.size]
                    onUnitSelected(next)
                },
                modifier = Modifier.testTag("measure-unit"),
            ) {
                Text(text = snapshot.unitLabel)
            }
        }
        Text(text = snapshot.bearingText ?: "Bearing: undefined")
        Button(
            onClick = onClear,
            modifier = Modifier.testTag("measure-clear"),
        ) {
            Text(text = "Clear measurement")
        }
    }
}
