// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.historical

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.core.GeoJsonGeometry
import com.sovereignatlas.atlas.core.HistoricalLicense
import com.sovereignatlas.atlas.core.LandPatent
import com.sovereignatlas.atlas.core.LngLat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalAssetMapperTest {

    @Test
    fun pointBecomesAMapLibrePoint() {
        val geometry = GeoJsonGeometry.Point(LngLat(-104.99, 39.74)).toMapLibreGeometry()

        val point = geometry as org.maplibre.geojson.Point
        assertEquals(-104.99, point.longitude(), 1e-9)
        assertEquals(39.74, point.latitude(), 1e-9)
    }

    @Test
    fun longitudeIsFlippedNotSwapped() {
        val geometry = GeoJsonGeometry.Point(LngLat(150.0, -30.0)).toMapLibreGeometry()

        val point = geometry as org.maplibre.geojson.Point
        assertEquals(150.0, point.longitude(), 1e-9)
        assertEquals(-30.0, point.latitude(), 1e-9)
    }

    @Test
    fun polygonBecomesAMapLibrePolygonWithTheRingOrderPreserved() {
        val geometry = GeoJsonGeometry.Polygon(
            listOf(
                listOf(
                    LngLat(-105.0, 39.0),
                    LngLat(-104.0, 39.0),
                    LngLat(-104.0, 40.0),
                    LngLat(-105.0, 39.0),
                ),
                listOf(
                    LngLat(-104.9, 39.1),
                    LngLat(-104.8, 39.1),
                    LngLat(-104.8, 39.2),
                    LngLat(-104.9, 39.1),
                ),
            ),
        ).toMapLibreGeometry()

        val polygon = geometry as org.maplibre.geojson.Polygon
        assertEquals(2, polygon.coordinates().size)
        val exterior = polygon.coordinates().first()
        assertEquals(-105.0, exterior.first().longitude(), 1e-9)
        assertEquals(39.0, exterior.first().latitude(), 1e-9)
        assertEquals(40.0, exterior[2].latitude(), 1e-9)
        // Interior ring follows the exterior, so a hole is not hoisted into slot 0.
        assertEquals(-104.9, polygon.coordinates()[1].first().longitude(), 1e-9)
    }

    @Test
    fun multiPolygonBecomesAMapLibreMultiPolygon() {
        val ring = listOf(LngLat(-171.0, -14.0), LngLat(-170.0, -14.0), LngLat(-170.0, -13.0))
        val geometry = GeoJsonGeometry.MultiPolygon(listOf(listOf(ring), listOf(ring)))
            .toMapLibreGeometry()

        val multi = geometry as org.maplibre.geojson.MultiPolygon
        assertEquals(2, multi.coordinates().size)
        assertEquals(4, multi.coordinates().first().first().size)
    }

    @Test
    fun anUnclosedRingIsClosedSoMapLibreDoesNotDropIt() {
        val geometry = GeoJsonGeometry.Polygon(
            listOf(
                listOf(
                    LngLat(-105.0, 39.0),
                    LngLat(-104.0, 39.0),
                    LngLat(-104.0, 40.0),
                ),
            ),
        ).toMapLibreGeometry()

        val ring = (geometry as org.maplibre.geojson.Polygon).coordinates().first()
        assertEquals(4, ring.size)
        assertEquals(ring.first().longitude(), ring.last().longitude(), 1e-9)
        assertEquals(ring.first().latitude(), ring.last().latitude(), 1e-9)
    }

    @Test
    fun anAlreadyClosedRingIsNotDoubled() {
        val geometry = GeoJsonGeometry.Polygon(
            listOf(
                listOf(
                    LngLat(-105.0, 39.0),
                    LngLat(-104.0, 39.0),
                    LngLat(-104.0, 40.0),
                    LngLat(-105.0, 39.0),
                ),
            ),
        ).toMapLibreGeometry()

        assertEquals(4, (geometry as org.maplibre.geojson.Polygon).coordinates().first().size)
    }

    @Test
    fun domainIdIsCarriedAsAPropertyForTapResolution() {
        assertEquals("lp-1", patent().toMapLibreFeature().domainAssetId())
    }

    @Test
    fun aRenderedFeatureIdIsPreferredOverTheProperty() {
        val feature = patent().toMapLibreFeature()
        val properties = com.google.gson.JsonObject()
        properties.addProperty(ASSET_ID_PROPERTY, "lp-1")
        val withId = org.maplibre.geojson.Feature.fromGeometry(
            feature.geometry(),
            properties,
            "lp-feature-id",
        )

        assertEquals("lp-feature-id", withId.domainAssetId())
    }

    @Test
    fun domainIdIsNullWhenNeitherCarrierIsPresent() {
        val bare = org.maplibre.geojson.Feature.fromGeometry(
            patent().toMapLibreFeature().geometry(),
        )

        assertNull(bare.domainAssetId())
    }

    @Test
    fun featureCarriesTheRecordedPatentProperties() {
        val feature = patent().toMapLibreFeature()

        assertEquals("1880-0042", feature.getStringProperty("patentNumber"))
        assertEquals("Abigail Vance", feature.getStringProperty("patenteeName"))
        assertEquals("1880-03-14", feature.getStringProperty("issueDate"))
    }

    @Test
    fun absentPropertiesAreOmittedRatherThanBlank() {
        val feature = patent(patenteeName = null, issueDate = null).toMapLibreFeature()

        assertNull(feature.getStringProperty("patenteeName"))
        assertNull(feature.getStringProperty("issueDate"))
    }

    @Test
    fun featureGeometryIsTheConvertedParcelOutline() {
        val feature = patent().toMapLibreFeature()

        val polygon = feature.geometry() as org.maplibre.geojson.Polygon
        assertEquals(1, polygon.coordinates().size)
    }

    @Test
    fun aCollectionPreservesEveryAssetInOrder() {
        val features = listOf(patent(), patent(id = "lp-2", number = "1891-0007"))
            .toMapLibreFeatureCollection()
            .features()
            .orEmpty()

        assertEquals(2, features.size)
        assertEquals("1880-0042", features.first().getStringProperty("patentNumber"))
        assertEquals("1891-0007", features.last().getStringProperty("patentNumber"))
    }

    @Test
    fun anEmptyCatalogueProducesACollectionWithNoFeatures() {
        val features = emptyList<LandPatent>().toMapLibreFeatureCollection().features().orEmpty()

        assertTrue(features.isEmpty())
        assertFalse(features.any { it.geometry() == null })
    }

    private fun patent(
        id: String = "lp-1",
        number: String = "1880-0042",
        patenteeName: String? = "Abigail Vance",
        issueDate: String? = "1880-03-14",
    ) = LandPatent(
        id = id,
        title = "Land Patent $number",
        year = 1880,
        boundingBox = AtlasBoundingBox(39.0, -105.0, 40.0, -104.0),
        license = HistoricalLicense.PublicDomainUsOnly(),
        patentNumber = number,
        patenteeName = patenteeName,
        issueDate = issueDate,
        geometry = GeoJsonGeometry.Polygon(
            listOf(
                listOf(
                    LngLat(-105.0, 39.0),
                    LngLat(-104.0, 39.0),
                    LngLat(-104.0, 40.0),
                    LngLat(-105.0, 39.0),
                ),
            ),
        ),
        attribution = "BLM GLO",
    )
}