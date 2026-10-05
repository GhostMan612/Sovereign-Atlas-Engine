// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.cot

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.cot.CotMarker
import com.sovereignatlas.atlas.geo.render.RenderFeature
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty

/**
 * CoT markers to renderable features.
 *
 * WAS WRITTEN AGAINST `org.maplibre.geojson`. It now emits pure types, so the mesh
 * marker path no longer imports a renderer at all. The Phase 0 audit flagged this as
 * one of only two mappers that were properly namespaced; the other is
 * [com.sovereignatlas.atlas.map.graphics.GraphicsGeoJsonMapper].
 */
object CotGeoJsonMapper {

    /**
     * Derives a simple affiliation string from a standard CoT type.
     *
     * The taxonomy is the third letter onward, NOT "a- is friendly" — see
     * [com.sovereignatlas.atlas.geo.cot.CotTrackKind] for the full rule and why
     * filing a hostile as a friendly is the error the whole classification exists to
     * prevent. `b-m-p-w` and anything unrecognised fall to `unknown`, which is what
     * puts an unknown marker on the unknown icon rather than a friendly one.
     */
    fun deriveAffiliation(cotType: String): String = when {
        cotType.startsWith("a-f") -> "friendly"
        cotType.startsWith("a-h") -> "hostile"
        cotType.startsWith("a-n") -> "neutral"
        else -> "unknown"
    }

    /**
     * Mesh-track features: one per marker, carrying the affiliation the style turns
     * into an icon name.
     *
     * The uid IS the feature id rather than a property named "uid". Hit-testing
     * recovers an id to act on, and reading it back out of a property bag is how a
     * mapper that forgets the property produces an unpickable feature.
     */
    fun toRenderFeatures(markers: List<CotMarker>): RenderFeatureCollection =
        RenderFeatureCollection(
            markers.map { marker ->
                RenderFeature(
                    geometry = RenderGeometry.Point(LngLat(marker.longitude, marker.latitude)),
                    id = marker.uid,
                    properties = mapOf(
                        "callsign" to RenderProperty.Text(marker.callsign),
                        "affiliation" to RenderProperty.Text(deriveAffiliation(marker.type)),
                    ),
                )
            },
        )
}
