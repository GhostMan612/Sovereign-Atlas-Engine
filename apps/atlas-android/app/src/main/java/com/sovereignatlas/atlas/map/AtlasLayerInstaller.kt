// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

object AtlasLayerIds {
    const val WAYPOINTS_SOURCE = "atlas-waypoints"
    const val WAYPOINTS_LAYER = "atlas-waypoints-layer"
    const val TRACK_SOURCE = "atlas-track"
    const val TRACK_LAYER = "atlas-track-layer"
    const val SCRUB_SOURCE = "atlas-scrub"
    const val SCRUB_LAYER = "atlas-scrub-layer"
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
    const val MGRS_LINE_SOURCE = "atlas-mgrs-lines"
    const val MGRS_LINE_LAYER = "atlas-mgrs-lines-layer"
    const val MGRS_LABEL_SOURCE = "atlas-mgrs-labels"
    const val MGRS_LABEL_LAYER = "atlas-mgrs-labels-layer"
    const val ROUTE_SOURCE = "atlas-tactical-route"
    const val ROUTE_LAYER = "atlas-tactical-route-layer"
    const val PLI_SOURCE = "atlas-tactical-pli"
    const val PLI_LAYER = "atlas-tactical-pli-layer"
    const val LOS_OBSERVER_SOURCE = "atlas-los-observer"
    const val LOS_OBSERVER_LAYER = "atlas-los-observer-layer"
    const val LOS_TARGET_SOURCE = "atlas-los-target"
    const val LOS_TARGET_LAYER = "atlas-los-target-layer"
    const val LOS_SOURCE = "atlas-los-ray"
    const val LOS_LAYER = "atlas-los-ray-layer"
    const val LOS_BLOCK_SOURCE = "atlas-los-block"
    const val LOS_BLOCK_LAYER = "atlas-los-block-layer"
}

fun installAtlasLayers(style: Style) {
    style.addSource(GeoJsonSource(AtlasLayerIds.WAYPOINTS_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.TRACK_SOURCE))
    style.addSource(
        GeoJsonSource(
            AtlasLayerIds.SCRUB_SOURCE,
            GeoJsonOptions().withSynchronousUpdate(true),
        ),
    )
    style.addSource(GeoJsonSource(AtlasLayerIds.MEASURE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MEASURE_DOTS_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.GRATICULE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.RINGS_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.POSITION_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.GOTO_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.FENCE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MGRS_LINE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MGRS_LABEL_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.ROUTE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.PLI_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.LOS_OBSERVER_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.LOS_TARGET_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.LOS_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.LOS_BLOCK_SOURCE))
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
        SymbolLayer(AtlasLayerIds.SCRUB_LAYER, AtlasLayerIds.SCRUB_SOURCE)
            .withProperties(
                PropertyFactory.iconImage("scrub-icon"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
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
        SymbolLayer(AtlasLayerIds.POSITION_LAYER, AtlasLayerIds.POSITION_SOURCE)
            .withProperties(
                PropertyFactory.iconImage("gps-puck-icon"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconColor("#39FF14"),
                PropertyFactory.iconAllowOverlap(true),
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
    style.addLayerBelow(
        LineLayer(AtlasLayerIds.MGRS_LINE_LAYER, AtlasLayerIds.MGRS_LINE_SOURCE)
            .withProperties(
                PropertyFactory.lineColor("#7A8A7A"),
                PropertyFactory.lineWidth(1.0f),
                PropertyFactory.lineOpacity(0.7f),
            ),
        AtlasLayerIds.WAYPOINTS_LAYER,
    )
    style.addLayerBelow(
        SymbolLayer(AtlasLayerIds.MGRS_LABEL_LAYER, AtlasLayerIds.MGRS_LABEL_SOURCE)
            .withProperties(
                PropertyFactory.textField(Expression.get("title")),
                PropertyFactory.textSize(10.0f),
                PropertyFactory.textColor("#9AA89A"),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textIgnorePlacement(false),
            ),
        AtlasLayerIds.WAYPOINTS_LAYER,
    )
    style.addLayerBelow(
        LineLayer(AtlasLayerIds.ROUTE_LAYER, AtlasLayerIds.ROUTE_SOURCE)
            .withProperties(
                PropertyFactory.lineColor("#39FF14"),
                PropertyFactory.lineWidth(4.0f),
                PropertyFactory.lineOpacity(0.8f),
            ),
        AtlasLayerIds.WAYPOINTS_LAYER,
    )
    style.addLayerBelow(
        SymbolLayer(AtlasLayerIds.PLI_LAYER, AtlasLayerIds.PLI_SOURCE)
            .withProperties(
                PropertyFactory.iconImage("blue-force-marker"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField(Expression.get("callsign")),
                PropertyFactory.textOffset(arrayOf(0.0f, 0.8f)),
                PropertyFactory.textSize(10.0f),
                PropertyFactory.textColor("#4287F5"),
            ),
        AtlasLayerIds.WAYPOINTS_LAYER,
    )
    style.addLayer(
        SymbolLayer(AtlasLayerIds.LOS_OBSERVER_LAYER, AtlasLayerIds.LOS_OBSERVER_SOURCE)
            .withProperties(
                PropertyFactory.iconImage("user-puck"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
            ),
    )
    style.addLayer(
        SymbolLayer(AtlasLayerIds.LOS_TARGET_LAYER, AtlasLayerIds.LOS_TARGET_SOURCE)
            .withProperties(
                PropertyFactory.iconImage("wp-icon"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
            ),
    )
    style.addLayer(
        LineLayer(AtlasLayerIds.LOS_LAYER, AtlasLayerIds.LOS_SOURCE)
            .withProperties(
                PropertyFactory.lineColor("#39FF14"),
                PropertyFactory.lineWidth(3.0f),
                PropertyFactory.lineOpacity(0.9f),
            ),
    )
    style.addLayer(
        SymbolLayer(AtlasLayerIds.LOS_BLOCK_LAYER, AtlasLayerIds.LOS_BLOCK_SOURCE)
            .withProperties(
                PropertyFactory.iconImage("scrub-icon"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
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
