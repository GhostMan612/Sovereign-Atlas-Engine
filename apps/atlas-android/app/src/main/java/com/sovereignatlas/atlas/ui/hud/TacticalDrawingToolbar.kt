// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var expanded by remember { mutableStateOf(false) }
    val activeCyan = Color(0xFF39C5F3)
    val charcoalSurface = Color(0xE6141C24)
    val chromeBorder = Color(0x55A8B5C1)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (currentMode != DrawingMode.NONE) {
            Surface(
                color = charcoalSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, chromeBorder),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Pts: $pointCount",
                        color = activeCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(
                        onClick = onUndo,
                        enabled = pointCount > 0,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 32.dp),
                    ) {
                        Text("UNDO", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onCommit,
                        enabled = (currentMode == DrawingMode.TACTICAL_LINE && pointCount >= 2) ||
                            (currentMode != DrawingMode.TACTICAL_LINE && pointCount >= 3),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 32.dp),
                    ) {
                        Text("COMMIT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Button(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                if (currentMode == DrawingMode.NONE) chromeBorder else activeCyan,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentMode == DrawingMode.NONE) charcoalSurface else activeCyan,
            ),
            modifier = Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 36.dp),
        ) {
            Text(
                text = currentMode.label() + if (expanded) " ▲" else " ▼",
                color = if (currentMode == DrawingMode.NONE) Color.White else Color(0xFF10151C),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (expanded) {
            Surface(
                color = charcoalSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, chromeBorder),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(6.dp),
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
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.defaultMinSize(minWidth = 68.dp, minHeight = 32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF39C5F3) else Color(0xFF1B2631),
        ),
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF10151C) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
