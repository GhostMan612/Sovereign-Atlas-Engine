// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.geo.render.RenderFeature
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty

enum class GisFormat(val extension: String, val displayName: String) {
    GEOPACKAGE("gpkg", "OGC GeoPackage"),
    KML("kml", "KML"),
    KMZ("kmz", "KMZ Archive"),
    GEOJSON("geojson", "GeoJSON"),
    SHAPEFILE("zip", "Zipped Shapefile"),
}

enum class GisGeometryType {
    POINT,
    LINE_STRING,
    POLYGON,
    MULTI_POLYGON,
}

enum class GisSrid(val srid: Int, val authName: String, val displayName: String) {
    WGS84(4326, "EPSG", "WGS 84"),
    WEB_MERCATOR(3857, "EPSG", "Web Mercator"),
    UTM(0, "EPSG", "UTM Zone"),
    UNKNOWN(-1, "UNKNOWN", "Unsupported CRS"),
}

data class GisStyle(
    val strokeColorHex: String = "#39C5F3",
    val strokeWidthDp: Float = 2.0f,
    val fillColorHex: String = "#2039C5F3",
    val pointColorHex: String = "#39C5F3",
    val pointRadiusDp: Float = 6.0f,
)

data class GisFeature(
    val id: String,
    val geometry: RenderGeometry,
    val properties: Map<String, RenderProperty> = emptyMap(),
) {
    fun toRenderFeature(): RenderFeature = RenderFeature(
        geometry = geometry,
        id = id,
        properties = properties,
    )
}

data class GisDataset(
    val id: String,
    val name: String,
    val format: GisFormat,
    val srid: GisSrid,
    val featureCount: Int,
    val features: List<GisFeature>,
    val style: GisStyle = GisStyle(),
    val isVisible: Boolean = true,
    val sourcePathOrUri: String? = null,
    val timestampEpochMs: Long = System.currentTimeMillis(),
) {
    fun toRenderFeatureCollection(): RenderFeatureCollection =
        RenderFeatureCollection(features.map { it.toRenderFeature() })
}

data class GisImportResult(
    val dataset: GisDataset?,
    val errorMessage: String? = null,
    val isSuccess: Boolean = dataset != null,
)
