// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.geo.navigation.ManeuverType
import com.sovereignatlas.atlas.geo.navigation.NavigationUpdate
import com.sovereignatlas.atlas.geo.navigation.RoadRouteResult
import java.util.Locale
import kotlin.math.roundToInt

internal fun maneuverIconRes(type: ManeuverType?): Int {
    return when (type) {
        ManeuverType.DEPART, ManeuverType.CONTINUE_STRAIGHT -> R.drawable.ic_nav_straight
        ManeuverType.TURN_SLIGHT_LEFT -> R.drawable.ic_nav_turn_slight_left
        ManeuverType.TURN_LEFT -> R.drawable.ic_nav_turn_left
        ManeuverType.TURN_SHARP_LEFT -> R.drawable.ic_nav_turn_sharp_left
        ManeuverType.TURN_SLIGHT_RIGHT -> R.drawable.ic_nav_turn_slight_right
        ManeuverType.TURN_RIGHT -> R.drawable.ic_nav_turn_right
        ManeuverType.TURN_SHARP_RIGHT -> R.drawable.ic_nav_turn_sharp_right
        ManeuverType.U_TURN -> R.drawable.ic_nav_uturn
        ManeuverType.ARRIVE -> R.drawable.ic_nav_arrive
        null -> R.drawable.ic_nav_straight
    }
}

@Composable
fun NavigationGuidanceHud(
    navUpdate: NavigationUpdate?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onStopNavigation: () -> Unit,
    activeRoute: RoadRouteResult? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xF0141C24),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 6.dp,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val maneuver = navUpdate?.currentManeuver
                    val iconRes = maneuverIconRes(maneuver?.maneuverType)
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = maneuver?.instruction ?: "Turn",
                        tint = Color(0xFF39C5F3),
                        modifier = Modifier.size(36.dp),
                    )
                    Column {
                        val instruction = maneuver?.instruction ?: "Proceed along route"
                        val distManeuver = navUpdate?.distanceToManeuverMeters?.roundToInt() ?: 0
                        Text(
                            text = if (distManeuver > 0) "In $distManeuver m" else "Now",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF39C5F3),
                        )
                        Text(
                            text = instruction,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onToggleMute) {
                        Text(
                            text = if (isMuted) "Unmute" else "Mute",
                            color = Color(0xFFA8B5C1),
                        )
                    }
                    Button(
                        onClick = onStopNavigation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF4444),
                        ),
                    ) {
                        Text("Stop")
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val distRemainingKm = (navUpdate?.remainingDistanceMeters ?: 0.0) / 1000.0
            val durRemainingMin = (navUpdate?.remainingDurationSeconds ?: 0.0) / 60.0
            val snapPct = navUpdate?.snappedPosition?.confidence?.let { (it * 100.0).roundToInt() }
            val profileLabel = activeRoute?.profile?.name ?: "DRIVING"
            val provenanceLabel = activeRoute?.etaProvenance ?: "Offline road-class speed model"
            Text(
                text = "%.1f km remaining  \u00B7  %.0f min (%s)  \u00B7  %s%s".format(
                    Locale.US,
                    distRemainingKm,
                    durRemainingMin,
                    profileLabel,
                    navUpdate?.status?.name ?: "NAVIGATING",
                    if (snapPct != null) "  \u00B7  Snap $snapPct%" else "",
                ),
                fontSize = 12.sp,
                color = Color(0xFFA8B5C1),
            )
            Text(
                text = "ETA source: $provenanceLabel",
                fontSize = 10.sp,
                color = Color(0xFF6B7D8D),
            )
        }
    }
}
