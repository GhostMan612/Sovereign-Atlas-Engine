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

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "pack_id" to packId,
            "provider_id" to providerId,
            "provider_title" to providerTitle,
            "z_min" to zMin,
            "z_max" to zMax,
            "x_min" to xMin,
            "x_max" to xMax,
            "y_min" to yMin,
            "y_max" to yMax,
            "bytes_per_tile" to bytesPerTile,
            "approved_bulk" to approvedBulk,
            "is_prefetch" to isPrefetch,
            "created_at" to createdAtEpoch,
            "lifecycle" to lifecycle.name,
            "received_tiles" to receivedTiles,
            "received_bytes" to receivedBytes,
            "failure_detail" to failureDetail,
            "persisted_tiles" to persistedTileCount,
            "persisted_bytes" to persistedEstimatedBytes,
        )
    }

    companion object {
        fun tryParse(raw: Any?): OfflinePackRecord? {
            if (raw !is Map<*, *>) return null
            val packId = raw["pack_id"] as? String ?: return null
            val providerId = raw["provider_id"] as? String ?: return null
            val providerTitle = raw["provider_title"] as? String ?: return null
            val zMin = (raw["z_min"] as? Number)?.toInt() ?: return null
            val zMax = (raw["z_max"] as? Number)?.toInt() ?: return null
            val xMin = (raw["x_min"] as? Number)?.toInt() ?: return null
            val xMax = (raw["x_max"] as? Number)?.toInt() ?: return null
            val yMin = (raw["y_min"] as? Number)?.toInt() ?: return null
            val yMax = (raw["y_max"] as? Number)?.toInt() ?: return null
            val lifecycle = try {
                OfflinePackLifecycle.valueOf(raw["lifecycle"] as? String ?: return null)
            } catch (error: IllegalArgumentException) {
                return null
            }
            val record = OfflinePackRecord(
                packId = packId,
                providerId = providerId,
                providerTitle = providerTitle,
                zMin = zMin,
                zMax = zMax,
                xMin = xMin,
                xMax = xMax,
                yMin = yMin,
                yMax = yMax,
                bytesPerTile = (raw["bytes_per_tile"] as? Number)?.toInt() ?: 0,
                approvedBulk = raw["approved_bulk"] as? Boolean ?: false,
                isPrefetch = raw["is_prefetch"] as? Boolean ?: false,
                createdAtEpoch = (raw["created_at"] as? Number)?.toLong() ?: return null,
            )
            record.lifecycle = lifecycle
            record.receivedTiles = (raw["received_tiles"] as? Number)?.toInt() ?: 0
            record.receivedBytes = (raw["received_bytes"] as? Number)?.toLong() ?: 0L
            record.failureDetail = raw["failure_detail"] as? String ?: ""
            record.persistedTileCount = (raw["persisted_tiles"] as? Number)?.toInt() ?: 0
            record.persistedEstimatedBytes =
                (raw["persisted_bytes"] as? Number)?.toLong() ?: 0L
            return record
        }
    }
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
