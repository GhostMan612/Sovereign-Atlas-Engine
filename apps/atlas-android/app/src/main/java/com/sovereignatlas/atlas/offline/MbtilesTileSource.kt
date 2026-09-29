// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

data class MbtilesTile(
    val data: ByteArray,
    val mimeType: String,
)

interface MbtilesTileSource {
    fun getTile(packName: String, z: Int, x: Int, y: Int): MbtilesTile?
    fun shutdown()
}

fun xyzToTmsY(z: Int, y: Int): Int = (1 shl z) - 1 - y

fun vectorTileTemplateUrl(templated: String): String =
    templated.replace(Regex("\\.png$"), ".pbf")
