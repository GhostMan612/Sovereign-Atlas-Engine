// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.historical

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private fun feature(
    title: String? = null,
    name: String? = null,
    description: String? = null,
    date: String? = null,
    year: String? = null,
    id: String? = null,
): Feature {
    val feature = Feature.fromGeometry(Point.fromLngLat(-93.1, 44.9))
    if (title != null) feature.addStringProperty("title", title)
    if (name != null) feature.addStringProperty("name", name)
    if (description != null) feature.addStringProperty("description", description)
    if (date != null) feature.addStringProperty("date", date)
    if (year != null) feature.addStringProperty("year", year)
    if (id != null) feature.addStringProperty("id", id)
    return feature
}

final class HistoricalFeatureMapperTest {
    @Test
    fun mapsCanonicalProperties() {
        val record = HistoricalFeatureMapper.fromFeature(
            feature(title = "Patent 4471", description = "Land patent", date = "1873", id = "p-1"),
            "patents-layer",
        )
        assertEquals("p-1", record.id)
        assertEquals("Patent 4471", record.title)
        assertEquals("Land patent", record.description)
        assertEquals("1873", record.date)
        assertEquals("patents-layer", record.sourceLayer)
    }

    @Test
    fun titleFallsBackToNameThenDefault() {
        assertEquals("Baghdad", HistoricalFeatureMapper.fromFeature(feature(name = "Baghdad"), "l").title)
        assertEquals("Unknown Entity", HistoricalFeatureMapper.fromFeature(feature(), "l").title)
    }

    @Test
    fun dateFallsBackToYearThenDefault() {
        assertEquals("1945", HistoricalFeatureMapper.fromFeature(feature(year = "1945"), "l").date)
        assertEquals("Unknown Date", HistoricalFeatureMapper.fromFeature(feature(), "l").date)
    }

    @Test
    fun missingTextPropertiesFallBackSafely() {
        val record = HistoricalFeatureMapper.fromFeature(feature(), "blueprints-layer")
        assertEquals("N/A", record.id)
        assertEquals("No details available.", record.description)
        assertEquals("blueprints-layer", record.sourceLayer)
    }

    @Test
    fun featureIdWinsOverIdProperty() {
        val properties = JsonObject().apply {
            addProperty("id", "property-id")
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(0.0, 0.0), properties, "feature-id")
        assertEquals("feature-id", HistoricalFeatureMapper.fromFeature(feature, "l").id)
    }

    @Test
    fun idPropertyUsedWhenFeatureHasNoId() {
        val properties = JsonObject().apply {
            addProperty("id", "property-id")
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(0.0, 0.0), properties)
        assertEquals("property-id", HistoricalFeatureMapper.fromFeature(feature, "l").id)
    }

    @Test
    fun mappingIsSideEffectFreeOnTheFeature() {
        val source = feature(title = "Parcel 12", id = "x-1")
        val first = HistoricalFeatureMapper.fromFeature(source, "parcels-layer")
        val second = HistoricalFeatureMapper.fromFeature(source, "parcels-layer")
        assertEquals(first, second)
    }
}
