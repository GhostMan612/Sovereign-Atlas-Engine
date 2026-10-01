// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import java.io.File

/**
 * The pure contract for acquiring tiles for an offline pack.
 *
 * Per ADR-006, the `HttpURLConnection` implementation moves to the `android/`
 * tier. This interface is what the pure tier codes against, and it deliberately
 * carries no transport type — no connection, no stream, no response code.
 *
 * The transport seam already existed: the concrete downloader takes a `chunk`
 * function that returns raw bytes, and the HTTP implementation is supplied as a
 * function reference at the call site. That is why this extraction costs almost
 * nothing — the pure logic was never holding a socket, it was calling one
 * through a lambda. What moves in Phase 2 is the lambda's implementation, not
 * the download loop.
 *
 * `download` is blocking and must run off the main thread. It reports progress
 * through a callback on the calling thread, and cancellation is polled between
 * tiles rather than signalled, so a cancel can take at most one tile's fetch to
 * take effect. That is a contract, not an implementation detail: a caller that
 * expects immediate cancellation is relying on something this does not promise.
 */
interface OfflineMapDownloader {

    /**
     * Fetches every tile in [record]'s zoom/x/y bounds into [dir], skipping tiles
     * already present and non-empty.
     *
     * Mutates [record] as it goes — lifecycle transitions to downloading, then to
     * complete, cancelled, or failed, with `receivedTiles`/`receivedBytes` updated
     * per tile and `failureDetail` set on failure. Callers read the result type
     * for the outcome and the record for the progress trail.
     *
     * Returns [DownloadResult.Complete] only after writing `manifest.json`.
     */
    fun download(
        record: OfflinePackRecord,
        descriptor: OfflineProviderDescriptor,
        dir: File,
        onProgress: (receivedTiles: Int, receivedBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
    ): DownloadResult
}