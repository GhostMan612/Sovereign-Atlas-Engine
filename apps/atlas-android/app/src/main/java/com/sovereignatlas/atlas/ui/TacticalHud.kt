// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
    // Translucent scrim instead of an opaque white box: readable over any basemap
    // without the oversized panel that dominated the lower-left corner.
    Surface(color = Color.Black.copy(alpha = 0.45f)) {
        Text(
            text = mgrsText,
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun TacticalCrosshair(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(
            text = "+",
            fontSize = 20.sp,
            color = Color(0xFF39FF14),
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
    val widthPx = nice / metersPerPixel
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = scaleLabel(nice), fontSize = 10.sp)
        Box(
            modifier = Modifier.width(widthPx.dp).height(4.dp)
                .background(Color(0xFF39FF14)),
        )
    }
}
