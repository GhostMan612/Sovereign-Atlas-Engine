// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.offline

import com.sovereignatlas.atlas.offline.OfflineProviderDescriptor
import com.sovereignatlas.atlas.offline.PER_TILE_TIMEOUT_MS
import com.sovereignatlas.atlas.offline.resolveTileUrl
import java.net.HttpURLConnection
import java.net.URL

/**
 * The `java.net.HttpURLConnection` half of tile acquisition, relocated here by
 * ADR-006.
 *
 * This is the entire platform surface of the offline download path. It was
 * previously `OfflineDownloader.httpChunk` in the pure tier, reached only through
 * the injected `chunk` function type. Now that the implementation lives in the
 * `android/` package, `offline/` holds no networking at all: the pure
 * [com.sovereignatlas.atlas.offline.OfflineMapDownloader] receives bytes through a
 * lambda and never learns how they were fetched.
 *
 * Policy stays in the pure tier and is imported, not redefined here. The 30 s
 * per-tile timeout is [PER_TILE_TIMEOUT_MS] in `offline/`, because a timeout is a
 * decision about how long to wait, not a fact about `HttpURLConnection`. Phase 2
 * deliberately did not move it.
 *
 * `User-Agent` is sent on every request even when the descriptor supplies none:
 * the OpenStreetMap Tile Usage Policy requires it, and a missing UA gets an app
 * blocked rather than throttled.
 */
object AndroidHttpDownloader {

    fun httpChunk(
        descriptor: OfflineProviderDescriptor,
        z: Int,
        x: Int,
        y: Int,
    ): ByteArray? {
        // A provider with no URL template - the local-bundle descriptor - has
        // nothing to fetch. Returning null is not an error: the caller treats it as
        // an empty tile and fails that pack with a detail, which is the honest
        // outcome for a provider that cannot serve tiles at all.
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