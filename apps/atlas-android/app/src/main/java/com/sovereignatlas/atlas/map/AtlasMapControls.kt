// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AtlasRailPillButton(
    label: String,
    stateDesc: String,
    @DrawableRes iconRes: Int? = null,
    active: Boolean = false,
    pulse: Boolean = false,
    reducedMotion: Boolean = false,
    onClick: () -> Unit,
) {
    val animMs = if (reducedMotion) 0 else 180
    val fill by animateColorAsState(
        targetValue = if (active) Color(0xE6162E3D) else Color(0xE6141C24),
        animationSpec = tween(animMs),
        label = "railPillFill",
    )
    val border by animateColorAsState(
        targetValue = if (active) Color(0xFF39C5F3) else Color(0x55A8B5C1),
        animationSpec = tween(animMs),
        label = "railPillBorder",
    )
    val content by animateColorAsState(
        targetValue = if (active) Color(0xFF39C5F3) else Color.White,
        animationSpec = tween(animMs),
        label = "railPillContent",
    )
    val beaconAlpha = if (pulse && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "railBeacon")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "railBeaconAlpha",
        )
        animated
    } else {
        1f
    }
    Surface(
        onClick = onClick,
        color = fill,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, border),
        shadowElevation = 4.dp,
        modifier = Modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 40.dp)
            .semantics { stateDescription = stateDesc },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier
                        .size(16.dp)
                        .alpha(if (pulse) beaconAlpha else 1f),
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
        }
    }
}

@Composable
fun WorkspaceQuickChip(
    label: String,
    active: Boolean,
    reducedMotion: Boolean = false,
    onClick: () -> Unit,
) {
    val animMs = if (reducedMotion) 0 else 160
    val fill by animateColorAsState(
        targetValue = if (active) Color(0xFF1B3A4B) else Color(0xFF1B2631),
        animationSpec = tween(animMs),
        label = "chipFill",
    )
    val border by animateColorAsState(
        targetValue = if (active) Color(0xFF39C5F3) else Color(0x33A8B5C1),
        animationSpec = tween(animMs),
        label = "chipBorder",
    )
    val content by animateColorAsState(
        targetValue = if (active) Color(0xFF39C5F3) else Color(0xFFA8B5C1),
        animationSpec = tween(animMs),
        label = "chipContent",
    )
    Surface(
        onClick = onClick,
        color = fill,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun ToolRow(
    label: String,
    description: String,
    @DrawableRes iconRes: Int? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = Color(0xFF1B2631),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (active) Color(0x8039C5F3) else Color(0x26A8B5C1),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .semantics { stateDescription = description },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = if (active) Color(0xFF39C5F3) else Color(0xFFA8B5C1),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color(0xFFA8B5C1),
                )
            }
        }
    }
}
