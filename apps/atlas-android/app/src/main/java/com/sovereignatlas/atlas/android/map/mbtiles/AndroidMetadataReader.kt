// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.map.mbtiles

import android.database.sqlite.SQLiteDatabase
import com.sovereignatlas.atlas.offline.mbtiles.MetadataReader
import java.io.File

class AndroidMetadataReader : MetadataReader {
    override fun read(file: File): Pair<String, String> {
        var name = ""
        var description = ""
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery(
                "SELECT name, value FROM metadata WHERE name IN ('name', 'description')",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    val value = cursor.getString(1) ?: ""
                    if (key == "name") name = value
                    if (key == "description") description = value
                }
            }
        } finally {
            runCatching { db.close() }
        }
        return Pair(name, description)
    }
}
