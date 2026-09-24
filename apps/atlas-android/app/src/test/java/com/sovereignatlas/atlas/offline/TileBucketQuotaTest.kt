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

private fun quotaGet(url: String): Int {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        return connection.responseCode
    } finally {
        connection.disconnect()
    }
}

final class TileBucketQuotaTest {
    private fun seededDir(): File {
        val dir = Files.createTempDirectory("atlas-quota").toFile()
        val base = File(dir, "pack-000001/10/1/2.png")
        base.parentFile?.mkdirs()
        base.writeBytes(byteArrayOf(1, 2, 3, 4))
        val dem = File(dir, "dem/10/1/2.png")
        dem.parentFile?.mkdirs()
        dem.writeBytes(byteArrayOf(5, 6, 7, 8))
        return dir
    }

    @Test
    fun basemapBucketExhaustsIndependently() {
        val server = PackTileServer(
            packsDir = { seededDir() },
            basemapQuota = 2,
            demQuota = 8,
            globalCap = 64,
        )
        val port = server.start()
        try {
            assertEquals(200, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(200, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(404, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(2L, server.basemapHits())
            assertEquals(200, quotaGet("http://127.0.0.1:$port/dem/10/1/2.png"))
            assertEquals(1L, server.demHits())
        } finally {
            server.stop()
        }
    }

    @Test
    fun demBucketExhaustsLeavingBasemapActive() {
        val server = PackTileServer(
            packsDir = { seededDir() },
            basemapQuota = 8,
            demQuota = 1,
            globalCap = 64,
        )
        val port = server.start()
        try {
            assertEquals(200, quotaGet("http://127.0.0.1:$port/dem/10/1/2.png"))
            assertEquals(404, quotaGet("http://127.0.0.1:$port/dem/10/1/2.png"))
            assertEquals(200, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(2L, server.tileHits())
        } finally {
            server.stop()
        }
    }

    @Test
    fun globalCapHaltsBothBuckets() {
        val server = PackTileServer(
            packsDir = { seededDir() },
            basemapQuota = 8,
            demQuota = 8,
            globalCap = 2,
        )
        val port = server.start()
        try {
            assertEquals(200, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(200, quotaGet("http://127.0.0.1:$port/dem/10/1/2.png"))
            assertEquals(404, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(404, quotaGet("http://127.0.0.1:$port/dem/10/1/2.png"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun demUrlAndAvailability() {
        val server = PackTileServer(packsDir = { seededDir() })
        assertNull(server.demTileUrl())
        val port = server.start()
        try {
            assertEquals(
                "http://127.0.0.1:$port/dem/{z}/{x}/{y}.png",
                server.demTileUrl(),
            )
            assertTrue(server.demAvailable())
        } finally {
            server.stop()
        }
        val bare = PackTileServer(
            packsDir = { Files.createTempDirectory("atlas-quota-bare").toFile() },
        )
        assertTrue(!bare.demAvailable())
    }
}
