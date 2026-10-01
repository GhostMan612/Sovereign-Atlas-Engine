// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * The decisive test here is [convertedOutputIsReadableByTheModelParser]: it feeds
 * the converter's output straight into [LandPatentGeoJsonParser]. Field-by-field
 * assertions on the emitted JSON would all pass while still producing something
 * the model silently discards, because the parser drops absent fields without
 * complaint. Parsing the result is the only check that proves the two agree.
 */
final class IimStatementConverterTest {

    @Test
    fun everyAssetBecomesAFeatureKeyedByTractId() {
        val features = convert().features()

        assertEquals(listOf("T-1", "T-2"), features.map { it.string("id") })
        assertEquals(
            listOf("T-1", "T-2"),
            features.map { it.child("properties").string("patent_number") },
        )
    }

    @Test
    fun aMissingPatenteeBecomesUnknownRatherThanBeingOmitted() {
        val properties = convert().features()[1].child("properties")

        assertEquals(IimStatementConverter.UNKNOWN_VALUE, properties.string("patentee_name"))
    }

    @Test
    fun locationSplitsIntoCountyFirstAndStateSecond() {
        val properties = convert().features().first().child("properties")

        assertEquals("OGLALA LAKOTA", properties.string("county"))
        assertEquals("SD", properties.string("state"))
    }

    @Test
    fun acreageIsEmittedAsAJsonNumberNotAString() {
        val raw = convert().features().first().child("properties")["acreage"]

        // A quoted acreage parses to null in the model and vanishes with no warning.
        assertTrue("acreage must be a JSON number, was $raw", raw is JsonPrimitive)
        assertEquals(640.0, (raw as JsonPrimitive).doubleOrNull!!, 1e-9)
    }

    @Test
    fun legalDescriptionCarriesOwnershipAndEveryEncumbrance() {
        val description = convert().features().first().child("properties").string("legal_description")!!

        // No percent sign. undivided_ownership is a fractional share in the real
        // statement, and relabelling it as a percentage would be a fabricated unit.
        assertTrue(description, description.contains("undivided ownership 100"))
        assertTrue(description, description.contains("Easement"))
        assertTrue(description, description.contains("Tribe of Pine Ridge"))
    }

    @Test
    fun encumbrancesAreGroupedByTractNotLeakedAcrossTracts() {
        val description = convert().features()[1].child("properties").string("legal_description")!!

        assertTrue(description, description.contains("no encumbrances recorded"))
        assertTrue("T-2 must not inherit T-1's easement", !description.contains("Easement"))
    }

    @Test
    fun everyFeatureIsFlaggedAsCarryingSyntheticGeometry() {
        for (feature in convert().features()) {
            val properties = feature.child("properties")
            val flag = properties[IimStatementConverter.PLACEHOLDER_GEOMETRY_PROPERTY] as JsonPrimitive
            assertTrue(flag.booleanOrNull == true)
        }
    }

    @Test
    fun anUnknownTractIsSkippedRatherThanEmittingAnUnkeyedFeature() {
        val features = featuresOf(
            """{"assets":[{"tract_name":"No Id Here"}],"encumbrances":[]}""",
        )

        assertTrue(features.isEmpty())
    }

    @Test
    fun aMalformedDocumentYieldsAnEmptyCollectionRatherThanThrowing() {
        assertEquals(0, featuresOf("not json at all").size)
        assertEquals(0, featuresOf("[]").size)
        assertEquals(0, featuresOf("{}").size)
    }

    @Test
    fun anAssetWithNoBoundsOnItselfStillProducesAPlaceholderGeometry() {
        val features = featuresOf("""{"assets":[{"tract_id":"T-9"}],"encumbrances":[]}""")

        val geometry = features.first().child("geometry")
        assertEquals("Polygon", geometry.string("type"))
        val ring = (geometry["coordinates"] as JsonArray).first() as JsonArray
        // A GeoJSON ring must repeat its first vertex or MapLibre drops it.
        assertEquals(5, ring.size)
        assertEquals(ring.first().toString(), ring.last().toString())
    }

    @Test
    fun convertedOutputIsReadableByTheModelParser() {
        val patents = LandPatentGeoJsonParser.parse(
            IimStatementConverter.convert(STATEMENT),
        )

        assertEquals(2, patents.size)
        val first = patents.first()
        assertEquals("T-1", first.id)
        assertEquals("T-1", first.patentNumber)
        assertEquals("Sitting Bull", first.patenteeName)
        assertEquals("SD", first.state)
        assertEquals("OGLALA LAKOTA", first.county)
        assertEquals(640.0, first.acreage!!, 1e-9)
        assertEquals(1880, first.year)
        assertTrue(first.legalDescription!!.contains("Easement"))

        // No tract_name on the second row, so the model must see the placeholder
        // rather than null: an absent patentee is a fact worth stating.
        assertEquals(IimStatementConverter.UNKNOWN_VALUE, patents[1].patenteeName)
        // tract_acres arrived as the string "160". It is coerced, not dropped: the
        // value is unambiguous, and discarding a number written as text would
        // silently lose acreage on a real export.
        assertEquals(160.0, patents[1].acreage!!, 1e-9)
        // Placeholder bounds are real coordinates, so bounds() has something to read.
        assertEquals(43.30, first.boundingBox.south, 1e-6)
        assertEquals(-102.50, first.boundingBox.west, 1e-6)
    }

    // ---- real statement ---------------------------------------------------------
    // These run against the operator's vault file. On CI, and on any machine without
    // it, assumeTrue skips them: an absent vault is a missing precondition, not a
    // converter defect. They must never assert against invented data.

    /** The real statement, or skip. */
    private fun realStatement(): String {
        val file = resolveInputFile()
        Assume.assumeTrue(
            "IIM statement not present at ${file?.absolutePath ?: IimStatementConverter.DEFAULT_INPUT_PATH}",
            file != null,
        )
        return file!!.readText()
    }

    @Test
    fun theRealStatementYieldsOneFeaturePerAsset() {
        val statement = realStatement()
        val assets = (Json.parseToJsonElement(statement).jsonObject["assets"] as JsonArray)
        val root = Json.parseToJsonElement(IimStatementConverter.convert(statement)).jsonObject
        val features = (root["features"] as JsonArray).map { it.jsonObject }

        assertEquals(assets.size, features.size)
        assertTrue("the real statement is not empty", features.isNotEmpty())
    }

    @Test
    fun everyRealAssetKeepsItsTractIdAndCountyState() {
        val features = Json.parseToJsonElement(IimStatementConverter.convert(realStatement()))
            .jsonObject.let { (it["features"] as JsonArray).map { f -> f.jsonObject } }

        for (feature in features) {
            val properties = feature.child("properties")
            val tractId = feature.string("id")
            assertNotNull("feature has no id", tractId)
            assertEquals(tractId, properties.string("patent_number"))
            // The real statement splits "OGLALA LAKOTA, SD" — county first.
            assertTrue(
                "county/state missing on $tractId",
                properties.string("county") != null && properties.string("state") != null,
            )
        }
    }

    @Test
    fun aNullTractNameInTheRealStatementBecomesUnknown() {
        val features = Json.parseToJsonElement(IimStatementConverter.convert(realStatement()))
            .jsonObject.let { (it["features"] as JsonArray).map { f -> f.jsonObject } }

        // Tract "344 M 3335" carries tract_name: null in the real export.
        val unknown = features.filter {
            it.child("properties").string("patentee_name") == IimStatementConverter.UNKNOWN_VALUE
        }
        assertTrue("expected at least one null tract_name in the real data", unknown.isNotEmpty())
    }

    @Test
    fun realUndividedOwnershipIsCarriedWithoutAPercentSign() {
        val features = Json.parseToJsonElement(IimStatementConverter.convert(realStatement()))
            .jsonObject.let { (it["features"] as JsonArray).map { f -> f.jsonObject } }

        for (feature in features) {
            val description = feature.child("properties").string("legal_description")!!
            // A bare fractional share is not a percentage; asserting one would invent a unit.
            assertFalse(
                "percent sign invented on ${feature.string("id")}: $description",
                description.contains("%"),
            )
        }
        assertTrue(
            "expected fractional shares in the real data",
            features.any { it.child("properties").string("legal_description")!!
                .contains("undivided ownership 0.") },
        )
    }

    @Test
    fun realEncumbrancesAreGroupedByTractAndNeverLeak() {
        val features = Json.parseToJsonElement(IimStatementConverter.convert(realStatement()))
            .jsonObject.let { (it["features"] as JsonArray).map { f -> f.jsonObject } }

        // Tract "344 8255" has exactly one encumbrance (a grazing permit); it must not
        // inherit the residential/utility encumbrances recorded against other tracts.
        val t8255 = features.firstOrNull { it.string("id") == "344 8255" }
        Assume.assumeTrue("tract 344 8255 absent from this statement", t8255 != null)
        val description = t8255!!.child("properties").string("legal_description")!!
        assertTrue(description, description.contains("GRAZING PERMIT"))
        assertFalse(description, description.contains("RESIDENTIAL"))
    }

    @Test
    fun realOutputIsReadableByTheModelParser() {
        val patents = LandPatentGeoJsonParser.parse(
            IimStatementConverter.convert(realStatement()),
        )

        assertTrue("parser dropped every real feature", patents.isNotEmpty())
        for (patent in patents) {
            // Placeholder bounds are real coordinates, so bounds() has something to read.
            assertNotNull("no acreage on ${patent.id}", patent.acreage)
            assertNotNull("no bounds on ${patent.id}", patent.boundingBox)
            assertNotNull("no tract id on ${patent.id}", patent.patentNumber)
        }
    }

    @Test
    fun convertingTheRealStatementWritesTheGeojsonArtifact() {
        realStatement()

        val output = resolveOutputFile()
        output.parentFile?.mkdirs()
        val json = IimStatementConverter.convert(realStatement())
        output.writeText(json)

        assertTrue("converter wrote no artifact at ${output.absolutePath}", output.isFile)
        assertTrue("artifact is empty", output.length() > 0)
        // The artifact must be the same FeatureCollection the parser accepts, not
        // just a non-empty file.
        assertTrue(
            "written artifact does not parse",
            LandPatentGeoJsonParser.parse(output.readText()).isNotEmpty(),
        )
    }

    private fun convert(): JsonObject = Json.parseToJsonElement(
        IimStatementConverter.convert(STATEMENT),
    ).jsonObject

    /** Converts an ad-hoc statement and returns just its features. */
    private fun featuresOf(statement: String): List<JsonObject> {
        val root = Json.parseToJsonElement(IimStatementConverter.convert(statement))
        val array = (root as JsonObject)["features"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.features(): List<JsonObject> =
        (this["features"] as JsonArray).map { it.jsonObject }

    private fun JsonObject.child(key: String): JsonObject = (this[key] as JsonObject)

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private companion object {
        /**
         * Mirrors the key set named in the authorization. This is a FIXTURE, not the
         * real export: it exists to pin converter behaviour on known input. Facts
         * about the real statement are asserted by [realStatement] instead, and those
         * tests skip when the vault file is absent rather than reporting a false green.
         */
        val STATEMENT = """
            {
              "assets": [
                {
                  "tract_id": "T-1",
                  "tract_name": "Sitting Bull",
                  "tract_acres": 640,
                  "location": "OGLALA LAKOTA, SD",
                  "undivided_ownership": 100,
                  "issue_date": "1880-05-01"
                },
                {
                  "tract_id": "T-2",
                  "tract_name": null,
                  "tract_acres": "160",
                  "location": "OGLALA LAKOTA, SD",
                  "undivided_ownership": 50.5
                }
              ],
              "encumbrances": [
                {
                  "tract_id": "T-1",
                  "encumbrance_type": "Easement",
                  "encumbrance_holder": "Tribe of Pine Ridge"
                }
              ]
            }
        """.trimIndent()
    }
}