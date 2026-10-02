// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The default-value policy, pinned independently of the database.
 *
 * `WaypointSharingPolicy` is the rule that decides whether data leaves the device.
 * It has no tests of its own elsewhere, and a change to its fallback behaviour would
 * not fail any query test — a corrupt or blank column would simply resolve quietly.
 */
final class WaypointSharingPolicyTest {

    @Test
    fun storedValuesRoundTrip() {
        for (policy in WaypointSharingPolicy.entries) {
            assertEquals(policy, WaypointSharingPolicy.fromStored(policy.storedValue))
        }
    }

    @Test
    fun everyMissingValueFallsBackToPrivate() {
        // Phase 10 §10.6 requires explicit opt-in. A blank, absent, or corrupt value
        // must never resolve to a policy that shares data.
        for (value in listOf(null, "", "   ", "\t")) {
            assertEquals(
                "a missing value ($value) must resolve to PRIVATE",
                WaypointSharingPolicy.Private,
                WaypointSharingPolicy.fromStored(value),
            )
        }
    }

    @Test
    fun anUnrecognisedValueFallsBackToPrivateRatherThanThrowing() {
        // A corrupt database must not become an exception out of a query, and it must
        // certainly not become a permissive policy.
        for (value in listOf("public", "SHARED", "everyone", "0", "PRIVATE ")) {
            assertEquals(
                "an unknown value ($value) must resolve to PRIVATE",
                WaypointSharingPolicy.Private,
                WaypointSharingPolicy.fromStored(value),
            )
        }
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertEquals(
            WaypointSharingPolicy.Team,
            WaypointSharingPolicy.fromStored("  TEAM  "),
        )
    }

    @Test
    fun privateIsTheFirstDeclaredValue() {
        // The enum order is the policy: Private leads, so a future `entries.first()`
        // or ordinal-based default lands on the safe option.
        assertEquals(
            WaypointSharingPolicy.Private,
            WaypointSharingPolicy.entries.first(),
        )
        assertEquals(0, WaypointSharingPolicy.Private.ordinal)
    }

    @Test
    fun theStoredValuesAreDistinct() {
        // Two policies sharing a stored string would make fromStored ambiguous and
        // one of them permanently unreachable.
        val values = WaypointSharingPolicy.entries.map { it.storedValue }
        assertEquals("stored values must be unique", values.size, values.toSet().size)
    }
}