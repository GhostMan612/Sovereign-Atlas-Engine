// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.AtlasServices
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.navigation.RoadRouteRequest
import com.sovereignatlas.atlas.geo.navigation.TravelProfile
import com.sovereignatlas.atlas.measure.MeasureSnapshot
import com.sovereignatlas.atlas.measure.MeasureUnit
import com.sovereignatlas.atlas.ui.GoToCard
import com.sovereignatlas.atlas.ui.MeasurePanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AtlasGoToCardHost(
    services: AtlasServices,
    usableFix: AtlasCoordinate?,
    activeTravelProfile: TravelProfile?,
    navigationFeedbackMessage: MutableState<String?>,
    activityBannerLabel: MutableState<String?>,
    mapScope: CoroutineScope,
    context: Context,
    modifier: Modifier = Modifier,
) {
    if (!services.goTo.isActive()) return
    Surface(
        color = Color(0xF0141C24),
        contentColor = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0x4DA8B5C1)),
        shadowElevation = 6.dp,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars))
            .padding(start = 10.dp, top = 54.dp),
    ) {
        GoToCard(
            goTo = services.goTo,
            usableFix = usableFix,
            onClear = {
                services.goTo.clear()
                services.roadNavigation.stopNavigation()
                navigationFeedbackMessage.value = null
            },
            onNavigate = {
                val target = services.goTo.targetOrNull()
                val fix = usableFix
                if (target != null && fix != null) {
                    mapScope.launch {
                        val engine = services.roadRoutingEngine
                        if (engine == null || engine.graph.nodes.isEmpty()) {
                            navigationFeedbackMessage.value = "Road network data unavailable"
                            Toast.makeText(
                                context,
                                "Road network data unavailable for this area. Direct-bearing guidance active.",
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        activityBannerLabel.value = "COMPUTING OPTIMAL ROAD ROUTE"
                        val result = try {
                            val startNode = withContext(Dispatchers.Default) {
                                engine.findNearestNode(fix.latitude, fix.longitude)
                            }
                            val targetNode = withContext(Dispatchers.Default) {
                                engine.findNearestNode(target.latitude, target.longitude)
                            }
                            if (startNode == null || targetNode == null) {
                                navigationFeedbackMessage.value = "Location outside road coverage"
                                Toast.makeText(
                                    context,
                                    "Location outside road network coverage. Direct-bearing guidance active.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@launch
                            }
                            withContext(Dispatchers.Default) {
                                engine.calculateRoute(
                                    RoadRouteRequest(
                                        startNodeId = startNode.id,
                                        targetNodeId = targetNode.id,
                                        profile = services.roadNavigation.profile.value,
                                    ),
                                )
                            }
                        } finally {
                            activityBannerLabel.value = null
                        }
                        if (result != null) {
                            val targetNode = engine.findNearestNode(target.latitude, target.longitude)
                            navigationFeedbackMessage.value = null
                            services.roadNavigation.startNavigation(
                                route = result,
                                voice = services.voiceGuidance,
                                onRerouteNeeded = { offRouteCoord ->
                                    if (targetNode != null) {
                                        mapScope.launch {
                                            val newStart = withContext(Dispatchers.Default) {
                                                engine.findNearestNode(offRouteCoord.latitude, offRouteCoord.longitude)
                                            }
                                            if (newStart != null) {
                                                val reroute = withContext(Dispatchers.Default) {
                                                    engine.calculateRoute(
                                                        RoadRouteRequest(
                                                            startNodeId = newStart.id,
                                                            targetNodeId = targetNode.id,
                                                            profile = services.roadNavigation.profile.value,
                                                        ),
                                                    )
                                                }
                                                if (reroute != null) {
                                                    services.roadNavigation.updateRoute(reroute)
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        } else {
                            navigationFeedbackMessage.value = "No road route found"
                            Toast.makeText(
                                context,
                                "No road route found between points. Direct-bearing guidance active.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            },
            navigationMessage = navigationFeedbackMessage.value,
            selectedProfile = activeTravelProfile,
            onProfileSelected = { profile ->
                services.roadNavigation.setProfile(profile)
            },
        )
    }
}

@Composable
fun AtlasMeasureCardHost(
    measureSnapshot: MeasureSnapshot,
    onUnitSelected: (MeasureUnit) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xF0141C24),
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0x4DA8B5C1)),
        shadowElevation = 6.dp,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 104.dp, bottom = 30.dp),
    ) {
        MeasurePanel(
            snapshot = measureSnapshot,
            units = MeasureUnit.values().toList(),
            onUnitSelected = onUnitSelected,
            onClose = onClose,
            onClear = onClear,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
