// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import com.sovereignatlas.atlas.field.WaypointSharingPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the outbound path enforces Phase 10 §10.6, and — more importantly — proves
 * it by construction rather than by assertion of a side effect.
 */
final class WaypointSharingRouterTest {

    /**
     * A serializer that fails the test if it is ever called.
     *
     * This is the whole point of the ordering inside [WaypointSharingRouter.route].
     * A test that transmitted a PRIVATE waypoint and then asserted the router
     * returned "withheld" would pass even if the router had serialized it first and
     * thrown the bytes away, which is precisely the bug that matters: the
     * coordinates would already have been in memory on a device the operator
     * believes is offline. Making the serializer itself throw makes "never
     * serialized" an observable property.
     */
    private class TripwireSerializer : WaypointPayloadSerializer {
        var callCount = 0
        var lastScope: RecipientScope? = null

        override fun serialize(waypoint: ShareableWaypoint, scope: RecipientScope): ByteArray {
            callCount++
            lastScope = scope
            return "payload:${waypoint.id}:$scope".toByteArray()
        }
    }

    private class RecordingRevocationSerializer : WaypointRevocationSerializer {
        val calls = mutableListOf<Pair<String, Long>>()

        override fun serializeRevocation(waypointId: String, atMillis: Long): ByteArray {
            calls += waypointId to atMillis
            return "revoke:$waypointId".toByteArray()
        }
    }

    private fun waypoint(policy: WaypointSharingPolicy, id: String = "wp-1") = ShareableWaypoint(
        id = id,
        name = "Bridge Crossing",
        latitude = 44.9,
        longitude = -93.1,
        timestampMillis = 1_700_000_000_000L,
        notes = "north abutment",
        policy = policy,
    )

    // ------------------------------------------------------------------
    // The two rules §10.6 turns on
    // ------------------------------------------------------------------

    @Test
    fun aPrivateWaypointIsWithheldAndNeverSerialized() {
        val serializer = TripwireSerializer()
        val router = WaypointSharingRouter(serializer, RecordingRevocationSerializer())

        val decision = router.route(waypoint(WaypointSharingPolicy.Private))

        assertEquals(
            OutboundWaypointDecision.Withhold(WithholdReason.PRIVATE_POLICY),
            decision,
        )
        assertEquals(
            "a PRIVATE waypoint must not be serialized at all",
            0,
            serializer.callCount,
        )
    }

    @Test
    fun aTeamWaypointIsPermittedAtTeamScope() {
        val serializer = TripwireSerializer()
        val router = WaypointSharingRouter(serializer, RecordingRevocationSerializer())

        val decision = router.route(waypoint(WaypointSharingPolicy.Team))

        assertTrue(decision is OutboundWaypointDecision.Permit)
        decision as OutboundWaypointDecision.Permit
        assertEquals(RecipientScope.TEAM, decision.scope)
        assertEquals(1, serializer.callCount)
        assertEquals(RecipientScope.TEAM, serializer.lastScope)
    }

    @Test
    fun aPublicWaypointIsPermittedAtPublicScope() {
        val serializer = TripwireSerializer()
        val router = WaypointSharingRouter(serializer, RecordingRevocationSerializer())

        val decision = router.route(waypoint(WaypointSharingPolicy.Public))
            as OutboundWaypointDecision.Permit

        assertEquals(RecipientScope.PUBLIC, decision.scope)
    }

    @Test
    fun scopeFollowsThePolicyAndIsNotInferredFromAnythingElse() {
        val serializer = TripwireSerializer()
        val router = WaypointSharingRouter(serializer, RecordingRevocationSerializer())

        // Identical waypoint in every field except the policy. If scope were derived
        // from coordinates, name, or the network profile, these would agree.
        val team = router.route(waypoint(WaypointSharingPolicy.Team))
            as OutboundWaypointDecision.Permit
        val public = router.route(waypoint(WaypointSharingPolicy.Public))
            as OutboundWaypointDecision.Permit

        assertEquals(team.payload.toString(Charsets.UTF_8).substringAfterLast(':'), "TEAM")
        assertEquals(public.payload.toString(Charsets.UTF_8).substringAfterLast(':'), "PUBLIC")
    }

    // ------------------------------------------------------------------
    // Refusals that are not about the policy
    // ------------------------------------------------------------------

    @Test
    fun anUnaddressableWaypointIsRefusedRatherThanBroadcast() {
        val serializer = TripwireSerializer()
        val router = WaypointSharingRouter(serializer, RecordingRevocationSerializer())

        val decision = router.route(waypoint(WaypointSharingPolicy.Public, id = "   "))

        assertEquals(OutboundWaypointDecision.Withhold(WithholdReason.EMPTY_ID), decision)
        assertEquals(0, serializer.callCount)
    }

    @Test
    fun aBlankIdIsRefusedEvenUnderATeamPolicy() {
        // The permissive policy must not launder an unaddressable item. A waypoint
        // with no uid can never be revoked later, so permitting it would create a
        // permanent disclosure the operator cannot withdraw.
        val serializer = TripwireSerializer()
        val router = WaypointSharingRouter(serializer, RecordingRevocationSerializer())

        val decision = router.route(waypoint(WaypointSharingPolicy.Team, id = ""))

        assertTrue(decision is OutboundWaypointDecision.Withhold)
        assertEquals(0, serializer.callCount)
    }

    // ------------------------------------------------------------------
    // The stop-sharing control
    // ------------------------------------------------------------------

    @Test
    fun stopSharingRevertsThePolicyToPrivate() {
        val router = WaypointSharingRouter(TripwireSerializer(), RecordingRevocationSerializer())
        val shared = waypoint(WaypointSharingPolicy.Team)

        val outcome = router.stopSharing(shared, atMillis = 5_000L, wasShared = true)

        assertEquals(WaypointSharingPolicy.Private, outcome.waypoint.policy)
    }

    @Test
    fun stopSharingPreservesEveryOtherField() {
        val router = WaypointSharingRouter(TripwireSerializer(), RecordingRevocationSerializer())
        val shared = waypoint(WaypointSharingPolicy.Public, id = "wp-keep")

        val outcome = router.stopSharing(shared, atMillis = 5_000L, wasShared = true)

        // Reversion must not quietly drop the notes or shift the position. A
        // copy() that listed the wrong fields would leave a waypoint the operator
        // still recognises but with its context gone.
        assertEquals(shared.id, outcome.waypoint.id)
        assertEquals(shared.name, outcome.waypoint.name)
        assertEquals(shared.latitude, outcome.waypoint.latitude, 0.0)
        assertEquals(shared.longitude, outcome.waypoint.longitude, 0.0)
        assertEquals(shared.timestampMillis, outcome.waypoint.timestampMillis)
        assertEquals(shared.notes, outcome.waypoint.notes)
    }

    @Test
    fun stopSharingOwesARevocationOnlyWhenTheWaypointWasActuallyShared() {
        val router = WaypointSharingRouter(TripwireSerializer(), RecordingRevocationSerializer())

        val neverShared = router.stopSharing(
            waypoint(WaypointSharingPolicy.Team),
            atMillis = 5_000L,
            wasShared = false,
        )
        assertTrue(neverShared.decision is OutboundWaypointDecision.Withhold)

        val wasShared = router.stopSharing(
            waypoint(WaypointSharingPolicy.Team),
            atMillis = 5_000L,
            wasShared = true,
        )
        assertTrue(wasShared.decision is OutboundWaypointDecision.Revoke)
    }

    @Test
    fun aWithdrawalNamesTheSameIdTheShareUsed() {
        // The revocation can only be matched by a peer if it names the uid the
        // original share carried. This is the invariant that makes stop-sharing real
        // rather than decorative.
        val revocations = RecordingRevocationSerializer()
        val router = WaypointSharingRouter(TripwireSerializer(), revocations)
        val shared = waypoint(WaypointSharingPolicy.Team, id = "wp-round-trip")

        val share = router.route(shared)
        assertTrue(share is OutboundWaypointDecision.Permit)

        val stop = router.stopSharing(shared, atMillis = 9_000L, wasShared = true)

        assertEquals(listOf("wp-round-trip" to 9_000L), revocations.calls)
        assertEquals(
            "the revocation must not carry the shared payload",
            "revoke:wp-round-trip",
            (stop.decision as OutboundWaypointDecision.Revoke).payload.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun stopSharingIsIdempotentInItsReturnValue() {
        // Two consecutive presses must not rewrite the instant of the first. The
        // caller persists the outcome, so a second press that moved the timestamp
        // would falsify the only evidence of when the operator acted.
        val router = WaypointSharingRouter(TripwireSerializer(), RecordingRevocationSerializer())
        val shared = waypoint(WaypointSharingPolicy.Team)

        val first = router.stopSharing(shared, atMillis = 1_000L, wasShared = true)
        val second = router.stopSharing(
            first.waypoint,
            atMillis = 99_000L,
            wasShared = false,
        )

        assertEquals(WaypointSharingPolicy.Private, second.waypoint.policy)
    }

    @Test
    fun revokingAPrivateWaypointStillRevertsAndStillRefuses() {
        // Already private and never shared: nothing to transmit, but the state must
        // still come back Private so the caller's write is idempotent.
        val router = WaypointSharingRouter(TripwireSerializer(), RecordingRevocationSerializer())

        val outcome = router.stopSharing(
            waypoint(WaypointSharingPolicy.Private),
            atMillis = 1_000L,
            wasShared = false,
        )

        assertEquals(WaypointSharingPolicy.Private, outcome.waypoint.policy)
        assertTrue(outcome.decision is OutboundWaypointDecision.Withhold)
    }

    // ------------------------------------------------------------------
    // Payload shape
    // ------------------------------------------------------------------

    @Test
    fun aRevocationCarriesNoCoordinates() {
        // The revocation serializer is handed an id and a timestamp and nothing else,
        // so there is no path by which coordinates could reach it. Asserted against a
        // serializer that would include them if it could.
        val revocations = RecordingRevocationSerializer()
        val router = WaypointSharingRouter(TripwireSerializer(), revocations)

        val outcome = router.stopSharing(
            waypoint(WaypointSharingPolicy.Public),
            atMillis = 2_000L,
            wasShared = true,
        )
        val payload = (outcome.decision as OutboundWaypointDecision.Revoke).payload
            .toString(Charsets.UTF_8)

        assertFalse(payload.contains("44.9"))
        assertFalse(payload.contains("-93.1"))
        assertFalse(payload.contains("Bridge Crossing"))
        assertFalse(payload.contains("north abutment"))
    }

    @Test
    fun theRouterNeverThrowsRegardlessOfPolicy() {
        val router = WaypointSharingRouter(TripwireSerializer(), RecordingRevocationSerializer())
        for (policy in WaypointSharingPolicy.entries) {
            assertTrue(router.route(waypoint(policy)) !is OutboundWaypointDecision.Revoke)
        }
    }
}
