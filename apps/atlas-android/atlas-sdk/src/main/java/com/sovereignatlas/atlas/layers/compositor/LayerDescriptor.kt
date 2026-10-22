// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.layers.compositor

enum class LayerType {
    BASEMAP,
    RASTER_OVERLAY,
    VECTOR_OVERLAY,
    GEOJSON_OVERLAY,
    ELEVATION_TERRAIN,
    HISTORICAL_OVERLAY,
}

enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
}

data class LayerAttribution(
    val sourceName: String,
    val license: String,
    val attributionText: String,
    val sourceUrl: String? = null,
)

data class TimeRange(
    val startEpochMs: Long? = null,
    val endEpochMs: Long? = null,
) {
    fun contains(epochMs: Long): Boolean {
        val afterStart = startEpochMs == null || epochMs >= startEpochMs
        val beforeEnd = endEpochMs == null || epochMs <= endEpochMs
        return afterStart && beforeEnd
    }
}

data class ZoomRange(
    val minZoom: Double = 0.0,
    val maxZoom: Double = 24.0,
) {
    fun contains(zoom: Double): Boolean {
        return zoom in minZoom..maxZoom
    }
}

data class LayerDescriptor(
    val id: String,
    val title: String,
    val type: LayerType,
    val defaultVisibility: Boolean = true,
    val defaultOpacity: Float = 1.0f,
    val defaultZIndex: Int = 0,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val zoomRange: ZoomRange = ZoomRange(),
    val timeRange: TimeRange = TimeRange(),
    val attribution: LayerAttribution? = null,
)

data class LayerRuntimeState(
    val descriptor: LayerDescriptor,
    var isVisible: Boolean,
    var opacity: Float,
    var zIndex: Int,
) {
    fun isEffectiveVisible(zoom: Double, currentEpochMs: Long?): Boolean {
        if (!isVisible) return false
        if (opacity <= 0.0f) return false
        if (!descriptor.zoomRange.contains(zoom)) return false
        if (currentEpochMs != null && !descriptor.timeRange.contains(currentEpochMs)) return false
        return true
    }
}
