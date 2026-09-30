// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

/**
 * Read access to the Historical Encyclopedia asset catalogue.
 *
 * Pure interface: no Android, MapLibre, or java.net types. Implementations live
 * in the adapter layer and own the storage, which keeps this package testable
 * with plain in-memory fakes.
 *
 * Every method is a one-shot query. There is deliberately no Flow here, because
 * the pure engine must not decide how an adapter observes invalidation.
 */
interface HistoricalAssetRepository {
    /** Assets whose bounds intersect [bounds]. */
    suspend fun getAssets(bounds: AtlasBoundingBox): List<HistoricalAsset>

    /** The asset with [id], or null when no such asset is recorded. */
    suspend fun getAssetById(id: String): HistoricalAsset?
}