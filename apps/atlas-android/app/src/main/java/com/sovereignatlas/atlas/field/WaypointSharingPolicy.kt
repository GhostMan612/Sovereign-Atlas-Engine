// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

/**
 * Whether a waypoint may leave this device, and to whom.
 *
 * Added by ADR-007 to unblock master blueprint Phase 10 §10.6, which requires
 * sharing to be explicit, scoped, and controllable. The field it backs is
 * `Waypoint.sharingPolicy`, defaulting to [Private] on both fresh installs and
 * upgrades.
 *
 * THE ORDER IS THE POLICY. [Private] is first and is every fallback, because §10.6
 * demands EXPLICIT OPT-IN. An unrecognised or missing stored value resolves to
 * [Private] rather than throwing or, worse, defaulting to a permissive option: a
 * corrupt database must never become a reason to publish someone's field data.
 *
 * Pure Kotlin with no Android dependency, per RULES 2.1. It is deliberately NOT the
 * `NetworkProfile` enum from `android/settings`: that one is a device-wide
 * transport posture, this one is a per-item data policy, and conflating them would
 * make a waypoint's sharing depend on a global radio setting.
 */
enum class WaypointSharingPolicy(val storedValue: String) {
    /** Visible to this device only. The default for new and migrated rows. */
    Private("PRIVATE"),

    /** Shared with peers on the same mesh/team, subject to the network profile. */
    Team("TEAM"),

    /** Shared with any peer that can receive it, subject to the network profile. */
    Public("PUBLIC"),
    ;

    companion object {
        /**
         * Parses a stored value, falling back to [Private].
         *
         * Never throws and never returns null. The database column is NOT NULL with a
         * default, but a hand-edited or corrupt value must still resolve to the safe
         * option instead of propagating an exception out of a query.
         */
        fun fromStored(value: String?): WaypointSharingPolicy {
            if (value.isNullOrBlank()) return Private
            return entries.firstOrNull { it.storedValue == value.trim() } ?: Private
        }
    }
}
