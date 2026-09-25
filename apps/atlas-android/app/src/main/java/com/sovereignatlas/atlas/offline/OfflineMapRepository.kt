// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import kotlinx.coroutines.flow.StateFlow

data class OfflineMap(
    val name: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val format: String = "pbf",
)

interface OfflineMapRepository {
    val availableMaps: StateFlow<List<OfflineMap>>
    val activeMap: StateFlow<OfflineMap?>
    suspend fun scanForMaps()
    suspend fun setActiveMap(map: OfflineMap?)
}
