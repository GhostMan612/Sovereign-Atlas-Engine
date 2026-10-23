// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.ui.hud.AtlasMotionMath
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
    @DrawableRes iconRes: Int? = null,
    iconTint: Color = textColor,
    pulse: Boolean = false,
    reducedMotion: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val pulseAlpha = if (pulse && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "hudBeacon")
        val animated by transition.animateFloat(
            initialValue = 0.32f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "hudBeaconAlpha",
        )
        animated
    } else {
        1.0f
    }
    val surfaceModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        color = Color(0xD910151C),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = surfaceModifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (pulse) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .alpha(pulseAlpha)
                        .background(textColor, CircleShape),
                )
            }
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
        }
    }
}

@Composable
fun TacticalCrosshair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(28.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val arm = size.minDimension * 0.42f
        val gap = size.minDimension * 0.14f
        val halo = Color(0xB310151C)
        val cyan = Color(0xFF39C5F3)
        drawLine(halo, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = 3.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(halo, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = 3.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(halo, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = 3.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(halo, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = 3.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(cyan, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(cyan, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(cyan, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(cyan, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(cyan, radius = 1.8.dp.toPx(), center = Offset(cx, cy))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompassOverlay(
    bearing: Double,
    onLocate: () -> Unit,
    onFaceNorth: () -> Unit,
    reducedMotion: Boolean = false,
) {
    var unwrappedTarget by remember { mutableFloatStateOf(bearing.toFloat()) }
    val nextTarget = AtlasMotionMath.unwrapBearingTarget(unwrappedTarget, bearing.toFloat())
    SideEffect {
        unwrappedTarget = nextTarget
    }
    val animatedBearing by animateFloatAsState(
        targetValue = nextTarget,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "compassDialBearing",
    )
    Image(
        painter = painterResource(R.drawable.ic_compass_overlay),
        contentDescription = "Compass - tap to locate, long-press to face north",
        modifier = Modifier
            .size(48.dp)
            .rotate(-animatedBearing)
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
