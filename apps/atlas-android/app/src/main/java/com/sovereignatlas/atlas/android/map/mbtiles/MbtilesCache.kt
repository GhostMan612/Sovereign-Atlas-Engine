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
 * Serves tiles out of one or more pack directories.
 *
 * [baseDirs] is a list rather than a single directory because packs legitimately
 * live in more than one place: the pack journal under filesDir, and the
 * historical drop under externalFilesDir. It is a list and not a "search
 * everywhere" path because the containment guard below is the whole security
 * property of this class. A pack name is only honoured when it resolves to a real
 * file *inside* one of these roots, so adding a root is a deliberate widening of
 * what the app will open, not a side effect of calling it twice.
 *
 * Where two roots hold the same pack name, the first root wins. Callers that care
 * must keep pack file names unique across roots.
 */
class MbtilesCache(private val baseDirs: List<File>) : MbtilesTileSource {
    constructor(baseDir: File) : this(listOf(baseDir))

    private val dbHandles = ConcurrentHashMap<String, SQLiteDatabase>()
    private val mimeTypes = ConcurrentHashMap<String, String>()
    private val lock = Any()

    override fun getTile(packName: String, z: Int, x: Int, y: Int): MbtilesTile? {
        if (!packName.endsWith(".mbtiles")) return null

        val dbFile = resolveInsideRoots(packName) ?: return null

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

    /**
     * Resolves [packName] to an existing file inside one of the configured roots.
     *
     * `canonicalFile` is applied on both sides so `..` segments and symlinks
     * cannot walk a pack name out of its root. Returns null when the name escapes
     * every root or names a file that is not there.
     */
    private fun resolveInsideRoots(packName: String): File? {
        for (root in baseDirs) {
            val safeRoot = runCatching { root.canonicalFile }.getOrNull() ?: continue
            val candidate = runCatching { File(root, packName).canonicalFile }.getOrNull() ?: continue
            if (!candidate.path.startsWith(safeRoot.path + File.separator)) continue
            if (candidate.isFile) return candidate
        }
        return null
    }

    override fun shutdown() {
        dbHandles.values.forEach { runCatching { it.close() } }
        dbHandles.clear()
        mimeTypes.clear()
    }
}
