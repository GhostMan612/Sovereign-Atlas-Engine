// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

final class LandPatentGeoJsonParserTest {
    private fun feature(
        id: String? = null,
        patent: String? = "1880-0042",
        geometry: String? = """{"type":"Polygon","coordinates":[[
            [-104.9,39.0],[-104.8,39.0],[-104.8,39.1],[-104.9,39.1],[-104.9,39.0]
        ]]}""",
        properties: String? = null,
    ): String {
        val idPart = if (id == null) "" else """"id":"$id","""
        val geomPart = if (geometry == null) "" else """"geometry":$geometry,"""
        val propsPart = properties?.let { ""","properties":$it""" } ?: ""
        return "{$idPart$geomPart\"type\":\"Feature\"$propsPart}"
    }

    private fun collection(vararg features: String) =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    @Test
    fun happyPathMapsTwoFeatures() {
        val json = collection(
            feature(
                id = "lp-1",
                properties = """{"patent_number":"1880-0042","patentee_name":"Ada Survey",
                    "issue_date":"1880-03-14","acreage":640.5,
                    "legal_description":"T4S R65E","state":"CO","county":"Denver",
                    "township":"Springfield"}""",
            ),
            feature(
                id = "lp-2",
                properties = """{"patent_number":"1881-0007","issue_date":"1881","acreage":160.0}""",
                geometry = """{"type":"Point","coordinates":[-104.75,39.05]}""",
            ),
        )

        val parsed = LandPatentGeoJsonParser.parse(json)

        assertEquals(2, parsed.size)
        val first = parsed[0]
        assertEquals("lp-1", first.id)
        assertEquals("1880-0042", first.patentNumber)
        assertEquals("Land Patent 1880-0042", first.title)
        assertEquals(1880, first.year)
        assertEquals("Ada Survey", first.patenteeName)
        assertEquals("1880-03-14", first.issueDate)
        assertEquals(640.5, first.acreage!!, 1e-9)
        assertEquals("Denver", first.county)
        assertEquals("Springfield", first.township)
        assertTrue(first.geometry is GeoJsonGeometry.Polygon)
        assertEquals(-104.9, first.boundingBox.west, 1e-9)
        assertEquals(39.1, first.boundingBox.north, 1e-9)

        val second = parsed[1]
        assertEquals("lp-2", second.id)
        assertEquals(1881, second.year)
        assertTrue(second.geometry is GeoJsonGeometry.Point)
        assertNull(second.patenteeName)
    }

    @Test
    fun emptyFeatureCollectionReturnsEmptyList() {
        val parsed = LandPatentGeoJsonParser.parse(
            """{"type":"FeatureCollection","features":[]}""",
        )
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun featureMissingGeometryIsSkipped() {
        val json = collection(
            feature(id = "no-geom", geometry = null, properties = """{"patent_number":"X"}"""),
            feature(id = "ok", properties = """{"patent_number":"1880-0042"}"""),
        )

        val parsed = LandPatentGeoJsonParser.parse(json)

        assertEquals(1, parsed.size)
        assertEquals("ok", parsed[0].id)
    }

    @Test
    fun malformedJsonReturnsEmptyListWithoutThrowing() {
        assertTrue(LandPatentGeoJsonParser.parse("not json").isEmpty())
        assertTrue(LandPatentGeoJsonParser.parse("").isEmpty())
        assertTrue(LandPatentGeoJsonParser.parse("[]").isEmpty())
        assertTrue(LandPatentGeoJsonParser.parse("{}").isEmpty())
    }

    @Test
    fun missingPropertiesFallsBackAndStillProducesOneAsset() {
        val json = collection(feature(id = null, properties = null))

        val parsed = LandPatentGeoJsonParser.parse(json)

        assertEquals(1, parsed.size)
        val only = parsed[0]
        // id is generated, never null.
        assertNotNull(only.id)
        assertTrue(only.id.isNotBlank())
        assertEquals("UNKNOWN", only.patentNumber)
        assertEquals("Land Patent UNKNOWN", only.title)
        assertEquals(0, only.year)
        // Bounds are derived from geometry, never left unset.
        assertEquals(39.0, only.boundingBox.south, 1e-9)
        assertEquals(-104.8, only.boundingBox.east, 1e-9)
        assertEquals("BLM GLO", only.attribution)
        assertTrue(only.license is HistoricalLicense.PublicDomainUsOnly)
        // Optional properties map to null, not to invented values.
        assertNull(only.patenteeName)
        assertNull(only.issueDate)
        assertNull(only.acreage)
        assertNull(only.legalDescription)
        assertNull(only.state)
        assertNull(only.county)
        assertNull(only.township)
    }
}