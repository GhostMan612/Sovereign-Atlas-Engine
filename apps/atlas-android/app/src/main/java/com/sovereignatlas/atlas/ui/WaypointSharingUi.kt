// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import com.sovereignatlas.atlas.field.WaypointSharingPolicy
import com.sovereignatlas.atlas.geo.cot.RecipientScope

/**
 * Presentation logic for the sharing controls, kept free of Compose so it can be
 * asserted on the JVM.
 *
 * WHY THIS IS NOT INSIDE THE COMPOSABLE. Every rule below is a safety rule, and a
 * rule that can only be checked by reading Compose code is a rule nobody checks.
 * This project already has one precedent for moving a decision out of an untestable
 * holder — [com.sovereignatlas.atlas.geo.cot.CotMessageRouter] exists because the
 * hostile-marker classification lived somewhere it could not be asserted. The same
 * applies here: `androidx.compose.ui:ui-test-junit4` is an `androidTest` dependency,
 * so unit tests here are JVM-only and cannot compose a widget. Anything worth
 * asserting has to sit behind this boundary, with the composable reduced to drawing
 * what it is told.
 *
 * THE CENTRAL RULE IS §10.6'S EXPLICIT OPT-IN, MADE OPERATIONAL. [confirmationFor]
 * returns "confirm" for every move AWAY from [WaypointSharingPolicy.Private], and
 * "none" for every move back to it. That asymmetry is deliberate. Withdrawing data
 * should never be a two-step process the operator has to think through; publishing it
 * should never be something a thumb reaches while reaching for something else.
 */
object WaypointSharingUi {

    /** How a policy transition should be surfaced to the operator. */
    enum class Confirmation {
        /** Apply directly. Used for every move toward PRIVATE. */
        NONE,

        /**
         * Require a second, explicit acknowledgement naming the scope.
         *
         * Used for every move away from PRIVATE. The dialog states the scope in
         * words — "shared with any peer" — rather than echoing the enum name, because
         * the operator is deciding who can see a position, not picking a constant.
         */
        REQUIRED,
    }

    /** The short badge text drawn on the list item and next to the selector. */
    fun badgeFor(policy: WaypointSharingPolicy): String = when (policy) {
        WaypointSharingPolicy.Private -> "LOCAL"
        WaypointSharingPolicy.Team -> "TEAM"
        WaypointSharingPolicy.Public -> "PUBLIC"
    }

    /**
     * The one-line explanation under the selector.
     *
     * Spells out the actual consequence, including the one an operator is most likely
     * to get wrong: that the `NetworkProfile` still applies on top. Saying so here
     * is cheaper than a support conversation about a waypoint that did not appear.
     */
    fun descriptionFor(policy: WaypointSharingPolicy): String = when (policy) {
        WaypointSharingPolicy.Private ->
            "Stays on this device. Never transmitted, even if a sharing profile is active."
        WaypointSharingPolicy.Team ->
            "Permitted on the mesh for peers on your team. The active network profile still applies."
        WaypointSharingPolicy.Public ->
            "Permitted for any peer that can receive it. The active network profile still applies."
    }

    /**
     * The accessibility label for the indicator, which carries more than the badge.
     *
     * A bare "LOCAL" or "TEAM" read aloud gives a screen-reader user the badge and
     * not its meaning. This states the exposure in a sentence instead.
     */
    fun indicatorDescriptionFor(policy: WaypointSharingPolicy): String = when (policy) {
        WaypointSharingPolicy.Private -> "Sharing: local only, not transmitted"
        WaypointSharingPolicy.Team -> "Sharing: permitted for team peers"
        WaypointSharingPolicy.Public -> "Sharing: permitted for any peer"
    }

    /** Whether this policy is exposed beyond this device, for conditional styling. */
    fun isExposed(policy: WaypointSharingPolicy): Boolean =
        policy != WaypointSharingPolicy.Private

    /**
     * The confirmation the transition requires.
     *
     * CONFIRM ONLY ON CROSSING INTO EXPOSURE, NOT WITHIN IT. The rule is not "any
     * change away from PRIVATE" — that would demand acknowledgement when an operator
     * narrows an already-public waypoint down to their team, which is a REDUCTION in
     * exposure and should not be a step to undo. What needs an acknowledgement is the
     * one transition that publishes something that was local, because that is the
     * instant data leaves the device.
     *
     * So: confirmation is owed exactly when [from] withholds and [to] does not.
     *
     * [from] is the currently stored policy. The stored column is NOT NULL, and a
     * corrupt value is already [WaypointSharingPolicy.Private] by the time it arrives
     * here via `WaypointSharingPolicy.fromStored`.
     */
    fun confirmationFor(
        from: WaypointSharingPolicy,
        to: WaypointSharingPolicy,
    ): Confirmation = when {
        to == from -> Confirmation.NONE
        to == WaypointSharingPolicy.Private -> Confirmation.NONE
        isExposed(from) -> Confirmation.NONE
        else -> Confirmation.REQUIRED
    }

    /**
     * The scope this transition would publish at, or null when it withholds.
     *
     * Null means the move publishes nothing. Returning it rather than a default is
     * what lets the caller refuse an unknown policy without a second lookup.
     */
    fun scopeFor(policy: WaypointSharingPolicy): RecipientScope? =
        RecipientScope.forPolicy(policy)

    /**
     * The wording of the confirmation dialog for a pending move away from PRIVATE.
     *
     * Returns null when [confirmationFor] is [Confirmation.NONE], so the dialog and
     * the decision that produced it cannot disagree about whether one is needed.
     */
    fun confirmationMessage(
        from: WaypointSharingPolicy,
        to: WaypointSharingPolicy,
        waypointName: String,
    ): String? {
        if (confirmationFor(from, to) != Confirmation.REQUIRED) return null
        val name = waypointName.ifBlank { "this waypoint" }
        val consequence = when (to) {
            WaypointSharingPolicy.Team ->
                "peers on your team will be able to see ${name}'s position and notes"
            WaypointSharingPolicy.Public ->
                "ANY peer that can receive it will be able to see ${name}'s position and notes"
            // Unreachable: confirmationFor is NONE whenever the target is Private.
            WaypointSharingPolicy.Private -> return null
        }
        return "Share $consequence? You can withdraw this later, but anyone who already " +
            "received it may keep a copy."
    }

    /** The three policies, in the order the selector lists them. */
    val selectablePolicies: List<WaypointSharingPolicy> = listOf(
        WaypointSharingPolicy.Private,
        WaypointSharingPolicy.Team,
        WaypointSharingPolicy.Public,
    )
}
