// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun get(url: String): Pair<Int, ByteArray> {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val code = connection.responseCode
        val body = if (code == 200) {
            connection.inputStream.use { it.readBytes() }
        } else {
            ByteArray(0)
        }
        return code to body
    } finally {
        connection.disconnect()
    }
}

private class FakeMbtiles(
    val fn: (String, Int, Int, Int) -> MbtilesTile?,
) : MbtilesTileSource {
    val seen = mutableListOf<String>()
    var shutdownCalls = 0
    override fun getTile(packName: String, z: Int, x: Int, y: Int): MbtilesTile? {
        seen += "$packName/$z/$x/$y"
        return fn(packName, z, x, y)
    }
    override fun shutdown() {
        shutdownCalls += 1
    }
}

final class PackTileServerTest {
    private fun seededDir(): File {
        val dir = Files.createTempDirectory("atlas-serve").toFile()
        val tile = File(dir, "pack-000001/10/1/2.png")
        tile.parentFile?.mkdirs()
        tile.writeBytes(byteArrayOf(1, 2, 3, 4))
        return dir
    }

    private fun mbtilesServer(
        fake: FakeMbtiles,
        basemapQuota: Int = BASEMAP_SESSION_TILES,
    ): PackTileServer {
        val server = PackTileServer(packsDir = { seededDir() }, basemapQuota = basemapQuota)
        server.mbtilesStore = fake
        return server
    }

    @Test
    fun servesStoredTile() {
        val server = PackTileServer(packsDir = { seededDir() })
        val port = server.start()
        try {
            assertTrue(port > 0)
            val (code, body) = get("http://127.0.0.1:$port/pack-000001/10/1/2.png")
            assertEquals(200, code)
            assertTrue(body.contentEquals(byteArrayOf(1, 2, 3, 4)))
            assertEquals(1L, server.tileHits())
        } finally {
            server.stop()
        }
        assertTrue(!server.isRunning())
    }

    @Test
    fun missingTileIs404() {
        val server = PackTileServer(packsDir = { seededDir() })
        val port = server.start()
        try {
            val (code, _) = get("http://127.0.0.1:$port/pack-000001/10/9/9.png")
            assertEquals(404, code)
        } finally {
            server.stop()
        }
    }

    @Test
    fun traversalIs404() {
        val server = PackTileServer(packsDir = { seededDir() })
        val port = server.start()
        try {
            val (code, _) = get("http://127.0.0.1:$port/../10/1/2.png")
            assertEquals(404, code)
        } finally {
            server.stop()
        }
    }

    @Test
    fun mbtilesHitServes200WithMime() {
        val fake = FakeMbtiles { _, _, _, _ -> MbtilesTile(byteArrayOf(9, 8, 7), "image/png") }
        val server = mbtilesServer(fake)
        val port = server.start()
        try {
            val connection = URL("http://127.0.0.1:$port/region.mbtiles/2/1/2.png").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                assertEquals(200, connection.responseCode)
                assertEquals("image/png", connection.contentType)
                val body = connection.inputStream.use { it.readBytes() }
                assertTrue(body.contentEquals(byteArrayOf(9, 8, 7)))
            } finally {
                connection.disconnect()
            }
            assertEquals(1L, server.basemapHits())
        } finally {
            server.stop()
        }
    }

    @Test
    fun mbtilesPassesXyzThroughToStore() {
        val fake = FakeMbtiles { _, _, _, _ -> MbtilesTile(byteArrayOf(1), "image/png") }
        val server = mbtilesServer(fake)
        val port = server.start()
        try {
            val (code, _) = get("http://127.0.0.1:$port/region.mbtiles/2/1/2.png")
            assertEquals(200, code)
            assertEquals(listOf("region.mbtiles/2/1/2"), fake.seen)
        } finally {
            server.stop()
        }
    }

    @Test
    fun xyzFlipsToTms() {
        assertEquals(0, xyzToTmsY(0, 0))
        assertEquals(1, xyzToTmsY(2, 2))
        assertEquals(511, xyzToTmsY(10, 512))
    }

    @Test
    fun mbtilesMissingIs404WithoutHit() {
        val fake = FakeMbtiles { _, _, _, _ -> null }
        val server = mbtilesServer(fake)
        val port = server.start()
        try {
            val (code, _) = get("http://127.0.0.1:$port/region.mbtiles/2/1/2.png")
            assertEquals(404, code)
            assertEquals(0L, server.basemapHits())
        } finally {
            server.stop()
        }
    }

    @Test
    fun mbtilesBadCoordsAre404WithoutStoreCall() {
        val fake = FakeMbtiles { _, _, _, _ -> MbtilesTile(byteArrayOf(1), "image/png") }
        val server = mbtilesServer(fake)
        val port = server.start()
        try {
            val (code, _) = get("http://127.0.0.1:$port/region.mbtiles/2/1/zz.png")
            assertEquals(404, code)
            assertTrue(fake.seen.isEmpty())
        } finally {
            server.stop()
        }
    }

    @Test
    fun mbtilesQuotaExhaustionIs404() {
        val fake = FakeMbtiles { _, _, _, _ -> MbtilesTile(byteArrayOf(1), "image/png") }
        val server = mbtilesServer(fake, basemapQuota = 1)
        val port = server.start()
        try {
            val (first, _) = get("http://127.0.0.1:$port/region.mbtiles/2/1/2.png")
            assertEquals(200, first)
            val (second, _) = get("http://127.0.0.1:$port/region.mbtiles/2/1/1.png")
            assertEquals(404, second)
            assertEquals(1L, server.basemapHits())
        } finally {
            server.stop()
        }
    }

    @Test
    fun stopShutsDownMbtilesStore() {
        val fake = FakeMbtiles { _, _, _, _ -> null }
        val server = mbtilesServer(fake)
        server.start()
        server.stop()
        assertEquals(1, fake.shutdownCalls)
    }

    @Test
    fun tileUrlNeedsRunningServer() {
        val server = PackTileServer(packsDir = { seededDir() })
        assertNull(server.tileUrl("pack-000001"))
        val port = server.start()
        try {
            assertEquals(
                "http://127.0.0.1:$port/pack-000001/{z}/{x}/{y}.png",
                server.tileUrl("pack-000001"),
            )
        } finally {
            server.stop()
        }
    }
}
