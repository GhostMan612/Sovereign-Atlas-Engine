// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

/**
 * A WGS84 position in RFC 7946 order.
 *
 * Longitude first, latitude second. That ordering is not a style choice: RFC 7946
 * fixes it for every GeoJSON `coordinates` array, so a reversed pair here would be
 * a silent, geographically wrong read rather than a compile error.
 */
data class LngLat(val longitude: Double, val latitude: Double)

/**
 * GeoJSON geometry, limited to the three shapes a land patent survey produces.
 *
 * Deliberately NOT annotated with `@Serializable`. The module runs
 * kotlinx-serialization as a runtime library only; the compiler plugin is not
 * applied, so `@Serializable` would generate no serializer and could not be
 * decoded. LandPatentGeoJsonParser walks the JSON tree explicitly instead, which
 * keeps this package free of any build-plugin dependency.
 *
 * `Point` is included because a patent parcel may degenerate to a single
 * coordinate in sparse survey data, and dropping it would make such a feature
 * silently unparseable.
 */
sealed class GeoJsonGeometry {
    data class Point(val coordinates: LngLat) : GeoJsonGeometry()

    /** Exterior ring first, then interior rings (holes). */
    data class Polygon(val coordinates: List<List<LngLat>>) : GeoJsonGeometry()

    data class MultiPolygon(val coordinates: List<List<List<LngLat>>>) : GeoJsonGeometry()

    /**
     * Every coordinate in this geometry, in traversal order.
     *
     * Used to derive an asset's bounding box without a second parse.
     */
    fun allCoordinates(): List<LngLat> = when (this) {
        is Point -> listOf(coordinates)
        is Polygon -> coordinates.flatten()
        is MultiPolygon -> coordinates.flatten().flatten()
    }

    /** Extent of this geometry, or null when it carries no coordinates. */
    fun bounds(): AtlasBoundingBox? {
        val points = allCoordinates()
        if (points.isEmpty()) return null
        return AtlasBoundingBox(
            south = points.minOf { it.latitude },
            west = points.minOf { it.longitude },
            north = points.maxOf { it.latitude },
            east = points.maxOf { it.longitude },
        )
    }
}