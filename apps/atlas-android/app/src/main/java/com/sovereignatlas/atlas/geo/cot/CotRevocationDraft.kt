// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

/**
 * DRAFT, NOT SPEC-VERIFIED. Builds the conventional CoT deletion event for a
 * waypoint that is being withdrawn from the mesh.
 *
 * ============================================================================
 * READ THIS BEFORE TRUSTING THE EVENT TYPE.
 *
 * The operator's brief said "if the CoT spec requires a revocation/tombstone event
 * when sharing stops, draft that logic." It does not. CoT has no mandate for
 * withdrawing a previously-broadcast waypoint, and I could not establish one from
 * the wire format. `t-x-d-d` is ATAK's CONVENTION for a deleted item — the `t-`
 * prefix is the tasking dimension, `x` undimension, and the trailing `d-d` reads as
 * delete — and peers built to ATAK's behaviour generally honour it.
 *
 * So this is a best-effort interoperability gesture, NOT a guaranteed withdrawal.
 * Two consequences the caller must accept:
 *
 * 1. A peer that ignores `t-x-d-d` keeps its copy until that symbol expires on its
 *    own `stale` clock. The tombstone does not reach into another node's store; it
 *    is a message, and messages can be ignored.
 * 2. The audit ledger, not this event, is the durable record of the withdrawal.
 *    [WaypointSharingAudit] is the thing that can answer "did this leave the
 *    device", and it answers it whether or not a peer honoured the wire.
 *
 * Treating this as verified would be exactly the invented-confidence RULES 2.3
 * exists to prevent. It is labelled DRAFT here, in the type name's own
 * documentation, and in the tests, so that whoever verifies it against a real ATAK
 * peer finds it rather than assuming it was checked.
 *
 * ============================================================================
 *
 * WHY IT IS BUILT HERE AND NOT IN THE GENERATORS. `CotGenerator` handles live PLI
 * and GeoChat, both of which are continuously re-broadcast and therefore need no
 * withdrawal. A tombstone is a one-shot, and putting it beside the periodic
 * broadcasts invites someone "simplifying" it into a resend loop. It also carries a
 * different obligation: it must be free of coordinates, which is why its
 * construction takes only a uid.
 *
 * No `java.time` here, deliberately. `Iso8601` in this package parses without it,
 * and this formatter is its exact inverse so the two agree by construction.
 */
object CotRevocationDraft {

    /** The conventional CoT type for a deleted item. See the warning above. */
    const val DELETE_TYPE = "t-x-d-d"

    /**
     * A withdrawal event for [waypointId], or null when the id is unusable.
     *
     * Null rather than a thrown error: this is built from whatever the operator
     * touched, and a blank id must not become a crash on the withdrawal path — the
     * one action taken to protect something.
     *
     * The event carries NO `<point>`. An earlier draft included the last known
     * position so a receiver could clean up a local store, which would have meant
     * the withdrawal transmits the very coordinates it is meant to stop publishing.
     */
    fun buildDeletion(waypointId: String, atMillis: Long): String? {
        val uid = waypointId.trim()
        if (uid.isEmpty()) return null

        val safeUid = escapeXml(uid)
        val time = formatIso8601Utc(atMillis)
        // Same 15-minute window the PLI generator uses. Long enough for a peer on a
        // congested mesh to see it before the symbol ages out anyway.
        val stale = formatIso8601Utc(atMillis + 15 * 60 * 1000L)

        return buildString {
            append("<event version=\"2.0\" uid=\"").append(safeUid)
            append("\" type=\"").append(DELETE_TYPE)
            append("\" time=\"").append(time)
            append("\" start=\"").append(time)
            append("\" stale=\"").append(stale)
            append("\" how=\"m-g\">")
            append("<detail>")
            append("<link uid=\"").append(safeUid)
            append("\" type=\"b-m-p-w\" relation=\"p-p\"/>")
            append("</detail>")
            append("</event>")
        }
    }

    /**
     * The wire form of a withdrawal, for [WaypointRevocationSerializer].
     *
     * Returns an empty array for a blank id. An empty payload is still a value the
     * caller must not transmit, and the router's own `EMPTY_ID` refusal means this
     * branch is unreachable in practice — but a serializer that threw here would
     * turn a malformed row into a crash on the stop-sharing path.
     */
    fun asSerializer(): WaypointRevocationSerializer =
        WaypointRevocationSerializer { waypointId, atMillis ->
            buildDeletion(waypointId, atMillis)?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        }

    /** XML attribute escaping, matching [CotGenerator.escapeXml]. */
    internal fun escapeXml(input: String): String = buildString {
        for (char in input) {
            when {
                char.code < 0x20 && char !in "\t\n\r" -> append('?')
                char == '&' -> append("&amp;")
                char == '<' -> append("&lt;")
                char == '>' -> append("&gt;")
                char == '"' -> append("&quot;")
                char == '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val MILLIS_PER_MINUTE = MINUTES_PER_HOUR * MILLIS_PER_SECOND
    private const val MILLIS_PER_DAY = 24L * 60L * MILLIS_PER_HOUR

    /**
     * Epoch millis to `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`, UTC.
     *
     * The inverse of `Iso8601.parseToEpochMillis`, and written as a civil-date
     * conversion for the same reason that parser is: no `java.time`, and no
     * `SimpleDateFormat` leniency. Formatting a tombstone in local time would put
     * the withdrawal's instant an hour off across a daylight-saving boundary, and
     * the timestamp on a revocation is the part an auditor reads.
     */
    internal fun formatIso8601Utc(epochMillis: Long): String {
        var days = epochMillis / MILLIS_PER_DAY
        val millisOfDay = epochMillis % MILLIS_PER_DAY
        // Floor the day count so pre-epoch instants format as 1969, not 1970.
        if (millisOfDay < 0) {
            days -= 1
        }
        val withinDay = (millisOfDay + MILLIS_PER_DAY) % MILLIS_PER_DAY

        val (year, month, day) = civilFromDays(days)
        val hour = (withinDay / MILLIS_PER_MINUTE) / SECONDS_PER_MINUTE
        val minute = (withinDay / MILLIS_PER_MINUTE) % MINUTES_PER_HOUR
        val second = (withinDay / MILLIS_PER_SECOND) % SECONDS_PER_MINUTE
        val milli = withinDay % MILLIS_PER_SECOND

        return buildString {
            append(year.toString().padStart(4, '0')); append('-')
            append(month.toString().padStart(2, '0')); append('-')
            append(day.toString().padStart(2, '0')); append('T')
            append(hour.toString().padStart(2, '0')); append(':')
            append(minute.toString().padStart(2, '0')); append(':')
            append(second.toString().padStart(2, '0')); append('.')
            append(milli.toString().padStart(3, '0')); append('Z')
        }
    }

    /** Days-since-epoch to a proleptic-Gregorian civil date. */
    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val shifted = days + 719_468
        val era = (if (shifted >= 0) shifted else shifted - 146_096) / 146_097
        val dayOfEra = shifted - era * 146_097
        val yearOfEra =
            (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val year = yearOfEra + era * 400
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val monthPrime = (5 * dayOfYear + 2) / 153
        val day = (dayOfYear - (153 * monthPrime + 2) / 5 + 1)
        val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
        val adjustedYear = if (month <= 2) year + 1 else year
        return Triple(adjustedYear.toInt(), month.toInt(), day.toInt())
    }
}
