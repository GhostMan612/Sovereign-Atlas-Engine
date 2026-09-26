// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

data class Rgb8Image(val width: Int, val height: Int, val pixels: IntArray)

fun interface ImageDecoder {
    fun decodeRgb8(imageBytes: ByteArray): Rgb8Image?
}
