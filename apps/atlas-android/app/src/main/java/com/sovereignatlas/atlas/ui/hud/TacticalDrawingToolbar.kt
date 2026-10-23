// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.geo.graphics.DrawingMode

private fun DrawingMode.label(): String = when (this) {
    DrawingMode.NONE -> "DRAW"
    DrawingMode.TACTICAL_LINE -> "LINE"
    DrawingMode.MEDEVAC_ZONE -> "MEDEVAC"
    DrawingMode.RESTRICTED_ZONE -> "RESTRICT"
}

@DrawableRes
private fun DrawingMode.iconRes(): Int = when (this) {
    DrawingMode.NONE -> R.drawable.ic_draw_pen
    DrawingMode.TACTICAL_LINE -> R.drawable.ic_draw_line
    DrawingMode.MEDEVAC_ZONE -> R.drawable.ic_draw_medevac
    DrawingMode.RESTRICTED_ZONE -> R.drawable.ic_draw_restrict
}

@Composable
fun TacticalDrawingToolbar(
    currentMode: DrawingMode,
    pointCount: Int,
    onModeSelected: (DrawingMode) -> Unit,
    onUndo: () -> Unit,
    onCommit: () -> Unit,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeCyan = Color(0xFF39C5F3)
    val charcoalSurface = Color(0xE6141C24)
    val chromeBorder = Color(0x55A8B5C1)
    val animMs = if (reducedMotion) 60 else 180
    val triggerFill by animateColorAsState(
        targetValue = if (currentMode == DrawingMode.NONE) charcoalSurface else activeCyan,
        animationSpec = tween(animMs),
        label = "drawTriggerFill",
    )
    val triggerContent = if (currentMode == DrawingMode.NONE) Color.White else Color(0xFF10151C)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedVisibility(
            visible = currentMode != DrawingMode.NONE,
            enter = fadeIn(tween(animMs)) + expandVertically(tween(animMs)),
            exit = fadeOut(tween(animMs)) + shrinkVertically(tween(animMs)),
        ) {
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
                containerColor = triggerFill,
            ),
            modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 36.dp),
        ) {
            Icon(
                painter = painterResource(currentMode.iconRes()),
                contentDescription = null,
                tint = triggerContent,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = currentMode.label() + if (expanded) " ▲" else " ▼",
                color = triggerContent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(animMs)) + expandVertically(tween(animMs)),
            exit = fadeOut(tween(animMs)) + shrinkVertically(tween(animMs)),
        ) {
            Surface(
                color = charcoalSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, chromeBorder),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(6.dp),
                ) {
                    ModeButton(
                        label = "LINE",
                        iconRes = R.drawable.ic_draw_line,
                        selected = currentMode == DrawingMode.TACTICAL_LINE,
                        animMs = animMs,
                    ) {
                        onModeSelected(DrawingMode.TACTICAL_LINE)
                        expanded = false
                    }
                    ModeButton(
                        label = "MEDEVAC",
                        iconRes = R.drawable.ic_draw_medevac,
                        selected = currentMode == DrawingMode.MEDEVAC_ZONE,
                        animMs = animMs,
                    ) {
                        onModeSelected(DrawingMode.MEDEVAC_ZONE)
                        expanded = false
                    }
                    ModeButton(
                        label = "RESTRICT",
                        iconRes = R.drawable.ic_draw_restrict,
                        selected = currentMode == DrawingMode.RESTRICTED_ZONE,
                        animMs = animMs,
                    ) {
                        onModeSelected(DrawingMode.RESTRICTED_ZONE)
                        expanded = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    animMs: Int,
    onClick: () -> Unit,
) {
    val fill by animateColorAsState(
        targetValue = if (selected) Color(0xFF39C5F3) else Color(0xFF1B2631),
        animationSpec = tween(animMs),
        label = "modeButtonFill",
    )
    val content = if (selected) Color(0xFF10151C) else Color.White
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.defaultMinSize(minWidth = 68.dp, minHeight = 32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = fill),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = content,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
