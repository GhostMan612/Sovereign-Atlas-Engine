// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.geo.LoSMode
import com.sovereignatlas.atlas.geo.los.LoSStatus
import com.sovereignatlas.atlas.geo.los.TerrainProfile
import com.sovereignatlas.atlas.geo.navigation.NavigationUpdate
import com.sovereignatlas.atlas.geo.navigation.RoadRouteResult
import com.sovereignatlas.atlas.layers.compositor.OverscalePolicy
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.ui.AtlasActivityBanner
import com.sovereignatlas.atlas.ui.CompassOverlay
import com.sovereignatlas.atlas.ui.HudTelemetryPill
import com.sovereignatlas.atlas.ui.MgrsHud
import com.sovereignatlas.atlas.ui.hud.CompassTape
import com.sovereignatlas.atlas.ui.hud.NavigationGuidanceHud
import com.sovereignatlas.atlas.ui.hud.TacNavHud

@Composable
fun AtlasTopHud(
    bearing: Double,
    targetBearing: Double?,
    targetLabel: String?,
    courseDeviationDeg: Double?,
    isNavigating: Boolean,
    navUpdate: NavigationUpdate?,
    isMuted: Boolean,
    activeRoadRoute: RoadRouteResult?,
    onToggleMute: () -> Unit,
    onStopNavigation: () -> Unit,
    activityBannerLabel: String?,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompassTape(
                bearing = bearing,
                targetBearing = targetBearing,
                targetLabel = targetLabel,
                courseDeviationDeg = courseDeviationDeg,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val hudAnimMs = if (reducedMotion) 60 else 200
        AnimatedVisibility(
            visible = isNavigating,
            enter = fadeIn(tween(hudAnimMs)) + expandVertically(tween(hudAnimMs)),
            exit = fadeOut(tween(hudAnimMs)) + shrinkVertically(tween(hudAnimMs)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars))
                .padding(top = 48.dp),
        ) {
            NavigationGuidanceHud(
                navUpdate = navUpdate,
                isMuted = isMuted,
                onToggleMute = onToggleMute,
                onStopNavigation = onStopNavigation,
                activeRoute = activeRoadRoute,
            )
        }
        AtlasActivityBanner(
            visible = activityBannerLabel != null,
            label = activityBannerLabel ?: "",
            reducedMotion = reducedMotion,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars))
                .padding(top = if (isNavigating) 144.dp else 54.dp),
        )
    }
}

@Composable
fun AtlasTelemetryHud(
    tacticalWorkspaceEnabled: Boolean,
    mgrsText: String,
    locStatus: String,
    speedText: String,
    packCount: Int,
    statusVal: AtlasLocationStatus,
    zoom: Double,
    activeSourceMaxZoom: Int,
    losMode: LoSMode,
    losProfile: TerrainProfile?,
    hudAccent: Color,
    reducedMotion: Boolean,
    tilt: Double = 0.0,
    onResetTilt: () -> Unit = {},
    meshState: RadioConnectionState = RadioConnectionState.DISCONNECTED,
    meshNodeCount: Int = 0,
    onOpenMeshRoster: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars))
            .padding(start = 10.dp, top = 54.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (meshState == RadioConnectionState.CONNECTED || meshState == RadioConnectionState.STREAMING) {
            HudTelemetryPill(
                text = "LORA: CONNECTED • 915 MHz • $meshNodeCount NODES",
                textColor = hudAccent,
                borderColor = hudAccent,
                iconRes = R.drawable.ic_hud_gps,
                iconTint = hudAccent,
                pulse = (meshState == RadioConnectionState.STREAMING),
                reducedMotion = reducedMotion,
                onClick = onOpenMeshRoster,
            )
        }
        if (tilt > 5.0) {
            HudTelemetryPill(
                text = "PERSPECTIVE • PITCH: ${tilt.toInt()}°",
                textColor = hudAccent,
                borderColor = hudAccent,
                onClick = onResetTilt,
            )
        }
        if (tacticalWorkspaceEnabled) {
            MgrsHud(mgrsText = mgrsText)
        }
        HudTelemetryPill(
            text = "GPS: $locStatus$speedText • OFFLINE: $packCount packs",
            textColor = Color(0xFFE2E8F0),
            iconRes = R.drawable.ic_hud_gps,
            iconTint = if (statusVal == AtlasLocationStatus.valid) {
                hudAccent
            } else {
                Color(0xFFA8B5C1)
            },
        )
        if (OverscalePolicy.isOverscaled(zoom, activeSourceMaxZoom)) {
            HudTelemetryPill(
                text = OverscalePolicy.formatBadge(zoom, activeSourceMaxZoom),
                textColor = Color(0xFFF4B85C),
                borderColor = Color(0x80F4B85C),
            )
        }
        when (losMode) {
            LoSMode.AwaitingObserver -> HudTelemetryPill(
                text = "LoS: tap observer point",
                textColor = hudAccent,
                borderColor = hudAccent,
                iconRes = R.drawable.ic_tool_los,
                pulse = true,
                reducedMotion = reducedMotion,
            )
            LoSMode.AwaitingTarget -> HudTelemetryPill(
                text = "LoS: tap target point",
                textColor = hudAccent,
                borderColor = hudAccent,
                iconRes = R.drawable.ic_tool_los,
                pulse = true,
                reducedMotion = reducedMotion,
            )
            LoSMode.Inactive -> {
                val verdict = losProfile?.lineOfSight
                val error = losProfile?.errorMessage
                if (verdict == null) {
                    Unit
                } else if (error != null) {
                    HudTelemetryPill(
                        text = error,
                        textColor = Color(0xFFF4B85C),
                        borderColor = Color(0x80F4B85C),
                    )
                } else {
                    if (verdict.status == LoSStatus.Clear) {
                        HudTelemetryPill(
                            text = "CLEAR",
                            textColor = Color(0xFF39FF14),
                            borderColor = Color(0x8039FF14),
                            iconRes = R.drawable.ic_tool_los,
                        )
                    } else {
                        HudTelemetryPill(
                            text = "OBSTRUCTED at ${verdict.blockingDistanceMeters?.toInt()}m",
                            textColor = Color(0xFFFF4444),
                            borderColor = Color(0x80FF4444),
                            iconRes = R.drawable.ic_tool_los,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AtlasNavInstrumentHud(
    isRecording: Boolean,
    pointCount: Int,
    bearingHud: Double,
    currentBearing: Float?,
    currentHeadingFrame: String?,
    tacticalWorkspaceEnabled: Boolean,
    hudAccent: Color,
    reducedMotion: Boolean,
    onLocate: () -> Unit,
    onFaceNorth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars))
            .padding(end = 10.dp, top = 54.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isRecording) {
            HudTelemetryPill(
                text = "REC • $pointCount pts",
                textColor = Color(0xFFFF4444),
                borderColor = Color(0x99FF4444),
                pulse = true,
                reducedMotion = reducedMotion,
            )
        }
        CompassOverlay(
            bearing = bearingHud,
            onLocate = onLocate,
            onFaceNorth = onFaceNorth,
            reducedMotion = reducedMotion,
        )
        if (tacticalWorkspaceEnabled) {
            TacNavHud(
                bearing = currentBearing,
                hudColor = hudAccent,
                frameLabel = currentHeadingFrame,
                showReticle = false,
                fillScreen = false,
                topPadding = 0.dp,
                modifier = Modifier
                    .width(136.dp)
                    .height(46.dp),
            )
        }
    }
}

@Composable
fun AtlasMapRailPills(
    following: Boolean,
    measureActive: Boolean,
    activeMbtilesPacksCount: Int,
    showTools: Boolean,
    studioWorkspaceEnabled: Boolean,
    explorerWorkspaceEnabled: Boolean,
    reducedMotion: Boolean,
    onToggleFollow: () -> Unit,
    onMeasure: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = 12.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AtlasRailPillButton(
            label = if (following) "Unfollow" else "Follow",
            stateDesc = if (following) "Follow active" else "Follow inactive",
            iconRes = R.drawable.ic_hud_follow,
            active = following,
            pulse = following,
            reducedMotion = reducedMotion,
            onClick = onToggleFollow,
        )
        if (studioWorkspaceEnabled) {
            AtlasRailPillButton(
                label = "Measure",
                stateDesc = if (measureActive) "Measure active" else "Measure inactive",
                iconRes = R.drawable.ic_hud_measure,
                active = measureActive,
                reducedMotion = reducedMotion,
                onClick = onMeasure,
            )
        }
        if (explorerWorkspaceEnabled) {
            AtlasRailPillButton(
                label = "Layers $activeMbtilesPacksCount",
                stateDesc = "Open encyclopedia layer manager",
                iconRes = R.drawable.ic_hud_layers,
                active = activeMbtilesPacksCount > 0,
                reducedMotion = reducedMotion,
                onClick = onOpenLayers,
            )
        }
        AtlasRailPillButton(
            label = "Tools",
            stateDesc = "Tools",
            iconRes = R.drawable.ic_hud_tools,
            active = showTools,
            reducedMotion = reducedMotion,
            onClick = onOpenTools,
        )
    }
}

@Composable
fun AtlasBottomAttribution(
    attribution: String,
    modifier: Modifier = Modifier,
) {
    if (attribution.isEmpty()) return
    Surface(
        color = Color(0xBF10151C),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 104.dp, bottom = 6.dp),
    ) {
        Text(
            text = attribution,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFFA8B5C1),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
