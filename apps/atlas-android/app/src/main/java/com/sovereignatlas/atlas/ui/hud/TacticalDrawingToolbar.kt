// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.geo.graphics.DrawingMode

private fun DrawingMode.label(): String = when (this) {
    DrawingMode.NONE -> "DRAW"
    DrawingMode.TACTICAL_LINE -> "LINE"
    DrawingMode.MEDEVAC_ZONE -> "MEDEVAC"
    DrawingMode.RESTRICTED_ZONE -> "RESTRICT"
}

@Composable
fun TacticalDrawingToolbar(
    currentMode: DrawingMode,
    pointCount: Int,
    onModeSelected: (DrawingMode) -> Unit,
    onUndo: () -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collapsed by design: the three modes are mutually exclusive choices, so one
    // button that shows the active mode and expands on tap replaces the permanent
    // three-button row that used to cover the bottom of the glass.
    var expanded by remember { mutableStateOf(false) }
    val accent = Color(0xFF39FF14)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (currentMode != DrawingMode.NONE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = "Pts: $pointCount", color = accent)
                Button(
                    onClick = onUndo,
                    enabled = pointCount > 0,
                    contentPadding = ButtonDefaults.ContentPadding,
                ) { Text("UNDO") }
                Button(
                    onClick = onCommit,
                    enabled = (currentMode == DrawingMode.TACTICAL_LINE && pointCount >= 2) ||
                        (currentMode != DrawingMode.TACTICAL_LINE && pointCount >= 3)
                ) { Text("COMMIT") }
            }
        }
        Button(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentMode == DrawingMode.NONE) Color.DarkGray else accent
            )
        ) {
            Text(
                text = currentMode.label() + if (expanded) " ▲" else " ▼",
                color = if (currentMode == DrawingMode.NONE) Color.White else Color.Black
            )
        }
        if (expanded) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ModeButton("LINE", currentMode == DrawingMode.TACTICAL_LINE) {
                    onModeSelected(DrawingMode.TACTICAL_LINE)
                    expanded = false
                }
                ModeButton("MEDEVAC", currentMode == DrawingMode.MEDEVAC_ZONE) {
                    onModeSelected(DrawingMode.MEDEVAC_ZONE)
                    expanded = false
                }
                ModeButton("RESTRICT", currentMode == DrawingMode.RESTRICTED_ZONE) {
                    onModeSelected(DrawingMode.RESTRICTED_ZONE)
                    expanded = false
                }
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
