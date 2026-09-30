// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.map.mbtiles

import android.database.sqlite.SQLiteDatabase
import com.sovereignatlas.atlas.offline.mbtiles.MetadataReader
import java.io.File

/**
 * Android SQLite implementation of [MetadataReader].
 *
 * The only reason this class exists is that opening a SQLite file is an Android
 * (or JDBC) capability, and the callers that need metadata are pure. Everything
 * above this boundary takes a [MetadataReader] and can be faked on the JVM.
 */
class AndroidMetadataReader : MetadataReader {

    override fun readAll(file: File): Map<String, String>? {
        val db = runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull() ?: return null

        return try {
            val rows = LinkedHashMap<String, String>()
            db.rawQuery(METADATA_QUERY, null).use { cursor ->
                while (cursor.moveToNext()) {
                    // A metadata table row with a null name is not a usable key.
                    val key = cursor.getString(0) ?: continue
                    rows[key] = cursor.getString(1) ?: ""
                }
            }
            rows
        } catch (_: Exception) {
            // A file that is not a SQLite database at all, or a schema without a
            // metadata table, must read as "no metadata" rather than take down
            // the caller. Returns null, not an empty map: no table is a different
            // fact from a table that happens to be empty.
            null
        } finally {
            runCatching { db.close() }
        }
    }

    /**
     * [readAll] already opened the database for this file, so [read] projects from
     * it rather than issuing a second query with a narrower WHERE clause.
     */
    override fun read(file: File): Pair<String, String>? =
        readAll(file)?.let { metadata -> (metadata["name"] ?: "") to (metadata["description"] ?: "") }

    private companion object {
        const val METADATA_QUERY = "SELECT name, value FROM metadata"
    }
}