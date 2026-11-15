// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.offline

import com.sovereignatlas.atlas.offline.BASEMAP_SESSION_TILES
import com.sovereignatlas.atlas.offline.MbtilesTile
import com.sovereignatlas.atlas.offline.MbtilesTileSource
import com.sovereignatlas.atlas.offline.xyzToTmsY
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.SocketException
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test for [PackTileServer], which binds a real loopback port and
 * serves real HTTP responses.
 *
 * Moved here from `offline/` by ADR-006 because the socket behaviour IS the
 * subject under test. Rewriting these against a `LocalTileServer` fake would have
 * tested the fake. They stay JVM unit tests rather than moving to `androidTest`,
 * since `java.net` needs no device and RULES 4.5 reserves connected tests for an
 * explicit ask.
 */

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
    fun vectorPbfRequestIsServed() {
        val fake = FakeMbtiles { _, _, _, _ -> MbtilesTile(byteArrayOf(7, 7, 7), "application/x-protobuf") }
        val server = mbtilesServer(fake)
        val port = server.start()
        try {
            val connection = URL("http://127.0.0.1:$port/region.mbtiles/2/1/2.pbf").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                assertEquals(200, connection.responseCode)
                assertEquals("application/x-protobuf", connection.contentType)
                assertTrue(connection.inputStream.use { it.readBytes() }.contentEquals(byteArrayOf(7, 7, 7)))
            } finally {
                connection.disconnect()
            }
            assertEquals(listOf("region.mbtiles/2/1/2"), fake.seen)
        } finally {
            server.stop()
        }
    }

    @Test
    fun unknownTileExtensionIs404() {
        val fake = FakeMbtiles { _, _, _, _ -> MbtilesTile(byteArrayOf(1), "image/png") }
        val server = mbtilesServer(fake)
        val port = server.start()
        try {
            val (code, _) = get("http://127.0.0.1:$port/region.mbtiles/2/1/2.gif")
            assertEquals(404, code)
            assertTrue(fake.seen.isEmpty())
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
    fun tileUrlPinsTestMbtilesRoute() {
        val server = PackTileServer(packsDir = { seededDir() })
        val port = server.start()
        try {
            assertEquals(
                "http://127.0.0.1:$port/test.mbtiles/{z}/{x}/{y}.png",
                server.tileUrl("test.mbtiles"),
            )
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

    @Test
    fun serverBindsStrictlyToLoopbackAddress() {
        val server = PackTileServer(packsDir = { seededDir() })
        server.start()
        try {
            val bound = server.boundAddress()
            assertTrue(bound != null && bound.isLoopbackAddress)
        } finally {
            server.stop()
        }
    }

    @Test
    fun encodedAndDotPrefixTraversalVariantsAreRejectedWith404() {
        val server = PackTileServer(packsDir = { seededDir() })
        val port = server.start()
        try {
            val attempts = listOf(
                "http://127.0.0.1:$port/.hidden/10/1/2.png",
                "http://127.0.0.1:$port/..%2fpack/10/1/2.png",
                "http://127.0.0.1:$port/pack..id/10/1/2.png",
                "http://127.0.0.1:$port/pack\$bad/10/1/2.png",
            )
            for (url in attempts) {
                val (code, _) = get(url)
                assertEquals(404, code)
            }
            assertEquals(0L, server.tileHits())
        } finally {
            server.stop()
        }
    }

    @Test
    fun restartAndConcurrentInstancesIsolateEphemeralLoopbackPorts() {
        val first = PackTileServer(packsDir = { seededDir() })
        val second = PackTileServer(packsDir = { seededDir() })
        val firstPortA = first.start()
        val firstPortRepeat = first.start()
        val secondPort = second.start()
        try {
            assertEquals(firstPortA, firstPortRepeat)
            assertTrue(firstPortA > 0 && secondPort > 0 && firstPortA != secondPort)
        } finally {
            first.stop()
            second.stop()
        }
        val restartedPort = first.start()
        try {
            assertTrue(restartedPort > 0)
        } finally {
            first.stop()
        }
    }

    private fun sendRawRequest(
        port: Int,
        raw: String,
        shutdownOut: Boolean = false,
    ): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5000
            val out = socket.getOutputStream()
            out.write(raw.toByteArray(Charsets.US_ASCII))
            out.flush()
            if (shutdownOut) {
                socket.shutdownOutput()
            }
            return socket.getInputStream().bufferedReader(Charsets.US_ASCII).readText()
        }
    }

    private fun awaitZeroResources(server: PackTileServer, timeoutMs: Long = 2_000L) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (server.activeConnectionCount() == 0 && server.activeWorkerCount() == 0) {
                return
            }
            Thread.sleep(10)
        }
        assertEquals(0, server.activeConnectionCount())
        assertEquals(0, server.activeWorkerCount())
    }

    @Test
    fun stalledHalfRequestTimesOutWith408AndReclaimsWorker() {
        val server = PackTileServer(
            packsDir = { seededDir() },
            limits = PackTileServerLimits(
                maxWorkers = 1,
                maxQueuedRequests = 0,
                socketReadTimeoutMs = 150,
                requestTotalTimeoutMs = 1_000L,
            ),
        )
        val port = server.start()
        try {
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 3000
                client.getOutputStream().write(
                    "GET /pack-000001/10/1/2.png HTTP/1.1\r\n".toByteArray(Charsets.US_ASCII),
                )
                client.getOutputStream().flush()
                val response = client.getInputStream().bufferedReader(Charsets.US_ASCII).readText()
                assertEquals("HTTP/1.1 408 Request Timeout", response.lineSequence().firstOrNull())
            }
            awaitZeroResources(server)
            val (code, _) = get("http://127.0.0.1:$port/pack-000001/10/1/2.png")
            assertEquals(200, code)
            assertEquals(1L, server.tileHits())
            awaitZeroResources(server)
        } finally {
            server.stop()
        }
    }

    @Test
    fun slowTrickleHeadersExceedTotalDeadlineWith408AndReclaimsWorker() {
        val server = PackTileServer(
            packsDir = { seededDir() },
            limits = PackTileServerLimits(
                maxWorkers = 1,
                maxQueuedRequests = 0,
                socketReadTimeoutMs = 500,
                requestTotalTimeoutMs = 180L,
            ),
        )
        val port = server.start()
        try {
            Socket("127.0.0.1", port).use { client ->
                client.tcpNoDelay = true
                client.soTimeout = 3000
                val out = client.getOutputStream()
                val readerDone = AtomicBoolean(false)
                val statusFuture = CompletableFuture.supplyAsync {
                    try {
                        client.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                    } finally {
                        readerDone.set(true)
                    }
                }
                out.write("GET /pack-000001/10/1/2.png HTTP/1.1\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                for (i in 0 until 15) {
                    if (readerDone.get()) break
                    Thread.sleep(60)
                    if (readerDone.get()) break
                    try {
                        out.write("X-Slow-$i: a\r\n".toByteArray(Charsets.US_ASCII))
                        out.flush()
                    } catch (_: SocketException) {
                        break
                    }
                }
                assertEquals("HTTP/1.1 408 Request Timeout", statusFuture.get(3, TimeUnit.SECONDS))
            }
            awaitZeroResources(server)
            val (code, _) = get("http://127.0.0.1:$port/pack-000001/10/1/2.png")
            assertEquals(200, code)
            awaitZeroResources(server)
        } finally {
            server.stop()
        }
    }

    @Test
    fun workerSaturationReturns503ThenRecoversCleanly() {
        val workerEntered = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val server = PackTileServer(
            packsDir = { seededDir() },
            limits = PackTileServerLimits(maxWorkers = 1, maxQueuedRequests = 1),
        )
        server.mbtilesStore = FakeMbtiles { packName, _, _, y ->
            if (packName == "slow.mbtiles" && y == 99) {
                workerEntered.countDown()
                releaseWorker.await(5, TimeUnit.SECONDS)
            }
            MbtilesTile(byteArrayOf(4, 5, 6), "image/png")
        }
        val port = server.start()
        try {
            val firstFuture = CompletableFuture.supplyAsync {
                get("http://127.0.0.1:$port/slow.mbtiles/2/1/99.png").first
            }
            assertTrue(workerEntered.await(5, TimeUnit.SECONDS))

            val secondFuture = CompletableFuture.supplyAsync {
                get("http://127.0.0.1:$port/region.mbtiles/2/1/2.png").first
            }
            val queueDeadline = System.nanoTime() + 2_000_000_000L
            while (server.activeConnectionCount() < 2 && System.nanoTime() < queueDeadline) {
                Thread.sleep(5)
            }
            assertTrue(server.activeConnectionCount() >= 2)

            val (overloadedCode, _) = get("http://127.0.0.1:$port/region.mbtiles/2/1/2.png")
            assertEquals(503, overloadedCode)

            releaseWorker.countDown()
            assertEquals(200, firstFuture.get(5, TimeUnit.SECONDS))
            assertEquals(200, secondFuture.get(5, TimeUnit.SECONDS))
            awaitZeroResources(server)

            val (recoveredCode, _) = get("http://127.0.0.1:$port/region.mbtiles/2/1/2.png")
            assertEquals(200, recoveredCode)
            awaitZeroResources(server)
        } finally {
            releaseWorker.countDown()
            server.stop()
        }
    }

    @Test
    fun oversizedHttpRequestLineReturns414AndHeaderLimitsReturn431() {
        val server = PackTileServer(
            packsDir = { seededDir() },
            limits = PackTileServerLimits(
                maxRequestLineBytes = 64,
                maxHeaderCount = 2,
                maxTotalHeaderBytes = 128,
            ),
        )
        val port = server.start()
        try {
            val longTarget = "/pack-000001/" + "a".repeat(80) + ".png"
            val tooLongResp = sendRawRequest(port, "GET $longTarget HTTP/1.1\r\n\r\n")
            assertEquals("HTTP/1.1 414 URI Too Long", tooLongResp.lineSequence().firstOrNull())

            val tooManyHeaders = sendRawRequest(
                port,
                "GET /pack-000001/10/1/2.png HTTP/1.1\r\nX-1: a\r\nX-2: b\r\nX-3: c\r\n\r\n",
            )
            assertEquals(
                "HTTP/1.1 431 Request Header Fields Too Large",
                tooManyHeaders.lineSequence().firstOrNull(),
            )

            val tooLargeHeaders = sendRawRequest(
                port,
                "GET /pack-000001/10/1/2.png HTTP/1.1\r\nX-Big: ${"b".repeat(100)}\r\n\r\n",
            )
            assertEquals(
                "HTTP/1.1 431 Request Header Fields Too Large",
                tooLargeHeaders.lineSequence().firstOrNull(),
            )

            assertEquals(0L, server.tileHits())
            val withinLimits = sendRawRequest(
                port,
                "GET /pack-000001/10/1/2.png HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Ok: 1\r\n\r\n",
            )
            assertEquals("HTTP/1.1 200 OK", withinLimits.lineSequence().firstOrNull())
            assertEquals(1L, server.tileHits())
            awaitZeroResources(server)
        } finally {
            server.stop()
        }
    }

    @Test
    fun malformedHttpSyntaxAndUnsupportedMethodsAreRejectedPromptly() {
        val server = PackTileServer(packsDir = { seededDir() })
        val port = server.start()
        try {
            val partialEof = sendRawRequest(port, "GET /pack-000001/10/1/2.png", shutdownOut = true)
            assertEquals("HTTP/1.1 400 Bad Request", partialEof.lineSequence().firstOrNull())

            val badVersion = sendRawRequest(port, "GET /pack-000001/10/1/2.png HTTP/2.0\r\n\r\n")
            assertEquals("HTTP/1.1 400 Bad Request", badVersion.lineSequence().firstOrNull())

            val missingColon = sendRawRequest(
                port,
                "GET /pack-000001/10/1/2.png HTTP/1.1\r\nBadHeader\r\n\r\n",
            )
            assertEquals("HTTP/1.1 400 Bad Request", missingColon.lineSequence().firstOrNull())

            val controlChar = sendRawRequest(
                port,
                "GET /pack-000001/\u0001/1/2.png HTTP/1.1\r\n\r\n",
            )
            assertEquals("HTTP/1.1 400 Bad Request", controlChar.lineSequence().firstOrNull())

            val nonzeroLength = sendRawRequest(
                port,
                "GET /pack-000001/10/1/2.png HTTP/1.1\r\nContent-Length: 5\r\n\r\n",
            )
            assertEquals("HTTP/1.1 400 Bad Request", nonzeroLength.lineSequence().firstOrNull())

            val transferEncoding = sendRawRequest(
                port,
                "GET /pack-000001/10/1/2.png HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n",
            )
            assertEquals("HTTP/1.1 400 Bad Request", transferEncoding.lineSequence().firstOrNull())

            val postResp = sendRawRequest(port, "POST /pack-000001/10/1/2.png HTTP/1.1\r\n\r\n")
            val postLines = postResp.lines()
            assertEquals("HTTP/1.1 405 Method Not Allowed", postLines.firstOrNull())
            assertTrue(postLines.any { it == "Allow: GET" })

            val zeroContentLengthOk = sendRawRequest(
                port,
                "GET /pack-000001/10/1/2.png HTTP/1.1\r\nContent-Length: 0\r\n\r\n",
            )
            assertEquals("HTTP/1.1 200 OK", zeroContentLengthOk.lineSequence().firstOrNull())
            assertEquals(1L, server.tileHits())
            awaitZeroResources(server)
        } finally {
            server.stop()
        }
    }

    @Test
    fun oversizedTilesReturn413AcrossDiskAndMbtilesWithoutConsumingQuota() {
        val dir = seededDir()
        File(dir, "pack-000001/10/1/3.png").writeBytes(ByteArray(64) { 0x11 })
        val server = PackTileServer(
            packsDir = { dir },
            basemapQuota = 2,
            limits = PackTileServerLimits(maxTileBytes = 32),
        )
        server.mbtilesStore = FakeMbtiles { packName, _, _, _ ->
            if (packName == "huge.mbtiles") {
                MbtilesTile(ByteArray(64) { 0x22 }, "image/png")
            } else {
                MbtilesTile(byteArrayOf(1, 2, 3), "image/png")
            }
        }
        val port = server.start()
        try {
            assertEquals(413, get("http://127.0.0.1:$port/pack-000001/10/1/3.png").first)
            assertEquals(413, get("http://127.0.0.1:$port/huge.mbtiles/2/1/2.png").first)
            assertEquals(0L, server.basemapHits())
            assertEquals(0L, server.tileHits())

            assertEquals(200, get("http://127.0.0.1:$port/pack-000001/10/1/2.png").first)
            assertEquals(200, get("http://127.0.0.1:$port/ok.mbtiles/2/1/2.png").first)
            assertEquals(404, get("http://127.0.0.1:$port/ok.mbtiles/2/1/2.png").first)
            assertEquals(2L, server.basemapHits())
            assertEquals(2L, server.tileHits())
            awaitZeroResources(server)
        } finally {
            server.stop()
        }
    }

    @Test
    fun stopInterruptsStalledClientsAndAllowsCleanRestart() {
        val server = PackTileServer(
            packsDir = { seededDir() },
            limits = PackTileServerLimits(
                maxWorkers = 2,
                maxQueuedRequests = 2,
                socketReadTimeoutMs = 30_000,
                requestTotalTimeoutMs = 30_000L,
            ),
        )
        val port = server.start()
        val stalled = mutableListOf<Socket>()
        try {
            repeat(2) {
                val client = Socket("127.0.0.1", port)
                stalled += client
                client.getOutputStream().write(
                    "GET /pack-000001/10/1/2.png HTTP/1.1\r\n".toByteArray(Charsets.US_ASCII),
                )
                client.getOutputStream().flush()
            }
            val waitDeadline = System.nanoTime() + 2_000_000_000L
            while (server.activeConnectionCount() < 2 && System.nanoTime() < waitDeadline) {
                Thread.sleep(5)
            }
            assertEquals(2, server.activeConnectionCount())

            val stopStartNanos = System.nanoTime()
            server.stop()
            val stopElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartNanos)
            assertTrue("stop() took ${stopElapsedMs}ms", stopElapsedMs < 5_000L)
            assertTrue(!server.isRunning())
            awaitZeroResources(server)
        } finally {
            for (client in stalled) {
                try {
                    client.close()
                } catch (_: Throwable) {
                    Unit
                }
            }
        }

        val restartedPort = server.start()
        try {
            assertTrue(restartedPort > 0)
            val (code, body) = get("http://127.0.0.1:$restartedPort/pack-000001/10/1/2.png")
            assertEquals(200, code)
            assertTrue(body.contentEquals(byteArrayOf(1, 2, 3, 4)))
            awaitZeroResources(server)
        } finally {
            server.stop()
        }
    }

    @Test
    fun abruptClientDisconnectsDoNotLeakConnectionsOrWorkers() {
        val server = PackTileServer(packsDir = { seededDir() })
        val port = server.start()
        try {
            repeat(10) {
                Socket("127.0.0.1", port).use { }
            }
            repeat(10) {
                Socket("127.0.0.1", port).use { client ->
                    client.getOutputStream().write("GET /pack-000001/10".toByteArray(Charsets.US_ASCII))
                    client.getOutputStream().flush()
                }
            }
            awaitZeroResources(server)
            val (code, _) = get("http://127.0.0.1:$port/pack-000001/10/1/2.png")
            assertEquals(200, code)
            assertEquals(1L, server.tileHits())
            awaitZeroResources(server)
        } finally {
            server.stop()
        }
    }
}
