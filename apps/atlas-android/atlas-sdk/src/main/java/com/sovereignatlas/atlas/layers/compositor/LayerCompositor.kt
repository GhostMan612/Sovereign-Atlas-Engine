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
}
