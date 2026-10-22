// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.Mgrs
import com.sovereignatlas.atlas.geo.navigation.TravelProfile
import com.sovereignatlas.atlas.goto.GoToState
import java.util.Locale

@Composable
fun GoToCard(
    goTo: GoToState,
    usableFix: AtlasCoordinate?,
    onClear: () -> Unit,
    onNavigate: (() -> Unit)? = null,
    navigationMessage: String? = null,
    selectedProfile: TravelProfile? = null,
    onProfileSelected: ((TravelProfile) -> Unit)? = null,
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
        Text("MGRS: ${Mgrs.format(target.latitude, target.longitude)}")
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
        if (selectedProfile != null && onProfileSelected != null) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (profile in TravelProfile.values()) {
                    if (profile == selectedProfile) {
                        Button(
                            onClick = { onProfileSelected(profile) },
                            modifier = Modifier.testTag("go-to-profile-${profile.name.lowercase()}"),
                        ) {
                            Text(profile.name)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onProfileSelected(profile) },
                            modifier = Modifier.testTag("go-to-profile-${profile.name.lowercase()}"),
                        ) {
                            Text(profile.name)
                        }
                    }
                }
            }
        }
        if (navigationMessage != null) {
            Text(
                text = navigationMessage,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp).testTag("go-to-nav-message"),
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onClear,
                modifier = Modifier.testTag("go-to-clear"),
            ) {
                Text("Clear")
            }
            if (onNavigate != null) {
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.testTag("go-to-navigate"),
                ) {
                    Text("Navigate")
                }
            }
        }
    }
}
