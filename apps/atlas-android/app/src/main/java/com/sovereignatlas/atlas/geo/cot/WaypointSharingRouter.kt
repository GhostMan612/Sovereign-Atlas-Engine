// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import com.sovereignatlas.atlas.field.WaypointSharingPolicy

/**
 * Serializes a waypoint for the mesh. Injected, so the router can be proven not to
 * call it without needing a protobuf codec — or, more to the point, without needing
 * to observe bytes at all.
 *
 * Implementations receive the scope alongside the waypoint so they can narrow the
 * payload: a TEAM share and a PUBLIC share of the same coordinates are not
 * necessarily the same message.
 */
fun interface WaypointPayloadSerializer {
    fun serialize(waypoint: ShareableWaypoint, scope: RecipientScope): ByteArray
}

/**
 * Serializes a withdrawal. Separate from [WaypointPayloadSerializer] for the reason
 * that matters most in this file: a revocation must never be able to carry
 * coordinates, and giving it the same signature as a share would invite exactly
 * that. This function is handed an id and nothing else.
 */
fun interface WaypointRevocationSerializer {
    fun serializeRevocation(waypointId: String, atMillis: Long): ByteArray
}

/** What the outbound path decided to do with one waypoint. */
sealed interface OutboundWaypointDecision {

    /** Serialize and transmit. The payload was built at the given scope. */
    data class Permit(
        val payload: ByteArray,
        val scope: RecipientScope,
    ) : OutboundWaypointDecision

    /**
     * Do not transmit. [reason] says why, because "nothing appeared on the mesh" is
     * indistinguishable from a radio fault unless the refusal is reportable.
     */
    data class Withhold(val reason: WithholdReason) : OutboundWaypointDecision

    /**
     * Transmit a withdrawal for something previously shared.
     *
     * Distinct from [Permit] on purpose: a revocation is not a share, and folding it
     * into one would let a caller logging "sent" be unable to tell that the item has
     * left the mesh.
     */
    data class Revoke(val payload: ByteArray) : OutboundWaypointDecision
}

/** Why a waypoint was not put on the wire. */
enum class WithholdReason {
    /**
     * The policy is [WaypointSharingPolicy.Private]. The overwhelming majority of
     * refusals, and the only one that is a feature rather than a fault.
     */
    PRIVATE_POLICY,

    /**
     * An empty id. Such a waypoint cannot be addressed and therefore cannot be
     * revoked later, so it is refused at the boundary rather than becoming a
     * permanent, unrecallable disclosure.
     */
    EMPTY_ID,
}

/**
 * Decides whether a waypoint may leave this device.
 *
 * WHY A SEPARATE CLASS RATHER THAN A BRANCH IN `AtakBroadcaster`. The broadcaster
 * already gates on `NetworkProfile`, and adding a second `if` there would make both
 * checks unfalsifiable on the JVM: the class takes `WifiManager` and `PowerManager`,
 * so a test asserting "a PRIVATE waypoint never reaches the socket" could not be
 * written at all. The same reasoning that produced [CotMessageRouter] for the inbound
 * direction. The broadcaster stays the tier that owns the socket; this stays the tier
 * that owns the decision.
 *
 * THE ORDER OF OPERATIONS IS THE SAFETY PROPERTY. The policy is consulted before
 * [WaypointPayloadSerializer] is invoked, so a PRIVATE waypoint is not merely
 * discarded after serialization — it is never turned into bytes at all. Serializing
 * first and filtering after would put the coordinates in memory, in whatever buffer
 * the serializer allocated, on a device the operator believes is offline. Tests
 * assert this with a serializer that fails the test if it is ever called.
 */
class WaypointSharingRouter(
    private val serializer: WaypointPayloadSerializer,
    private val revocationSerializer: WaypointRevocationSerializer,
) {

    /**
     * Decides the outbound fate of one waypoint.
     *
     * Never throws for a policy decision: an unrecognised policy resolves to
     * [WaypointSharingPolicy.Private] upstream, and a blank one resolves here.
     */
    fun route(waypoint: ShareableWaypoint): OutboundWaypointDecision {
        if (waypoint.id.isBlank()) {
            return OutboundWaypointDecision.Withhold(WithholdReason.EMPTY_ID)
        }
        val scope = RecipientScope.forPolicy(waypoint.policy)
            ?: return OutboundWaypointDecision.Withhold(WithholdReason.PRIVATE_POLICY)
        return OutboundWaypointDecision.Permit(serializer.serialize(waypoint, scope), scope)
    }

    /**
     * The stop-sharing control.
     *
     * Reverts [waypoint] to [WaypointSharingPolicy.Private] and reports whether a
     * withdrawal is owed. Pure state mutation plus a decision — the caller persists
     * the returned waypoint and transmits the decision, and this function touches
     * neither a database nor a socket.
     *
     * A revocation is owed ONLY when the waypoint was actually shared. Withdrawing
     * something that never left would put a tombstone for a uid no peer has ever
     * seen on the mesh, which is noise at best and, in a system where uids are
     * observable, a small disclosure that a waypoint with that id exists. So the
     * caller passes [wasShared] from the audit ledger rather than this guessing it.
     *
     * [wasShared] is a parameter rather than an injected ledger lookup because this
     * type is pure and stateless; wiring the real ledger is the caller's job, and
     * keeping it a parameter is what lets a test assert the refusal.
     */
    fun stopSharing(
        waypoint: ShareableWaypoint,
        atMillis: Long,
        wasShared: Boolean,
    ): StopSharingOutcome {
        val reverted = waypoint.copy(policy = WaypointSharingPolicy.Private)
        return if (wasShared) {
            StopSharingOutcome(
                waypoint = reverted,
                decision = OutboundWaypointDecision.Revoke(
                    revocationSerializer.serializeRevocation(reverted.id, atMillis),
                ),
            )
        } else {
            StopSharingOutcome(
                waypoint = reverted,
                decision = OutboundWaypointDecision.Withhold(WithholdReason.PRIVATE_POLICY),
            )
        }
    }
}

/**
 * The result of the stop-sharing control.
 *
 * Carries BOTH the reverted waypoint and the decision, so a caller cannot persist
 * the revert and forget the revocation — or transmit the revocation without having
 * reverted the row. Those two must happen together; bundling them is what makes the
 * pairing a type rather than a convention.
 */
data class StopSharingOutcome(
    val waypoint: ShareableWaypoint,
    val decision: OutboundWaypointDecision,
)
