// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.UUID

object GeoJsonParser {

    const val MAX_VERTICES = 100_000

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(
        jsonString: String,
        datasetId: String = UUID.randomUUID().toString(),
        datasetName: String = "GeoJSON Layer",
        sourcePathOrUri: String? = null,
    ): GisDataset {
        val root = json.parseToJsonElement(jsonString)
        val features = ArrayList<GisFeature>()
        var vertexCount = 0

        fun checkVertexBudget() = vertexCount < MAX_VERTICES

        when (root) {
            is JsonObject -> {
                val type = (root["type"] as? JsonPrimitive)?.content
                when (type) {
                    "FeatureCollection" -> {
                        val array = root["features"] as? JsonArray
                        if (array != null) {
                            for (item in array) {
                                if (item is JsonObject) {
                                    val f = parseFeature(item, features.size)
                                    if (f != null && checkVertexBudget()) {
                                        features.add(f)
                                        vertexCount += f.geometry.allCoordinates().size
                                    }
                                }
                            }
                        }
                    }
                    "Feature" -> {
                        val f = parseFeature(root, 0)
                        if (f != null) features.add(f)
                    }
                    else -> {
                        val geom = parseGeometry(root)
                        if (geom != null) {
                            features.add(
                                GisFeature(
                                    id = "feature-0",
                                    geometry = geom,
                                    properties = emptyMap(),
                                ),
                            )
                        }
                    }
                }
            }
            is JsonArray -> {
                for (item in root) {
                    if (item is JsonObject && checkVertexBudget()) {
                        val f = parseFeature(item, features.size)
                        if (f != null) {
                            features.add(f)
                            vertexCount += f.geometry.allCoordinates().size
                        }
                    }
                }
            }
            else -> {}
        }

        return GisDataset(
            id = datasetId,
            name = datasetName,
            format = GisFormat.GEOJSON,
            srid = GisSrid.WGS84,
            featureCount = features.size,
            features = features,
            sourcePathOrUri = sourcePathOrUri,
        )
    }

    private fun parseFeature(obj: JsonObject, index: Int): GisFeature? {
        val geomObj = obj["geometry"] as? JsonObject ?: return null
        val geometry = parseGeometry(geomObj) ?: return null
        val idVal = (obj["id"] as? JsonPrimitive)?.content ?: "feature-$index"

        val propObj = obj["properties"] as? JsonObject
        val props = HashMap<String, RenderProperty>()
        if (propObj != null) {
            for ((key, value) in propObj) {
                if (value is JsonPrimitive) {
                    val bool = value.booleanOrNull
                    val num = value.doubleOrNull
                    when {
                        bool != null -> props[key] = RenderProperty.Flag(bool)
                        num != null -> props[key] = RenderProperty.Number(num)
                        else -> props[key] = RenderProperty.Text(value.content)
                    }
                }
            }
        }

        return GisFeature(
            id = idVal,
            geometry = geometry,
            properties = props,
        )
    }

    private fun parseGeometry(obj: JsonObject): RenderGeometry? {
        val type = (obj["type"] as? JsonPrimitive)?.content ?: return null
        val coords = obj["coordinates"] ?: return null

        return when (type) {
            "Point" -> parsePoint(coords)
            "LineString" -> parseLineString(coords)
            "Polygon" -> parsePolygon(coords)
            "MultiPolygon" -> parseMultiPolygon(coords)
            "MultiPoint" -> parseMultiPoint(coords)
            "MultiLineString" -> parseMultiLineString(coords)
            else -> null
        }
    }

    private fun parsePoint(coords: JsonElement): RenderGeometry.Point? {
        val arr = coords as? JsonArray ?: return null
        val pt = parseCoord(arr) ?: return null
        return RenderGeometry.Point(pt)
    }

    private fun parseLineString(coords: JsonElement): RenderGeometry.LineString? {
        val arr = coords as? JsonArray ?: return null
        val pts = ArrayList<LngLat>(arr.size)
        for (item in arr) {
            val pt = parseCoord(item as? JsonArray) ?: continue
            pts.add(pt)
        }
        if (pts.size < 2) return null
        return RenderGeometry.LineString(pts)
    }

    private fun parsePolygon(coords: JsonElement): RenderGeometry.Polygon? {
        val ringsArr = coords as? JsonArray ?: return null
        val rings = ArrayList<List<LngLat>>(ringsArr.size)
        for (ringElem in ringsArr) {
            val ringArr = ringElem as? JsonArray ?: continue
            val ringPts = ArrayList<LngLat>(ringArr.size)
            for (item in ringArr) {
                val pt = parseCoord(item as? JsonArray) ?: continue
                ringPts.add(pt)
            }
            if (ringPts.isNotEmpty()) {
                val first = ringPts.first()
                val last = ringPts.last()
                if (first != last) {
                    ringPts.add(first)
                }
                rings.add(ringPts)
            }
        }
        if (rings.isEmpty()) return null
        return RenderGeometry.Polygon(rings)
    }

    private fun parseMultiPolygon(coords: JsonElement): RenderGeometry.MultiPolygon? {
        val polyArr = coords as? JsonArray ?: return null
        val polys = ArrayList<List<List<LngLat>>>(polyArr.size)
        for (pElem in polyArr) {
            val ringsArr = pElem as? JsonArray ?: continue
            val rings = ArrayList<List<LngLat>>(ringsArr.size)
            for (ringElem in ringsArr) {
                val ringArr = ringElem as? JsonArray ?: continue
                val ringPts = ArrayList<LngLat>(ringArr.size)
                for (item in ringArr) {
                    val pt = parseCoord(item as? JsonArray) ?: continue
                    ringPts.add(pt)
                }
                if (ringPts.isNotEmpty()) {
                    val first = ringPts.first()
                    val last = ringPts.last()
                    if (first != last) {
                        ringPts.add(first)
                    }
                    rings.add(ringPts)
                }
            }
            if (rings.isNotEmpty()) {
                polys.add(rings)
            }
        }
        if (polys.isEmpty()) return null
        return RenderGeometry.MultiPolygon(polys)
    }

    private fun parseMultiPoint(coords: JsonElement): RenderGeometry.Point? {
        val arr = coords as? JsonArray ?: return null
        val first = arr.firstOrNull() as? JsonArray ?: return null
        val pt = parseCoord(first) ?: return null
        return RenderGeometry.Point(pt)
    }

    private fun parseMultiLineString(coords: JsonElement): RenderGeometry.LineString? {
        val arr = coords as? JsonArray ?: return null
        val first = arr.firstOrNull() as? JsonArray ?: return null
        val pts = ArrayList<LngLat>(first.size)
        for (item in first) {
            val pt = parseCoord(item as? JsonArray) ?: continue
            pts.add(pt)
        }
        if (pts.size < 2) return null
        return RenderGeometry.LineString(pts)
    }

    private fun parseCoord(arr: JsonArray?): LngLat? {
        if (arr == null || arr.size < 2) return null
        val lon = (arr[0] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lat = (arr[1] as? JsonPrimitive)?.doubleOrNull ?: return null
        if (!lon.isFinite() || !lat.isFinite()) return null
        if (lon < -180.0 || lon > 180.0 || lat < -90.0 || lat > 90.0) return null
        return LngLat(lon, lat)
    }
}
