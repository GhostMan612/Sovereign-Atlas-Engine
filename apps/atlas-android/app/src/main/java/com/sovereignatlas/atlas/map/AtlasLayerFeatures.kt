// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.google.gson.JsonObject
import com.sovereignatlas.atlas.field.StoredTrack
import com.sovereignatlas.atlas.field.StoredWaypoint
import com.sovereignatlas.atlas.geo.AtlasBoundingBox
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGrids
import com.sovereignatlas.atlas.geo.AtlasRangeRings
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

fun waypointPoint(record: StoredWaypoint): Point {
    return Point.fromLngLat(record.longitude, record.latitude)
}

fun waypointsToFeatures(records: List<StoredWaypoint>): FeatureCollection {
    return FeatureCollection.fromFeatures(
        records.map { record ->
            val properties = JsonObject()
            properties.addProperty("id", record.id)
            properties.addProperty("label", record.label)
            properties.addProperty(
                "source",
                com.sovereignatlas.atlas.field.waypointSourceName(record.source),
            )
            Feature.fromGeometry(waypointPoint(record), properties)
        },
    )
}

fun trackToFeatures(track: StoredTrack): FeatureCollection {
    return FeatureCollection.fromFeatures(
        listOf(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    track.points.map { point ->
                        Point.fromLngLat(point.longitude, point.latitude)
                    },
                ),
            ),
        ),
    )
}

fun measureToFeatures(a: AtlasCoordinate, b: AtlasCoordinate): FeatureCollection {
    return FeatureCollection.fromFeatures(
        listOf(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(a.longitude, a.latitude),
                        Point.fromLngLat(b.longitude, b.latitude),
                    ),
                ),
            ),
        ),
    )
}

fun graticuleToFeatures(
    bounds: AtlasBoundingBox,
    interval: Double,
): FeatureCollection {
    val grid = AtlasGrids.graticuleFor(bounds, interval)
    val features = ArrayList<Feature>(grid.meridians.size + grid.parallels.size)
    for (meridian in grid.meridians) {
        features.add(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(meridian, bounds.south),
                        Point.fromLngLat(meridian, bounds.north),
                    ),
                ),
            ),
        )
    }
    for (parallel in grid.parallels) {
        features.add(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(bounds.west, parallel),
                        Point.fromLngLat(bounds.east, parallel),
                    ),
                ),
            ),
        )
    }
    return FeatureCollection.fromFeatures(features)
}

fun ringsToFeatures(
    center: AtlasCoordinate?,
    stepIndex: Int,
): FeatureCollection {
    val set = AtlasRangeRings.generate(center, stepIndex)
    if (set.isEmpty) return FeatureCollection.fromFeatures(emptyList())
    val features = ArrayList<Feature>(set.rings.size + set.spokes.size)
    for (ring in set.rings + set.spokes) {
        features.add(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    ring.map { point ->
                        Point.fromLngLat(point.longitude, point.latitude)
                    },
                ),
            ),
        )
    }
    return FeatureCollection.fromFeatures(features)
}

fun positionToFeature(center: AtlasCoordinate): Feature {
    return Feature.fromGeometry(
        Point.fromLngLat(center.longitude, center.latitude),
    )
}

fun goToToFeature(latitude: Double, longitude: Double, label: String): Feature {
    val properties = JsonObject()
    properties.addProperty("label", label)
    return Feature.fromGeometry(
        Point.fromLngLat(longitude, latitude),
        properties,
    )
}
