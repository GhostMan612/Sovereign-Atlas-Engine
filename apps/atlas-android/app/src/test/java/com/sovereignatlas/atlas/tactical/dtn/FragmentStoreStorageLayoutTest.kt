// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.dtn

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FragmentStoreStorageLayoutTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var storeDir: File
    private lateinit var inboxDir: File
    private lateinit var outboxDir: File
    private lateinit var sentinels: List<File>

    private val unsafeIds = listOf(
        "../..",
        "..",
        ".",
        "/",
        "../../outside",
        "a/../../..",
        "..\\..\\outside",
        "",
    )

    @Before
    fun setUp() {
        root = temporaryFolder.newFolder("root")
        storeDir = File(root, "store")
        inboxDir = File(storeDir, "inbox")
        outboxDir = File(storeDir, "outbox")
        inboxDir.mkdirs()
        outboxDir.mkdirs()
        sentinels = listOf(
            File(root, "root.keep"),
            File(storeDir, "store.keep"),
            File(inboxDir, "inbox.keep"),
            File(outboxDir, "outbox.keep"),
        )
        sentinels.forEach { it.writeText("keep") }
    }

    @Test
    fun unsafeIdsMapToDirectoriesInsideTheStore() {
        val store = FragmentStore(storageDir = storeDir)
        for (id in unsafeIds) {
            val outcome = store.registerInbound(id, 0, 2, FragmentTier.PUBLIC, "peer", 0L, byteArrayOf(1, 2, 3))
            assertEquals(RegisterOutcome.STORED, outcome)
            store.registerOutbound(id, 1, FragmentTier.PUBLIC, "peer", 0L, listOf(byteArrayOf(4, 5)))
        }

        assertSentinelsIntact()
        assertEquals(setOf("root.keep", "store"), root.list()!!.toSet())
        assertEquals(setOf("store.keep", "inbox", "outbox"), storeDir.list()!!.toSet())
        val names = unsafeIds.map(::directoryName).toSet()
        assertEquals(names + "inbox.keep", inboxDir.list()!!.toSet())
        assertEquals(names + "outbox.keep", outboxDir.list()!!.toSet())
        assertArrayEquals(byteArrayOf(1, 2, 3), chunkFile(inboxDir, "../..").readBytes())
        assertArrayEquals(byteArrayOf(4, 5), chunkFile(outboxDir, "../..").readBytes())
    }

    @Test
    fun sweepKeepsEverythingOutsideTheSweptTransfers() {
        val store = FragmentStore(storageDir = storeDir)
        for (id in unsafeIds) {
            store.registerInbound(id, 0, 2, FragmentTier.PUBLIC, "peer", 100L, byteArrayOf(1), now = 10L)
            store.registerOutbound(id, 1, FragmentTier.PUBLIC, "peer", 100L, listOf(byteArrayOf(2)), now = 10L)
        }
        store.registerInbound("kept-in", 0, 2, FragmentTier.PUBLIC, "peer", 10_000L, byteArrayOf(3), now = 10L)
        store.registerOutbound("kept-out", 1, FragmentTier.PUBLIC, "peer", 10_000L, listOf(byteArrayOf(4)), now = 10L)

        store.sweep(now = 200L)

        assertSentinelsIntact()
        assertEquals(setOf("inbox.keep", directoryName("kept-in")), inboxDir.list()!!.toSet())
        assertEquals(setOf("outbox.keep", directoryName("kept-out")), outboxDir.list()!!.toSet())
        assertTrue(chunkFile(inboxDir, "kept-in").isFile)
        assertTrue(chunkFile(outboxDir, "kept-out").isFile)
    }

    @Test
    fun evictionKeepsEverythingOutsideTheEvictedTransfer() {
        val store = FragmentStore(storageDir = storeDir, maxInboundTransfers = 2, maxOutboundTransfers = 2)
        store.registerInbound("../..", 0, 2, FragmentTier.PUBLIC, "peer", 0L, byteArrayOf(1))
        store.registerInbound("second-in", 0, 2, FragmentTier.PUBLIC, "peer", 0L, byteArrayOf(2))
        store.registerInbound("third-in", 0, 2, FragmentTier.PUBLIC, "peer", 0L, byteArrayOf(3))
        store.registerOutbound("../..", 1, FragmentTier.PUBLIC, "peer", 0L, listOf(byteArrayOf(4)))
        store.registerOutbound("second-out", 1, FragmentTier.PUBLIC, "peer", 0L, listOf(byteArrayOf(5)))
        store.registerOutbound("third-out", 1, FragmentTier.PUBLIC, "peer", 0L, listOf(byteArrayOf(6)))

        assertSentinelsIntact()
        assertEquals(
            setOf("inbox.keep", directoryName("second-in"), directoryName("third-in")),
            inboxDir.list()!!.toSet(),
        )
        assertEquals(
            setOf("outbox.keep", directoryName("second-out"), directoryName("third-out")),
            outboxDir.list()!!.toSet(),
        )
    }

    @Test
    fun storeDirectoryWrittenWithDotSegmentsStillPersists() {
        val store = FragmentStore(storageDir = File(storeDir, "../store"))

        store.registerInbound("plain-id", 0, 2, FragmentTier.PUBLIC, "peer", 0L, byteArrayOf(7))

        assertArrayEquals(byteArrayOf(7), chunkFile(inboxDir, "plain-id").readBytes())
    }

    @Test
    fun linkedTransferDirectoryIsNeverFollowed() {
        val outside = temporaryFolder.newFolder("outside")
        val victim = File(outside, "victim.keep")
        victim.writeText("keep")
        val created = try {
            Files.createSymbolicLink(File(inboxDir, directoryName("linked")).toPath(), outside.toPath())
            true
        } catch (e: Exception) {
            false
        }
        assumeTrue("symbolic links are not permitted here", created)
        val store = FragmentStore(storageDir = storeDir)

        var rejected = false
        try {
            store.registerInbound("linked", 0, 2, FragmentTier.PUBLIC, "peer", 100L, byteArrayOf(9), now = 10L)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        store.sweep(now = 200L)

        assertEquals(listOf("victim.keep"), outside.list()!!.toList())
        assertEquals("keep", victim.readText())
        assertSentinelsIntact()
    }

    private fun assertSentinelsIntact() {
        for (file in sentinels) {
            assertTrue("${file.name} is missing", file.isFile)
            assertEquals("keep", file.readText())
        }
    }

    private fun chunkFile(subDir: File, transferId: String): File =
        File(File(subDir, directoryName(transferId)), "chunk_0000.dat")

    private fun directoryName(transferId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(transferId.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return "sha256-$hex"
    }
}
