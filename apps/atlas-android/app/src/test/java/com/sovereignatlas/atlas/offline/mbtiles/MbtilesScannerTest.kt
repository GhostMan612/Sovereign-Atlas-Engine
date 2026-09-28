// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline.mbtiles

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMetadataReader(
    private val fn: (File) -> Pair<String, String>? = { it.name to "" },
) : MetadataReader {
    var reads: Int = 0
    override fun read(file: File): Pair<String, String>? {
        reads += 1
        return fn(file)
    }
}

private fun dirWith(vararg names: String): File {
    val dir = Files.createTempDirectory("atlas-scan").toFile()
    for (name in names) {
        File(dir, name).writeBytes(byteArrayOf(1, 2, 3))
    }
    return dir
}

final class MbtilesScannerTest {
    @Test
    fun metadataMapsIntoDomain() = runBlocking {
        val dir = dirWith("survey.mbtiles")
        val reader = FakeMetadataReader { "Old Survey" to "1870 plat" }
        val packs = DefaultMbtilesScanner({ dir }, reader).scanPacks()
        assertEquals(1, packs.size)
        assertEquals("survey.mbtiles", packs[0].packId)
        assertEquals("Old Survey", packs[0].name)
        assertEquals("1870 plat", packs[0].description)
    }

    @Test
    fun unreadableMetadataFallsBackToFileName() = runBlocking {
        val dir = dirWith("broken.mbtiles")
        val packs = DefaultMbtilesScanner({ dir }, FakeMetadataReader { null }).scanPacks()
        assertEquals(1, packs.size)
        assertEquals("broken.mbtiles", packs[0].name)
        assertEquals("", packs[0].description)
    }

    @Test
    fun blankMetadataNameFallsBackToFileName() = runBlocking {
        val dir = dirWith("blank.mbtiles")
        val packs = DefaultMbtilesScanner({ dir }, FakeMetadataReader { "" to "desc" }).scanPacks()
        assertEquals("blank.mbtiles", packs[0].name)
        assertEquals("desc", packs[0].description)
    }

    @Test
    fun unsafeFileNamesAreExcluded() = runBlocking {
        val dir = dirWith("good.mbtiles", "bad name.mbtiles", "pct%20name.mbtiles", "quest ion.mbtiles")
        val reader = FakeMetadataReader()
        val packs = DefaultMbtilesScanner({ dir }, reader).scanPacks()
        assertEquals(1, packs.size)
        assertEquals("good.mbtiles", packs[0].packId)
        assertEquals(1, reader.reads)
    }

    @Test
    fun nonMbtilesFilesAreIgnored() = runBlocking {
        val dir = dirWith("good.mbtiles", "notes.txt", "index.json")
        val reader = FakeMetadataReader()
        val packs = DefaultMbtilesScanner({ dir }, reader).scanPacks()
        assertEquals(1, packs.size)
        assertEquals("good.mbtiles", packs[0].packId)
        assertEquals(1, reader.reads)
    }

    @Test
    fun resultsAreOrderedByPackId() = runBlocking {
        val dir = dirWith("zulu.mbtiles", "alpha.mbtiles", "mike.mbtiles")
        val packs = DefaultMbtilesScanner({ dir }, FakeMetadataReader()).scanPacks()
        assertEquals(
            listOf("alpha.mbtiles", "mike.mbtiles", "zulu.mbtiles"),
            packs.map { it.packId },
        )
    }

    @Test
    fun missingDirectoryYieldsEmptyList() = runBlocking {
        val missing = File(System.getProperty("java.io.tmpdir"), "atlas-absent-dir-xyz")
        val reader = FakeMetadataReader()
        val packs = DefaultMbtilesScanner({ missing }, reader).scanPacks()
        assertTrue(packs.isEmpty())
        assertEquals(0, reader.reads)
    }

    @Test
    fun cancelledScanPerformsNoReadsAndYieldsNothing() = runBlocking {
        val dir = dirWith("a.mbtiles", "b.mbtiles")
        val reader = FakeMetadataReader()
        val owner = Job()
        owner.cancel()
        val outcome = runCatching {
            withContext(owner) { DefaultMbtilesScanner({ dir }, reader).scanPacks() }
        }
        assertNull(outcome.getOrNull())
        assertEquals(0, reader.reads)
    }

    @Test
    fun servableNameCharsetIsExplicit() {
        assertTrue(isServablePackName("survey_1870-v2.mbtiles"))
        assertFalse(isServablePackName("bad name.mbtiles"))
        assertFalse(isServablePackName("slash/name.mbtiles"))
        assertFalse(isServablePackName("percent%20.mbtiles"))
    }

    @Test
    fun safeIdsAreStableAndPrefixed() {
        // The prescribed charset excludes dots, so they normalize to underscores too.
        assertEquals("a_b_mbtiles", mbtilesSafeId("a b.mbtiles"))
        assertEquals("mbtiles-source-a_b_mbtiles", mbtilesSourceId("a b.mbtiles"))
        assertEquals("mbtiles-layer-a_b_mbtiles", mbtilesLayerId("a b.mbtiles"))
        assertEquals("mbtiles-source-survey_mbtiles", mbtilesSourceId("survey.mbtiles"))
        assertEquals("mbtiles-layer-survey_mbtiles", mbtilesLayerId("survey.mbtiles"))
    }
}
