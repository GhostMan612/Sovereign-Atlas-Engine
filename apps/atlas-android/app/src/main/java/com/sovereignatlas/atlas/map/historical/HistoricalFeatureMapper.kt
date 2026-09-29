// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.historical

import com.sovereignatlas.atlas.core.HistoricalRecord
import org.maplibre.geojson.Feature

object HistoricalFeatureMapper {
    fun fromFeature(feature: Feature, layerId: String): HistoricalRecord {
        val title = feature.getStringProperty("title")
            ?: feature.getStringProperty("name")
            ?: "Unknown Entity"
        val description = feature.getStringProperty("description") ?: "No details available."
        val date = feature.getStringProperty("date")
            ?: feature.getStringProperty("year")
            ?: "Unknown Date"
        val id = feature.id()
            ?: feature.getStringProperty("id")
            ?: "N/A"

        return HistoricalRecord(
            id = id,
            title = title,
            description = description,
            date = date,
            sourceLayer = layerId
        )
    }
}
