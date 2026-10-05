// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.graphics

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.graphics.OperationalGraphic
import com.sovereignatlas.atlas.geo.render.RenderFeature
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty

/**
 * Operator tactical drawings to renderable features.
 *
 * WAS WRITTEN AGAINST `org.maplibre.geojson`. Both shapes now come out as pure
 * [RenderGeometry], and the single conversion to MapLibre happens in
 * [com.sovereignatlas.atlas.map.MapLibreFeatureSink].
 *
 * THE RING-CLOSURE RULE IS UNCHANGED AND STILL HAPPENS HERE, not in the sink. RFC 7946
 * requires a linear ring's first and last position to be identical, and the style
 * silently drops a polygon that violates it. That is a domain decision about the
 * operator's drawing, so it belongs with the mapper rather than in a generic
 * converter that would have to guess.
 */
object GraphicsGeoJsonMapper {

    private fun pointOf(coordinate: AtlasCoordinate): LngLat =
        LngLat(coordinate.longitude, coordinate.latitude)

    fun toRenderFeatures(graphics: List<OperationalGraphic>): RenderFeatureCollection =
        RenderFeatureCollection(
            graphics.mapNotNull { graphic ->
                when (graphic) {
                    is OperationalGraphic.TacticalLine -> {
                        if (graphic.points.size < 2) return@mapNotNull null
                        RenderFeature(
                            geometry = RenderGeometry.LineString(
                                graphic.points.map { pointOf(it) },
                            ),
                            id = graphic.id,
                            properties = mapOf(
                                "color" to RenderProperty.Text(graphic.colorHex),
                                // Float to Double: RenderProperty.Number is a Double
                                // because a property bag holds numbers of any width,
                                // and Float would put a second numeric case in the
                                // union for no benefit at these magnitudes.
                                "width" to RenderProperty.Number(graphic.width.toDouble()),
                            ),
                        )
                    }
                    is OperationalGraphic.TacticalZone -> {
                        // Fewer than three points is not an area. Skipping it keeps the
                        // zone fill from drawing a degenerate sliver for a tap that
                        // was really a mis-tap.
                        if (graphic.points.size < 3) return@mapNotNull null
                        val ring = graphic.points.map { pointOf(it) }
                        val closed =
                            if (ring.first() == ring.last()) ring else ring + ring.first()
                        RenderFeature(
                            geometry = RenderGeometry.Polygon(listOf(closed)),
                            id = graphic.id,
                            properties = mapOf(
                                "fillColor" to RenderProperty.Text(graphic.type.hexColor),
                                "fillOpacity" to RenderProperty.Number(
                                    graphic.type.alpha.toDouble(),
                                ),
                            ),
                        )
                    }
                }
            },
        )
}
