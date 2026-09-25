// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

object AtlasLayerIds {
    const val WAYPOINTS_SOURCE = "atlas-waypoints"
    const val WAYPOINTS_LAYER = "atlas-waypoints-layer"
    const val TRACK_SOURCE = "atlas-track"
    const val TRACK_LAYER = "atlas-track-layer"
    const val MEASURE_SOURCE = "atlas-measure"
    const val MEASURE_LAYER = "atlas-measure-layer"
    const val MEASURE_DOTS_SOURCE = "atlas-measure-dots"
    const val MEASURE_DOTS_LAYER = "atlas-measure-dots-layer"
    const val GRATICULE_SOURCE = "atlas-graticule"
    const val GRATICULE_LAYER = "atlas-graticule-layer"
    const val RINGS_SOURCE = "atlas-rings"
    const val RINGS_LAYER = "atlas-rings-layer"
    const val POSITION_SOURCE = "atlas-position"
    const val POSITION_LAYER = "atlas-position-layer"
    const val GOTO_SOURCE = "atlas-goto"
    const val GOTO_LAYER = "atlas-goto-layer"
    const val FENCE_SOURCE = "atlas-fence"
    const val FENCE_LAYER = "atlas-fence-layer"
}

fun installAtlasLayers(style: Style) {
    style.addSource(GeoJsonSource(AtlasLayerIds.WAYPOINTS_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.TRACK_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MEASURE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MEASURE_DOTS_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.GRATICULE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.RINGS_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.POSITION_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.GOTO_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.FENCE_SOURCE))
    style.addLayer(
        SymbolLayer(
            AtlasLayerIds.WAYPOINTS_LAYER,
            AtlasLayerIds.WAYPOINTS_SOURCE,
        ).withProperties(
            PropertyFactory.iconImage("wp-icon"),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.textField("{label}"),
            PropertyFactory.textSize(12.0f),
            PropertyFactory.textOpacity(0.9f),
        ),
    )
    style.addLayer(
        LineLayer(AtlasLayerIds.TRACK_LAYER, AtlasLayerIds.TRACK_SOURCE)
            .withProperties(
                PropertyFactory.lineColor("#39FF14"),
                PropertyFactory.lineWidth(4.0f),
                PropertyFactory.lineOpacity(0.9f),
            ),
    )
    style.addLayer(
        LineLayer(AtlasLayerIds.MEASURE_LAYER, AtlasLayerIds.MEASURE_SOURCE)
            .withProperties(
                PropertyFactory.lineWidth(3.0f),
                PropertyFactory.lineOpacity(0.9f),
            ),
    )
    style.addLayer(
        CircleLayer(
            AtlasLayerIds.MEASURE_DOTS_LAYER,
            AtlasLayerIds.MEASURE_DOTS_SOURCE,
        ).withProperties(
            PropertyFactory.circleRadius(8.0f),
            PropertyFactory.circleOpacity(0.9f),
        ),
    )
    style.addLayer(
        LineLayer(AtlasLayerIds.GRATICULE_LAYER, AtlasLayerIds.GRATICULE_SOURCE)
            .withProperties(
                PropertyFactory.lineWidth(1.0f),
                PropertyFactory.lineOpacity(0.7f),
            ),
    )
    style.addLayer(
        LineLayer(AtlasLayerIds.RINGS_LAYER, AtlasLayerIds.RINGS_SOURCE)
            .withProperties(
                PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineOpacity(0.7f),
            ),
    )
    style.addLayer(
        CircleLayer(AtlasLayerIds.POSITION_LAYER, AtlasLayerIds.POSITION_SOURCE)
            .withProperties(
                PropertyFactory.circleRadius(10.0f),
                PropertyFactory.circleOpacity(0.9f),
            ),
    )
    style.addLayer(
        CircleLayer(AtlasLayerIds.GOTO_LAYER, AtlasLayerIds.GOTO_SOURCE)
            .withProperties(
                PropertyFactory.circleRadius(12.0f),
                PropertyFactory.circleOpacity(0.9f),
            ),
    )
    style.addLayer(
        LineLayer(AtlasLayerIds.FENCE_LAYER, AtlasLayerIds.FENCE_SOURCE)
            .withProperties(
                PropertyFactory.lineWidth(2.0f),
                PropertyFactory.lineOpacity(0.9f),
            ),
    )
}

fun setAtlasLayerVisible(style: Style, layerId: String, visible: Boolean) {
    val layer = style.getLayer(layerId) ?: return
    val value = if (visible) Property.VISIBLE else Property.NONE
    when (layer) {
        is SymbolLayer -> layer.setProperties(PropertyFactory.visibility(value))
        is LineLayer -> layer.setProperties(PropertyFactory.visibility(value))
        is CircleLayer -> layer.setProperties(PropertyFactory.visibility(value))
    }
}

fun pushFeatures(style: Style, sourceId: String, collection: FeatureCollection) {
    style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(collection)
}
