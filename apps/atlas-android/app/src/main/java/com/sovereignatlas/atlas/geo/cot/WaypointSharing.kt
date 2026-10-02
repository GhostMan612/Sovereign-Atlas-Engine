// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import com.sovereignatlas.atlas.field.WaypointSharingPolicy

/**
 * How far a waypoint is permitted to travel once it leaves this device.
 *
 * DELIBERATELY NOT THE SAME THING AS `NetworkProfile`. That enum, in
 * `android/settings`, is a device-wide TRANSPORT posture: it answers "is this radio
 * allowed to emit anything at all". This answers "is THIS ITEM allowed to travel,
 * and how far". They are independent, and they are checked independently:
 *
 * - `NetworkProfile` is enforced in [com.sovereignatlas.atlas.android.comms.AtakBroadcaster],
 *   the tier that owns the socket.
 * - this type is enforced in [WaypointSharingRouter], the tier that decides what to
 *   serialize.
 *
 * Keeping them separate is the point. Folding them together would mean a waypoint's
 * sharing decided by a global radio setting, and Phase 10 §10.6 asks for per-item
 * explicit opt-in instead. An operator on MESH_ONLY who marked a waypoint PRIVATE
 * must still see it stay local; an operator on HYBRID with a PRIVATE waypoint must
 * not have the bridge publish it for them.
 */
enum class RecipientScope {
    /** Peers on the same mesh or team. */
    TEAM,

    /** Any peer able to receive it. */
    PUBLIC,
    ;

    companion object {
        /**
         * The scope a policy permits, or null when the policy forbids transmission.
         *
         * Null rather than a "None" value, and the [when] is written out instead of
         * being derived from `entries`, so that ADDING A POLICY TO THE ENUM MAKES
         * THIS FUNCTION FAIL TO COMPILE. That is the intended failure: a new policy
         * must be given an explicit transmission scope, and the compiler is a more
         * reliable witness of that than a reviewer reading a diff.
         */
        fun forPolicy(policy: WaypointSharingPolicy): RecipientScope? = when (policy) {
            WaypointSharingPolicy.Private -> null
            WaypointSharingPolicy.Team -> TEAM
            WaypointSharingPolicy.Public -> PUBLIC
        }
    }
}

/**
 * A waypoint as the outbound path sees it.
 *
 * NOT THE SQLDELIGHT ROW. `db.Waypoint` is generated code owned by the persistence
 * tier; importing it into `geo/` would put a database row type into the pure
 * geometry layer and make the outbound decision impossible to test without a
 * driver. This is the same adapter question ADR-006 answered for offline tiles: the
 * pure tier declares the shape it needs, and the tier holding the row maps into it.
 *
 * [id] IS ALSO THE CoT `uid`, and that is an invariant rather than a coincidence.
 * A revocation can only be matched by a peer if it names the same uid the original
 * share did, so a mapping that minted a fresh uid per transmission would silently
 * break stop-sharing while still looking correct on screen. [WaypointSharingRouter]
 * asserts the linkage.
 */
data class ShareableWaypoint(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val notes: String?,
    val policy: WaypointSharingPolicy,
)

/**
 * One share, or one un-share, of a single waypoint.
 *
 * This is the durable evidence that §10.6 asks for: not merely that a waypoint may
 * be sent, but that it WAS sent, to whom, when, and whether that was later withdrawn.
 *
 * Field names carry the unit suffix ([sharedAtMillis]) because the ambiguity this
 * avoids is real and has bitten this codebase before: a bare `sharedAt` invites a
 * reader to assume seconds, and a seconds value in a millisecond column reads as a
 * date in 1970 rather than as an error.
 *
 * A record is ACTIVE while [revokedAtMillis] is null. Revocation does not delete the
 * record: "this was shared, then withdrawn" is the fact that matters after the fact,
 * and a delete would destroy the only evidence that it happened at all.
 */
data class WaypointAuditRecord(
    val waypointId: String,
    val sharedAtMillis: Long,
    val recipientScope: RecipientScope,
    val revokedAtMillis: Long? = null,
) {
    /** True while this share has not been withdrawn. */
    fun isActive(): Boolean = revokedAtMillis == null

    /**
     * Withdraws this record at [atMillis].
     *
     * A second revocation is a no-op returning the record untouched, so a
     * double-tapped stop-sharing button cannot rewrite the moment the operator
     * actually pressed it — which is the moment the record has to be able to prove.
     */
    fun revoke(atMillis: Long): WaypointAuditRecord =
        if (revokedAtMillis != null) this else copy(revokedAtMillis = atMillis)
}

/**
 * In-memory record of who was given what.
 *
 * Phase 10 §10.6 foundation only. There is deliberately NO SQLDelight migration for
 * this yet: the persistence shape should be reviewed against this domain model
 * before a column is committed to, because a schema change is far harder to walk
 * back than a class. This is the pure logic a store would later front.
 *
 * Ordered append, last record per waypoint wins. A waypoint that is shared, revoked,
 * and shared again has two records, and [isCurrentlyShared] reports the latest —
 * which is what an operator asking "is this on the mesh right now?" needs.
 */
class WaypointSharingAudit {

    private val records = mutableListOf<WaypointAuditRecord>()

    /** Appends a share that just happened. */
    fun recordShared(
        waypointId: String,
        recipientScope: RecipientScope,
        atMillis: Long,
    ): WaypointAuditRecord {
        val record = WaypointAuditRecord(
            waypointId = waypointId,
            sharedAtMillis = atMillis,
            recipientScope = recipientScope,
        )
        records += record
        return record
    }

    /**
     * Withdraws every active share for [waypointId]. Returns the records touched.
     *
     * Returns an empty list when nothing was active, which is the signal
     * [WaypointSharingRouter.stopSharing] uses to decide a revocation is not owed.
     */
    fun recordRevoked(waypointId: String, atMillis: Long): List<WaypointAuditRecord> {
        val revoked = mutableListOf<WaypointAuditRecord>()
        for (index in records.indices) {
            val record = records[index]
            if (record.waypointId != waypointId || !record.isActive()) continue
            val replacement = record.revoke(atMillis)
            records[index] = replacement
            revoked += replacement
        }
        return revoked
    }

    /** Every record for [waypointId], oldest first. */
    fun recordsFor(waypointId: String): List<WaypointAuditRecord> =
        records.filter { it.waypointId == waypointId }

    /** True when the most recent record for [waypointId] is an un-withdrawn share. */
    fun isCurrentlyShared(waypointId: String): Boolean =
        records.lastOrNull { it.waypointId == waypointId }?.isActive() == true

    /** The full ledger, in the order it was written. */
    fun all(): List<WaypointAuditRecord> = records.toList()
}
