// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.field.WaypointSharingPolicy

/**
 * The sharing selector, plus the indicator that says whether a waypoint is exposed.
 *
 * EXTRACTION IS WHY THE RULES ARE TESTABLE. Every decision this file makes about
 * wording, ordering, and whether a change needs acknowledgement lives in
 * [WaypointSharingUi]. What remains here is drawing. That split exists because
 * `ui-test-junit4` is an `androidTest` dependency in this project, so the host gate
 * cannot compose a widget — a rule buried in a `@Composable` would never be
 * asserted by anything the gate runs.
 *
 * THE ICON SET IS CHOSEN FOR WHAT IT AFFIRMS, NOT FOR PRETTINESS. [Icons.Filled.Lock]
 * for PRIVATE, a radio glyph for TEAM (reaching peers), a globe for PUBLIC (everyone).
 * An exposed waypoint is drawn in amber rather than red: red would collide with the
 * hostile-contact colour already in use on this map, and a badge that means the same
 * thing as a threat marker teaches the operator nothing.
 */
object SharingIndicatorIcons {
    /** Lock for a local-only waypoint. */
    val LocalOnly: ImageVector get() = Icons.Filled.Lock

    /** Radio glyph for a team-scoped share. */
    val TeamShared: ImageVector get() = Icons.Filled.RecordVoiceOver

    /** Globe for an unrestricted share. */
    val PublicShared: ImageVector get() = Icons.Filled.Public
}

/** Icon for [policy], exposed for the list item and the sheet. */
@Composable
fun sharingIconFor(policy: WaypointSharingPolicy): ImageVector = when (policy) {
    WaypointSharingPolicy.Private -> SharingIndicatorIcons.LocalOnly
    WaypointSharingPolicy.Team -> SharingIndicatorIcons.TeamShared
    WaypointSharingPolicy.Public -> SharingIndicatorIcons.PublicShared
}

/**
 * Colour for the indicator.
 *
 * Amber for anything exposed, on-theme for local. Deliberately NOT red: this map
 * already uses red for hostile contacts, and reusing it here would make a sharing
 * badge look like a threat marker.
 */
@Composable
fun sharingTintFor(policy: WaypointSharingPolicy): Color =
    if (WaypointSharingUi.isExposed(policy)) {
        Color(0xFFFFA000)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

/**
 * The badge: icon, short label, and an accessibility description that carries the
 * meaning rather than just the text.
 *
 * Drawn for EVERY policy including PRIVATE. The badge is not only for marking what is
 * exposed — a visible "LOCAL" is what tells the operator the control was looked at and
 * found wanting, which is the reassurance §10.6's opt-in model depends on.
 */
@Composable
fun SharingIndicator(
    policy: WaypointSharingPolicy,
    modifier: Modifier = Modifier,
) {
    val tint = sharingTintFor(policy)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.testTag("sharing-indicator-${policy.storedValue}"),
    ) {
        Icon(
            imageVector = sharingIconFor(policy),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = WaypointSharingUi.badgeFor(policy),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * The indicator alone, for the list row where the sheet is not open.
 *
 * Carries a non-null `contentDescription` — unlike [SharingIndicator], which sits
 * beside text the screen reader already reads and would otherwise be announced as an
 * unlabelled graphic.
 */
@Composable
fun SharingIndicatorCompact(
    policy: WaypointSharingPolicy,
    modifier: Modifier = Modifier,
) {
    val tint = sharingTintFor(policy)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.testTag("sharing-badge-${policy.storedValue}"),
    ) {
        Icon(
            imageVector = sharingIconFor(policy),
            contentDescription = WaypointSharingUi.indicatorDescriptionFor(policy),
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = WaypointSharingUi.badgeFor(policy),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * The sharing selector.
 *
 * Radio buttons rather than a switch, because there are three states and a switch
 * cannot express "which of the two ways out" — an operator wanting TEAM would have to
 * guess how many taps it takes. A list states all three at once.
 *
 * [onPolicySelected] is called only after any required acknowledgement; this
 * composable owns the confirmation dialog so the caller cannot apply a change without
 * the dialog having been offered.
 */
@Composable
fun SharingPolicySelector(
    current: WaypointSharingPolicy,
    waypointName: String,
    onPolicySelected: (WaypointSharingPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending: WaypointSharingPolicy? by remember { mutableStateOf(null) }

    fun request(target: WaypointSharingPolicy) {
        if (WaypointSharingUi.confirmationFor(current, target) ==
            WaypointSharingUi.Confirmation.REQUIRED
        ) {
            pending = target
        } else {
            onPolicySelected(target)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Sharing",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = WaypointSharingUi.descriptionFor(current),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SharingIndicator(policy = current, modifier = Modifier.padding(vertical = 4.dp))
        WaypointSharingUi.selectablePolicies.forEach { policy ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sharing-option-${policy.storedValue}")
                    .padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = policy == current,
                    onClick = { request(policy) },
                )
                Text(
                    text = WaypointSharingUi.badgeFor(policy),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    val target = pending
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Confirm sharing") },
            text = {
                Text(
                    WaypointSharingUi.confirmationMessage(
                        from = current,
                        to = target,
                        waypointName = waypointName,
                    ).orEmpty(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        onPolicySelected(target)
                    },
                    modifier = Modifier.testTag("sharing-confirm-${target.storedValue}"),
                ) {
                    Text("Share anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
