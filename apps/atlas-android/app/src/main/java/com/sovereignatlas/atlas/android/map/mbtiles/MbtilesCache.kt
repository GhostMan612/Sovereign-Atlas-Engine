// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.map.mbtiles

import android.database.sqlite.SQLiteDatabase
import com.sovereignatlas.atlas.offline.MbtilesTile
import com.sovereignatlas.atlas.offline.MbtilesTileSource
import com.sovereignatlas.atlas.offline.xyzToTmsY
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MbtilesCache(private val baseDir: File) : MbtilesTileSource {
    private val dbHandles = ConcurrentHashMap<String, SQLiteDatabase>()
    private val mimeTypes = ConcurrentHashMap<String, String>()
    private val lock = Any()

    override fun getTile(packName: String, z: Int, x: Int, y: Int): MbtilesTile? {
        if (!packName.endsWith(".mbtiles")) return null

        val safeBaseDir = baseDir.canonicalFile
        val dbFile = File(baseDir, packName).canonicalFile

        if (!dbFile.path.startsWith(safeBaseDir.path + File.separator) || !dbFile.exists()) {
            return null
        }

        val db = dbHandles[packName] ?: synchronized(lock) {
            dbHandles[packName] ?: runCatching {
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            }.getOrNull()?.also { dbHandles[packName] = it }
        }

        if (db == null) return null

        val mimeType = mimeTypes.getOrPut(packName) {
            var format = "png"
            runCatching {
                db.rawQuery("SELECT value FROM metadata WHERE name = 'format'", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        format = cursor.getString(0) ?: "png"
                    }
                }
            }
            when (format.lowercase()) {
                "pbf" -> "application/x-protobuf"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "image/png"
            }
        }

        val tmsY = xyzToTmsY(z, y)

        runCatching {
            db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(z.toString(), x.toString(), tmsY.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val data = cursor.getBlob(0)
                    if (data != null) {
                        return MbtilesTile(data, mimeType)
                    }
                }
            }
        }

        return null
    }

    override fun shutdown() {
        dbHandles.values.forEach { runCatching { it.close() } }
        dbHandles.clear()
        mimeTypes.clear()
    }
}
