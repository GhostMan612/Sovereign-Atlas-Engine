// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline.mbtiles

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class MbtilesPack(val packId: String, val name: String, val description: String)

/**
 * Reads an MBTiles metadata table. Pure interface so the pack scanner and the
 * historical catalogue can be tested on the JVM with a fake; the Android SQLite
 * implementation lives in the adapter layer.
 */
interface MetadataReader {
    fun read(file: File): Pair<String, String>?

    /**
     * The complete `metadata` table as key-value pairs, or null when the file is
     * not a readable MBTiles database.
     *
     * [read] is a two-field projection of this and is what the pack list needs.
     * Consumers that want zoom range, format, or bounds need the whole table,
     * and re-querying per field would reopen the database per field.
     *
     * The default derives a two-entry map from [read] so a reader that only cares
     * about name and description keeps working without implementing this. A
     * reader that overrides this should leave [read] alone unless the pair is
     * genuinely derivable, otherwise the two can disagree.
     */
    fun readAll(file: File): Map<String, String>? = read(file)?.let { (name, description) ->
        buildMap {
            if (name.isNotEmpty()) put("name", name)
            if (description.isNotEmpty()) put("description", description)
        }
    }
}

interface MbtilesScanner {
    suspend fun scanPacks(): List<MbtilesPack>
}

private val PACK_NAME_PATTERN = Regex("^[A-Za-z0-9_.\\-]+$")

fun isServablePackName(name: String): Boolean = PACK_NAME_PATTERN.matches(name)

fun mbtilesSourceId(packId: String): String = "mbtiles-source-${mbtilesSafeId(packId)}"

fun mbtilesLayerId(packId: String): String = "mbtiles-layer-${mbtilesSafeId(packId)}"

fun mbtilesSafeId(packId: String): String = packId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

class DefaultMbtilesScanner(
    private val packsDir: () -> File,
    private val metadataReader: MetadataReader
) : MbtilesScanner {
    override suspend fun scanPacks(): List<MbtilesPack> = withContext(Dispatchers.IO) {
        val dir = packsDir()
        if (!dir.exists()) return@withContext emptyList()

        val files = dir.listFiles { _, name -> name.endsWith(".mbtiles") } ?: return@withContext emptyList()
        val results = mutableListOf<MbtilesPack>()

        for (file in files) {
            if (!isActive) return@withContext emptyList()

            if (!isServablePackName(file.name)) continue

            val (name, desc) = runCatching { metadataReader.read(file) }.getOrNull() ?: Pair(file.name, "")
            results.add(MbtilesPack(packId = file.name, name = name.ifEmpty { file.name }, description = desc))
        }
        results.sortedBy { it.packId }
    }
}
