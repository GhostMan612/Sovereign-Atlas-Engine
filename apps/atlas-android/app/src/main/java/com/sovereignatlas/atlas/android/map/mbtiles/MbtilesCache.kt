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

/**
 * Serves tiles out of the pack directory.
 *
 * [baseDir] is a single root on purpose. The containment guard below is the whole
 * security property of this class: a pack name is only honoured when it resolves to
 * a real file *inside* that root, so a name carrying `..` or a symlink cannot walk
 * the app into opening an arbitrary file. Widening this to several roots would make
 * it a search-anywhere path, so historical packs in external storage are read by
 * MapLibre's own `mbtiles://` scheme instead and do not come through here.
 */
class MbtilesCache(private val baseDir: File) : MbtilesTileSource {
    private val dbHandles = ConcurrentHashMap<String, SQLiteDatabase>()
    private val mimeTypes = ConcurrentHashMap<String, String>()
    private val lock = Any()

    override fun getTile(packName: String, z: Int, x: Int, y: Int): MbtilesTile? {
        if (!packName.endsWith(".mbtiles")) return null

        val safeBaseDir = runCatching { baseDir.canonicalFile }.getOrNull() ?: return null
        val dbFile = runCatching { File(baseDir, packName).canonicalFile }.getOrNull() ?: return null

        if (!dbFile.path.startsWith(safeBaseDir.path + File.separator) || !dbFile.isFile) {
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
