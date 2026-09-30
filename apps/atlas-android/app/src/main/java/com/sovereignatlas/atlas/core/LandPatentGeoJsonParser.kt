// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads BLM General Land Office land patents from a GeoJSON FeatureCollection.
 *
 * Parses by explicit tree walk rather than `@Serializable` codegen: the module
 * carries kotlinx-serialization as a runtime library and does not apply the
 * compiler plugin, so reflective decoding is unavailable here by design.
 *
 * CONTRACT: never throws. Malformed JSON, a wrong root type, a feature with no
 * usable geometry, or a single unparseable property all skip that feature and
 * leave the rest intact. A caller may hand this untrusted feed output without a
 * try/catch. Provenance defaults follow RULES 2.3: unknown beats invented, so an
 * unrecorded licence becomes [HistoricalLicense.PublicDomainUsOnly] rather than
 * an unqualified public-domain claim.
 */
object LandPatentGeoJsonParser {
    private const val UNKNOWN_PATENT = "UNKNOWN"
    private const val ATTRIBUTION = "BLM GLO"

    fun parse(json: String): List<LandPatent> = runCatching { parseUnsafe(json) }
        .getOrDefault(emptyList())

    private fun parseUnsafe(json: String): List<LandPatent> {
        val root = Json.parseToJsonElement(json).jsonObject
        if (root.stringOrNull("type") != "FeatureCollection") return emptyList()
        val features = root["features"] as? JsonArray ?: return emptyList()

        val results = mutableListOf<LandPatent>()
        for (element in features) {
            val feature = element as? JsonObject ?: continue
            parseFeature(feature)?.let(results::add)
        }
        return results
    }

    private fun parseFeature(feature: JsonObject): LandPatent? {
        val geometry = parseGeometry(feature["geometry"] as? JsonObject ?: return null)
            ?: return null
        // A degenerate geometry carries no coordinates, so it cannot yield bounds,
        // and boundingBox is non-nullable on HistoricalAsset.
        val bounds = geometry.bounds() ?: return null

        val properties = feature["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val patentNumber = properties.stringOrNull("patent_number") ?: UNKNOWN_PATENT
        val id = feature.stringOrNull("id")
            ?: properties.stringOrNull("id")
            ?: UUID.randomUUID().toString()
        val issueDate = properties.stringOrNull("issue_date")

        return LandPatent(
            id = id,
            title = "Land Patent $patentNumber",
            year = yearFrom(issueDate),
            boundingBox = bounds,
            license = HistoricalLicense.PublicDomainUsOnly(),
            patentNumber = patentNumber,
            township = properties.stringOrNull("township"),
            patenteeName = properties.stringOrNull("patentee_name"),
            issueDate = issueDate,
            acreage = properties.doubleOrNull("acreage"),
            legalDescription = properties.stringOrNull("legal_description"),
            state = properties.stringOrNull("state"),
            county = properties.stringOrNull("county"),
            geometry = geometry,
            attribution = ATTRIBUTION,
        )
    }

    private fun parseGeometry(node: JsonObject): GeoJsonGeometry? {
        val type = node.stringOrNull("type")
        val coordinates = node["coordinates"] as? JsonArray ?: return null
        return when (type) {
            "Point" -> parsePoint(coordinates)?.let { GeoJsonGeometry.Point(it) }
            "Polygon" -> parseRings(coordinates)?.let { GeoJsonGeometry.Polygon(it) }
            "MultiPolygon" -> GeoJsonGeometry.MultiPolygon(
                coordinates.mapNotNull { ring -> parseRings(ring as? JsonArray ?: return@mapNotNull null) },
            )
            else -> null
        }
    }

    private fun parsePoint(coordinates: JsonArray): LngLat? {
        if (coordinates.size < 2) return null
        val longitude = coordinates[0].asDoubleOrNull() ?: return null
        val latitude = coordinates[1].asDoubleOrNull() ?: return null
        return LngLat(longitude, latitude)
    }

    /**
     * Parses one polygon as a list of rings. Returns null when the array is absent
     * or is not a flat ring array, which is how a malformed Polygon is rejected.
     */
    private fun parseRings(polygon: JsonArray): List<List<LngLat>>? {
        val rings = polygon.mapNotNull { ring ->
            val points = ring as? JsonArray ?: return@mapNotNull null
            points.mapNotNull { point -> parsePoint(point as? JsonArray ?: return@mapNotNull null) }
        }
        return rings.takeIf { it.isNotEmpty() }
    }

    /**
     * Year from an issue date. Falls back to 0 rather than guessing a century:
     * a wrong year on a historical asset is a fabricated fact, and 0 is visibly
     * absent rather than confidently wrong. The full date is preserved
     * separately on issueDate.
     */
    private fun yearFrom(issueDate: String?): Int {
        val digits = issueDate?.takeWhile { it.isDigit() }.orEmpty()
        return digits.take(4).toIntOrNull() ?: 0
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.let { element ->
            if (element is JsonPrimitive && element.isString) element.content else null
        }

    private fun JsonObject.doubleOrNull(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonElement.asDoubleOrNull(): Double? =
        (this as? JsonPrimitive)?.doubleOrNull
}