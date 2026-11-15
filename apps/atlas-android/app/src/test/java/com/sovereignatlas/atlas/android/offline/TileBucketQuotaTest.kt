// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.offline

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test for the per-bucket session quota in [PackTileServer].
 *
 * Moved here from `offline/` by ADR-006 for the same reason as
 * [PackTileServerTest]: the quota is enforced while serving real requests, so a
 * fake transport would not exercise the code path being tested. Kept as a JVM
 * unit test; no device required.
 */

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

    @Test
    fun concurrentRequestsStrictlyRespectBasemapDemAndGlobalCaps() {
        val server = PackTileServer(
            packsDir = { seededDir() },
            basemapQuota = 5,
            demQuota = 4,
            globalCap = 7,
            limits = PackTileServerLimits(maxWorkers = 8, maxQueuedRequests = 64),
        )
        val port = server.start()
        val pool = Executors.newFixedThreadPool(16)
        try {
            val threadCount = 16
            val ready = CountDownLatch(threadCount)
            val start = CountDownLatch(1)
            val done = CountDownLatch(threadCount)
            val baseOk = AtomicInteger(0)
            val demOk = AtomicInteger(0)
            val unexpectedStatus = AtomicInteger(0)

            for (i in 0 until threadCount) {
                pool.submit {
                    ready.countDown()
                    start.await(5, TimeUnit.SECONDS)
                    try {
                        repeat(4) { step ->
                            val isDem = (i + step) % 2 == 1
                            val path = if (isDem) "/dem/10/1/2.png" else "/pack-000001/10/1/2.png"
                            when (quotaGet("http://127.0.0.1:$port$path")) {
                                200 -> if (isDem) demOk.incrementAndGet() else baseOk.incrementAndGet()
                                404 -> Unit
                                else -> unexpectedStatus.incrementAndGet()
                            }
                        }
                    } finally {
                        done.countDown()
                    }
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(done.await(15, TimeUnit.SECONDS))

            assertEquals(0, unexpectedStatus.get())
            assertTrue(baseOk.get() <= 5)
            assertTrue(demOk.get() <= 4)
            assertEquals(7, baseOk.get() + demOk.get())
            assertEquals(baseOk.get().toLong(), server.basemapHits())
            assertEquals(demOk.get().toLong(), server.demHits())
            assertEquals(7L, server.tileHits())
        } finally {
            pool.shutdownNow()
            server.stop()
        }
    }

    @Test
    fun concurrentMissingAndOversizedRejectionsRollBackReservedQuota() {
        val dir = seededDir()
        val oversized = File(dir, "pack-000001/10/1/3.png")
        oversized.writeBytes(ByteArray(128) { 0x42 })
        val oversizedDem = File(dir, "dem/10/1/3.png")
        oversizedDem.writeBytes(ByteArray(128) { 0x43 })

        val server = PackTileServer(
            packsDir = { dir },
            basemapQuota = 3,
            demQuota = 2,
            globalCap = 4,
            limits = PackTileServerLimits(maxWorkers = 8, maxQueuedRequests = 64, maxTileBytes = 32),
        )
        val port = server.start()
        val pool = Executors.newFixedThreadPool(12)
        try {
            val workerCount = 12
            val ready = CountDownLatch(workerCount)
            val start = CountDownLatch(1)
            val done = CountDownLatch(workerCount)
            val badStatuses = AtomicInteger(0)

            for (i in 0 until workerCount) {
                pool.submit {
                    ready.countDown()
                    start.await(5, TimeUnit.SECONDS)
                    try {
                        repeat(4) { step ->
                            val (url, expected) = when ((i + step) % 4) {
                                0 -> "http://127.0.0.1:$port/pack-000001/10/9/9.png" to 404
                                1 -> "http://127.0.0.1:$port/dem/10/9/9.png" to 404
                                2 -> "http://127.0.0.1:$port/pack-000001/10/1/3.png" to 413
                                else -> "http://127.0.0.1:$port/dem/10/1/3.png" to 413
                            }
                            if (quotaGet(url) != expected) {
                                badStatuses.incrementAndGet()
                            }
                        }
                    } finally {
                        done.countDown()
                    }
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(done.await(15, TimeUnit.SECONDS))

            assertEquals(0, badStatuses.get())
            assertEquals(0L, server.basemapHits())
            assertEquals(0L, server.demHits())
            assertEquals(0L, server.tileHits())

            assertEquals(200, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(200, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(200, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(404, quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png"))
            assertEquals(200, quotaGet("http://127.0.0.1:$port/dem/10/1/2.png"))
            assertEquals(404, quotaGet("http://127.0.0.1:$port/dem/10/1/2.png"))
            assertEquals(3L, server.basemapHits())
            assertEquals(1L, server.demHits())
            assertEquals(4L, server.tileHits())
        } finally {
            pool.shutdownNow()
            server.stop()
        }
    }

    @Test
    fun concurrentMixedWorkloadMatchesCountersWithoutOvershoot() {
        val dir = seededDir()
        File(dir, "pack-000001/10/1/3.png").writeBytes(ByteArray(128) { 0x7A })

        val server = PackTileServer(
            packsDir = { dir },
            basemapQuota = 4,
            demQuota = 3,
            globalCap = 6,
            limits = PackTileServerLimits(maxWorkers = 8, maxQueuedRequests = 64, maxTileBytes = 32),
        )
        val port = server.start()
        val pool = Executors.newFixedThreadPool(12)
        try {
            val workerCount = 12
            val ready = CountDownLatch(workerCount)
            val start = CountDownLatch(1)
            val done = CountDownLatch(workerCount)
            val baseOk = AtomicInteger(0)
            val demOk = AtomicInteger(0)
            val unexpected = AtomicInteger(0)

            for (i in 0 until workerCount) {
                pool.submit {
                    ready.countDown()
                    start.await(5, TimeUnit.SECONDS)
                    try {
                        repeat(6) { step ->
                            when ((i + step) % 4) {
                                0 -> when (quotaGet("http://127.0.0.1:$port/pack-000001/10/1/2.png")) {
                                    200 -> baseOk.incrementAndGet()
                                    404 -> Unit
                                    else -> unexpected.incrementAndGet()
                                }
                                1 -> when (quotaGet("http://127.0.0.1:$port/dem/10/1/2.png")) {
                                    200 -> demOk.incrementAndGet()
                                    404 -> Unit
                                    else -> unexpected.incrementAndGet()
                                }
                                2 -> {
                                    val code = quotaGet("http://127.0.0.1:$port/pack-000001/10/1/3.png")
                                    if (code != 413 && code != 404) unexpected.incrementAndGet()
                                }
                                else -> if (quotaGet("http://127.0.0.1:$port/pack-000001/10/9/9.png") != 404) {
                                    unexpected.incrementAndGet()
                                }
                            }
                        }
                    } finally {
                        done.countDown()
                    }
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(done.await(15, TimeUnit.SECONDS))

            assertEquals(0, unexpected.get())
            assertTrue(baseOk.get() <= 4)
            assertTrue(demOk.get() <= 3)
            assertTrue(baseOk.get() + demOk.get() <= 6)
            assertEquals(baseOk.get().toLong(), server.basemapHits())
            assertEquals(demOk.get().toLong(), server.demHits())
            assertEquals((baseOk.get() + demOk.get()).toLong(), server.tileHits())
        } finally {
            pool.shutdownNow()
            server.stop()
        }
    }
}
