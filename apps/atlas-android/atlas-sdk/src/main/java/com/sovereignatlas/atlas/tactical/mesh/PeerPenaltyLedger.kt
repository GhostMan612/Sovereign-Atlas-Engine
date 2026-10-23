// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import java.util.concurrent.ConcurrentHashMap

class PeerPenaltyLedger<K : Any>(private val clock: () -> Long = System::currentTimeMillis) {

    private val structuralTracker = ConcurrentHashMap<K, RateWindow>()
    private val semanticTracker = ConcurrentHashMap<K, RateWindow>()
    private val blocklist = ConcurrentHashMap<K, Long>()

    fun recordStructural(peer: K): Boolean {
        val window = structuralTracker.getOrPut(peer) { RateWindow(STRUCTURAL_WINDOW_MS) }
        val count = window.record(clock())
        return count > MAX_INVALID_FRAMES_PER_SEC && block(peer)
    }

    fun recordSemantic(peer: K): Boolean {
        val window = semanticTracker.getOrPut(peer) { RateWindow(SEMANTIC_WINDOW_MS) }
        val count = window.record(clock())
        return count > MAX_SEMANTIC_REJECTS_PER_WINDOW && block(peer)
    }

    fun isBlocked(peer: K): Boolean {
        val until = blocklist[peer] ?: return false
        if (clock() >= until) {
            blocklist.remove(peer, until)
            structuralTracker.remove(peer)
            semanticTracker.remove(peer)
            return false
        }
        return true
    }

    fun blockedUntil(peer: K): Long? = blocklist[peer]

    fun unblock(peer: K) {
        blocklist.remove(peer)
        structuralTracker.remove(peer)
        semanticTracker.remove(peer)
    }

    fun clear() {
        blocklist.clear()
        structuralTracker.clear()
        semanticTracker.clear()
    }

    private fun block(peer: K): Boolean =
        blocklist.putIfAbsent(peer, clock() + BLOCK_DURATION_MS) == null

    private class RateWindow(private val windowMs: Long) {
        private var windowStart = 0L
        private var count = 0

        @Synchronized
        fun record(now: Long): Int {
            if (now - windowStart >= windowMs) {
                windowStart = now
                count = 1
            } else {
                count++
            }
            return count
        }
    }

    companion object {
        const val MAX_INVALID_FRAMES_PER_SEC: Int = 50
        private const val STRUCTURAL_WINDOW_MS: Long = 1_000L

        const val MAX_SEMANTIC_REJECTS_PER_WINDOW: Int = 5
        private const val SEMANTIC_WINDOW_MS: Long = 60_000L

        const val BLOCK_DURATION_MS: Long = 30_000L
    }
}
