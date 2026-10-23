// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.core.CompassMath
import kotlin.math.roundToInt

@Composable
fun CompassTape(
    bearing: Double,
    targetBearing: Double? = null,
    targetLabel: String? = null,
    courseDeviationDeg: Double? = null,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
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
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "compassTapeBearing",
    )
    val effectiveBearing = if (reducedMotion) {
        (bearing / 5.0).roundToInt() * 5.0
    } else {
        (((animatedBearing.toDouble() % 360.0) + 360.0) % 360.0)
    }
    val backgroundColor = Color(0xFF10151C).copy(alpha = 0.85f)
    val chromeMajor = Color(0xFFA8B5C1)
    val chromeMinor = Color(0xFF6B7D8D)
    val navCyan = Color(0xFF39C5F3)
    val lubberColor = Color(0xFFFF4444)
    val cdiColor = Color(0xFFF4B85C)

    val textMeasurer = rememberTextMeasurer()
    val cardinalStyle = TextStyle(
        color = chromeMajor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
    val targetStyle = TextStyle(
        color = navCyan,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )

    val semanticDescription = buildString {
        append("Heading ")
        append(effectiveBearing.roundToInt())
        append(" degrees")
        if (targetBearing != null) {
            append(", target ")
            if (targetLabel != null) append(targetLabel).append(" ")
            append("at ")
            append(targetBearing.roundToInt())
            append(" degrees")
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(backgroundColor)
            .semantics { contentDescription = semanticDescription }
    ) {
        val fov = 90.0
        val pixelsPerDegree = size.width / fov
        val halfWidth = size.width / 2f
        val startDeg = kotlin.math.floor(effectiveBearing - fov / 2).toInt()
        val endDeg = kotlin.math.ceil(effectiveBearing + fov / 2).toInt()

        for (deg in startDeg..endDeg) {
            if (deg % 5 == 0) {
                val xOffset = ((deg - (effectiveBearing - fov / 2)) * pixelsPerDegree).toFloat()
                val isMajor = deg % 45 == 0
                val isMinor = deg % 15 == 0
                val tickHeight = when {
                    isMajor -> size.height * 0.38f
                    isMinor -> size.height * 0.22f
                    else -> size.height * 0.12f
                }
                val tickColor = if (isMajor) chromeMajor else chromeMinor
                val strokeWidth = if (isMajor) 2.5f else 1.2f

                drawLine(
                    color = tickColor,
                    start = Offset(xOffset, 0f),
                    end = Offset(xOffset, tickHeight),
                    strokeWidth = strokeWidth,
                )

                if (isMajor) {
                    val label = CompassMath.getLabel(deg)
                    val layout = textMeasurer.measure(text = label, style = cardinalStyle)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = xOffset - (layout.size.width / 2f),
                            y = size.height - layout.size.height - 4f,
                        ),
                    )
                }
            }
        }

        if (targetBearing != null) {
            val rawDiff = (targetBearing - effectiveBearing + 540.0) % 360.0 - 180.0
            if (rawDiff in -45.0..45.0) {
                val targetX = (halfWidth + rawDiff * pixelsPerDegree).toFloat()
                val targetPath = Path().apply {
                    moveTo(targetX, size.height * 0.45f)
                    lineTo(targetX - 7f, size.height * 0.45f + 10f)
                    lineTo(targetX, size.height * 0.45f + 20f)
                    lineTo(targetX + 7f, size.height * 0.45f + 10f)
                    close()
                }
                drawPath(path = targetPath, color = navCyan)
                val labelText = targetLabel ?: "TGT"
                val layout = textMeasurer.measure(text = labelText, style = targetStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = (targetX - layout.size.width / 2f).coerceIn(4f, size.width - layout.size.width - 4f),
                        y = 2f,
                    ),
                )
            } else if (rawDiff < -45.0) {
                val labelText = "◀ ${targetLabel ?: "TGT"}"
                val layout = textMeasurer.measure(text = labelText, style = targetStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x = 6f, y = size.height / 2f - layout.size.height / 2f),
                )
            } else {
                val labelText = "${targetLabel ?: "TGT"} ▶"
                val layout = textMeasurer.measure(text = labelText, style = targetStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = size.width - layout.size.width - 6f,
                        y = size.height / 2f - layout.size.height / 2f,
                    ),
                )
            }
        }

        if (courseDeviationDeg != null) {
            val clampedCdi = courseDeviationDeg.coerceIn(-20.0, 20.0)
            val cdiX = (halfWidth + (clampedCdi / 20.0) * (size.width * 0.25f)).toFloat()
            drawLine(
                color = cdiColor,
                start = Offset(cdiX, size.height * 0.65f),
                end = Offset(cdiX, size.height * 0.95f),
                strokeWidth = 2.0f,
            )
        }

        val centerPath = Path().apply {
            moveTo(halfWidth - 9f, 0f)
            lineTo(halfWidth + 9f, 0f)
            lineTo(halfWidth, 14f)
            close()
        }
        drawPath(path = centerPath, color = lubberColor)
    }
}
