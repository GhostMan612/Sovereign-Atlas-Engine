// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerPenaltyLedgerTest {

    @Test
    fun structuralFloodingBlocksPeer() {
        var now = 1000L
        val ledger = PeerPenaltyLedger<String>(clock = { now })
        val peer = "node-attacker"

        assertFalse(ledger.isBlocked(peer))

        repeat(50) {
            val blocked = ledger.recordStructural(peer)
            assertFalse(blocked)
        }
        assertFalse(ledger.isBlocked(peer))

        val newlyBlocked = ledger.recordStructural(peer)
        assertTrue(newlyBlocked)
        assertTrue(ledger.isBlocked(peer))
    }

    @Test
    fun semanticViolationsBlockPeer() {
        var now = 1000L
        val ledger = PeerPenaltyLedger<String>(clock = { now })
        val peer = "node-forger"

        repeat(5) {
            val blocked = ledger.recordSemantic(peer)
            assertFalse(blocked)
        }
        assertFalse(ledger.isBlocked(peer))

        val newlyBlocked = ledger.recordSemantic(peer)
        assertTrue(newlyBlocked)
        assertTrue(ledger.isBlocked(peer))
    }

    @Test
    fun blockExpiresAndSelfHeals() {
        var now = 10_000L
        val ledger = PeerPenaltyLedger<String>(clock = { now })
        val peer = "node-transient"

        repeat(51) { ledger.recordStructural(peer) }
        assertTrue(ledger.isBlocked(peer))

        now += 29_999L
        assertTrue(ledger.isBlocked(peer))

        now += 2L
        assertFalse(ledger.isBlocked(peer))
    }
}
