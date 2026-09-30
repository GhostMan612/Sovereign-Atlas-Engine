// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.data

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.core.HistoricalAsset
import com.sovereignatlas.atlas.core.HistoricalAssetRepository
import com.sovereignatlas.atlas.core.HistoricalLicense
import com.sovereignatlas.atlas.core.LandPatent
import com.sovereignatlas.atlas.core.LandPatentGeoJsonParser
import com.sovereignatlas.atlas.core.SanbornBlueprint
import com.sovereignatlas.atlas.offline.mbtiles.MetadataReader
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val HISTORICAL_ASSET_DIR = "historical"

private const val GEOJSON_EXTENSION = "geojson"
private const val MBTILES_EXTENSION = "mbtiles"

/**
 * Filesystem adapter for the Historical Encyclopedia catalogue.
 *
 * Reads every `.geojson` FeatureCollection and every `.mbtiles` pack under
 * [directoryProvider]. GeoJSON goes to the pure [LandPatentGeoJsonParser]; MBTiles
 * goes to the injected [MetadataReader], whose SQLite implementation is an Android
 * capability that must not leak into this JVM-tested adapter. This class owns no
 * parsing logic and no provenance rules, only storage concerns: locating the
 * directory, reading bytes, and skipping files that cannot be read.
 *
 * The directory is supplied as a provider lambda rather than a Context, matching
 * the app's existing wiring style (see [com.sovereignatlas.atlas.offline.OfflineStore]
 * and [com.sovereignatlas.atlas.offline.mbtiles.DefaultMbtilesScanner]). That keeps
 * this adapter unit-testable on the JVM with a temp folder instead of an emulator,
 * and keeps Context out of everything but the composition root.
 *
 * Scans are re-run per call and no cache is held: a dropped file must be visible on
 * the next query without an explicit invalidation path, and the pure interface
 * specifies one-shot queries.
 *
 * A missing or absent external-storage directory yields an empty catalogue rather
 * than an exception. Historical assets are supplementary to the map, so an absent
 * optional data drop must not take down the caller; the caller sees zero assets.
 */
class LocalHistoricalAssetRepository(
    private val directoryProvider: () -> File?,
    private val metadataReader: MetadataReader,
) : HistoricalAssetRepository {

    override suspend fun getAssets(bounds: AtlasBoundingBox): List<HistoricalAsset> =
        withContext(Dispatchers.IO) { loadCatalogue().filter { it.boundingBox.intersects(bounds) } }

    override suspend fun getAssetById(id: String): HistoricalAsset? =
        withContext(Dispatchers.IO) { loadCatalogue().firstOrNull { it.id == id } }

    private fun loadCatalogue(): List<HistoricalAsset> {
        val directory = directoryProvider() ?: return emptyList()
        if (!directory.isDirectory) return emptyList()

        val files = directory.listFiles { file ->
            if (!file.isFile) return@listFiles false
            val extension = file.extension.lowercase()
            extension == GEOJSON_EXTENSION || extension == MBTILES_EXTENSION
        } ?: return emptyList()

        val assets = mutableListOf<HistoricalAsset>()
        for (file in files.sortedBy { it.name }) {
            when (file.extension.lowercase()) {
                GEOJSON_EXTENSION -> {
                    val text = runCatching { file.readText() }.getOrNull() ?: continue
                    assets += LandPatentGeoJsonParser.parse(text)
                }
                MBTILES_EXTENSION -> blueprintFor(file)?.let(assets::add)
            }
        }
        return assets
    }

    /**
     * Builds a blueprint from a pack's metadata table, or null when the file is not
     * a readable MBTiles database. A pack with no `bounds` cannot produce the
     * non-nullable `boundingBox` on [HistoricalAsset], so it is skipped rather than
     * centred on 0,0 over Null Island, which would place a real survey sheet in the
     * Gulf of Guinea.
     */
    private fun blueprintFor(file: File): SanbornBlueprint? {
        val metadata = runCatching { metadataReader.readAll(file) }.getOrNull() ?: return null
        val bounds = parseBounds(metadata["bounds"]) ?: return null

        val sheet = metadata["sheet"] ?: file.nameWithoutExtension
        val edition = metadata["edition"]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 1

        return SanbornBlueprint(
            // A stable id derived from the file path, NOT a random UUID. The
            // repository re-scans on every call, so a random id would be different
            // on each scan and getAssetById could never resolve it: the asset would
            // exist in getAssets and be unreachable by the one lookup the interface
            // offers. nameUUIDFromBytes is a deterministic (type 3) UUID, so it is
            // still a UUID and still stable for a given file.
            id = metadata["id"]?.takeIf { it.isNotBlank() }
                ?: UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString(),
            title = metadata["name"]?.takeIf { it.isNotBlank() } ?: file.name,
            // Sanborn metadata carries no year. 0 means unrecorded, and a guessed
            // century on a hand-drafted sheet is a fabricated fact.
            year = 0,
            boundingBox = bounds,
            license = HistoricalLicense.Unverified(UNVERIFIED_NOTE),
            attribution = metadata["attribution"]?.takeIf { it.isNotBlank() },
            edition = edition,
            sheet = sheet,
            minZoom = metadata["minzoom"]?.toFloatOrNull(),
            maxZoom = metadata["maxzoom"]?.toFloatOrNull(),
            format = metadata["format"]?.takeIf { it.isNotBlank() },
            filePath = file.absolutePath,
        )
    }
}

/**
 * MBTiles `bounds` is `west,south,east,north` — longitude first, and the opposite
 * corner order from [AtlasBoundingBox], which is south/west/north/east. Swapping
 * these silently mirrors a survey sheet, so the order is explicit here.
 *
 * Returns null for a malformed, absent, or non-numeric entry. A missing edge is
 * not a zero edge: guessing 0.0 would invent a hemisphere.
 */
private fun parseBounds(raw: String?): AtlasBoundingBox? {
    val parts = raw?.split(",")?.map { it.trim() } ?: return null
    if (parts.size != 4) return null
    val west = parts[0].toDoubleOrNull() ?: return null
    val south = parts[1].toDoubleOrNull() ?: return null
    val east = parts[2].toDoubleOrNull() ?: return null
    val north = parts[3].toDoubleOrNull() ?: return null
    if (listOf(west, south, east, north).any { !it.isFinite() }) return null
    if (south > north) return null
    if (west < -180.0 || west > 180.0 || east < -180.0 || east > 180.0) return null
    if (south < -90.0 || south > 90.0 || north < -90.0 || north > 90.0) return null
    return AtlasBoundingBox(south = south, west = west, north = north, east = east)
}

/**
 * An MBTiles pack does not assert its own rights. Scanning one proves the file
 * exists, not that it is redistributable, so the asset stays Unverified until a
 * human confirms it against the Library of Congress. PublicDomain would be a
 * fabricated provenance claim (RULES 2.3).
 */
private const val UNVERIFIED_NOTE =
    "Licence not recorded in the MBTiles metadata; confirm against the Library of " +
        "Congress before redistributing."

/**
 * Two boxes overlap when neither is strictly north/west of the other. Touching
 * edges count as overlap, which is the behaviour a viewport query wants: a patent
 * whose boundary sits exactly on the viewport edge is inside the viewport.
 *
 * Boxes crossing the antimeridian are compared after [AtlasBoundingBox.unwrap],
 * since `west > east` is a legitimate encoded crossing, not an empty range.
 */
private fun AtlasBoundingBox.intersects(other: AtlasBoundingBox): Boolean {
    if (crossesAntimeridian || other.crossesAntimeridian) {
        return unwrap().any { mine -> other.unwrap().any { theirs -> mine.overlaps(theirs) } }
    }
    return overlaps(other)
}

private fun AtlasBoundingBox.overlaps(other: AtlasBoundingBox): Boolean =
    south <= other.north && other.south <= north && west <= other.east && other.west <= east