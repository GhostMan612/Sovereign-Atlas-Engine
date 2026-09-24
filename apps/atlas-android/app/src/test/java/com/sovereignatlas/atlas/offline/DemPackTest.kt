// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class DemPackTest {
    private fun spec(): DemPackSpec {
        return DemPackSpec(
            packId = "dem-test",
            datasetVersion = "NASADEM_HGT v1",
            retrievalUrl = "https://e4ftl01.cr.usgs.gov/MEASURES/NASADEM_HGT.001/",
            bounds = DemBounds(
                minLat = 44.0,
                minLng = -93.5,
                maxLat = 45.0,
                maxLng = -92.5,
            ),
        )
    }

    @Test
    fun metadataPinsTypeEncodingBoundsAndLicense() {
        val meta = DemPackMetadata.mbtilesMetadata(spec())
        assertEquals("raster-dem", meta["type"])
        assertEquals("mapbox", meta["encoding"])
        assertEquals("-93.5,44.0,-92.5,45.0", meta["bounds"])
        assertEquals("See SOURCE.txt", meta["license"])
    }

    @Test
    fun sourceTextCarriesProvenanceChainVersionAndUrl() {
        val text = DemSidecars.sourceText(spec())
        assertTrue(text.contains("SRTM -> NASADEM -> LP DAAC"))
        assertTrue(text.contains("NASADEM_HGT v1"))
        assertTrue(text.contains("https://e4ftl01.cr.usgs.gov/MEASURES/NASADEM_HGT.001/"))
    }

    @Test
    fun sidecarsAreWrittenWithVerifiableSha256Sums() {
        val root = Files.createTempDirectory("atlas-dem").toFile()
        val payload = File(root, "tile.bin")
        payload.writeBytes(byteArrayOf(9, 8, 7))
        DemSidecars.write(root, spec(), listOf(payload))
        assertTrue(File(root, "LICENSE.txt").isFile)
        assertTrue(File(root, "SOURCE.txt").isFile)
        val sums = File(root, "SHA256SUMS").readText(Charsets.UTF_8).trim().split("\n")
        assertEquals(3, sums.size)
        val digest = MessageDigest.getInstance("SHA-256")
        for (line in sums) {
            val parts = line.split("  ")
            assertEquals(2, parts.size)
            assertTrue(parts[0].matches(Regex("[0-9a-f]{64}")))
            digest.reset()
            val expected = digest.digest(File(root, parts[1]).readBytes())
                .joinToString("") { String.format(Locale.US, "%02x", it) }
            assertEquals(expected, parts[0])
        }
    }

    @Test
    fun losHeaderHasDocumentedLayout() {
        assertEquals(44, LosTileCodec.HEADER_BYTES)
        assertEquals(1, LosTileCodec.VERSION)
    }
}
