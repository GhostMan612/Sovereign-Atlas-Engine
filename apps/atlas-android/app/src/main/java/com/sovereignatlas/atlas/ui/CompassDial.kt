// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.heading.HeadingService

@Composable
fun CompassDial(
    heading: HeadingService,
    orientToken: String,
    onFaceNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val degrees = heading.displayDeg()
    if (degrees == null) {
        Text(
            text = orientToken,
            fontSize = 10.sp,
            modifier = modifier.testTag("compass-off"),
        )
        return
    }
    Column(
        modifier = modifier.testTag("compass"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .alpha(if (heading.isDimmed()) 0.4f else 1.0f)
                .clickable(onClick = onFaceNorth),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "N",
                fontSize = 24.sp,
                modifier = Modifier.graphicsLayer {
                    rotationZ = -degrees.toFloat()
                },
            )
        }
        Text(text = heading.frameLabel(), fontSize = 10.sp)
        Text(text = orientToken, fontSize = 10.sp)
    }
}
