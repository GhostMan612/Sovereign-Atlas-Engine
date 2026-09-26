// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.BitmapFactory
import android.util.Log
import com.sovereignatlas.atlas.geo.DemEngine
import com.sovereignatlas.atlas.geo.ImageDecoder
import com.sovereignatlas.atlas.geo.Rgb8Image
import com.sovereignatlas.atlas.offline.DemTileStore

class SqliteDemTileStore(private val db: SQLiteDatabase) : DemTileStore {
    override fun tileBytes(z: Int, x: Int, y: Int): ByteArray? {
        return try {
            db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(z.toString(), x.toString(), y.toString()),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getBlob(0) else null
            }
        } catch (error: SQLiteException) {
            Log.w("DemStore", "Tile read failed $z/$x/$y", error)
            null
        }
    }

    override fun tileFormat(): String {
        return try {
            db.rawQuery("SELECT value FROM metadata WHERE name = 'format'", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) ?: "png" else "png"
            }
        } catch (error: SQLiteException) {
            "png"
        }
    }
}

class AndroidImageDecoder : ImageDecoder {
    override fun decodeRgb8(imageBytes: ByteArray): Rgb8Image? {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Rgb8Image(bitmap.width, bitmap.height, pixels)
    }
}

// In-process DEM session: opened when a DEM map is activated, closed on
// deactivation. The track service reads the engine through here so profiling
// follows DEM selection without service restarts.
object DemSession {
    @Volatile
    var engine: DemEngine? = null

    @Volatile
    var store: DemTileStore? = null
}
