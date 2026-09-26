// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import kotlinx.coroutines.flow.StateFlow

enum class OfflineMapKind {
    VECTOR,
    DEM,
}

data class OfflineMap(
    val name: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val format: String = "pbf",
    val kind: OfflineMapKind = OfflineMapKind.VECTOR,
)

interface DemTileStore {
    fun tileBytes(z: Int, x: Int, y: Int): ByteArray?
    fun tileFormat(): String
}

interface OfflineMapRepository {
    val availableMaps: StateFlow<List<OfflineMap>>
    val activeMap: StateFlow<OfflineMap?>
    val activeDem: StateFlow<OfflineMap?>
    suspend fun scanForMaps()
    suspend fun setActiveMap(map: OfflineMap?)
    suspend fun setActiveDem(map: OfflineMap?)
}
