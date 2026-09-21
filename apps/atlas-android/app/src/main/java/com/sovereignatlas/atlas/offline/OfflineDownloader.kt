// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import com.sovereignatlas.atlas.field.JournalJson
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val PER_TILE_TIMEOUT_MS = 30_000

sealed interface DownloadResult {
    data class Complete(val tiles: Int, val bytes: Long) : DownloadResult
    data class Cancelled(val tiles: Int, val bytes: Long) : DownloadResult
    data class Failed(val detail: String, val tiles: Int, val bytes: Long) : DownloadResult
}

class OfflineDownloader(
    private val chunk: (descriptor: OfflineProviderDescriptor, z: Int, x: Int, y: Int) -> ByteArray?,
    private val minIntervalMs: Long = 500L,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    fun download(
        record: OfflinePackRecord,
        descriptor: OfflineProviderDescriptor,
        dir: File,
        onProgress: (receivedTiles: Int, receivedBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
    ): DownloadResult {
        record.lifecycle = OfflinePackLifecycle.downloading
        var tiles = 0
        var bytes = 0L
        var lastRequestAt = 0L
        try {
            for (z in record.zMin..record.zMax) {
                for (x in record.xMin..record.xMax) {
                    for (y in record.yMin..record.yMax) {
                        if (isCancelled()) {
                            record.lifecycle = OfflinePackLifecycle.cancelled
                            return DownloadResult.Cancelled(tiles, bytes)
                        }
                        val file = File(dir, "$z/$x/$y.png")
                        if (file.isFile && file.length() > 0) {
                            tiles += 1
                            bytes += file.length()
                            record.receivedTiles = tiles
                            record.receivedBytes = bytes
                            onProgress(tiles, bytes)
                            continue
                        }
                        throttle(lastRequestAt)
                        lastRequestAt = clockMs()
                        val body = try {
                            chunk(descriptor, z, x, y)
                        } catch (error: Throwable) {
                            record.lifecycle = OfflinePackLifecycle.failed
                            record.failureDetail = error.message ?: error.toString()
                            return DownloadResult.Failed(record.failureDetail, tiles, bytes)
                        }
                        if (body == null || body.isEmpty()) {
                            record.lifecycle = OfflinePackLifecycle.failed
                            record.failureDetail = "empty tile $z/$x/$y"
                            return DownloadResult.Failed(record.failureDetail, tiles, bytes)
                        }
                        file.parentFile?.mkdirs()
                        file.writeBytes(body)
                        tiles += 1
                        bytes += body.size
                        record.receivedTiles = tiles
                        record.receivedBytes = bytes
                        onProgress(tiles, bytes)
                    }
                }
            }
        } catch (error: Throwable) {
            record.lifecycle = OfflinePackLifecycle.failed
            record.failureDetail = error.message ?: error.toString()
            return DownloadResult.Failed(record.failureDetail, tiles, bytes)
        }
        File(dir, "manifest.json").writeText(
            JournalJson.render(
                mapOf(
                    "pack_id" to record.packId,
                    "provider_id" to record.providerId,
                    "tiles" to tiles,
                    "bytes" to bytes,
                ),
            ),
        )
        record.lifecycle = OfflinePackLifecycle.complete
        return DownloadResult.Complete(tiles, bytes)
    }

    private fun throttle(lastRequestAt: Long) {
        if (minIntervalMs <= 0L || lastRequestAt <= 0L) return
        val wait = minIntervalMs - (clockMs() - lastRequestAt)
        if (wait > 0) sleeper(wait)
    }

    companion object {
        fun httpChunk(
            descriptor: OfflineProviderDescriptor,
            z: Int,
            x: Int,
            y: Int,
        ): ByteArray? {
            val raw = resolveTileUrl(descriptor, z, x, y) ?: return null
            val connection = URL(raw).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = PER_TILE_TIMEOUT_MS
                connection.readTimeout = PER_TILE_TIMEOUT_MS
                connection.setRequestProperty(
                    "User-Agent",
                    descriptor.headers["User-Agent"] ?: "SovereignAtlasEngine/0.1.0",
                )
                for ((key, value) in descriptor.headers) {
                    if (!key.equals("User-Agent", ignoreCase = true)) {
                        connection.setRequestProperty(key, value)
                    }
                }
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                return connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
