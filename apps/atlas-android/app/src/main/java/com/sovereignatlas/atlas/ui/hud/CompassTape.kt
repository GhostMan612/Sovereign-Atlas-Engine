// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.core.CompassMath

@Composable
fun CompassTape(bearing: Double, modifier: Modifier = Modifier) {
    val activeColor = Color(0xFF39FF14)
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = activeColor, fontSize = 14.sp)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        val fov = 90.0
        val pixelsPerDegree = size.width / fov

        val startDeg = kotlin.math.floor(bearing - fov / 2).toInt()
        val endDeg = kotlin.math.ceil(bearing + fov / 2).toInt()

        for (i in startDeg..endDeg) {
            if (i % 15 == 0) {
                val xOffset = ((i - (bearing - fov / 2)) * pixelsPerDegree).toFloat()
                val isCardinal = i % 90 == 0
                val tickHeight = if (isCardinal) size.height * 0.4f else size.height * 0.2f

                drawLine(
                    color = activeColor,
                    start = Offset(xOffset, 0f),
                    end = Offset(xOffset, tickHeight),
                    strokeWidth = if (isCardinal) 3f else 1.5f
                )

                if (i % 45 == 0) {
                    val label = CompassMath.getLabel(i)
                    val textLayoutResult = textMeasurer.measure(text = label, style = textStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x = xOffset - (textLayoutResult.size.width / 2f),
                            y = size.height - textLayoutResult.size.height - 5f
                        )
                    )
                }
            }
        }

        val centerPath = Path().apply {
            moveTo(size.width / 2f - 15f, 0f)
            lineTo(size.width / 2f + 15f, 0f)
            lineTo(size.width / 2f, 20f)
            close()
        }
        drawPath(path = centerPath, color = Color.Red)
    }
}
