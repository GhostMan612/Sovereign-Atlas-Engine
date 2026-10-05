// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.db.Waypoint
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGrids
import com.sovereignatlas.atlas.geo.AtlasRangeRings
import com.sovereignatlas.atlas.geo.MgrsGridLine
import com.sovereignatlas.atlas.geo.MgrsGridLabel
import com.sovereignatlas.atlas.geo.render.RenderFeature
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import com.sovereignatlas.atlas.track.parseTrackGeometry

/**
 * Domain objects to renderable features, in pure types.
 *
 * WAS A MapLibre MAPPER FILE. Every function here used to return
 * `org.maplibre.geojson.FeatureCollection`, which meant the conversion from domain to
 * map was written against a renderer's vocabulary — the coupling Phase 0 §0.4 exists
 * to remove. The return types are now [RenderFeatureCollection] and the actual
 * translation to MapLibre happens in exactly one place, [MapLibreFeatureSink].
 *
 * The `Log.w` calls are gone with the MapLibre dependency: the skip decisions they
 * reported are preserved as null returns, and the reason is stated at each site
 * rather than at runtime.
 */

/** Longitude-first, matching RFC 7946 and [LngLat]. */
private fun at(latitude: Double, longitude: Double) = LngLat(longitude, latitude)

private fun AtlasCoordinate.toLngLat() = LngLat(longitude, latitude)

/**
 * A waypoint as a symbol feature.
 *
 * `label` falls back to the id because the style renders `{label}` and a waypoint with
 * an empty name would otherwise draw an invisible symbol that still occupies the
 * layer. `sharingPolicy` is carried as a property so a plugin reading features back
 * out of the style can tell an exposed waypoint from a local one without a database
 * round trip.
 */
fun waypointToFeature(record: Waypoint): RenderFeature? {
    val label = record.name.ifEmpty { record.id }
    return RenderFeature(
        geometry = RenderGeometry.Point(at(record.latitude, record.longitude)),
        id = record.id,
        properties = mapOf(
            "name" to RenderProperty.Text(record.name),
            "label" to RenderProperty.Text(label),
            "sharingPolicy" to RenderProperty.Text(record.sharingPolicy),
        ),
    )
}

fun waypointsToFeatures(records: List<Waypoint>): RenderFeatureCollection =
    RenderFeatureCollection(records.mapNotNull { waypointToFeature(it) })

/**
 * A stored track as a line feature, or null when its geometry is unusable.
 *
 * A track with fewer than two parsed positions is skipped rather than emitted as a
 * degenerate line: [RenderGeometry.LineString] rejects that at construction, and a
 * corrupted track must not take down the whole layer's push.
 */
fun trackToFeature(track: Track): RenderFeature? {
    val points = parseTrackGeometry(track.geometry)
    if (points == null || points.size < 2) return null
    return RenderFeature(
        geometry = RenderGeometry.LineString(points.map { at(it.latitude, it.longitude) }),
        id = track.id,
        properties = mapOf("name" to RenderProperty.Text(track.name)),
    )
}

fun tracksToFeatures(tracks: List<Track>): RenderFeatureCollection =
    RenderFeatureCollection(tracks.mapNotNull { trackToFeature(it) })

fun mgrsLinesToFeatures(lines: List<MgrsGridLine>): RenderFeatureCollection =
    RenderFeatureCollection(
        lines.map { line ->
            RenderFeature(
                geometry = RenderGeometry.LineString(
                    line.coordinates.map { (latitude, longitude) -> at(latitude, longitude) },
                ),
                // Grid lines have no natural identity. The index is stable within one
                // generated grid, which is all the layer needs — nothing selects on
                // an MGRS line id.
                id = "mgrs-line-${line.coordinates.hashCode()}",
            )
        },
    )

fun mgrsLabelsToFeatures(labels: List<MgrsGridLabel>): RenderFeatureCollection =
    RenderFeatureCollection(
        labels.map { label ->
            RenderFeature(
                geometry = RenderGeometry.Point(at(label.latitude, label.longitude)),
                id = "mgrs-label-${label.text}",
                properties = mapOf("title" to RenderProperty.Text(label.text)),
            )
        },
    )

fun measureToFeatures(a: AtlasCoordinate, b: AtlasCoordinate): RenderFeatureCollection =
    RenderFeatureCollection(
        listOf(
            RenderFeature(
                geometry = RenderGeometry.LineString(
                    listOf(a.toLngLat(), b.toLngLat()),
                ),
                id = "measure",
            ),
        ),
    )

fun graticuleToFeatures(
    bounds: AtlasBoundingBox,
    interval: Double,
): RenderFeatureCollection {
    val grid = AtlasGrids.graticuleFor(bounds, interval)
    val features = ArrayList<RenderFeature>(grid.meridians.size + grid.parallels.size)
    for (meridian in grid.meridians) {
        features.add(
            RenderFeature(
                geometry = RenderGeometry.LineString(
                    listOf(at(bounds.south, meridian), at(bounds.north, meridian)),
                ),
                id = "meridian-$meridian",
            ),
        )
    }
    for (parallel in grid.parallels) {
        features.add(
            RenderFeature(
                geometry = RenderGeometry.LineString(
                    listOf(at(parallel, bounds.west), at(parallel, bounds.east)),
                ),
                id = "parallel-$parallel",
            ),
        )
    }
    return RenderFeatureCollection(features)
}

fun ringsToFeatures(
    center: AtlasCoordinate?,
    stepIndex: Int,
): RenderFeatureCollection {
    val set = AtlasRangeRings.generate(center, stepIndex)
    if (set.isEmpty) return RenderFeatureCollection.EMPTY
    val features = ArrayList<RenderFeature>(set.rings.size + set.spokes.size)
    for ((index, ring) in (set.rings + set.spokes).withIndex()) {
        features.add(
            RenderFeature(
                geometry = RenderGeometry.LineString(ring.map { it.toLngLat() }),
                id = "ring-$index",
            ),
        )
    }
    return RenderFeatureCollection(features)
}

fun positionToFeature(center: AtlasCoordinate): RenderFeature =
    positionToFeature(center, null)

/**
 * Own position, with the heading as a property.
 *
 * The style reads `bearing` and rotates the icon by it, so the value is a NUMBER here
 * and an expression there. Rendering it as a property rather than a fixed rotation is
 * what lets the puck turn without a layer rebuild.
 */
fun positionToFeature(center: AtlasCoordinate, bearingDeg: Double?): RenderFeature =
    RenderFeature(
        geometry = RenderGeometry.Point(center.toLngLat()),
        id = "position",
        properties = mapOf("bearing" to RenderProperty.Number(bearingDeg ?: 0.0)),
    )

fun goToToFeature(latitude: Double, longitude: Double, label: String): RenderFeature =
    RenderFeature(
        geometry = RenderGeometry.Point(at(latitude, longitude)),
        id = "goto",
        properties = mapOf("label" to RenderProperty.Text(label)),
    )
