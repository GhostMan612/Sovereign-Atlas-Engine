// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.layers.compositor

class LayerCompositor {

    private val layerMap = LinkedHashMap<String, LayerRuntimeState>()

    fun registerLayer(descriptor: LayerDescriptor): LayerRuntimeState {
        val state = LayerRuntimeState(
            descriptor = descriptor,
            isVisible = descriptor.defaultVisibility,
            opacity = descriptor.defaultOpacity.coerceIn(0.0f, 1.0f),
            zIndex = descriptor.defaultZIndex,
        )
        layerMap[descriptor.id] = state
        return state
    }

    fun unregisterLayer(layerId: String): Boolean {
        return layerMap.remove(layerId) != null
    }

    fun getLayer(layerId: String): LayerRuntimeState? {
        return layerMap[layerId]
    }

    fun setVisibility(layerId: String, isVisible: Boolean): Boolean {
        val state = layerMap[layerId] ?: return false
        state.isVisible = isVisible
        return true
    }

    fun setOpacity(layerId: String, opacity: Float): Boolean {
        val state = layerMap[layerId] ?: return false
        state.opacity = opacity.coerceIn(0.0f, 1.0f)
        return true
    }

    fun setZIndex(layerId: String, zIndex: Int): Boolean {
        val state = layerMap[layerId] ?: return false
        state.zIndex = zIndex
        return true
    }

    fun getRenderStack(zoom: Double = 10.0, currentEpochMs: Long? = null): List<LayerRuntimeState> {
        return layerMap.values
            .filter { it.isEffectiveVisible(zoom, currentEpochMs) }
            .sortedBy { it.zIndex }
    }

    fun getAllLayers(): List<LayerRuntimeState> {
        return layerMap.values.toList()
    }

    fun getAttributionRollup(zoom: Double = 10.0, currentEpochMs: Long? = null): String {
        val active = getRenderStack(zoom, currentEpochMs)
        val attributions = active
            .mapNotNull { it.descriptor.attribution?.attributionText }
            .filter { it.isNotBlank() }
            .distinct()

        return attributions.joinToString(" | ")
    }

    fun applyCombinationPreset(preset: LayerCombinationPreset) {
        val configuredIds = preset.entries.map { it.layerId }.toSet()
        for (state in layerMap.values) {
            if (state.descriptor.id !in configuredIds &&
                (state.descriptor.type == LayerType.RASTER_OVERLAY ||
                    state.descriptor.type == LayerType.VECTOR_OVERLAY)
            ) {
                state.isVisible = false
            }
        }
        for (entry in preset.entries) {
            setVisibility(entry.layerId, entry.isVisible)
            setOpacity(entry.layerId, entry.opacity)
        }
    }
}

data class LayerBlendEntry(
    val layerId: String,
    val isVisible: Boolean,
    val opacity: Float = 1.0f,
)

data class LayerCombinationPreset(
    val presetId: String,
    val title: String,
    val description: String,
    val baseProviderId: String,
    val entries: List<LayerBlendEntry>,
)

object AtlasLayerPresets {
    val satelliteTopoRelief = LayerCombinationPreset(
        presetId = "satellite-topo-relief",
        title = "Satellite + Topo + Hillshade",
        description = "Blends Esri World Imagery with USGS Topographic contours and 3DEP shaded relief",
        baseProviderId = "esri-imagery",
        entries = listOf(
            LayerBlendEntry("blend-usgs-topo", isVisible = true, opacity = 0.45f),
            LayerBlendEntry("blend-usgs-hillshade", isVisible = true, opacity = 0.35f),
            LayerBlendEntry("waypoints", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("track", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("route", isVisible = true, opacity = 1.0f),
        ),
    )

    val terrainHydroSurvey = LayerCombinationPreset(
        presetId = "terrain-hydro-survey",
        title = "Topo + 3DEP Relief + NHD Waterways",
        description = "Combines OpenTopoMap with USGS 3DEP hillshade, NHD hydrography, and lat/lon graticule",
        baseProviderId = "opentopomap",
        entries = listOf(
            LayerBlendEntry("blend-usgs-hillshade", isVisible = true, opacity = 0.40f),
            LayerBlendEntry("blend-usgs-hydrography", isVisible = true, opacity = 0.75f),
            LayerBlendEntry("graticule", isVisible = true, opacity = 0.80f),
            LayerBlendEntry("waypoints", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("track", isVisible = true, opacity = 1.0f),
        ),
    )

    val historicalArchival = LayerCombinationPreset(
        presetId = "historical-archival",
        title = "Historical Dossiers + Patents + Blueprints",
        description = "Displays USGS Topo with NAIP hybrid imagery, archival dossiers, BLM land patents, and Sanborn blueprints",
        baseProviderId = "usgs-topo",
        entries = listOf(
            LayerBlendEntry("blend-usgs-imagery-topo", isVisible = true, opacity = 0.45f),
            LayerBlendEntry("historical-dossiers", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("historical-patents", isVisible = true, opacity = 0.75f),
            LayerBlendEntry("historical-blueprints", isVisible = true, opacity = 0.85f),
            LayerBlendEntry("graticule", isVisible = true, opacity = 0.70f),
        ),
    )

    val tacticalFullSpectrum = LayerCombinationPreset(
        presetId = "tactical-full-spectrum",
        title = "Tactical C2 + MGRS + Nautical + Relief",
        description = "High-contrast Dark Matter basemap with MGRS grid, range rings, CoT markers, PLI mesh, ops graphics, and nautical marks",
        baseProviderId = "carto-dark-matter",
        entries = listOf(
            LayerBlendEntry("blend-usgs-hillshade", isVisible = true, opacity = 0.30f),
            LayerBlendEntry("blend-openseamap-nautical", isVisible = true, opacity = 0.85f),
            LayerBlendEntry("mgrs", isVisible = true, opacity = 0.85f),
            LayerBlendEntry("rings", isVisible = true, opacity = 0.90f),
            LayerBlendEntry("cot-markers", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("pli-mesh", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("ops-graphics", isVisible = true, opacity = 0.90f),
            LayerBlendEntry("fence", isVisible = true, opacity = 0.90f),
            LayerBlendEntry("route", isVisible = true, opacity = 1.0f),
        ),
    )

    val dualStreetImagery = LayerCombinationPreset(
        presetId = "dual-street-imagery",
        title = "Hybrid Street + Satellite + Parcels",
        description = "Blends Esri World Imagery with OpenStreetMap streets, CARTO labels, and BLM land patent boundaries",
        baseProviderId = "esri-imagery",
        entries = listOf(
            LayerBlendEntry("blend-osm-standard", isVisible = true, opacity = 0.40f),
            LayerBlendEntry("blend-carto-positron", isVisible = true, opacity = 0.35f),
            LayerBlendEntry("historical-patents", isVisible = true, opacity = 0.65f),
            LayerBlendEntry("waypoints", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("route", isVisible = true, opacity = 1.0f),
        ),
    )

    val standardNavigation = LayerCombinationPreset(
        presetId = "standard-navigation",
        title = "Standard Navigation (Reset Overlays)",
        description = "Resets optional raster and historical overlays to clean navigation defaults",
        baseProviderId = "osm-standard",
        entries = listOf(
            LayerBlendEntry("waypoints", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("track", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("measure", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("route", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("cot-markers", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("pli-mesh", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("ops-graphics", isVisible = true, opacity = 0.90f),
            LayerBlendEntry("fence", isVisible = true, opacity = 0.90f),
            LayerBlendEntry("historical-dossiers", isVisible = true, opacity = 1.0f),
            LayerBlendEntry("historical-patents", isVisible = true, opacity = 0.70f),
            LayerBlendEntry("historical-blueprints", isVisible = true, opacity = 0.85f),
        ),
    )

    val all: List<LayerCombinationPreset> = listOf(
        satelliteTopoRelief,
        terrainHydroSurvey,
        historicalArchival,
        tacticalFullSpectrum,
        dualStreetImagery,
        standardNavigation,
    )

    fun lookup(presetId: String): LayerCombinationPreset? = all.firstOrNull { it.presetId == presetId }
}
