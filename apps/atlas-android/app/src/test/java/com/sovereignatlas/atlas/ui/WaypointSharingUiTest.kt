// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import com.sovereignatlas.atlas.field.WaypointSharingPolicy
import com.sovereignatlas.atlas.geo.cot.RecipientScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sharing UI's rules, tested without Compose.
 *
 * This exists because `ui-test-junit4` is an `androidTest` dependency in this
 * project, so nothing the host gate runs can compose a widget. Every safety rule
 * therefore had to live in [WaypointSharingUi] to be reachable by these tests, and
 * these tests are the only thing standing between the operator and a sharing control
 * that quietly does nothing.
 */
final class WaypointSharingUiTest {

    // ------------------------------------------------------------------
    // The asymmetry: publishing asks, withdrawing does not
    // ------------------------------------------------------------------

    @Test
    fun everyMoveAwayFromPrivateRequiresConfirmation() {
        for (target in WaypointSharingUi.selectablePolicies) {
            if (target == WaypointSharingPolicy.Private) continue
            assertEquals(
                "moving from PRIVATE to $target must be confirmed",
                WaypointSharingUi.Confirmation.REQUIRED,
                WaypointSharingUi.confirmationFor(WaypointSharingPolicy.Private, target),
            )
        }
    }

    @Test
    fun everyMoveBackToPrivateAppliesImmediately() {
        // Withdrawing must never be a two-step process. An operator pulling data off
        // the mesh under time pressure should not be stopped by a dialog.
        for (source in WaypointSharingUi.selectablePolicies) {
            assertEquals(
                "moving from $source to PRIVATE must not need confirmation",
                WaypointSharingUi.Confirmation.NONE,
                WaypointSharingUi.confirmationFor(source, WaypointSharingPolicy.Private),
            )
        }
    }

    @Test
    fun aNoOpSelectionNeedsNoConfirmation() {
        assertEquals(
            WaypointSharingUi.Confirmation.NONE,
            WaypointSharingUi.confirmationFor(WaypointSharingPolicy.Team, WaypointSharingPolicy.Team),
        )
    }

    @Test
    fun narrowingTheScopeNeedsNoConfirmation() {
        // PUBLIC -> TEAM withholds from peers that already could see it. Requiring
        // acknowledgement to REDUCE exposure would make withholding a multi-step
        // process, which is exactly backwards.
        assertEquals(
            WaypointSharingUi.Confirmation.NONE,
            WaypointSharingUi.confirmationFor(WaypointSharingPolicy.Public, WaypointSharingPolicy.Team),
        )
    }

    @Test
    fun wideningTheScopeNeedsNoConfirmationEither() {
        // TEAM -> PUBLIC is an increase in audience, and it still needs no
        // confirmation: the waypoint is already on the mesh, so the operator has
        // already made the decision to publish and already answered the question.
        // Confirmation gates the local->exposed crossing, not every subsequent edit.
        assertEquals(
            WaypointSharingUi.Confirmation.NONE,
            WaypointSharingUi.confirmationFor(WaypointSharingPolicy.Team, WaypointSharingPolicy.Public),
        )
    }

    @Test
    fun confirmationIsOwedExactlyWhenCrossingIntoExposure() {
        // The whole rule stated once, over every ordered pair, so the asymmetry cannot
        // drift as policies are added.
        for (from in WaypointSharingUi.selectablePolicies) {
            for (to in WaypointSharingUi.selectablePolicies) {
                val expected =
                    if (to != from && to != WaypointSharingPolicy.Private && !WaypointSharingUi.isExposed(from)) {
                        WaypointSharingUi.Confirmation.REQUIRED
                    } else {
                        WaypointSharingUi.Confirmation.NONE
                    }
                assertEquals(
                    "confirmationFor($from -> $to)",
                    expected,
                    WaypointSharingUi.confirmationFor(from, to),
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // The confirmation wording
    // ------------------------------------------------------------------

    @Test
    fun noMessageIsOfferedWhenNoConfirmationIsRequired() {
        assertNull(
            WaypointSharingUi.confirmationMessage(
                WaypointSharingPolicy.Team,
                WaypointSharingPolicy.Private,
                "Bridge",
            ),
        )
        assertNull(
            WaypointSharingUi.confirmationMessage(
                WaypointSharingPolicy.Team,
                WaypointSharingPolicy.Team,
                "Bridge",
            ),
        )
    }

    @Test
    fun aMessageIsOfferedWhenOneIsRequired() {
        assertNotNull(
            WaypointSharingUi.confirmationMessage(
                WaypointSharingPolicy.Private,
                WaypointSharingPolicy.Public,
                "Bridge",
            ),
        )
    }

    @Test
    fun thePublicMessageNamesTheWorstCaseAudience() {
        val message = WaypointSharingUi.confirmationMessage(
            WaypointSharingPolicy.Private,
            WaypointSharingPolicy.Public,
            "Bridge Crossing",
        )!!

        assertTrue(message.contains("ANY peer"))
        assertTrue(message.contains("Bridge Crossing"))
        // The sentence the operator most needs, and the one an implementation is most
        // tempted to omit because it reads as legalistic. Withdrawal cannot un-send.
        assertTrue(message.contains("may keep a copy"))
    }

    @Test
    fun theTeamMessageDoesNotOverstateTheAudience() {
        val message = WaypointSharingUi.confirmationMessage(
            WaypointSharingPolicy.Private,
            WaypointSharingPolicy.Team,
            "Bridge Crossing",
        )!!

        assertTrue(message.contains("team"))
        assertFalse("a TEAM share must not claim the audience is everyone", message.contains("ANY peer"))
    }

    @Test
    fun aBlankNameStillProducesAReadableSentence() {
        // A waypoint can legitimately have an empty name, and "Share 's position" is
        // not a sentence an operator should be asked to confirm.
        val message = WaypointSharingUi.confirmationMessage(
            WaypointSharingPolicy.Private,
            WaypointSharingPolicy.Team,
            "   ",
        )!!

        // "this waypoint's position" — the possessive here is correct English, so
        // the assertion checks for the doubled-quote breakage the blank name would
        // cause, not for the apostrophe itself.
        assertTrue(message.contains("this waypoint's position"))
        assertFalse(message.contains("''"))
        assertFalse(message.contains(" 's position"))
    }

    @Test
    fun theMessageStatesBothWhatIsSharedAndThatItCanBeWithdrawn() {
        val message = WaypointSharingUi.confirmationMessage(
            WaypointSharingPolicy.Private,
            WaypointSharingPolicy.Team,
            "Cache",
        )!!

        assertTrue("must say what is exposed", message.contains("position and notes"))
        assertTrue("must say withdrawal is available", message.contains("withdraw"))
    }

    // ------------------------------------------------------------------
    // Exposure
    // ------------------------------------------------------------------

    @Test
    fun onlyPrivateIsUnexposed() {
        assertFalse(WaypointSharingUi.isExposed(WaypointSharingPolicy.Private))
        assertTrue(WaypointSharingUi.isExposed(WaypointSharingPolicy.Team))
        assertTrue(WaypointSharingUi.isExposed(WaypointSharingPolicy.Public))
    }

    @Test
    fun exposureMatchesWhatTheOutboundRouterWouldDo() {
        // The indicator and the router must not disagree. If a policy is drawn as
        // exposed but the router withholds it, or the reverse, the operator's reading
        // of the map is wrong in the direction that matters.
        for (policy in WaypointSharingUi.selectablePolicies) {
            val scope = WaypointSharingUi.scopeFor(policy)
            assertEquals(
                "indicator/router disagreement for $policy",
                WaypointSharingUi.isExposed(policy),
                scope != null,
            )
        }
    }

    @Test
    fun scopeForPrivateIsNull() {
        assertNull(WaypointSharingUi.scopeFor(WaypointSharingPolicy.Private))
    }

    @Test
    fun scopeForTheSharedPoliciesMatchesTheRecipientScope() {
        assertEquals(
            RecipientScope.TEAM,
            WaypointSharingUi.scopeFor(WaypointSharingPolicy.Team),
        )
        assertEquals(
            RecipientScope.PUBLIC,
            WaypointSharingUi.scopeFor(WaypointSharingPolicy.Public),
        )
    }

    // ------------------------------------------------------------------
    // Labels and accessibility
    // ------------------------------------------------------------------

    @Test
    fun everyPolicyHasItsOwnBadge() {
        val badges = WaypointSharingUi.selectablePolicies.map { WaypointSharingUi.badgeFor(it) }
        assertEquals("badges must be distinguishable", badges.size, badges.toSet().size)
    }

    @Test
    fun theBadgeNamesTheExposureRatherThanTheEnum() {
        // "PRIVATE" would read as a claim about the object; "LOCAL" states the
        // consequence, which is what the operator is deciding.
        assertEquals("LOCAL", WaypointSharingUi.badgeFor(WaypointSharingPolicy.Private))
        assertEquals("TEAM", WaypointSharingUi.badgeFor(WaypointSharingPolicy.Team))
        assertEquals("PUBLIC", WaypointSharingUi.badgeFor(WaypointSharingPolicy.Public))
    }

    @Test
    fun everyPolicyHasADistinctAccessibilityDescription() {
        val descriptions = WaypointSharingUi.selectablePolicies
            .map { WaypointSharingUi.indicatorDescriptionFor(it) }
        assertEquals(descriptions.size, descriptions.toSet().size)
    }

    @Test
    fun theAccessibilityDescriptionCarriesMeaningNotJustTheBadge() {
        // A screen reader announcing only "LOCAL" or "TEAM" gives the operator a word
        // and not the exposure it stands for.
        assertTrue(
            WaypointSharingUi.indicatorDescriptionFor(WaypointSharingPolicy.Private)
                .contains("not transmitted"),
        )
        assertTrue(
            WaypointSharingUi.indicatorDescriptionFor(WaypointSharingPolicy.Public)
                .contains("any peer"),
        )
    }

    @Test
    fun everyPolicyHasADescription() {
        for (policy in WaypointSharingUi.selectablePolicies) {
            assertTrue(WaypointSharingUi.descriptionFor(policy).isNotBlank())
        }
    }

    @Test
    fun theDescriptionsSayTheNetworkProfileStillApplies() {
        // The assumption most likely to be wrong: that selecting TEAM publishes
        // regardless of the radio. Stating the interaction up front is cheaper than
        // the support conversation about a waypoint that did not appear.
        for (policy in listOf(WaypointSharingPolicy.Team, WaypointSharingPolicy.Public)) {
            assertTrue(
                "the $policy description must mention the network profile",
                WaypointSharingUi.descriptionFor(policy).contains("network profile"),
            )
        }
    }

    @Test
    fun thePrivateDescriptionSaysItIsNeverTransmitted() {
        assertTrue(
            WaypointSharingUi.descriptionFor(WaypointSharingPolicy.Private)
                .contains("Never transmitted"),
        )
    }

    // ------------------------------------------------------------------
    // Ordering
    // ------------------------------------------------------------------

    @Test
    fun theSelectorListsPrivateFirst() {
        // The list order is the safe default's position. Reading down the options
        // should put the one that publishes nothing at the top.
        assertEquals(
            WaypointSharingPolicy.Private,
            WaypointSharingUi.selectablePolicies.first(),
        )
    }

    @Test
    fun theSelectorListsEveryPolicy() {
        assertEquals(
            WaypointSharingPolicy.entries.toList(),
            WaypointSharingUi.selectablePolicies,
        )
    }
}
