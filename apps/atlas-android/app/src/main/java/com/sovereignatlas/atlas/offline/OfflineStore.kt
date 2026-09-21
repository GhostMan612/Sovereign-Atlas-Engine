// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import com.sovereignatlas.atlas.field.JournalJson
import java.io.File

const val PACK_INDEX_VERSION = 1

sealed interface PlanOutcome {
    data class Planned(val record: OfflinePackRecord) : PlanOutcome
    data class Refused(val reason: String) : PlanOutcome
    data class Blocked(val reason: String) : PlanOutcome
}

class OfflineStore(
    private val directoryProvider: () -> File,
    private val clockEpoch: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val packs = LinkedHashMap<String, OfflinePackRecord>()
    private val events = ArrayList<String>()
    private var packSequence = 0
    private var lastError: Throwable? = null
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun packs(): List<OfflinePackRecord> = packs.values.toList()

    fun lookup(packId: String): OfflinePackRecord? = packs[packId]

    fun events(): List<String> = events.reversed()

    fun lastErrorOrNull(): Throwable? = lastError

    fun plan(
        providerId: String,
        zMin: Int,
        zMax: Int,
        xMin: Int,
        xMax: Int,
        yMin: Int,
        yMax: Int,
        approvedBulk: Boolean,
        isPrefetch: Boolean,
    ): PlanOutcome {
        val provider = OfflineBuiltinProviders.lookup(providerId)
            ?: return PlanOutcome.Refused("unknown provider $providerId").also {
                log("refuse $providerId unknown-provider")
            }
        if (provider.urlTemplate == null) {
            return PlanOutcome.Blocked("provider ${provider.id} has no downloadable tiles")
        }
        if (zMin > zMax || xMin > xMax || yMin > yMax) {
            return PlanOutcome.Refused("empty tile range")
        }
        if (!provider.prefetchAllowed && !approvedBulk) {
            val guard = provider.bulkGuard ?: "bulk download requires approval"
            return PlanOutcome.Refused("BULK_GUARD: $guard").also {
                log("refuse ${provider.id} bulk-guard")
            }
        }
        val total = countTiles(
            zMin = zMin,
            zMax = zMax,
            xMin = xMin,
            xMax = xMax,
            yMin = yMin,
            yMax = yMax,
        )
        if (total > MAX_SESSION_TILES) {
            return PlanOutcome.Refused(
                "quota: $total tiles exceeds session cap $MAX_SESSION_TILES",
            ).also {
                log("refuse ${provider.id} quota $total")
            }
        }
        packSequence += 1
        val packId = "pack-" + packSequence.toString().padStart(6, '0')
        val record = OfflinePackRecord(
            packId = packId,
            providerId = provider.id,
            providerTitle = provider.title,
            zMin = zMin,
            zMax = zMax,
            xMin = xMin,
            xMax = xMax,
            yMin = yMin,
            yMax = yMax,
            bytesPerTile = 0,
            approvedBulk = approvedBulk,
            isPrefetch = isPrefetch,
            createdAtEpoch = clockEpoch(),
        )
        packs[packId] = record
        record.persistedTileCount = total
        lastError = null
        persist()
        log("plan $packId ${provider.id} $total tiles")
        notifyListeners()
        return PlanOutcome.Planned(record)
    }

    fun removePack(packId: String): Boolean {
        if (packs.remove(packId) == null) return false
        deletePackDir(packId)
        val index = File(packsDir(), PACK_INDEX_FILE)
        if (index.exists()) persist()
        lastError = null
        log("remove $packId")
        notifyListeners()
        return true
    }

    fun packDir(packId: String): File = File(packsDir(), packId)

    fun persist() {
        try {
            val dir = packsDir()
            File(dir, PACK_INDEX_FILE).writeText(
                JournalJson.render(indexEnvelope()),
            )
        } catch (error: Throwable) {
            lastError = error
            notifyListeners()
        }
    }

    fun restore() {
        try {
            restoreUnsafe()
        } catch (error: Throwable) {
            lastError = error
            notifyListeners()
        }
    }

    fun log(message: String) {
        events.add("${clockEpoch()} $message")
        if (events.size > EVENT_LOG_BOUND) {
            events.subList(0, events.size - EVENT_LOG_BOUND).clear()
        }
    }

    fun notifyChanged() {
        notifyListeners()
    }

    private fun packsDir(): File {
        val dir = File(directoryProvider(), PACK_JOURNAL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun deletePackDir(packId: String) {
        packDir(packId).deleteRecursively()
    }

    private fun indexEnvelope(): Map<String, Any?> {
        return mapOf(
            "version" to PACK_INDEX_VERSION,
            "packs" to packs.values.map { it.toMap() },
        )
    }

    private fun restoreUnsafe() {
        val file = File(packsDir(), PACK_INDEX_FILE)
        if (!file.exists()) {
            lastError = null
            notifyListeners()
            return
        }
        val decoded = JournalJson.parse(file.readText())
        if (decoded !is Map<*, *>) {
            lastError = IllegalStateException("pack index is not an object")
            notifyListeners()
            return
        }
        if ((decoded["version"] as? Number)?.toInt() != PACK_INDEX_VERSION) {
            lastError = IllegalStateException("unsupported pack index version")
            notifyListeners()
            return
        }
        val rawPacks = decoded["packs"]
        if (rawPacks !is List<*>) {
            lastError = IllegalStateException("pack index packs is not a list")
            notifyListeners()
            return
        }
        val loaded = LinkedHashMap<String, OfflinePackRecord>()
        var skipped = 0
        for (entry in rawPacks) {
            val record = OfflinePackRecord.tryParse(entry)
            if (record == null || loaded.containsKey(record.packId)) {
                skipped += 1
                continue
            }
            loaded[record.packId] = record
            packSequence = maxOf(packSequence, packSequenceOf(record.packId))
        }
        packs.clear()
        packs.putAll(loaded)
        lastError = null
        log("restore ${loaded.size} packs ($skipped skipped)")
        notifyListeners()
    }

    private fun packSequenceOf(packId: String): Int {
        return packId.removePrefix("pack-").toIntOrNull() ?: 0
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener()
        }
    }
}
