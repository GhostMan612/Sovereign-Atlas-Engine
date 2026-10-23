// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.dtn

import com.sovereignatlas.atlas.tactical.mesh.FragmentAckCodec
import com.sovereignatlas.atlas.tactical.mesh.FragmentAckReport
import java.io.ByteArrayOutputStream
import java.io.File

enum class RegisterOutcome {
    STORED,
    DUPLICATE,
    INVALID_INDEX,
    INVALID_TOTAL,
    TOTAL_CHUNKS_MISMATCH,
    ALREADY_RESOLVED,
}

enum class FragmentTier {
    PUBLIC,
    SESSION,
    DIRECT,
}

data class FragmentSealingInfo(
    val tier: FragmentTier,
    val counterpartyId: String,
)

data class InboundTransferMeta(
    val transferId: String,
    val totalChunks: Int,
    val tier: FragmentTier,
    val counterpartyId: String,
    val expiresAt: Long,
    val firstSeenAt: Long,
    val lastChunkAt: Long,
    val round: Int = 0,
    val nextNackDueAt: Long = 0L,
    val rejected: Boolean = false,
    val delivered: Boolean = false,
)

data class OutboundTransferMeta(
    val transferId: String,
    val totalChunks: Int,
    val tier: FragmentTier,
    val counterpartyId: String,
    val expiresAt: Long,
    val createdAt: Long,
    val lastRepairSentAt: Long = 0L,
)

class FragmentStore(
    private val storageDir: File? = null,
    private val maxInboundTransfers: Int = DEFAULT_INBOUND_CAP,
    private val maxOutboundTransfers: Int = DEFAULT_OUTBOX_CAP,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val inboundMeta = LinkedHashMap<String, InboundTransferMeta>()
    private val inboundFrames = LinkedHashMap<String, MutableMap<Int, ByteArray>>()

    private val outboundMeta = LinkedHashMap<String, OutboundTransferMeta>()
    private val outboundFrames = LinkedHashMap<String, MutableMap<Int, ByteArray>>()

    init {
        storageDir?.mkdirs()
    }

    @Synchronized
    fun registerInbound(
        transferId: String,
        chunkIndex: Int,
        totalChunks: Int,
        tier: FragmentTier,
        counterpartyId: String,
        expiresAt: Long,
        frame: ByteArray,
        now: Long = clock(),
    ): RegisterOutcome {
        if (totalChunks < 1 || totalChunks > FragmentAckCodec.MAX_WINDOW_BITS) {
            return RegisterOutcome.INVALID_TOTAL
        }
        val existingMeta = inboundMeta[transferId]
        if (existingMeta != null) {
            if (existingMeta.rejected || existingMeta.delivered) {
                return RegisterOutcome.ALREADY_RESOLVED
            }
            if (existingMeta.totalChunks != totalChunks) {
                inboundMeta[transferId] = existingMeta.copy(rejected = true)
                return RegisterOutcome.TOTAL_CHUNKS_MISMATCH
            }
            if (chunkIndex !in 0 until totalChunks) {
                return RegisterOutcome.INVALID_INDEX
            }
            val framesMap = inboundFrames.getOrPut(transferId) { LinkedHashMap() }
            if (framesMap.containsKey(chunkIndex)) {
                return RegisterOutcome.DUPLICATE
            }
            framesMap[chunkIndex] = frame.copyOf()
            inboundMeta[transferId] = existingMeta.copy(
                lastChunkAt = now,
                round = 0,
                nextNackDueAt = 0L,
            )
            persistFrame(transferId, chunkIndex, frame, isInbound = true)
            return RegisterOutcome.STORED
        }

        if (chunkIndex !in 0 until totalChunks) {
            return RegisterOutcome.INVALID_INDEX
        }
        val meta = InboundTransferMeta(
            transferId = transferId,
            totalChunks = totalChunks,
            tier = tier,
            counterpartyId = counterpartyId,
            expiresAt = expiresAt,
            firstSeenAt = now,
            lastChunkAt = now,
        )
        inboundMeta[transferId] = meta
        val framesMap = LinkedHashMap<Int, ByteArray>()
        framesMap[chunkIndex] = frame.copyOf()
        inboundFrames[transferId] = framesMap
        persistFrame(transferId, chunkIndex, frame, isInbound = true)

        while (inboundMeta.size > maxInboundTransfers) {
            evictOldestInbound()
        }
        return RegisterOutcome.STORED
    }

    @Synchronized
    fun isInboundComplete(transferId: String): Boolean {
        val meta = inboundMeta[transferId] ?: return false
        if (meta.rejected) return false
        val frames = inboundFrames[transferId] ?: return false
        return frames.size >= meta.totalChunks
    }

    @Synchronized
    fun assembleInboundFrames(transferId: String): List<ByteArray>? {
        val meta = inboundMeta[transferId] ?: return null
        if (meta.rejected) return null
        val frames = inboundFrames[transferId] ?: return null
        if (frames.size < meta.totalChunks) return null
        val result = ArrayList<ByteArray>(meta.totalChunks)
        for (i in 0 until meta.totalChunks) {
            val chunk = frames[i] ?: return null
            result.add(chunk)
        }
        return result
    }

    @Synchronized
    fun assembleInboundPayload(transferId: String): ByteArray? {
        val frames = assembleInboundFrames(transferId) ?: return null
        val bos = ByteArrayOutputStream()
        for (f in frames) {
            bos.write(f)
        }
        return bos.toByteArray()
    }

    @Synchronized
    fun markDelivered(transferId: String): Boolean {
        val meta = inboundMeta[transferId] ?: return false
        inboundMeta[transferId] = meta.copy(delivered = true)
        return true
    }

    @Synchronized
    fun inboundSealingInfo(transferId: String): FragmentSealingInfo? {
        val meta = inboundMeta[transferId] ?: return null
        return FragmentSealingInfo(meta.tier, meta.counterpartyId)
    }

    @Synchronized
    fun getMissingChunkIndices(transferId: String): List<Int> {
        val meta = inboundMeta[transferId] ?: return emptyList()
        val frames = inboundFrames[transferId] ?: return emptyList()
        val missing = ArrayList<Int>()
        for (i in 0 until meta.totalChunks) {
            if (!frames.containsKey(i)) {
                missing.add(i)
            }
        }
        return missing
    }

    @Synchronized
    fun generateAckReport(transferId: String, round: Int): FragmentAckReport? {
        val meta = inboundMeta[transferId] ?: return null
        val frames = inboundFrames[transferId] ?: return null
        if (meta.rejected) return null

        var contiguousHigh = 0
        while (contiguousHigh < meta.totalChunks && frames.containsKey(contiguousHigh)) {
            contiguousHigh++
        }

        val windowBase = contiguousHigh
        val windowSize = minOf(meta.totalChunks - windowBase, FragmentAckCodec.MAX_WINDOW_BITS)
        if (windowSize <= 0) {
            return FragmentAckReport(
                transferId = transferId,
                round = round,
                totalChunks = meta.totalChunks,
                contiguousHigh = contiguousHigh,
                windowBase = windowBase,
                missing = BooleanArray(0),
            )
        }

        val missingBits = BooleanArray(windowSize) { offset ->
            !frames.containsKey(windowBase + offset)
        }

        return FragmentAckReport(
            transferId = transferId,
            round = round,
            totalChunks = meta.totalChunks,
            contiguousHigh = contiguousHigh,
            windowBase = windowBase,
            missing = missingBits,
        )
    }

    @Synchronized
    fun registerOutbound(
        transferId: String,
        totalChunks: Int,
        tier: FragmentTier,
        counterpartyId: String,
        expiresAt: Long,
        frames: List<ByteArray>,
        now: Long = clock(),
    ) {
        val meta = OutboundTransferMeta(
            transferId = transferId,
            totalChunks = totalChunks,
            tier = tier,
            counterpartyId = counterpartyId,
            expiresAt = expiresAt,
            createdAt = now,
        )
        outboundMeta[transferId] = meta
        val map = LinkedHashMap<Int, ByteArray>()
        for (i in frames.indices) {
            map[i] = frames[i].copyOf()
            persistFrame(transferId, i, frames[i], isInbound = false)
        }
        outboundFrames[transferId] = map

        while (outboundMeta.size > maxOutboundTransfers) {
            evictOldestOutbound()
        }
    }

    @Synchronized
    fun getOutboundFrame(transferId: String, chunkIndex: Int): ByteArray? {
        return outboundFrames[transferId]?.get(chunkIndex)
    }

    @Synchronized
    fun processAckReport(report: FragmentAckReport): List<ByteArray> {
        val meta = outboundMeta[report.transferId] ?: return emptyList()
        val frames = outboundFrames[report.transferId] ?: return emptyList()
        val repairs = ArrayList<ByteArray>()

        for (offset in report.missing.indices) {
            if (report.missing[offset]) {
                val neededIndex = report.windowBase + offset
                val frame = frames[neededIndex]
                if (frame != null) {
                    repairs.add(frame)
                }
            }
        }
        outboundMeta[report.transferId] = meta.copy(lastRepairSentAt = clock())
        return repairs
    }

    @Synchronized
    fun isOutboundComplete(transferId: String): Boolean {
        val meta = outboundMeta[transferId] ?: return false
        val frames = outboundFrames[transferId] ?: return false
        return frames.size >= meta.totalChunks
    }

    @Synchronized
    fun tick(now: Long = clock()): List<FragmentAckReport> {
        val reports = ArrayList<FragmentAckReport>()
        for ((transferId, meta) in inboundMeta.entries) {
            if (meta.rejected || meta.delivered) continue
            val frames = inboundFrames[transferId] ?: continue
            if (frames.size >= meta.totalChunks) continue

            val timeSinceLastChunk = now - meta.lastChunkAt
            if (timeSinceLastChunk >= STALL_THRESHOLD_MS && now >= meta.nextNackDueAt) {
                val nextRound = meta.round + 1
                if (nextRound <= MAX_NACK_ROUNDS) {
                    val report = generateAckReport(transferId, nextRound)
                    if (report != null) {
                        reports.add(report)
                        val backoff = minOf(BASE_BACKOFF_MS * (1 shl (nextRound - 1)), MAX_BACKOFF_MS)
                        inboundMeta[transferId] = meta.copy(
                            round = nextRound,
                            nextNackDueAt = now + backoff,
                        )
                    }
                }
            }
        }
        return reports
    }

    @Synchronized
    fun sweep(now: Long = clock()) {
        val expiredInbound = inboundMeta.filterValues { it.expiresAt in 1..now }.keys.toList()
        for (id in expiredInbound) {
            inboundMeta.remove(id)
            inboundFrames.remove(id)
            deleteTransferDir(id, isInbound = true)
        }

        val expiredOutbound = outboundMeta.filterValues { it.expiresAt in 1..now }.keys.toList()
        for (id in expiredOutbound) {
            outboundMeta.remove(id)
            outboundFrames.remove(id)
            deleteTransferDir(id, isInbound = false)
        }
    }

    private fun evictOldestInbound() {
        val oldestKey = inboundMeta.keys.firstOrNull() ?: return
        inboundMeta.remove(oldestKey)
        inboundFrames.remove(oldestKey)
        deleteTransferDir(oldestKey, isInbound = true)
    }

    private fun evictOldestOutbound() {
        val oldestKey = outboundMeta.keys.firstOrNull() ?: return
        outboundMeta.remove(oldestKey)
        outboundFrames.remove(oldestKey)
        deleteTransferDir(oldestKey, isInbound = false)
    }

    private fun persistFrame(transferId: String, chunkIndex: Int, frame: ByteArray, isInbound: Boolean) {
        val dir = storageDir ?: return
        val subDir = if (isInbound) File(dir, "inbox") else File(dir, "outbox")
        val transferDir = File(subDir, transferId)
        transferDir.mkdirs()
        File(transferDir, "chunk_%04d.dat".format(chunkIndex)).writeBytes(frame)
    }

    private fun deleteTransferDir(transferId: String, isInbound: Boolean) {
        val dir = storageDir ?: return
        val subDir = if (isInbound) File(dir, "inbox") else File(dir, "outbox")
        val transferDir = File(subDir, transferId)
        transferDir.deleteRecursively()
    }

    companion object {
        const val DEFAULT_INBOUND_CAP: Int = 128
        const val DEFAULT_OUTBOX_CAP: Int = 128

        const val STALL_THRESHOLD_MS: Long = 4_000L
        const val BASE_BACKOFF_MS: Long = 4_000L
        const val MAX_BACKOFF_MS: Long = 60_000L
        const val MAX_NACK_ROUNDS: Int = 5
    }
}
