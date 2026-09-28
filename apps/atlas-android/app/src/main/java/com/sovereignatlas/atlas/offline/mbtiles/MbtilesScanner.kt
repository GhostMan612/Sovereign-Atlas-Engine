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

interface MetadataReader {
    fun read(file: File): Pair<String, String>?
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
