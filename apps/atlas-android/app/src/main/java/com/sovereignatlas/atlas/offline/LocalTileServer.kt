// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

/**
 * The pure contract for serving local tiles to MapLibre over loopback HTTP.
 *
 * Per ADR-006, the socket implementation moves to the `android/` tier. This
 * interface is what the pure tier codes against, and it exists so that callers
 * never name a `ServerSocket`, a `Socket`, or an HTTP status line. Everything
 * here is a value, a count, a port number, or a URL string — types that mean the
 * same thing on a JVM test as they do on a device.
 *
 * The tile sources are declared as the existing [DemTileStore] and
 * [MbtilesTileSource] interfaces rather than as concrete stores, so a caller can
 * install a fake DEM without touching a filesystem. Those two were already
 * interfaces for exactly this reason; nothing about the extraction changes them.
 *
 * `port()` returns -1 when the server is not bound, and the URL builders return
 * null in that same state rather than a string pointing at port 0. A caller that
 * treats null as "no basemap available" is correct; one that treats it as an
 * error path is not, so the contract states it here once.
 */
interface LocalTileServer {

    /**
     * Binds the server and begins accepting connections.
     *
     * Idempotent: calling it while already running returns the current port
     * without rebinding. Returns the bound port, which differs from the
     * configured one when port 0 was requested and the OS chose.
     */
    fun start(): Int

    /** Unbinds, stops accepting, and releases the tile source. Safe to call when stopped. */
    fun stop()

    /** The bound port, or -1 when not running. */
    fun port(): Int

    fun isRunning(): Boolean

    /**
     * Loopback URL template for a basemap pack, or null when not bound.
     *
     * The template keeps `{z}/{x}/{y}` unexpanded: MapLibre substitutes them per
     * tile, so a caller must not pre-fill them.
     */
    fun tileUrl(packId: String): String?

    /** Loopback URL template for DEM tiles, or null when not bound. */
    fun demTileUrl(): String?

    /** Whether a DEM source is present on disk. Says nothing about whether it is reachable. */
    fun demAvailable(): Boolean

    /** Combined basemap + DEM tile count served. */
    fun tileHits(): Long

    fun basemapHits(): Long

    fun demHits(): Long

    /**
     * DEM source, installed when a DEM map is activated and cleared on teardown.
     * Nullable because "no DEM activated" is a real state the map layer handles.
     */
    var demStore: DemTileStore?

    /** MBTiles-backed basemap source. Nullable for the same reason. */
    var mbtilesStore: MbtilesTileSource?
}