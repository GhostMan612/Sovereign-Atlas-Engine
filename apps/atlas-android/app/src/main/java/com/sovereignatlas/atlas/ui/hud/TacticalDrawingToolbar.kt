// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.geo.graphics.DrawingMode

@Composable
fun TacticalDrawingToolbar(
    currentMode: DrawingMode,
    pointCount: Int,
    onModeSelected: (DrawingMode) -> Unit,
    onUndo: () -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(0xFF39FF14)

    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        contentColor = Color.White,
        modifier = modifier.wrapContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            ModeButton(
                label = "LINE",
                selected = currentMode == DrawingMode.TACTICAL_LINE,
                onClick = {
                    onModeSelected(
                        if (currentMode == DrawingMode.TACTICAL_LINE) DrawingMode.NONE
                        else DrawingMode.TACTICAL_LINE
                    )
                }
            )
            ModeButton(
                label = "MEDEVAC",
                selected = currentMode == DrawingMode.MEDEVAC_ZONE,
                onClick = {
                    onModeSelected(
                        if (currentMode == DrawingMode.MEDEVAC_ZONE) DrawingMode.NONE
                        else DrawingMode.MEDEVAC_ZONE
                    )
                }
            )
            ModeButton(
                label = "RESTRICT",
                selected = currentMode == DrawingMode.RESTRICTED_ZONE,
                onClick = {
                    onModeSelected(
                        if (currentMode == DrawingMode.RESTRICTED_ZONE) DrawingMode.NONE
                        else DrawingMode.RESTRICTED_ZONE
                    )
                }
            )
            if (currentMode != DrawingMode.NONE) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Pts: $pointCount", color = activeColor)
                Button(onClick = onUndo, enabled = pointCount > 0) { Text("UNDO") }
                Button(
                    onClick = onCommit,
                    enabled = (currentMode == DrawingMode.TACTICAL_LINE && pointCount >= 2) ||
                        (currentMode != DrawingMode.TACTICAL_LINE && pointCount >= 3)
                ) { Text("COMMIT") }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF39FF14) else Color.DarkGray
        )
    ) {
        Text(label, color = if (selected) Color.Black else Color.White)
    }
}
