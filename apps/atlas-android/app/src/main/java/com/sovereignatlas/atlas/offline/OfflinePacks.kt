// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import java.util.Locale

const val PACK_STORE_CAPACITY = 64
const val MAX_SESSION_TILES = 4096
const val EVENT_LOG_BOUND = 200
const val PACK_JOURNAL_DIR = "offline_packs"
const val PACK_INDEX_FILE = "index.json"

enum class OfflinePackLifecycle {
    planned,
    downloading,
    paused,
    quotaPaused,
    complete,
    failed,
    cancelled,
}

class OfflinePackRecord(
    val packId: String,
    val providerId: String,
    val providerTitle: String,
    val zMin: Int,
    val zMax: Int,
    val xMin: Int,
    val xMax: Int,
    val yMin: Int,
    val yMax: Int,
    val bytesPerTile: Int,
    val approvedBulk: Boolean,
    val isPrefetch: Boolean,
    val createdAtEpoch: Long,
) {
    var lifecycle = OfflinePackLifecycle.planned
    var receivedTiles = 0
    var receivedBytes = 0L
    var failureDetail = ""
    var persistedTileCount = 0
    var persistedEstimatedBytes = 0L

    val tileCount: Int get() = persistedTileCount
    val estimatedBytes: Long get() = persistedEstimatedBytes

    val isTerminal: Boolean get() =
        lifecycle == OfflinePackLifecycle.complete ||
            lifecycle == OfflinePackLifecycle.failed ||
            lifecycle == OfflinePackLifecycle.cancelled

    fun ageSeconds(nowEpoch: Long): Long = nowEpoch - createdAtEpoch
}

fun countTiles(
    zMin: Int,
    zMax: Int,
    xMin: Int,
    xMax: Int,
    yMin: Int,
    yMax: Int,
): Int {
    var total = 0L
    for (z in zMin..zMax) {
        val span = (xMax - xMin + 1L) * (yMax - yMin + 1L)
        total += span
        if (total > MAX_SESSION_TILES) return MAX_SESSION_TILES + 1
    }
    return total.toInt()
}

fun formatAge(seconds: Long): String {
    if (seconds < 0) return "clock-skew"
    if (seconds < 60) return "${seconds}s"
    if (seconds < 3600) return "${seconds / 60}m"
    if (seconds < 86400) return "${seconds / 3600}h"
    return "${seconds / 86400}d"
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1048576) return String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
    return String.format(Locale.US, "%.2f MiB", bytes / 1048576.0)
}
