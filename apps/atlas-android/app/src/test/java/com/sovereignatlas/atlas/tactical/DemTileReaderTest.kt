// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical

import com.sovereignatlas.atlas.offline.DemBounds
import com.sovereignatlas.atlas.offline.LosTileCodec
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

final class DemTileReaderTest {
    private val bounds = DemBounds(
        minLat = 44.0,
        minLng = -93.5,
        maxLat = 45.0,
        maxLng = -92.5,
    )

    private fun seededRoot(grid: ShortArray): File {
        val root = Files.createTempDirectory("atlas-los").toFile()
        LosTileCodec.writeTile(
            root = root,
            z = 10,
            x = 1,
            y = 2,
            width = 2,
            height = 2,
            nodata = -32768,
            bounds = bounds,
            grid = grid,
        )
        return root
    }

    @Test
    fun roundTripPreservesGrid() {
        val grid = shortArrayOf(100, 200, -50, 0)
        DemTileReader.configure(seededRoot(grid))
        try {
            val decoded = DemTileReader.read(10, 1, 2)
            assertTrue(decoded != null && decoded.contentEquals(grid))
        } finally {
            DemTileReader.configure(null)
        }
    }

    @Test
    fun missingTileReadsNull() {
        DemTileReader.configure(Files.createTempDirectory("atlas-los-empty").toFile())
        try {
            assertNull(DemTileReader.read(10, 1, 2))
        } finally {
            DemTileReader.configure(null)
        }
    }

    @Test
    fun corruptTileReadsNullWithoutThrowing() {
        val root = Files.createTempDirectory("atlas-los-corrupt").toFile()
        val file = LosTileCodec.tileFile(root, 10, 1, 2)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))
        DemTileReader.configure(root)
        try {
            assertNull(DemTileReader.read(10, 1, 2))
        } finally {
            DemTileReader.configure(null)
        }
    }

    @Test
    fun flatEarthFallbackIsZero() {
        DemTileReader.configure(null)
        assertEquals(0.0, AtlasRadioLink.demHeightMeters(10, 1, 2, 0), 0.0)
    }

    @Test
    fun decodedHeightIsReturned() {
        val grid = shortArrayOf(100, 200, 300, 400)
        DemTileReader.configure(seededRoot(grid))
        try {
            assertEquals(300.0, AtlasRadioLink.demHeightMeters(10, 1, 2, 2), 0.0)
        } finally {
            DemTileReader.configure(null)
        }
    }
}
