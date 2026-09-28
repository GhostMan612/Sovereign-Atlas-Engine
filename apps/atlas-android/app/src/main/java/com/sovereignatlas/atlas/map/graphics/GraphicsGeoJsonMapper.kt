// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.graphics

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.graphics.OperationalGraphic
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object GraphicsGeoJsonMapper {
    private fun pointOf(coordinate: AtlasCoordinate): Point =
        Point.fromLngLat(coordinate.longitude, coordinate.latitude)

    fun toFeatureCollection(graphics: List<OperationalGraphic>): FeatureCollection {
        val features = graphics.mapNotNull { graphic ->
            when (graphic) {
                is OperationalGraphic.TacticalLine -> {
                    if (graphic.points.size < 2) return@mapNotNull null
                    val feature = Feature.fromGeometry(
                        LineString.fromLngLats(graphic.points.map { pointOf(it) }),
                    )
                    feature.addStringProperty("id", graphic.id)
                    feature.addStringProperty("color", graphic.colorHex)
                    feature.addNumberProperty("width", graphic.width)
                    feature
                }
                is OperationalGraphic.TacticalZone -> {
                    if (graphic.points.size < 3) return@mapNotNull null
                    val pts = graphic.points.map { pointOf(it) }
                    val ring = if (
                        pts.first().longitude() == pts.last().longitude() &&
                        pts.first().latitude() == pts.last().latitude()
                    ) {
                        pts
                    } else {
                        pts + pts.first()
                    }
                    val feature = Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
                    feature.addStringProperty("id", graphic.id)
                    feature.addStringProperty("fillColor", graphic.type.hexColor)
                    feature.addNumberProperty("fillOpacity", graphic.type.alpha)
                    feature
                }
            }
        }
        return FeatureCollection.fromFeatures(features)
    }
}
