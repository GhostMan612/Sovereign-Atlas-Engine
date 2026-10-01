// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure tier has no `java.time`, so this arithmetic is the only thing standing
 * between a CoT `stale` attribute and a track expiry. A wrong answer here silently
 * keeps a dead track on the map or drops a live one, so the boundaries are pinned.
 */
final class Iso8601Test {

    @Test
    fun parsesTheUnixEpoch() {
        assertEquals(0L, Iso8601.parseToEpochMillis("1970-01-01T00:00:00Z"))
    }

    @Test
    fun parsesAKnownInstant() {
        // 2026-10-01T00:00:00Z = 1790812800 s. Derived from the Unix epoch via
        // 2020-01-01 (1577836800) plus 2192 leap-aware days to 2026-01-01 plus
        // 273 days to October. Cross-checked against .NET, not asserted from memory.
        assertEquals(1_790_812_800_000L, Iso8601.parseToEpochMillis("2026-10-01T00:00:00Z"))
    }

    @Test
    fun parsesFractionalSeconds() {
        assertEquals(
            Iso8601.parseToEpochMillis("2026-10-01T00:00:00Z"),
            Iso8601.parseToEpochMillis("2026-10-01T00:00:00.000Z"),
        )
        // A half second is genuinely half a second later.
        assertEquals(
            Iso8601.parseToEpochMillis("2026-10-01T00:00:00Z")!! + 500L,
            Iso8601.parseToEpochMillis("2026-10-01T00:00:00.500Z"),
        )
        // Sub-millisecond precision truncates rather than rounding up.
        assertEquals(
            Iso8601.parseToEpochMillis("2026-10-01T00:00:01Z"),
            Iso8601.parseToEpochMillis("2026-10-01T00:00:00.999Z")!! + 1L,
        )
    }

    @Test
    fun appliesPositiveAndNegativeOffsets() {
        val utc = Iso8601.parseToEpochMillis("2026-10-01T12:00:00Z")!!

        // 13:00 at +01:00 is the same instant as 12:00Z.
        assertEquals(utc, Iso8601.parseToEpochMillis("2026-10-01T13:00:00+01:00"))
        // 07:00 at -05:00 likewise.
        assertEquals(utc, Iso8601.parseToEpochMillis("2026-10-01T07:00:00-05:00"))
    }

    @Test
    fun treatsAMissingDesignatorAsUtc() {
        // Assuming local time would put a contact an hour off under daylight saving.
        assertEquals(
            Iso8601.parseToEpochMillis("2026-10-01T12:00:00Z"),
            Iso8601.parseToEpochMillis("2026-10-01T12:00:00"),
        )
    }

    @Test
    fun handlesLeapYearsAndLeapDays() {
        assertEquals(
            Iso8601.parseToEpochMillis("2024-02-28T00:00:00Z")!! + 86_400_000L,
            Iso8601.parseToEpochMillis("2024-02-29T00:00:00Z"),
        )
        // 1900 is not a leap year; 2000 is.
        assertNull(Iso8601.parseToEpochMillis("1900-02-29T00:00:00Z"))
        assertEquals(
            Iso8601.parseToEpochMillis("2000-02-28T00:00:00Z")!! + 86_400_000L,
            Iso8601.parseToEpochMillis("2000-02-29T00:00:00Z"),
        )
    }

    @Test
    fun rejectsImpossibleCalendarValues() {
        // A "2026-13-45" silently becoming a date in 2027 would put a track's
        // expiry a year out.
        assertNull(Iso8601.parseToEpochMillis("2026-13-01T00:00:00Z"))
        assertNull(Iso8601.parseToEpochMillis("2026-01-45T00:00:00Z"))
        assertNull(Iso8601.parseToEpochMillis("2026-10-01T24:00:01Z"))
    }

    @Test
    fun rejectsGarbageAndEmptyInput() {
        assertNull(Iso8601.parseToEpochMillis(null))
        assertNull(Iso8601.parseToEpochMillis(""))
        assertNull(Iso8601.parseToEpochMillis("not a timestamp"))
        assertNull(Iso8601.parseToEpochMillis("2026-10-01"))
        assertNull(Iso8601.parseToEpochMillis("2026/10/01T00:00:00Z"))
        assertNull(Iso8601.parseToEpochMillis("2026-10-01T00:00:00.5Q"))
    }
}