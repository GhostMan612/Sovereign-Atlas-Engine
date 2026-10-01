// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Converts a BLM Indian Irrigation Management (IIM) statement into a GeoJSON
 * FeatureCollection that [LandPatentGeoJsonParser] can read.
 *
 * A build-time tool rather than runtime code, so it lives in the test source set.
 * It is pure: no Android, no MapLibre, no `java.net`. It walks the JSON tree by
 * hand with `Json.parseToJsonElement` because the module runs kotlinx-serialization
 * as a runtime library and deliberately does not apply the compiler plugin, so
 * `@Serializable` would generate no serializer and could not decode.
 *
 * Every read is defensive and nothing in [convert] can throw. An IIM statement is a
 * large federal export whose key set varies by agency and vintage, so one
 * unexpected row aborting the run means no output at all. A skipped feature is
 * recoverable; a converter that dies on row 4,000 is not.
 *
 * GEOMETRY IS A PLACEHOLDER. An IIM statement carries tract identifiers and legal
 * descriptions but no coordinates, so every feature gets the same synthetic
 * polygon outside Pine Ridge. That polygon is a fabrication — it is not where any of
 * these tracts are. Each feature therefore carries
 * [PLACEHOLDER_GEOMETRY_PROPERTY] = true so nothing downstream can mistake this
 * file for georeferenced data. Real geometry has to come from a survey boundary
 * source; a converter cannot invent where the federal document was silent
 * (RULES 2.3: unknown beats invented).
 */
object IimStatementConverter {

    const val INPUT_FILE_NAME = "iim_statement.json"
    const val OUTPUT_FILE_NAME = "iim_patents.geojson"

    /**
     * The real statement lives outside the repository, in the operator's vault.
     *
     * Overridable by the `iim.statement.path` system property or the
     * `IIM_STATEMENT_PATH` environment variable so the same code runs on a
     * workstation and on CI, where this path does not exist. Tests that need the
     * real file skip themselves when it is absent; they never fail for its absence.
     */
    const val INPUT_PATH_PROPERTY = "iim.statement.path"
    const val INPUT_PATH_ENV = "IIM_STATEMENT_PATH"
    const val DEFAULT_INPUT_PATH = "C:\\sovereign_mantle\\vault\\iim_statement.json"
    const val OUTPUT_PATH_PROPERTY = "iim.output.path"

    /** Marks a feature whose coordinates are synthetic. See the object note. */
    const val PLACEHOLDER_GEOMETRY_PROPERTY = "placeholderGeometry"

    const val UNKNOWN_VALUE = "UNKNOWN"

    /**
     * Synthetic polygon as `[west, south, east, north]`. Deliberately tiny so that
     * hundreds of placeholder features stacked on one point are visibly wrong on the
     * map instead of looking like one real parcel.
     */
    private const val PLACEHOLDER_WEST = -102.50
    private const val PLACEHOLDER_SOUTH = 43.30
    private const val PLACEHOLDER_EAST = -102.49
    private const val PLACEHOLDER_NORTH = 43.31

    /** Converts a statement document to a FeatureCollection JSON string. */
    fun convert(statementJson: String): String {
        val root = runCatching {
            Json.parseToJsonElement(statementJson) as? JsonObject
        }.getOrNull() ?: return EMPTY_COLLECTION

        val assets = root.arrayOrEmpty("assets").objects()
        val encumbrancesByTract = root.arrayOrEmpty("encumbrances")
            .objects()
            .groupBy { it.stringOrNull("tract_id") }

        val features = assets.mapNotNull { asset -> featureFor(asset, encumbrancesByTract) }

        return buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") { features.forEach { add(it) } }
        }.toString()
    }

    private fun featureFor(
        asset: JsonObject,
        encumbrancesByTract: Map<String?, List<JsonObject>>,
    ): JsonObject? {
        val tractId = asset.stringOrNull("tract_id") ?: return null

        val encumbrances = encumbrancesByTract[tractId].orEmpty()
        // "OGLALA LAKOTA, SD" -> county first, state second.
        val location = asset.stringOrNull("location")?.split(",")?.map { it.trim() }
        val county = location?.firstOrNull()?.takeIf { it.isNotEmpty() }
        val state = location?.getOrNull(1)?.takeIf { it.isNotEmpty() }

        return buildJsonObject {
            put("type", "Feature")
            put("id", tractId)
            putJsonObject("geometry") {
                put("type", "Polygon")
                putJsonArray("coordinates") { add(placeholderRing()) }
            }
            putJsonObject("properties") {
                put("patent_number", tractId)
                put("patentee_name", asset.stringOrNull("tract_name") ?: UNKNOWN_VALUE)
                putNumberOrAbsent(this, "acreage", asset.numberOrNull("tract_acres"))
                put("state", state ?: UNKNOWN_VALUE)
                put("county", county ?: UNKNOWN_VALUE)
                put("legal_description", legalDescription(asset, encumbrances))
                // Optional on the model. Carried only when the export has one, so the
                // parser's year 0 keeps meaning "no issue date recorded".
                asset.stringOrNull("issue_date")?.let { put("issue_date", it) }
                put(PLACEHOLDER_GEOMETRY_PROPERTY, true)
            }
        }
    }

    /**
     * Legal description carrying undivided ownership plus every encumbrance recorded
     * against the tract.
     *
     * `undivided_ownership` is rendered WITHOUT a percent sign. The real statement
     * carries fractional shares (0.0034722222, 0.0052083334, ...), not percentages, and
     * a bare fraction is not a unit this converter may relabel: "0.0034722222%" and
     * "0.34722222%" are different claims about how much land someone owns, and the
     * statement says neither. The value is carried through exactly as recorded and the
     * unit is left unstated (RULES 2.3: unknown beats invented).
     *
     * An encumbrance with no type or holder still appears: "there is an encumbrance
     * and the export did not say what" is a materially different fact from "no
     * encumbrance", and collapsing the two would produce a falsely clean record.
     */
    private fun legalDescription(asset: JsonObject, encumbrances: List<JsonObject>): String {
        val share = asset.numberOrNull("undivided_ownership")
        val shareText = if (share == null) {
            "undivided ownership not recorded"
        } else {
            "undivided ownership ${renderNumber(share)}"
        }
        if (encumbrances.isEmpty()) return "$shareText; no encumbrances recorded"

        val rendered = encumbrances.joinToString("; ") { encumbrance ->
            val type = encumbrance.stringOrNull("encumbrance_type") ?: "unspecified type"
            val holder = encumbrance.stringOrNull("encumbrance_holder") ?: "unspecified holder"
            "$type held by $holder"
        }
        return "$shareText; $rendered"
    }

    /** Closed GeoJSON ring, longitude first per RFC 7946. */
    private fun placeholderRing(): JsonArray {
        fun point(longitude: Double, latitude: Double): JsonArray = buildJsonArray {
            add(JsonPrimitive(longitude))
            add(JsonPrimitive(latitude))
        }
        return buildJsonArray {
            add(point(PLACEHOLDER_WEST, PLACEHOLDER_SOUTH))
            add(point(PLACEHOLDER_WEST, PLACEHOLDER_NORTH))
            add(point(PLACEHOLDER_EAST, PLACEHOLDER_NORTH))
            add(point(PLACEHOLDER_EAST, PLACEHOLDER_SOUTH))
            add(point(PLACEHOLDER_WEST, PLACEHOLDER_SOUTH))
        }
    }

    /** Renders 640.0 as "640", so a whole-number share does not read as "640.0%". */
    private fun renderNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private const val EMPTY_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

    // ---- defensive readers -----------------------------------------------------
    // Each answers "absent, wrong type, or null" with null rather than throwing, so
    // one malformed row cannot end the conversion.

    private fun JsonObject.arrayOrEmpty(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = this[key] ?: return null
        // JsonNull is itself a JsonPrimitive, so it has to be rejected before the
        // isString check rather than relied on to fail it.
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        // Only a genuine string counts. Coercing a numeric id here would put the
        // wrong type into patent_number.
        if (!primitive.isString) return null
        return primitive.content.trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    /** Accepts a JSON number or a numeric string; federal exports use both. */
    private fun JsonObject.numberOrNull(key: String): Double? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.content.trim().toDoubleOrNull()
    }

    /**
     * The model reads `acreage` with `doubleOrNull`, which only accepts a JSON
     * number. Emitting the value as a quoted string would parse to null and silently
     * drop acreage from every feature.
     */
    private fun putNumberOrAbsent(builder: JsonObjectBuilder, key: String, value: Double?) {
        if (value == null || !value.isFinite()) return
        builder.put(key, JsonPrimitive(value))
    }
}

/**
 * Resolves the statement to convert.
 *
 * Order: explicit `iim.statement.path` system property, then the
 * `IIM_STATEMENT_PATH` environment variable, then the operator's vault path, then
 * an ancestor walk for a statement dropped into the project root. The ancestor walk
 * is last because the real file is not in the repository, and a converter that
 * silently falls back to a different document than the one asked for is worse than
 * one that fails loudly.
 */
fun resolveInputFile(): File? {
    System.getProperty(IimStatementConverter.INPUT_PATH_PROPERTY)
        ?.takeIf { it.isNotBlank() }
        ?.let { return File(it).takeIf(File::isFile) }
    System.getenv(IimStatementConverter.INPUT_PATH_ENV)
        ?.takeIf { it.isNotBlank() }
        ?.let { return File(it).takeIf(File::isFile) }
    return File(IimStatementConverter.DEFAULT_INPUT_PATH).takeIf(File::isFile)
        ?: findAncestorFile(IimStatementConverter.INPUT_FILE_NAME)
}

/**
 * Output location. Deliberately inside the build directory, not beside the input:
 * the input lives in the operator's vault, and a converter must not write into a
 * directory it was only granted read access to (RULES 1.7).
 */
fun resolveOutputFile(): File {
    val configured = System.getProperty(IimStatementConverter.OUTPUT_PATH_PROPERTY)
        ?.takeIf { it.isNotBlank() }
    if (configured != null) return File(configured)
    return File(System.getProperty("user.dir") ?: ".")
        .resolve("build")
        .resolve(IimStatementConverter.OUTPUT_FILE_NAME)
}

/**
 * Standalone entry point. Reads the statement resolved by [resolveInputFile] and
 * writes the FeatureCollection to [resolveOutputFile].
 */
fun main() {
    val input = resolveInputFile()
    if (input == null) {
        println(
            "FAILED: no IIM statement found. Looked at the ${IimStatementConverter.INPUT_PATH_PROPERTY} " +
                "system property, the ${IimStatementConverter.INPUT_PATH_ENV} environment variable, " +
                "${IimStatementConverter.DEFAULT_INPUT_PATH}, and every parent of the working directory.",
        )
        return
    }

    val output = resolveOutputFile()
    output.parentFile?.mkdirs()
    val converted = runCatching {
        val json = IimStatementConverter.convert(input.readText())
        output.writeText(json)
        val root = Json.parseToJsonElement(json) as JsonObject
        val features = root["features"] as? JsonArray ?: JsonArray(emptyList())
        features.size
    }

    val count = converted.getOrElse { error ->
        println("FAILED to convert ${input.absolutePath}: ${error.message}")
        return
    }

    println("Read $count feature(s) from ${input.absolutePath}")
    println("Wrote $count feature(s) to ${output.absolutePath}")
    println(
        "WARNING: feature geometry is a synthetic placeholder, not the real location " +
            "of these tracts. This file is not georeferenced.",
    )
}

private fun findAncestorFile(name: String): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        val candidate = File(dir, name)
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    return null
}