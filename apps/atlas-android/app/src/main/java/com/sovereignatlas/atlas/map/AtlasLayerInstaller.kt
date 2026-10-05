// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
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
    const val MARKER_SOURCE = "cot-marker-source"
    const val MARKER_LAYER = "cot-marker-layer"
    const val MBTILES_LAYER_ID_PREFIX = "mbtiles-layer-"
    const val MBTILES_SOURCE_ID_PREFIX = "mbtiles-source-"
    const val MESH_TRACK_SOURCE = "mesh-track-source"
    const val MESH_TRACK_LAYER = "mesh-track-layer"
    const val OPS_GRAPHICS_SOURCE = "ops-graphics-source"
    const val OPS_ZONE_LAYER = "ops-zone-layer"
    const val OPS_LINE_LAYER = "ops-line-layer"
    const val HISTORICAL_FILL_PREFIX = "historical-fill-"
    const val HISTORICAL_LINE_PREFIX = "historical-line-"
    const val HISTORICAL_PATENTS_SOURCE = "historical-patents-source"
    const val HISTORICAL_PATENTS_FILL = "historical-patents-fill"
    const val HISTORICAL_PATENTS_BORDER = "historical-patents-border"
    const val HISTORICAL_RASTER_SOURCE_PREFIX = "historical-raster-source-"
    const val HISTORICAL_RASTER_LAYER_PREFIX = "historical-raster-layer-"
    const val LOS_OBSERVER_SOURCE = "atlas-los-observer"
    const val LOS_OBSERVER_LAYER = "atlas-los-observer-layer"
    const val LOS_TARGET_SOURCE = "atlas-los-target"
    const val LOS_TARGET_LAYER = "atlas-los-target-layer"
    const val LOS_SOURCE = "atlas-los-ray"
    const val LOS_LAYER = "atlas-los-ray-layer"
    const val LOS_BLOCK_SOURCE = "atlas-los-block"
    const val LOS_BLOCK_LAYER = "atlas-los-block-layer"

    /**
     * Image name the LoS observer layer requests.
     *
     * Previously a bare `"user-puck"` literal in [installAtlasLayers] that nothing
     * ever registered. Promoted to a constant so the layer and the registration
     * cannot drift apart again — the bug was two hardcoded strings that did not
     * match, and only one of them was in this file.
     */
    const val USER_PUCK_IMAGE = "user-puck"
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
    // Synchronous update: the patents collection is pushed wholesale on camera
    // idle, and a deferred update leaves a visible gap between the camera settling
    // and the parcels appearing.
    style.addSource(
        GeoJsonSource(
            AtlasLayerIds.HISTORICAL_PATENTS_SOURCE,
            GeoJsonOptions().withSynchronousUpdate(true),
        ),
    )
    style.addSource(GeoJsonSource(AtlasLayerIds.GOTO_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.FENCE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MGRS_LINE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MGRS_LABEL_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.ROUTE_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.PLI_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MARKER_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.MESH_TRACK_SOURCE))
    style.addSource(GeoJsonSource(AtlasLayerIds.OPS_GRAPHICS_SOURCE))
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
    // Patent parcels sit above the terrain/raster basemap and below every
    // navigational overlay. addLayer is used deliberately: plain addLayer is
    // the form verified to paint on device, and addLayerBelow has produced
    // layers present in style.layers that never render.
    style.addLayer(
        FillLayer(AtlasLayerIds.HISTORICAL_PATENTS_FILL, AtlasLayerIds.HISTORICAL_PATENTS_SOURCE)
            .withProperties(
                PropertyFactory.fillColor("#C8912B"),
                PropertyFactory.fillOpacity(0.3f),
            ),
    )
    style.addLayer(
        LineLayer(AtlasLayerIds.HISTORICAL_PATENTS_BORDER, AtlasLayerIds.HISTORICAL_PATENTS_SOURCE)
            .withProperties(
                PropertyFactory.lineColor("#8A5F13"),
                PropertyFactory.lineWidth(2.0f),
            ),
    )
    // These three were previously added below WAYPOINTS_LAYER. Both forms render
    // on device, so this is defence in depth rather than a bug fix: it removes
    // three more uses of addLayerBelow, leaving only the two ops-graphics layers,
    // which must stay below MESH_TRACK_LAYER by design, and PLI.
    style.addLayer(
        LineLayer(AtlasLayerIds.MGRS_LINE_LAYER, AtlasLayerIds.MGRS_LINE_SOURCE)
            .withProperties(
                PropertyFactory.lineColor("#7A8A7A"),
                PropertyFactory.lineWidth(1.0f),
                PropertyFactory.lineOpacity(0.7f),
            ),
    )
    style.addLayer(
        SymbolLayer(AtlasLayerIds.MGRS_LABEL_LAYER, AtlasLayerIds.MGRS_LABEL_SOURCE)
            .withProperties(
                PropertyFactory.textField(Expression.get("title")),
                PropertyFactory.textSize(10.0f),
                PropertyFactory.textColor("#9AA89A"),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textIgnorePlacement(false),
            ),
    )
    style.addLayer(
        LineLayer(AtlasLayerIds.ROUTE_LAYER, AtlasLayerIds.ROUTE_SOURCE)
            .withProperties(
                PropertyFactory.lineColor("#39FF14"),
                PropertyFactory.lineWidth(4.0f),
                PropertyFactory.lineOpacity(0.8f),
            ),
    )
    // Plain addLayer, not addLayerBelow: with addLayerBelow the CoT marker layer
    // sat in the style but never painted on device while its source held the
    // data, and addLayer rendered immediately. Markers belong on top regardless.
    style.addLayer(
        CircleLayer(AtlasLayerIds.MARKER_LAYER, AtlasLayerIds.MARKER_SOURCE)
            .withProperties(
                PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor("#000000"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
    )
    style.addLayerAbove(
        SymbolLayer(AtlasLayerIds.MESH_TRACK_LAYER, AtlasLayerIds.MESH_TRACK_SOURCE)
            .withProperties(
                PropertyFactory.iconImage(Expression.get("affiliation")),
                PropertyFactory.iconSize(0.75f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.textField("{callsign}"),
                PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                PropertyFactory.textSize(11.0f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1f),
            ),
        AtlasLayerIds.WAYPOINTS_LAYER,
    )
    // Operational graphics sit directly below the CoT track layer so drawn
    // shapes never obscure track icons or their callsign labels. Geometry
    // filters split one source into zone fills and tactical lines.
    style.addLayerBelow(
        FillLayer(AtlasLayerIds.OPS_ZONE_LAYER, AtlasLayerIds.OPS_GRAPHICS_SOURCE)
            .withProperties(
                PropertyFactory.fillColor(Expression.get("fillColor")),
                PropertyFactory.fillOpacity(Expression.get("fillOpacity")),
            )
            .withFilter(
                Expression.eq(Expression.geometryType(), Expression.literal("Polygon")),
            ),
        AtlasLayerIds.MESH_TRACK_LAYER,
    )
    style.addLayerBelow(
        LineLayer(AtlasLayerIds.OPS_LINE_LAYER, AtlasLayerIds.OPS_GRAPHICS_SOURCE)
            .withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(Expression.get("width")),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            )
            .withFilter(
                Expression.eq(Expression.geometryType(), Expression.literal("LineString")),
            ),
        AtlasLayerIds.MESH_TRACK_LAYER,
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
                PropertyFactory.iconImage(AtlasLayerIds.USER_PUCK_IMAGE),
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

// The old pushFeatures(style, sourceId, FeatureCollection) is gone. Every one of
// its call sites now goes through FeatureSink, which replaced the raw sourceId String
// with the AtlasLayer sealed type. Nothing should be adding to a source by string id
// any more: a typo produced a write to a source that does not exist, and two of the
// three call paths did so silently.
