// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.offline.DemTileStore
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DemTileKey(val z: Int, val x: Int, val y: Int)

class DemEngine(
    private val tiles: DemTileStore,
    private val decoder: ImageDecoder,
) {
    companion object {
        const val ZOOM = 14
        const val CACHE_SIZE = 8
    }

    private val tileCache = object : LinkedHashMap<DemTileKey, Rgb8Image>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<DemTileKey, Rgb8Image>,
        ): Boolean {
            return size > CACHE_SIZE
        }
    }

    @Synchronized
    private fun getCachedTile(key: DemTileKey): Rgb8Image? = tileCache[key]

    @Synchronized
    private fun putCachedTile(key: DemTileKey, image: Rgb8Image) {
        tileCache[key] = image
    }

    fun getElevation(latitude: Double, longitude: Double): Double? {
        if (latitude < -85.05112878 || latitude > 85.05112878) return null
        val (key, fracX, fracY) = latLngToTileFraction(latitude, longitude, ZOOM)
        val image = getCachedTile(key) ?: run {
            val bytes = tiles.tileBytes(key.z, key.x, key.y) ?: return null
            val decoded = decoder.decodeRgb8(bytes) ?: return null
            putCachedTile(key, decoded)
            decoded
        }
        return sampleBilinear(image, fracX * image.width, fracY * image.height)
    }

    suspend fun getElevationsBatch(points: List<Pair<Double, Double>>): List<Double?> =
        withContext(Dispatchers.IO) {
            val results = arrayOfNulls<Double>(points.size)
            val missing = LinkedHashMap<DemTileKey, MutableList<Int>>()
            val fractionOf = HashMap<Int, Pair<Double, Double>>()
            points.forEachIndexed { index, (latitude, longitude) ->
                if (latitude < -85.05112878 || latitude > 85.05112878) return@forEachIndexed
                val (key, fracX, fracY) = latLngToTileFraction(latitude, longitude, ZOOM)
                fractionOf[index] = fracX to fracY
                val cached = getCachedTile(key)
                if (cached != null) {
                    results[index] = sampleBilinear(cached, fracX * cached.width, fracY * cached.height)
                } else {
                    missing.getOrPut(key) { ArrayList() }.add(index)
                }
            }
            for ((key, indices) in missing) {
                val bytes = tiles.tileBytes(key.z, key.x, key.y) ?: continue
                val image = decoder.decodeRgb8(bytes) ?: continue
                putCachedTile(key, image)
                for (index in indices) {
                    val (fracX, fracY) = fractionOf[index] ?: continue
                    results[index] = sampleBilinear(image, fracX * image.width, fracY * image.height)
                }
            }
            results.toList()
        }

    fun latLngToTileFraction(latitude: Double, longitude: Double, zoom: Int): Triple<DemTileKey, Double, Double> {
        val scale = (1 shl zoom).toDouble()
        val exactX = (longitude + 180.0) / 360.0 * scale
        val exactY = (1.0 - kotlin.math.ln(
            kotlin.math.tan(Math.toRadians(latitude)) +
                1.0 / kotlin.math.cos(Math.toRadians(latitude)),
        ) / Math.PI) / 2.0 * scale
        val key = DemTileKey(zoom, floor(exactX).toInt(), floor(exactY).toInt())
        return Triple(key, exactX - floor(exactX), exactY - floor(exactY))
    }

    fun decodeElevation(red: Int, green: Int, blue: Int): Double {
        return -10000.0 + ((red * 256 * 256 + green * 256 + blue) * 0.1)
    }

    private fun sampleBilinear(image: Rgb8Image, x: Double, y: Double): Double {
        val x0 = floor(x).toInt().coerceIn(0, image.width - 2)
        val y0 = floor(y).toInt().coerceIn(0, image.height - 2)
        val fx = (x - x0).coerceIn(0.0, 1.0)
        val fy = (y - y0).coerceIn(0.0, 1.0)
        val e00 = elevationAt(image, x0, y0)
        val e10 = elevationAt(image, x0 + 1, y0)
        val e01 = elevationAt(image, x0, y0 + 1)
        val e11 = elevationAt(image, x0 + 1, y0 + 1)
        val top = e00 + (e10 - e00) * fx
        val bottom = e01 + (e11 - e01) * fx
        return top + (bottom - top) * fy
    }

    private fun elevationAt(image: Rgb8Image, x: Int, y: Int): Double {
        val pixel = image.pixels[y * image.width + x]
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return decodeElevation(red, green, blue)
    }
}
