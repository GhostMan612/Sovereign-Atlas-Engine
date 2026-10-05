// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.google.gson.JsonObject
import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.render.AtlasLayer
import com.sovereignatlas.atlas.geo.render.FeatureSink
import com.sovereignatlas.atlas.geo.render.RenderFeature
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * The MapLibre implementation of [FeatureSink].
 *
 * WHERE THE CONVERSION LIVES, AND WHY HERE. The pure tier emits
 * [RenderFeatureCollection]; MapLibre needs `org.maplibre.geojson.FeatureCollection`.
 * That translation is the one thing in this system that has to know both vocabularies,
 * so it is isolated in a single file with a single entry point. Nothing else in
 * `map/` — and nothing at all in `geo/` — imports both.
 *
 * THE NULL SOURCE IS LOGGED, ALWAYS. This is the behaviour the audit found broken in
 * two of three places: `pushLosState` and the two ops-graphics pushes did
 * `getSourceAs<GeoJsonSource>(id) ?: return` and said nothing, which is
 * indistinguishable from "there is no data" once the log is silent. It is the
 * standing-facts failure mode — features reach their source, nothing draws, no
 * explanation. One implementation means one logging policy, and here it logs.
 *
 * Throws nothing. A missing source is a styling bug; turning it into an exception
 * would crash the map over a layer that simply is not installed yet.
 */
class MapLibreFeatureSink(
    private val styleProvider: () -> Style?,
) : FeatureSink {

    /**
     * The live style, for the few UI-tier callers that must mutate paint.
     *
     * NOT ON [FeatureSink], and that is the point. The audit found one place —
     * the LoS ray colour — that both pushes features and repaints a layer. Folding
     * paint into the interface would have made every renderer implement MapLibre
     * layer access, defeating the seam. So `pushLosState` casts, which is a visible
     * local cost paid once instead of a permanent tax on the abstraction.
     */
    fun currentStyle(): Style? = styleProvider()

    /**
     * A transitional path for the one source whose mapper has not been converted to
     * pure types yet.
     *
     * NOT ON [FeatureSink] ON PURPOSE. Putting it on the interface would let any
     * renderer accept MapLibre geometry, which is the coupling being removed. It
     * lives here, on the concrete class, so it is greppable and disappears when the
     * mapper is converted. The log behaviour is identical to [replace] — including
     * the warning on a missing source, which is the point of consolidating the two
     * silent call paths into one implementation.
     */
    fun replaceRaw(layer: AtlasLayer, collection: FeatureCollection) {
        val style = styleProvider()
        if (style == null) {
            android.util.Log.d(
                TAG,
                "replaceRaw(${layer.layerId}): style not ready, ${collection.features()?.size ?: 0} features dropped",
            )
            return
        }
        val source = style.getSourceAs<GeoJsonSource>(layer.sourceId)
        if (source == null) {
            android.util.Log.w(
                TAG,
                "replaceRaw(${layer.layerId}): source '${layer.sourceId}' is not on " +
                    "the style, features dropped",
            )
            return
        }
        source.setGeoJson(collection)
    }

    override fun replace(layer: AtlasLayer, features: RenderFeatureCollection) {
        val style = styleProvider()
        if (style == null) {
            // The style is not loaded yet. Normal during the gap between setStyle and
            // onStyleLoaded, and the audit found 23 long-lived listeners that re-read
            // the style from Compose state. Logging at debug level keeps it visible
            // without making the normal startup path look like a fault.
            android.util.Log.d(
                TAG,
                "replace(${layer.layerId}): style not ready, ${features.size} features dropped",
            )
            return
        }
        val source = style.getSourceAs<GeoJsonSource>(layer.sourceId)
        if (source == null) {
            android.util.Log.w(
                TAG,
                "replace(${layer.layerId}): source '${layer.sourceId}' is not on the " +
                    "style, ${features.size} features dropped",
            )
            return
        }
        source.setGeoJson(toMapLibre(features))
    }

    /** The single conversion point between the two geometry vocabularies. */
    private fun toMapLibre(collection: RenderFeatureCollection): FeatureCollection =
        FeatureCollection.fromFeatures(collection.features.map { toMapLibre(it) })

    /**
     * The pure id goes to MapLibre's ROOT id, not into the property bag.
     *
     * THE REGRESSION THIS FIXES. [RenderFeature.id] was a first-class field, and the
     * conversion only ever emitted the property bag — so a hit-test reading
     * `getStringProperty("id")` got null, selection silently cleared, and tapping a
     * waypoint did nothing. The paint was unaffected, which is why it looked like a
     * rendering success while interaction was broken.
     *
     * Root id is the right target, not a workaround. MapLibre's own
     * `queryRenderedFeatures` returns the id at the root, and it is what the renderer
     * uses to correlate a queried feature back to its source. `fromGeometry` has an
     * overload taking `(Geometry, JsonObject, String)` where that String is the id —
     * probed with `atlas_maplibre_probe` rather than assumed, because the three- and
     * four-argument overloads differ only in whether a BoundingBox is present.
     */
    private fun toMapLibre(feature: RenderFeature): Feature =
        Feature.fromGeometry(
            toMapLibre(feature.geometry),
            toJsonProperties(feature.properties),
            feature.id,
        )

    /**
     * Pure geometry to MapLibre geometry.
     *
     * The `LngLat` ordering flip happens here and nowhere else. [LngLat] is
     * longitude-first because RFC 7946 fixes that order for every `coordinates`
     * array, while MapLibre's `Point.fromLngLat` takes the arguments in the same
     * order — so the conversion is a reordering of FIELDS, not of values. Getting it
     * wrong is a silent, geographically wrong read rather than a compile error, which
     * is why it is worth naming in the code.
     */
    private fun toMapLibre(geometry: RenderGeometry) = when (geometry) {
        is RenderGeometry.Point ->
            Point.fromLngLat(geometry.coordinates.longitude, geometry.coordinates.latitude)
        is RenderGeometry.LineString ->
            LineString.fromLngLats(geometry.coordinates.map { it.toMapLibrePoint() })
        is RenderGeometry.Polygon ->
            Polygon.fromLngLats(geometry.coordinates.map { ring -> ring.map { it.toMapLibrePoint() } })
        is RenderGeometry.MultiPolygon ->
            org.maplibre.geojson.MultiPolygon.fromLngLats(
                geometry.coordinates.map { polygon ->
                    polygon.map { ring -> ring.map { it.toMapLibrePoint() } }
                },
            )
    }

    private fun LngLat.toMapLibrePoint(): Point =
        Point.fromLngLat(longitude, latitude)

    /**
     * Property bag to Gson's `JsonObject`.
     *
     * The `RenderProperty` union means only text, number, and boolean can reach this
     * line. That is the point: the previous code handed raw `JsonObject`s to the
     * style, so a mapper could put a nested object or an array in a property the
     * expression expected to be a number, and the layer would fail to paint with
     * nothing in the log.
     */
    private fun toJsonProperties(properties: Map<String, RenderProperty>): JsonObject {
        val json = JsonObject()
        for ((key, value) in properties) {
            when (value) {
                is RenderProperty.Text -> json.addProperty(key, value.value)
                is RenderProperty.Number -> json.addProperty(key, value.value)
                is RenderProperty.Flag -> json.addProperty(key, value.value)
            }
        }
        return json
    }

    private companion object {
        const val TAG = "AtlasFeatureSink"
    }
}
