// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.viewshed

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.render.RenderGeometry

data class ViewshedConfig(
    val observer: AtlasCoordinate,
    val observerHeightAglMeters: Double = 2.0,
    val targetHeightAglMeters: Double = 0.0,
    val maxRadiusMeters: Double = 10000.0,
    val rayStepMeters: Double = 50.0,
    val angularStepDeg: Double = 2.0,
)

data class ViewshedSample(
    val coordinate: AtlasCoordinate,
    val distanceMeters: Double,
    val terrainElevationMeters: Double,
    val isVisible: Boolean,
)

data class ViewshedRay(
    val bearingDeg: Double,
    val maxVisibleDistanceMeters: Double,
    val horizonCoordinate: AtlasCoordinate,
    val isObscuredAtMaxRange: Boolean,
    val samples: List<ViewshedSample> = emptyList(),
)

data class ViewshedResult(
    val config: ViewshedConfig,
    val observerElevationMeters: Double,
    val rays: List<ViewshedRay>,
    val visiblePerimeter: List<AtlasCoordinate>,
    val maxRangeCircle: List<AtlasCoordinate>,
) {
    fun toVisiblePolygon(): RenderGeometry.Polygon {
        val outer = ArrayList<LngLat>()
        val center = LngLat(config.observer.longitude, config.observer.latitude)
        outer.add(center)
        for (coord in visiblePerimeter) {
            outer.add(LngLat(coord.longitude, coord.latitude))
        }
        if (outer.size > 2) {
            outer.add(outer.first())
            return RenderGeometry.Polygon(listOf(outer))
        }
        val circleRing = maxRangeCircle.map { LngLat(it.longitude, it.latitude) }
        return RenderGeometry.Polygon(listOf(circleRing))
    }

    fun toBoundaryLine(): RenderGeometry.LineString {
        val points = visiblePerimeter.map { LngLat(it.longitude, it.latitude) }
        val closed = if (points.size >= 2 && points.first() != points.last()) {
            points + points.first()
        } else {
            points
        }
        return if (closed.size >= 2) {
            RenderGeometry.LineString(closed)
        } else {
            val c = LngLat(config.observer.longitude, config.observer.latitude)
            RenderGeometry.LineString(listOf(c, c))
        }
    }

    fun toObscuredPolygon(): RenderGeometry.Polygon {
        val outerCircle = ArrayList<LngLat>()
        for (coord in maxRangeCircle) {
            outerCircle.add(LngLat(coord.longitude, coord.latitude))
        }
        if (outerCircle.isNotEmpty() && outerCircle.first() != outerCircle.last()) {
            outerCircle.add(outerCircle.first())
        }

        val holeHole = ArrayList<LngLat>()
        for (coord in visiblePerimeter) {
            holeHole.add(LngLat(coord.longitude, coord.latitude))
        }
        if (holeHole.isNotEmpty() && holeHole.first() != holeHole.last()) {
            holeHole.add(holeHole.first())
        }

        val rings = ArrayList<List<LngLat>>()
        if (outerCircle.size >= 4) {
            rings.add(outerCircle)
            if (holeHole.size >= 4) {
                rings.add(holeHole)
            }
        }
        return if (rings.isNotEmpty()) {
            RenderGeometry.Polygon(rings)
        } else {
            RenderGeometry.Polygon(listOf(outerCircle))
        }
    }
}
