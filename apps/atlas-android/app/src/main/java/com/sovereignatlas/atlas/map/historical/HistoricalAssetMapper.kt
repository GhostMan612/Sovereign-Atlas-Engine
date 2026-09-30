// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.historical

import com.sovereignatlas.atlas.core.GeoJsonGeometry
import com.sovereignatlas.atlas.core.LandPatent
import com.sovereignatlas.atlas.core.LngLat
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Translates pure historical geometry and assets into MapLibre GeoJSON objects.
 *
 * This is the boundary adapter between `core/`, which knows nothing of MapLibre,
 * and the map layer that renders it. Nothing here decides policy: the parcel
 * outline, its licence, and its provenance were already decided by the parser.
 * The only judgement is structural, and it is forced by MapLibre's own geometry
 * rules, not by preference.
 *
 * MapLibre requires a closed ring. GeoJSON from BLM survey data often omits the
 * repetition of the first vertex, so a ring is closed here when it is not already.
 * Without that, MapLibre drops the ring as invalid and the parcel renders as
 * nothing at all while the feature sits in the source looking healthy.
 */
fun GeoJsonGeometry.toMapLibreGeometry(): Geometry = when (this) {
    is GeoJsonGeometry.Point -> Point.fromLngLat(coordinates.longitude, coordinates.latitude)
    is GeoJsonGeometry.Polygon -> Polygon.fromLngLats(coordinates.map { ring -> ring.toClosedRing() })
    is GeoJsonGeometry.MultiPolygon ->
        MultiPolygon.fromLngLats(coordinates.map { polygon -> polygon.map { ring -> ring.toClosedRing() } })
}

/**
 * Closes a ring by repeating its first vertex, unless it is already closed or too
 * short to have a meaningful first vertex. A ring of one point is returned
 * unchanged rather than closed onto itself, since that produces a zero-length line
 * MapLibre would reject.
 */
private fun List<LngLat>.toClosedRing(): List<Point> {
    val points = map { Point.fromLngLat(it.longitude, it.latitude) }
    if (points.size < 2) return points
    val first = points.first()
    val last = points.last()
    val isClosed = first.longitude() == last.longitude() && first.latitude() == last.latitude()
    return if (isClosed) points else points + first
}

/**
 * Converts a patent to a renderable feature.
 *
 * Properties are carried as strings because MapLibre's expression language has no
 * null literal: an absent `patenteeName` must be omitted from the property bag
 * rather than written as an empty string, or a popup would present a blank field as
 * if the surveyor had recorded nothing for that patentee. Unknown beats invented,
 * so nothing is filled in here.
 *
 * The year is omitted rather than coerced to 0, since the parser already uses 0 to
 * mean "no issue date recorded" and 0 AD is a fact this map must not assert.
 */
fun LandPatent.toMapLibreFeature(): Feature {
    // The domain id is carried as a PROPERTY, not as the GeoJSON feature id. There
    // is no Feature.fromGeometry(Geometry, String) overload to set an id directly,
    // and the overloads that do take one require a gson JsonObject, which this
    // mapper has no business constructing. A property survives every source
    // serialisation path, and a tap that cannot resolve an id leaves a plainly
    // visible parcel inert.
    val feature = Feature.fromGeometry(geometry.toMapLibreGeometry())
    feature.addStringProperty(ASSET_ID_PROPERTY, id)
    feature.addStringProperty("patentNumber", patentNumber)
    patenteeName?.let { feature.addStringProperty("patenteeName", it) }
    issueDate?.let { feature.addStringProperty("issueDate", it) }
    feature.addStringProperty("state", state ?: UNKNOWN_FIELD)
    feature.addStringProperty("county", county ?: UNKNOWN_FIELD)
    feature.addStringProperty("attribution", attribution ?: UNKNOWN_FIELD)
    return feature
}

private const val UNKNOWN_FIELD = "Unknown"

/** Property carrying the domain id, mirrored from the GeoJSON feature id. */
const val ASSET_ID_PROPERTY = "assetId"

/**
 * Reads the domain id back off a rendered feature. Feature id is preferred, with
 * the property as the fallback, so a tap resolves even when the source stripped
 * the feature id.
 */
fun Feature.domainAssetId(): String? = id() ?: getStringProperty(ASSET_ID_PROPERTY)

/** Builds the collection pushed to the patents source in one map step. */
fun List<LandPatent>.toMapLibreFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(map { patent -> patent.toMapLibreFeature() })