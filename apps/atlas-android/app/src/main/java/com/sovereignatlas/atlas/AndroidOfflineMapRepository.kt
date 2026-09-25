// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.sovereignatlas.atlas.offline.OfflineMap
import com.sovereignatlas.atlas.offline.OfflineMapRepository
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidOfflineMapRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OfflineMapRepository {
    private val _availableMaps = MutableStateFlow<List<OfflineMap>>(emptyList())
    override val availableMaps: StateFlow<List<OfflineMap>> = _availableMaps.asStateFlow()

    private val _activeMap = MutableStateFlow<OfflineMap?>(null)
    override val activeMap: StateFlow<OfflineMap?> = _activeMap.asStateFlow()

    override suspend fun scanForMaps() {
        val maps = withContext(ioDispatcher) {
            val dir = context.getExternalFilesDir("mbtiles") ?: return@withContext emptyList()
            dir.listFiles { file -> file.extension == "mbtiles" }
                ?.mapNotNull { validateMbtiles(it) }
                ?.sortedBy { it.name }
                ?: emptyList()
        }
        _availableMaps.value = maps
    }

    override suspend fun setActiveMap(map: OfflineMap?) {
        _activeMap.value = map
    }

    private fun validateMbtiles(file: File): OfflineMap? {
        val header = ByteArray(16)
        FileInputStream(file).use { it.read(header) }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") return null

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            db.rawQuery("SELECT value FROM metadata WHERE name = 'format'", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getString(0) == "pbf") {
                    return OfflineMap(
                        name = file.name,
                        absolutePath = file.absolutePath,
                        sizeBytes = file.length(),
                        format = "pbf",
                    )
                }
            }
        } catch (error: SQLiteException) {
            Log.w("OfflineRepo", "Invalid MBTiles: ${file.name}", error)
        } finally {
            db?.close()
        }
        return null
    }
}
