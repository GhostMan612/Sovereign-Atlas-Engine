// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The audit ledger, and the scope mapping that gates every transmission.
 */
final class WaypointSharingAuditTest {

    // ------------------------------------------------------------------
    // RecipientScope.forPolicy
    // ------------------------------------------------------------------

    @Test
    fun privateHasNoScope() {
        // null, not a NONE member: "no scope" and "a scope that happens to be
        // restrictive" are different statements, and only the first is true here.
        assertNull(RecipientScope.forPolicy(com.sovereignatlas.atlas.field.WaypointSharingPolicy.Private))
    }

    @Test
    fun teamAndPublicMapToTheirOwnScopes() {
        assertEquals(
            RecipientScope.TEAM,
            RecipientScope.forPolicy(com.sovereignatlas.atlas.field.WaypointSharingPolicy.Team),
        )
        assertEquals(
            RecipientScope.PUBLIC,
            RecipientScope.forPolicy(com.sovereignatlas.atlas.field.WaypointSharingPolicy.Public),
        )
    }

    @Test
    fun everyPolicyIsAccountedFor() {
        // Guards the compiler guard: if a fourth policy is added to the enum without
        // a scope, this fails even if the `when` were loosened to a default branch.
        val covered = com.sovereignatlas.atlas.field.WaypointSharingPolicy.entries
            .mapNotNull { RecipientScope.forPolicy(it) }
            .toSet()
        assertEquals(RecipientScope.entries.toSet(), covered)
    }

    // ------------------------------------------------------------------
    // Recording a share
    // ------------------------------------------------------------------

    @Test
    fun aFreshRecordIsActive() {
        val audit = WaypointSharingAudit()
        val record = audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)

        assertEquals("wp-1", record.waypointId)
        assertEquals(1_000L, record.sharedAtMillis)
        assertEquals(RecipientScope.TEAM, record.recipientScope)
        assertNull(record.revokedAtMillis)
        assertTrue(record.isActive())
    }

    @Test
    fun aSharedWaypointIsCurrentlyShared() {
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)

        assertTrue(audit.isCurrentlyShared("wp-1"))
    }

    @Test
    fun anUnknownWaypointIsNotCurrentlyShared() {
        // This is what makes the stop-sharing refusal reachable: a waypoint never
        // shared has no active record, so the caller passes wasShared = false.
        assertFalse(WaypointSharingAudit().isCurrentlyShared("never-existed"))
    }

    @Test
    fun recordsAreKeptInOrder() {
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)
        audit.recordShared("wp-2", RecipientScope.PUBLIC, atMillis = 2_000L)
        audit.recordShared("wp-1", RecipientScope.PUBLIC, atMillis = 3_000L)

        assertEquals(
            listOf(1_000L, 2_000L, 3_000L),
            audit.all().map { it.sharedAtMillis },
        )
    }

    // ------------------------------------------------------------------
    // Revocation
    // ------------------------------------------------------------------

    @Test
    fun revokingMarksTheRecordAndKeepsIt() {
        // The record survives its revocation. "This was shared, then withdrawn" is
        // the fact that matters; deleting the row would erase the only evidence.
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)

        val revoked = audit.recordRevoked("wp-1", atMillis = 5_000L)

        assertEquals(1, revoked.size)
        assertEquals(5_000L, revoked.first().revokedAtMillis)
        assertFalse(revoked.first().isActive())
        assertEquals(1, audit.all().size)
    }

    @Test
    fun aRevokedWaypointIsNoLongerCurrentlyShared() {
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)
        audit.recordRevoked("wp-1", atMillis = 5_000L)

        assertFalse(audit.isCurrentlyShared("wp-1"))
    }

    @Test
    fun revokingReturnsEmptyWhenNothingWasActive() {
        // The signal the router uses to decide a tombstone is not owed.
        val audit = WaypointSharingAudit()

        assertTrue(audit.recordRevoked("never-shared", atMillis = 5_000L).isEmpty())
        assertTrue(audit.all().isEmpty())
    }

    @Test
    fun revokingTwiceKeepsTheFirstInstant() {
        // A double-tapped stop-sharing must not rewrite when the operator acted.
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)
        audit.recordRevoked("wp-1", atMillis = 5_000L)

        audit.recordRevoked("wp-1", atMillis = 9_000L)

        assertEquals(5_000L, audit.all().single().revokedAtMillis)
    }

    @Test
    fun revocationTouchesOnlyTheNamedWaypoint() {
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)
        audit.recordShared("wp-2", RecipientScope.TEAM, atMillis = 1_100L)

        val revoked = audit.recordRevoked("wp-1", atMillis = 5_000L)

        assertEquals(listOf("wp-1"), revoked.map { it.waypointId })
        assertTrue(audit.isCurrentlyShared("wp-2"))
    }

    @Test
    fun resharingAfterRevocationIsActiveAgain() {
        // Stop-sharing is not terminal: the operator may re-share deliberately.
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)
        audit.recordRevoked("wp-1", atMillis = 2_000L)
        assertFalse(audit.isCurrentlyShared("wp-1"))

        audit.recordShared("wp-1", RecipientScope.PUBLIC, atMillis = 3_000L)

        assertTrue(audit.isCurrentlyShared("wp-1"))
        assertEquals(2, audit.recordsFor("wp-1").size)
    }

    @Test
    fun revocationWithdrawsEveryActiveShareOfThatWaypoint() {
        // A waypoint shared twice without an intervening stop is on the mesh twice;
        // withdrawing must clear both, or the second copy outlives the decision.
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 2_000L)

        val revoked = audit.recordRevoked("wp-1", atMillis = 5_000L)

        assertEquals(2, revoked.size)
        assertTrue(revoked.none { it.isActive() })
        assertFalse(audit.isCurrentlyShared("wp-1"))
    }

    @Test
    fun recordsForAnotherWaypointAreNotReturned() {
        val audit = WaypointSharingAudit()
        audit.recordShared("wp-1", RecipientScope.TEAM, atMillis = 1_000L)

        assertTrue(audit.recordsFor("wp-2").isEmpty())
    }

    // ------------------------------------------------------------------
    // Record invariants
    // ------------------------------------------------------------------

    @Test
    fun revokeOnAnAlreadyRevokedRecordIsANoOp() {
        val record = WaypointAuditRecord("wp-1", 1_000L, RecipientScope.TEAM, revokedAtMillis = 5_000L)

        assertEquals(record, record.revoke(9_000L))
    }

    @Test
    fun aRecordWithNoRevocationInstantIsActive() {
        assertTrue(WaypointAuditRecord("wp-1", 1_000L, RecipientScope.PUBLIC).isActive())
    }

    @Test
    fun theScopeIsCarriedOnTheRecord() {
        // The record has to say WHO was given it, or "shared" is an unfalsifiable
        // claim and a TEAM share is indistinguishable from a PUBLIC one after the
        // fact.
        assertEquals(
            RecipientScope.PUBLIC,
            WaypointAuditRecord("wp-1", 1_000L, RecipientScope.PUBLIC).recipientScope,
        )
    }
}
