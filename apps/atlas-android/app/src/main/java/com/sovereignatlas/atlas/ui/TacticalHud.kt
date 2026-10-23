// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.R
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun niceDistance(metersPerPixel: Double, targetPixels: Int = 100): Double {
    val raw = metersPerPixel * targetPixels
    if (raw <= 0.0 || raw.isNaN()) return 0.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val nice = when {
        normalized < 1.5 -> 1.0
        normalized < 3.5 -> 2.0
        normalized < 7.5 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

fun scaleLabel(niceMeters: Double): String {
    if (niceMeters <= 0.0) return ""
    if (niceMeters >= 1000.0) {
        val km = niceMeters / 1000.0
        if (km == floor(km)) return "${km.toInt()} km"
        return "$km km"
    }
    if (niceMeters == floor(niceMeters)) return "${niceMeters.toInt()} m"
    return "$niceMeters m"
}

@Composable
fun MgrsHud(mgrsText: String) {
    if (mgrsText.isEmpty()) return
    Surface(
        color = Color(0xD910151C),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0x4DA8B5C1)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "MGRS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF39C5F3),
            )
            Text(
                text = mgrsText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
            )
        }
    }
}

@Composable
fun HudTelemetryPill(
    text: String,
    textColor: Color = Color(0xFFE2E8F0),
    borderColor: Color = Color(0x40A8B5C1),
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xD910151C),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun TacticalCrosshair(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(
            text = "+",
            fontSize = 20.sp,
            color = Color(0xFF39C5F3),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompassOverlay(
    bearing: Double,
    onLocate: () -> Unit,
    onFaceNorth: () -> Unit,
) {
    Image(
        painter = painterResource(R.drawable.ic_compass_overlay),
        contentDescription = "Compass - tap to locate, long-press to face north",
        modifier = Modifier.size(48.dp)
            .rotate(-bearing.toFloat())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onFaceNorth,
                onClick = onLocate,
            ),
    )
}

@Composable
fun ScaleBar(metersPerPixel: Double, targetPixels: Int = 100) {
    if (metersPerPixel <= 0.0) return
    val nice = niceDistance(metersPerPixel, targetPixels)
    if (nice <= 0.0) return
    val widthPx = (nice / metersPerPixel).coerceIn(28.0, 160.0)
    Surface(
        color = Color(0xD910151C),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0x4DA8B5C1)),
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = scaleLabel(nice),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Box(
                modifier = Modifier
                    .width(widthPx.dp)
                    .height(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(widthPx.dp)
                        .height(2.5.dp)
                        .background(Color(0xFF39C5F3)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(2.dp)
                        .height(6.dp)
                        .background(Color(0xFFA8B5C1)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(2.dp)
                        .height(6.dp)
                        .background(Color(0xFFA8B5C1)),
                )
            }
        }
    }
}
