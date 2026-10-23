// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.geo.TacticalMath

@Composable
fun TacNavHud(
    bearing: Float?,
    hudColor: Color,
    frameLabel: String? = null,
    showReticle: Boolean = true,
    showReadout: Boolean = true,
    fillScreen: Boolean = true,
    topPadding: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = if (fillScreen) modifier.fillMaxSize() else modifier) {
        val centerX = size.width / 2f
        if (showReticle) {
            val centerY = size.height / 2f
            val outerRadius = 24.dp.toPx()
            val innerGap = 7.dp.toPx()
            val tickHalf = 3.5.dp.toPx()
            val haloColor = Color(0x99000000)
            val haloStroke = 3.5.dp.toPx()
            val mainStroke = 1.6.dp.toPx()

            val segments = listOf(
                Offset(centerX - outerRadius, centerY) to Offset(centerX - innerGap, centerY),
                Offset(centerX + innerGap, centerY) to Offset(centerX + outerRadius, centerY),
                Offset(centerX, centerY - outerRadius) to Offset(centerX, centerY - innerGap),
                Offset(centerX, centerY + innerGap) to Offset(centerX, centerY + outerRadius),
                Offset(centerX - outerRadius, centerY - tickHalf) to Offset(centerX - outerRadius, centerY + tickHalf),
                Offset(centerX + outerRadius, centerY - tickHalf) to Offset(centerX + outerRadius, centerY + tickHalf),
                Offset(centerX - tickHalf, centerY - outerRadius) to Offset(centerX + tickHalf, centerY - outerRadius),
                Offset(centerX - tickHalf, centerY + outerRadius) to Offset(centerX + tickHalf, centerY + outerRadius),
            )
            for ((startPt, endPt) in segments) {
                drawLine(
                    color = haloColor,
                    start = startPt,
                    end = endPt,
                    strokeWidth = haloStroke,
                )
            }
            drawCircle(
                color = haloColor,
                radius = 3.dp.toPx(),
                center = Offset(centerX, centerY),
            )
            for ((startPt, endPt) in segments) {
                drawLine(
                    color = hudColor,
                    start = startPt,
                    end = endPt,
                    strokeWidth = mainStroke,
                )
            }
            drawCircle(
                color = hudColor,
                radius = 1.8.dp.toPx(),
                center = Offset(centerX, centerY),
            )
        }

        if (!showReadout) return@Canvas
        if (bearing == null) return@Canvas

        val bearingLayout = textMeasurer.measure(
            text = "${TacticalMath.formatBearing(bearing)}\u00B0",
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
        )
        val milsText = if (frameLabel == null) {
            "${TacticalMath.degreesToMils(bearing)} mils"
        } else {
            "${TacticalMath.degreesToMils(bearing)} mils $frameLabel"
        }
        val milsLayout = textMeasurer.measure(
            text = milsText,
            style = TextStyle(
                color = hudColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            ),
        )

        val contentWidth = maxOf(bearingLayout.size.width, milsLayout.size.width).toFloat()
        val padH = 10.dp.toPx()
        val padV = 5.dp.toPx()
        val gapV = 2.dp.toPx()
        val boxWidth = (contentWidth + padH * 2f).coerceAtMost(size.width)
        val boxHeight = bearingLayout.size.height + milsLayout.size.height + padV * 2f + gapV
        val boxLeft = ((size.width - boxWidth) / 2f).coerceAtLeast(0f)
        val boxTop = if (fillScreen) topPadding.toPx() else ((size.height - boxHeight) / 2f).coerceAtLeast(0f)
        val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        drawRoundRect(
            color = Color(0xD910151C),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxWidth, boxHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color(0x4DA8B5C1),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxWidth, boxHeight),
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx()),
        )

        val boxCenterX = boxLeft + boxWidth / 2f
        drawText(
            textLayoutResult = bearingLayout,
            topLeft = Offset(boxCenterX - (bearingLayout.size.width / 2f), boxTop + padV),
        )
        drawText(
            textLayoutResult = milsLayout,
            topLeft = Offset(
                boxCenterX - (milsLayout.size.width / 2f),
                boxTop + padV + bearingLayout.size.height + gapV,
            ),
        )
    }
}
