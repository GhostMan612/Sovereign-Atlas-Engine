// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.render

import com.sovereignatlas.atlas.core.LngLat

/**
 * GeoJSON geometry, in pure Kotlin.
 *
 * WHY THIS EXISTS RATHER THAN USING `org.maplibre.geojson.Point`. The audit of
 * `map/` found that exactly five MapLibre types actually crossed the rendering
 * boundary as types: `Style`, `Feature`, `FeatureCollection`, `Layer`, and
 * `Geometry`. Three of those five are geometry. Each one is a renderer library's
 * internal representation of something RFC 7946 already defines, so importing them
 * into `geo/` would put a renderer vocabulary in the tier that has to stay portable
 * — which is the entire point of extracting this seam to unblock the Phase 12
 * Plugin SDK. A plugin compiled against MapLibre's `Point` could not load here.
 *
 * `core.GeoJsonGeometry` already models three of these shapes. It was not reused
 * because it is scoped to land patent surveys (its own documentation says so) and it
 * has no `LineString` — which is most of what this map draws. Extending it would
 * have meant widening a type whose contract is deliberately narrow, and the ring
 * orientation rules differ: patent exterior rings are wound one way, GeoJSON
 * requires the other.
 *
 * Ring order follows RFC 7946 exactly, because the conversion to MapLibre is
 * mechanical and a wrong winding here would render a hole as a fill:
 * exterior ring first, then interior rings, and the first and last position of a
 * closed ring are identical.
 */
sealed class RenderGeometry {

    /** A single position. */
    data class Point(val coordinates: LngLat) : RenderGeometry()

    /** Two or more positions. A single-position line is a [Point] and always was. */
    data class LineString(val coordinates: List<LngLat>) : RenderGeometry() {
        init {
            require(coordinates.size >= 2) {
                "a LineString needs at least two positions, got ${coordinates.size}"
            }
        }
    }

    /** Exterior ring first, then any interior rings (holes). */
    data class Polygon(val coordinates: List<List<LngLat>>) : RenderGeometry() {
        init {
            require(coordinates.isNotEmpty()) { "a Polygon needs an exterior ring" }
        }
    }

    data class MultiPolygon(val coordinates: List<List<List<LngLat>>>) : RenderGeometry()

    /**
     * Every coordinate in traversal order.
     *
     * Bounds derivation and hit-testing both need this without caring which shape it
     * is, and re-implementing the `when` at each call site is how the traversal order
     * ends up inconsistent between them.
     */
    fun allCoordinates(): List<LngLat> = when (this) {
        is Point -> listOf(coordinates)
        is LineString -> coordinates
        is Polygon -> coordinates.flatten()
        is MultiPolygon -> coordinates.flatten().flatten()
    }

    /** Whether the shape has no coordinates at all. */
    fun isEmpty(): Boolean = allCoordinates().isEmpty()
}

/**
 * A property value carried from the domain to the renderer.
 *
 * A sealed union rather than `Any` or `Map<String, String>`. The previous encoding
 * used Gson's `JsonObject`, which meant a property bag could hold a nested object or
 * an array by accident and the style would fail to resolve it at paint time with no
 * compile-time warning. Three cases is what this map actually uses; adding a fourth
 * is a deliberate edit to this file rather than something a caller can do by reaching
 * for a raw map.
 */
sealed class RenderProperty {
    data class Text(val value: String) : RenderProperty()
    data class Number(val value: Double) : RenderProperty()
    data class Flag(val value: Boolean) : RenderProperty()
}

/**
 * One renderable thing: a shape plus the properties a style expression reads.
 *
 * The id is a first-class field rather than a property called "id". Every style in
 * this app selects on it for hit-testing, so making it a convention means a mapper
 * that forgets it produces an unpickable feature rather than a compile error. The
 * audit found `queryRenderedFeatures` used purely to recover an id that a mapper had
 * stashed in a bag.
 */
data class RenderFeature(
    val geometry: RenderGeometry,
    val id: String,
    val properties: Map<String, RenderProperty> = emptyMap(),
)

/**
 * A replaceable set of features for one layer.
 *
 * NAMED `RenderFeatureCollection`, NOT `FeatureCollection`, ON PURPOSE. The MapLibre
 * type of that name is the thing this seam exists to remove from the pure tier, and
 * an import alias would make two unrelated types look interchangeable at every call
 * site while converting between them. The distinct name makes each conversion
 * visible, and there are only a handful of them.
 *
 * REPLACE, NOT APPEND. Every one of the 28 push sites was a whole-layer overwrite,
 * and two of them pushed an EMPTY collection specifically to clear a layer. A sink
 * that could append would make clearing ambiguous and would let a layer accumulate
 * features that were withdrawn long ago.
 */
data class RenderFeatureCollection(val features: List<RenderFeature>) {

    companion object {
        val EMPTY = RenderFeatureCollection(emptyList())

        /** A collection of points, one per item. */
        fun points(items: List<Pair<String, LngLat>>): RenderFeatureCollection =
            RenderFeatureCollection(
                items.map { (id, at) -> RenderFeature(RenderGeometry.Point(at), id) },
            )
    }

    val isEmpty: Boolean get() = features.isEmpty()

    val size: Int get() = features.size
}
