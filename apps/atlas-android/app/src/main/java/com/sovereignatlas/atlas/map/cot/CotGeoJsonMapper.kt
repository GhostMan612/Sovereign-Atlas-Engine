// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.cot

import com.sovereignatlas.atlas.geo.cot.CotMarker
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object CotGeoJsonMapper {
    /**
     * Derives a simple affiliation string from a standard CoT type (e.g., "a-f-G" -> "friendly").
     */
    fun deriveAffiliation(cotType: String): String {
        return when {
            cotType.startsWith("a-f") -> "friendly"
            cotType.startsWith("a-h") -> "hostile"
            cotType.startsWith("a-n") -> "neutral"
            else -> "unknown"
        }
    }

    fun toFeatureCollection(markers: List<CotMarker>): FeatureCollection {
        val features = markers.map { marker ->
            val feature = Feature.fromGeometry(Point.fromLngLat(marker.longitude, marker.latitude))
            feature.addStringProperty("uid", marker.uid)
            feature.addStringProperty("callsign", marker.callsign)
            feature.addStringProperty("affiliation", deriveAffiliation(marker.type))
            feature
        }
        return FeatureCollection.fromFeatures(features)
    }
}
