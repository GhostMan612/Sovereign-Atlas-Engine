// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.sdk

import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier
import com.sovereignatlas.atlas.core.knowledge.HistoricalKnowledgeIndex
import com.sovereignatlas.atlas.core.knowledge.KnowledgeBundleSerializer
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.Mgrs
import com.sovereignatlas.atlas.geo.navigation.NavigationSession
import com.sovereignatlas.atlas.geo.navigation.RoadNetworkGraph
import com.sovereignatlas.atlas.geo.navigation.RoadRouteRequest
import com.sovereignatlas.atlas.geo.navigation.RoadRouteResult
import com.sovereignatlas.atlas.geo.navigation.RoadRoutingEngine
import com.sovereignatlas.atlas.geo.navigation.VoiceGuidance
import com.sovereignatlas.atlas.layers.compositor.AtlasLayerPresets
import com.sovereignatlas.atlas.layers.compositor.AtlasWorkspaceConfig
import com.sovereignatlas.atlas.layers.compositor.LayerAttribution
import com.sovereignatlas.atlas.layers.compositor.LayerCombinationPreset
import com.sovereignatlas.atlas.layers.compositor.LayerCompositor
import com.sovereignatlas.atlas.layers.compositor.LayerDescriptor
import com.sovereignatlas.atlas.layers.compositor.LayerType
import com.sovereignatlas.atlas.layers.compositor.OverscalePolicy

sealed class SdkResult<out T> {
    data class Success<out T>(val value: T) : SdkResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : SdkResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
}

object AtlasSdk {
    const val VERSION: String = "1.0.0"

    object Spatial {
        fun distanceMeters(from: AtlasCoordinate, to: AtlasCoordinate): Double {
            return AtlasGeoMath.haversineKm(from, to) * 1000.0
        }

        fun bearingDegrees(from: AtlasCoordinate, to: AtlasCoordinate): Double {
            return AtlasGeoMath.initialBearingDeg(from, to)
        }

        fun toMgrs(coordinate: AtlasCoordinate): String {
            return Mgrs.format(coordinate.latitude, coordinate.longitude)
        }
    }

    object Navigation {
        fun createRoutingEngine(graph: RoadNetworkGraph): RoadRoutingEngine {
            return RoadRoutingEngine(graph)
        }

        fun planRoute(
            engine: RoadRoutingEngine,
            request: RoadRouteRequest,
        ): SdkResult<RoadRouteResult> {
            val result = engine.calculateRoute(request)
            return if (result != null) {
                SdkResult.Success(result)
            } else {
                SdkResult.Failure("No route found between node ${request.startNodeId} and ${request.targetNodeId}")
            }
        }

        fun planRouteAlternatives(
            engine: RoadRoutingEngine,
            request: RoadRouteRequest,
            maxAlternatives: Int = 2,
        ): List<RoadRouteResult> {
            return engine.calculateRouteAlternatives(request, maxAlternatives)
        }

        fun createSession(
            route: RoadRouteResult,
            voice: VoiceGuidance? = null,
        ): NavigationSession {
            return NavigationSession(route, voice)
        }
    }

    object Layers {
        fun createCompositor(): LayerCompositor {
            return LayerCompositor()
        }

        fun presets(): List<LayerCombinationPreset> {
            return AtlasLayerPresets.all
        }

        fun isOverscaled(currentZoom: Double, sourceMaxZoom: Int): Boolean {
            return OverscalePolicy.isOverscaled(currentZoom, sourceMaxZoom)
        }

        fun defaultWorkspaces(): AtlasWorkspaceConfig {
            return AtlasWorkspaceConfig.ALL_WORKSPACES
        }

        fun cleanNavigateWorkspace(): AtlasWorkspaceConfig {
            return AtlasWorkspaceConfig.CLEAN_NAVIGATE
        }

        fun createDescriptor(
            id: String,
            title: String,
            type: LayerType,
            defaultVisibility: Boolean = true,
            defaultOpacity: Float = 1.0f,
            defaultZIndex: Int = 0,
            attribution: LayerAttribution? = null,
        ): LayerDescriptor {
            return LayerDescriptor(
                id = id,
                title = title,
                type = type,
                defaultVisibility = defaultVisibility,
                defaultOpacity = defaultOpacity,
                defaultZIndex = defaultZIndex,
                attribution = attribution,
            )
        }
    }

    object Knowledge {
        fun createIndex(): HistoricalKnowledgeIndex {
            return HistoricalKnowledgeIndex()
        }

        fun getSerializer(): KnowledgeBundleSerializer {
            return KnowledgeBundleSerializer
        }
    }
}
