// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.data

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.core.HistoricalAsset
import com.sovereignatlas.atlas.core.HistoricalAssetRepository
import com.sovereignatlas.atlas.core.LandPatent
import com.sovereignatlas.atlas.core.LandPatentGeoJsonParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val HISTORICAL_ASSET_DIR = "historical"

private const val GEOJSON_EXTENSION = "geojson"

/**
 * Filesystem adapter for the Historical Encyclopedia catalogue.
 *
 * Reads every `.geojson` FeatureCollection under [directoryProvider] and defers
 * all parsing to the pure [LandPatentGeoJsonParser]. This class owns no parsing
 * logic and no provenance rules, only storage concerns: locating the directory,
 * reading bytes, and skipping files that cannot be read.
 *
 * The directory is supplied as a provider lambda rather than a Context, matching
 * the app's existing wiring style (see [com.sovereignatlas.atlas.offline.OfflineStore]
 * and [com.sovereignatlas.atlas.offline.mbtiles.DefaultMbtilesScanner]). That keeps
 * this adapter unit-testable on the JVM with a temp folder instead of an emulator,
 * and keeps Context out of everything but the composition root.
 *
 * Scans are re-run per call and no cache is held: a dropped `.geojson` file must be
 * visible on the next query without an explicit invalidation path, and the pure
 * interface specifies one-shot queries.
 *
 * A missing or absent external-storage directory yields an empty catalogue rather
 * than an exception. Historical assets are supplementary to the map, so an absent
 * optional data drop must not take down the caller; the caller sees zero assets.
 */
class LocalHistoricalAssetRepository(
    private val directoryProvider: () -> File?,
) : HistoricalAssetRepository {

    override suspend fun getAssets(bounds: AtlasBoundingBox): List<HistoricalAsset> =
        withContext(Dispatchers.IO) { loadCatalogue().filter { it.boundingBox.intersects(bounds) } }

    override suspend fun getAssetById(id: String): HistoricalAsset? =
        withContext(Dispatchers.IO) { loadCatalogue().firstOrNull { it.id == id } }

    private fun loadCatalogue(): List<LandPatent> {
        val directory = directoryProvider() ?: return emptyList()
        if (!directory.isDirectory) return emptyList()

        val files = directory.listFiles { file ->
            file.isFile && file.extension.equals(GEOJSON_EXTENSION, ignoreCase = true)
        } ?: return emptyList()

        val assets = mutableListOf<LandPatent>()
        for (file in files.sortedBy { it.name }) {
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            assets += LandPatentGeoJsonParser.parse(text)
        }
        return assets
    }
}

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