// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.goto.GoToState
import java.util.Locale

@Composable
fun GoToCard(
    goTo: GoToState,
    usableFix: AtlasCoordinate?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = goTo.targetOrNull() ?: return
    val distanceKm = goTo.distanceKmTo(usableFix)
    val bearingDeg = goTo.bearingDegTo(usableFix)
    Column(
        modifier = modifier
            .testTag("go-to-card")
            .padding(12.dp),
    ) {
        Text("GO-TO ${target.label}")
        Text(
            String.format(
                Locale.US,
                "%.4f, %.4f",
                target.latitude,
                target.longitude,
            ),
        )
        Text(
            if (distanceKm == null) {
                "Distance: unavailable"
            } else {
                "Distance: ${AtlasGeoMath.formatDistance(distanceKm)}"
            },
        )
        Text(
            if (bearingDeg == null) {
                "Bearing: unavailable"
            } else {
                AtlasGeoMath.formatBearing(bearingDeg)
            },
        )
        Button(
            onClick = onClear,
            modifier = Modifier.testTag("go-to-clear"),
        ) {
            Text("Clear")
        }
    }
}
