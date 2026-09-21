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

final class PackTileServerTest {
    private fun seededDir(): File {
        val dir = Files.createTempDirectory("atlas-serve").toFile()
        val tile = File(dir, "pack-000001/10/1/2.png")
        tile.parentFile?.mkdirs()
        tile.writeBytes(byteArrayOf(1, 2, 3, 4))
        return dir
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
