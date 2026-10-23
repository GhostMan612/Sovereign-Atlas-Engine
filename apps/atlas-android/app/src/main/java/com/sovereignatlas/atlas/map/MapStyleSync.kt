// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import android.content.Context
import android.graphics.Color
import com.sovereignatlas.atlas.AtlasServices
import com.sovereignatlas.atlas.geo.gis.GisDataset
import com.sovereignatlas.atlas.geo.AtlasAngles
import com.sovereignatlas.atlas.camera.AtlasCameraState
import com.sovereignatlas.atlas.core.SanbornBlueprint
import com.sovereignatlas.atlas.layers.compositor.LayerCompositor
import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders
import com.sovereignatlas.atlas.offline.mbtiles.mbtilesLayerId
import com.sovereignatlas.atlas.offline.mbtiles.mbtilesSafeId
import com.sovereignatlas.atlas.offline.mbtiles.mbtilesSourceId
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

const val BASE_SOURCE_ID = "atlas-base"
const val BASE_LAYER_ID = "atlas-base-layer"
const val DEM_SOURCE_ID = "atlas-dem"
const val RING_STEP_INDEX = 3
const val MAX_GRATICULE_LINES = 240

const val OVERVIEW_LAT = 0.0
const val OVERVIEW_LNG = 0.0
const val OVERVIEW_ZOOM = 2.0

const val BLANK_STYLE = """
{
  "version": 8,
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#111111" }
    }
  ]
}
"""

const val BLANK_STYLE_TERRAIN = """
{
  "version": 8,
  "sources": {},
  "terrain": { "source": "atlas-dem", "exaggeration": 1.0 },
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#111111" }
    }
  ]
}
"""

fun buildMbtilesStyleJson(context: Context, absolutePath: String): String {
    return buildMbtilesStyleJson(context, absolutePath, null)
}

fun buildMbtilesStyleJson(
    context: Context,
    absolutePath: String,
    demTileUrl: String?,
): String {
    val template = context.assets.open("offline_style.json").bufferedReader().use { it.readText() }
    var json = template.replace("___FILE_URI___", "mbtiles://file://$absolutePath")
    if (demTileUrl != null) {
        json = json.replace(
            "\"atlas-offline\": {",
            "\"dem-source\": {\n" +
                "      \"type\": \"raster-dem\",\n" +
                "      \"encoding\": \"mapbox\",\n" +
                "      \"tiles\": [\"$demTileUrl\"],\n" +
                "      \"tileSize\": 256\n" +
                "    },\n" +
                "    \"atlas-offline\": {",
        )
        json = json.replace(
            "\"id\": \"water\",",
            "\"id\": \"hillshade-layer\",\n" +
                "      \"type\": \"hillshade\",\n" +
                "      \"source\": \"dem-source\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"water\",",
        )
    }
    return json
}

fun applyCameraIntent(map: MapLibreMap, intent: AtlasCameraState) {
    map.moveCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(intent.center.latitude, intent.center.longitude))
                .zoom(intent.zoom)
                .bearing(intent.bearing)
                .build(),
        ),
    )
}

fun rotateMap(map: MapLibreMap, bearing: Double) {
    map.moveCamera(
        CameraUpdateFactory.bearingTo(AtlasAngles.normalizeBearingDeg(bearing)),
    )
}

fun syncBlendedProviderLayers(
    style: Style,
    compositor: LayerCompositor,
    cartoKey: String? = null,
) {
    for (layerState in compositor.getAllLayers().sortedBy { it.zIndex }) {
        val compId = layerState.descriptor.id
        if (!compId.startsWith("blend-")) continue
        val providerId = compId.removePrefix("blend-")
        val descriptor = OfflineBuiltinProviders.lookup(providerId) ?: continue
        var template = descriptor.urlTemplate ?: continue
        for ((param, value) in descriptor.params) {
            template = template.replace("{$param}", value)
        }
        template = template.replace("{key}", cartoKey ?: "")

        val sourceId = AtlasLayerIds.BLEND_SOURCE_PREFIX + providerId
        val layerId = AtlasLayerIds.BLEND_LAYER_PREFIX + providerId

        if (layerState.isVisible) {
            if (style.getSource(sourceId) == null) {
                val tileSet = TileSet("2.2.0", template)
                tileSet.minZoom = descriptor.minZoom.toFloat()
                tileSet.maxZoom = descriptor.maxZoom.toFloat()
                style.addSource(RasterSource(sourceId, tileSet, 256))
            }
            if (style.getLayer(layerId) == null) {
                val rasterLayer = RasterLayer(layerId, sourceId).withProperties(
                    PropertyFactory.rasterOpacity(layerState.opacity.coerceIn(0.0f, 1.0f)),
                    PropertyFactory.visibility(Property.VISIBLE),
                )
                rasterLayer.minZoom = descriptor.minZoom.toFloat()
                if (style.getLayer(AtlasLayerIds.WAYPOINTS_LAYER) != null) {
                    style.addLayerBelow(rasterLayer, AtlasLayerIds.WAYPOINTS_LAYER)
                } else {
                    style.addLayer(rasterLayer)
                }
            } else {
                setAtlasLayerVisible(style, layerId, true)
                setAtlasLayerOpacity(style, layerId, layerState.opacity)
            }
        } else {
            if (style.getLayer(layerId) != null) {
                setAtlasLayerVisible(style, layerId, false)
            }
        }
    }
}

fun combineAttribution(baseAttribution: String, compositorRollup: String): String {
    val parts = buildList {
        if (baseAttribution.isNotBlank()) add(baseAttribution)
        if (compositorRollup.isNotBlank()) {
            addAll(compositorRollup.split(" | ").map { it.trim() }.filter { it.isNotBlank() })
        }
    }.distinct()
    return parts.joinToString(" | ")
}

fun applyOverlayVisibility(
    style: Style,
    compositor: LayerCompositor? = null,
    showGraticule: Boolean,
    showMgrsGrid: Boolean,
    showRings: Boolean,
    showWaypoints: Boolean,
    showTrack: Boolean,
    showMeasure: Boolean,
    cartoKey: String? = null,
) {
    compositor?.setVisibility("graticule", showGraticule)
    compositor?.setVisibility("mgrs", showMgrsGrid)
    compositor?.setVisibility("rings", showRings)
    compositor?.setVisibility("waypoints", showWaypoints)
    compositor?.setVisibility("track", showTrack)
    compositor?.setVisibility("measure", showMeasure)

    compositor?.setVisibility(AtlasLayerIds.GRATICULE_LAYER, showGraticule)
    compositor?.setVisibility(AtlasLayerIds.MGRS_LINE_LAYER, showMgrsGrid)
    compositor?.setVisibility(AtlasLayerIds.MGRS_LABEL_LAYER, showMgrsGrid)
    compositor?.setVisibility(AtlasLayerIds.RINGS_LAYER, showRings)
    compositor?.setVisibility(AtlasLayerIds.WAYPOINTS_LAYER, showWaypoints)
    compositor?.setVisibility(AtlasLayerIds.TRACK_LAYER, showTrack)
    compositor?.setVisibility(AtlasLayerIds.MEASURE_LAYER, showMeasure)
    compositor?.setVisibility(AtlasLayerIds.MEASURE_DOTS_LAYER, showMeasure)

    setAtlasLayerVisible(style, AtlasLayerIds.GRATICULE_LAYER, showGraticule)
    setAtlasLayerVisible(style, AtlasLayerIds.MGRS_LINE_LAYER, showMgrsGrid)
    setAtlasLayerVisible(style, AtlasLayerIds.MGRS_LABEL_LAYER, showMgrsGrid)
    setAtlasLayerVisible(style, AtlasLayerIds.RINGS_LAYER, showRings)
    setAtlasLayerVisible(style, AtlasLayerIds.WAYPOINTS_LAYER, showWaypoints)
    setAtlasLayerVisible(style, AtlasLayerIds.TRACK_LAYER, showTrack)
    setAtlasLayerVisible(style, AtlasLayerIds.MEASURE_LAYER, showMeasure)
    setAtlasLayerVisible(style, AtlasLayerIds.MEASURE_DOTS_LAYER, showMeasure)

    if (compositor != null) {
        compositor.getLayer("graticule")?.let {
            setAtlasLayerOpacity(style, AtlasLayerIds.GRATICULE_LAYER, it.opacity)
        }
        compositor.getLayer("mgrs")?.let {
            setAtlasLayerOpacity(style, AtlasLayerIds.MGRS_LINE_LAYER, it.opacity)
            setAtlasLayerOpacity(style, AtlasLayerIds.MGRS_LABEL_LAYER, it.opacity)
        }
        compositor.getLayer("rings")?.let {
            setAtlasLayerOpacity(style, AtlasLayerIds.RINGS_LAYER, it.opacity)
        }
        compositor.getLayer("waypoints")?.let {
            setAtlasLayerOpacity(style, AtlasLayerIds.WAYPOINTS_LAYER, it.opacity)
        }
        compositor.getLayer("track")?.let {
            setAtlasLayerOpacity(style, AtlasLayerIds.TRACK_LAYER, it.opacity)
        }
        compositor.getLayer("measure")?.let {
            setAtlasLayerOpacity(style, AtlasLayerIds.MEASURE_LAYER, it.opacity)
            setAtlasLayerOpacity(style, AtlasLayerIds.MEASURE_DOTS_LAYER, it.opacity)
        }
        compositor.getLayer("historical-dossiers")?.let {
            setAtlasLayerVisible(style, AtlasLayerIds.HISTORICAL_DOSSIERS_LAYER, it.isVisible)
            setAtlasLayerOpacity(style, AtlasLayerIds.HISTORICAL_DOSSIERS_LAYER, it.opacity)
        }
        compositor.getLayer("historical-patents")?.let {
            setAtlasLayerVisible(style, AtlasLayerIds.HISTORICAL_PATENTS_FILL, it.isVisible)
            setAtlasLayerVisible(style, AtlasLayerIds.HISTORICAL_PATENTS_BORDER, it.isVisible)
            setAtlasLayerOpacity(style, AtlasLayerIds.HISTORICAL_PATENTS_FILL, it.opacity * 0.45f)
            setAtlasLayerOpacity(style, AtlasLayerIds.HISTORICAL_PATENTS_BORDER, it.opacity)
        }
        compositor.getLayer("historical-blueprints")?.let { blueprintState ->
            for (layer in style.layers) {
                if (layer.id.startsWith(AtlasLayerIds.HISTORICAL_RASTER_LAYER_PREFIX)) {
                    setAtlasLayerVisible(style, layer.id, blueprintState.isVisible)
                    setAtlasLayerOpacity(style, layer.id, blueprintState.opacity)
                }
            }
        }
        compositor.getLayer("cot-markers")?.let {
            setAtlasLayerVisible(style, AtlasLayerIds.MARKER_LAYER, it.isVisible)
            setAtlasLayerOpacity(style, AtlasLayerIds.MARKER_LAYER, it.opacity)
        }
        compositor.getLayer("pli-mesh")?.let {
            setAtlasLayerVisible(style, AtlasLayerIds.PLI_LAYER, it.isVisible)
            setAtlasLayerVisible(style, AtlasLayerIds.MESH_TRACK_LAYER, it.isVisible)
            setAtlasLayerOpacity(style, AtlasLayerIds.PLI_LAYER, it.opacity)
            setAtlasLayerOpacity(style, AtlasLayerIds.MESH_TRACK_LAYER, it.opacity)
        }
        compositor.getLayer("ops-graphics")?.let {
            setAtlasLayerVisible(style, AtlasLayerIds.OPS_ZONE_LAYER, it.isVisible)
            setAtlasLayerVisible(style, AtlasLayerIds.OPS_LINE_LAYER, it.isVisible)
            setAtlasLayerOpacity(style, AtlasLayerIds.OPS_LINE_LAYER, it.opacity)
        }
        compositor.getLayer("fence")?.let {
            setAtlasLayerVisible(style, AtlasLayerIds.FENCE_LAYER, it.isVisible)
            setAtlasLayerOpacity(style, AtlasLayerIds.FENCE_LAYER, it.opacity)
        }
        compositor.getLayer("route")?.let {
            setAtlasLayerVisible(style, AtlasLayerIds.ROUTE_LAYER, it.isVisible)
            setAtlasLayerOpacity(style, AtlasLayerIds.ROUTE_LAYER, it.opacity)
        }
        syncBlendedProviderLayers(style, compositor, cartoKey)
    }
}

fun ensureBaseLayer(style: Style, providerId: String, key: String? = null) {
    val descriptor = OfflineBuiltinProviders.lookup(providerId) ?: return
    var template = descriptor.urlTemplate ?: return
    for ((param, value) in descriptor.params) {
        template = template.replace("{$param}", value)
    }
    template = template.replace("{key}", key ?: "")
    ensureBaseTemplate(style, template, descriptor.minZoom, descriptor.maxZoom)
}

fun ensureBaseTemplate(style: Style, template: String, minZoom: Int, maxZoom: Int) {
    removeBaseLayer(style)
    val tileSet = TileSet("2.2.0", template)
    tileSet.minZoom = minZoom.toFloat()
    tileSet.maxZoom = maxZoom.toFloat()
    style.addSource(RasterSource(BASE_SOURCE_ID, tileSet, 256))
    val layer = RasterLayer(BASE_LAYER_ID, BASE_SOURCE_ID)
    layer.minZoom = minZoom.toFloat()
    if (style.getLayer(AtlasLayerIds.WAYPOINTS_LAYER) != null) {
        style.addLayerBelow(layer, AtlasLayerIds.WAYPOINTS_LAYER)
    } else {
        style.addLayer(layer)
    }
}

fun removeBaseLayer(style: Style) {
    if (style.getLayer(BASE_LAYER_ID) != null) {
        style.removeLayer(BASE_LAYER_ID)
    }
    if (style.getSource(BASE_SOURCE_ID) != null) {
        style.removeSource(BASE_SOURCE_ID)
    }
    for (layer in style.layers) {
        val id = layer.id
        if (id.startsWith(AtlasLayerIds.HISTORICAL_FILL_PREFIX) ||
            id.startsWith(AtlasLayerIds.HISTORICAL_LINE_PREFIX)
        ) {
            style.removeLayer(id)
        }
    }
}

fun ensureVectorBaseTemplate(
    style: Style,
    template: String,
    minZoom: Int,
    maxZoom: Int,
    vectorLayerIds: List<String>,
) {
    removeBaseLayer(style)
    val tileSet = TileSet("2.2.0", template)
    tileSet.minZoom = minZoom.toFloat()
    tileSet.maxZoom = maxZoom.toFloat()
    style.addSource(VectorSource(BASE_SOURCE_ID, tileSet))
    val anchor = if (style.getLayer(AtlasLayerIds.MGRS_LINE_LAYER) != null) {
        AtlasLayerIds.MGRS_LINE_LAYER
    } else if (style.getLayer(AtlasLayerIds.WAYPOINTS_LAYER) != null) {
        AtlasLayerIds.WAYPOINTS_LAYER
    } else {
        null
    }
    for (internalLayerId in vectorLayerIds) {
        val fillLayerId = AtlasLayerIds.HISTORICAL_FILL_PREFIX + internalLayerId
        if (style.getLayer(fillLayerId) == null) {
            val fillLayer = FillLayer(fillLayerId, BASE_SOURCE_ID)
                .withSourceLayer(internalLayerId)
                .withProperties(
                    PropertyFactory.fillColor("#4A90E2"),
                    PropertyFactory.fillOpacity(0.4f),
                )
            addBelowAnchor(style, fillLayer, anchor)
        }
        val lineLayerId = AtlasLayerIds.HISTORICAL_LINE_PREFIX + internalLayerId
        if (style.getLayer(lineLayerId) == null) {
            val lineLayer = LineLayer(lineLayerId, BASE_SOURCE_ID)
                .withSourceLayer(internalLayerId)
                .withProperties(
                    PropertyFactory.lineColor("#003366"),
                    PropertyFactory.lineWidth(1.5f),
                )
            addBelowAnchor(style, lineLayer, anchor)
        }
    }
}

private fun addBelowAnchor(style: Style, layer: Layer, anchor: String?) {
    if (anchor == null) {
        style.addLayer(layer)
    } else {
        style.addLayerBelow(layer, anchor)
    }
}

fun syncHistoricalBlueprintLayers(
    style: Style,
    inView: List<SanbornBlueprint>,
) {
    val visible = inView.associateBy { blueprint -> historicalRasterLayerId(blueprint.id) }

    for (blueprint in inView) {
        if (blueprint.filePath.isEmpty()) continue
        val layerId = historicalRasterLayerId(blueprint.id)
        if (style.getLayer(layerId) != null) continue
        val tileSet = TileSet("2.2.0", "mbtiles://file://${blueprint.filePath}")
        blueprint.maxZoom?.let { tileSet.maxZoom = it }
        blueprint.minZoom?.let { tileSet.minZoom = it }
        style.addSource(RasterSource(historicalRasterSourceId(blueprint.id), tileSet))
        style.addLayer(RasterLayer(layerId, historicalRasterSourceId(blueprint.id)))
    }

    for (layer in style.layers) {
        val id = layer.id
        if (!id.startsWith(AtlasLayerIds.HISTORICAL_RASTER_LAYER_PREFIX)) continue
        val shouldShow = visible.containsKey(id)
        val current = layer.visibility?.value
        val target = if (shouldShow) Property.VISIBLE else Property.NONE
        if (current == target) continue
        layer.setProperties(PropertyFactory.visibility(target))
    }
}

fun historicalRasterSourceId(assetId: String): String =
    AtlasLayerIds.HISTORICAL_RASTER_SOURCE_PREFIX + mbtilesSafeId(assetId)

fun historicalRasterLayerId(assetId: String): String =
    AtlasLayerIds.HISTORICAL_RASTER_LAYER_PREFIX + mbtilesSafeId(assetId)

fun ensureMbtilesPackLayer(style: Style, services: AtlasServices, packId: String) {
    val sourceId = mbtilesSourceId(packId)
    if (style.getSource(sourceId) != null) return
    val url = services.tiles.tileUrl(packId) ?: return
    val tileSet = TileSet("2.2.0", url)
    services.offline.lookup(packId)?.zMax?.let { tileSet.maxZoom = it.toFloat() }
    style.addSource(RasterSource(sourceId, tileSet))
    val layer = RasterLayer(mbtilesLayerId(packId), sourceId)
    if (style.getLayer(AtlasLayerIds.WAYPOINTS_LAYER) != null) {
        style.addLayerBelow(layer, AtlasLayerIds.WAYPOINTS_LAYER)
    } else {
        style.addLayer(layer)
    }
}

fun removeMbtilesPackLayer(style: Style, packId: String) {
    val layerId = mbtilesLayerId(packId)
    if (style.getLayer(layerId) != null) {
        style.removeLayer(layerId)
    }
    val sourceId = mbtilesSourceId(packId)
    if (style.getSource(sourceId) != null) {
        style.removeSource(sourceId)
    }
}

fun mountedMbtilesSafeIds(style: Style): Set<String> {
    return style.layers
        .map { it.id }
        .filter { it.startsWith(AtlasLayerIds.MBTILES_LAYER_ID_PREFIX) }
        .map { it.removePrefix(AtlasLayerIds.MBTILES_LAYER_ID_PREFIX) }
        .toSet()
}

fun syncMbtilesPackLayers(style: Style, services: AtlasServices, activePacks: Set<String>) {
    val wanted = activePacks.map { mbtilesSafeId(it) }.toSet()
    for (safeId in mountedMbtilesSafeIds(style) - wanted) {
        removeMbtilesPackLayer(style, safeId)
    }
    for (packId in activePacks) {
        ensureMbtilesPackLayer(style, services, packId)
    }
}

fun ensureDemSource(style: Style, demUrl: String) {
    if (style.getSource(DEM_SOURCE_ID) != null) {
        style.removeSource(DEM_SOURCE_ID)
    }
    val tileSet = TileSet("2.2.0", demUrl)
    tileSet.minZoom = 0f
    tileSet.maxZoom = 15f
    tileSet.encoding = "mapbox"
    style.addSource(RasterDemSource(DEM_SOURCE_ID, tileSet, 256))
}

fun removeDemSource(style: Style) {
    if (style.getSource(DEM_SOURCE_ID) != null) {
        style.removeSource(DEM_SOURCE_ID)
    }
}

fun applyBaseSource(
    style: Style,
    services: AtlasServices,
    providerId: String,
    packId: String?,
    key: String? = null,
): String {
    if (packId != null) {
        val pack = services.offline.lookup(packId)
        val url = services.tiles.tileUrl(packId)
        if (pack != null && url != null) {
            ensureBaseTemplate(style, url, pack.zMin, pack.zMax)
            return "${pack.providerTitle} (offline)"
        }
    }
    ensureBaseLayer(style, providerId, key)
    return OfflineBuiltinProviders.lookup(providerId)?.attribution ?: ""
}

fun isCartoProvider(providerId: String): Boolean {
    return providerId == "carto-positron" || providerId == "carto-dark-matter"
}

fun syncUserGisLayers(style: Style, datasets: List<GisDataset>) {
    val activeIds = datasets.map { it.id }.toSet()
    val existingSourceIds = style.sources
        .map { it.id }
        .filter { it.startsWith(AtlasLayerIds.USER_GIS_SOURCE_PREFIX) }
        .map { it.removePrefix(AtlasLayerIds.USER_GIS_SOURCE_PREFIX) }
        .toSet()

    for (oldId in existingSourceIds - activeIds) {
        val fillId = "${AtlasLayerIds.USER_GIS_FILL_PREFIX}$oldId"
        val lineId = "${AtlasLayerIds.USER_GIS_LINE_PREFIX}$oldId"
        val circleId = "${AtlasLayerIds.USER_GIS_CIRCLE_PREFIX}$oldId"
        val sourceId = "${AtlasLayerIds.USER_GIS_SOURCE_PREFIX}$oldId"
        if (style.getLayer(fillId) != null) style.removeLayer(fillId)
        if (style.getLayer(lineId) != null) style.removeLayer(lineId)
        if (style.getLayer(circleId) != null) style.removeLayer(circleId)
        if (style.getSource(sourceId) != null) style.removeSource(sourceId)
    }

    for (dataset in datasets) {
        val sourceId = "${AtlasLayerIds.USER_GIS_SOURCE_PREFIX}${dataset.id}"
        val fillId = "${AtlasLayerIds.USER_GIS_FILL_PREFIX}${dataset.id}"
        val lineId = "${AtlasLayerIds.USER_GIS_LINE_PREFIX}${dataset.id}"
        val circleId = "${AtlasLayerIds.USER_GIS_CIRCLE_PREFIX}${dataset.id}"

        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId))
        }

        val visibility = if (dataset.isVisible) Property.VISIBLE else Property.NONE

        if (style.getLayer(fillId) == null) {
            val fillLayer = FillLayer(fillId, sourceId)
            fillLayer.setProperties(
                PropertyFactory.fillColor(Color.parseColor(dataset.style.fillColorHex)),
                PropertyFactory.visibility(visibility),
            )
            style.addLayer(fillLayer)
        } else {
            style.getLayer(fillId)?.setProperties(
                PropertyFactory.fillColor(Color.parseColor(dataset.style.fillColorHex)),
                PropertyFactory.visibility(visibility),
            )
        }

        if (style.getLayer(lineId) == null) {
            val lineLayer = LineLayer(lineId, sourceId)
            lineLayer.setProperties(
                PropertyFactory.lineColor(Color.parseColor(dataset.style.strokeColorHex)),
                PropertyFactory.lineWidth(dataset.style.strokeWidthDp),
                PropertyFactory.visibility(visibility),
            )
            style.addLayer(lineLayer)
        } else {
            style.getLayer(lineId)?.setProperties(
                PropertyFactory.lineColor(Color.parseColor(dataset.style.strokeColorHex)),
                PropertyFactory.lineWidth(dataset.style.strokeWidthDp),
                PropertyFactory.visibility(visibility),
            )
        }

        if (style.getLayer(circleId) == null) {
            val circleLayer = CircleLayer(circleId, sourceId)
            circleLayer.setProperties(
                PropertyFactory.circleColor(Color.parseColor(dataset.style.pointColorHex)),
                PropertyFactory.circleRadius(dataset.style.pointRadiusDp),
                PropertyFactory.visibility(visibility),
            )
            style.addLayer(circleLayer)
        } else {
            style.getLayer(circleId)?.setProperties(
                PropertyFactory.circleColor(Color.parseColor(dataset.style.pointColorHex)),
                PropertyFactory.circleRadius(dataset.style.pointRadiusDp),
                PropertyFactory.visibility(visibility),
            )
        }
    }
}
