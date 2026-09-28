// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
            val crosshairSize = 40.dp.toPx()

            drawLine(
                color = hudColor,
                start = Offset(centerX - crosshairSize, centerY),
                end = Offset(centerX - crosshairSize * 0.3f, centerY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = hudColor,
                start = Offset(centerX + crosshairSize * 0.3f, centerY),
                end = Offset(centerX + crosshairSize, centerY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = hudColor,
                start = Offset(centerX, centerY - crosshairSize),
                end = Offset(centerX, centerY - crosshairSize * 0.3f),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = hudColor,
                start = Offset(centerX, centerY + crosshairSize * 0.3f),
                end = Offset(centerX, centerY + crosshairSize),
                strokeWidth = 2.dp.toPx()
            )
        }

        if (!showReadout) return@Canvas
        if (bearing == null) return@Canvas

        val readoutTopPadding = topPadding.toPx()

        val bearingLayout = textMeasurer.measure(
            text = TacticalMath.formatBearing(bearing),
            style = TextStyle(color = hudColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )
        drawText(
            textLayoutResult = bearingLayout,
            topLeft = Offset(centerX - (bearingLayout.size.width / 2f), readoutTopPadding)
        )

        val milsText = if (frameLabel == null) {
            "${TacticalMath.degreesToMils(bearing)} mils"
        } else {
            "${TacticalMath.degreesToMils(bearing)} mils $frameLabel"
        }
        val milsLayout = textMeasurer.measure(
            text = milsText,
            style = TextStyle(color = hudColor, fontSize = 14.sp)
        )
        drawText(
            textLayoutResult = milsLayout,
            topLeft = Offset(
                centerX - (milsLayout.size.width / 2f),
                readoutTopPadding + bearingLayout.size.height + 4.dp.toPx()
            )
        )
    }
}
