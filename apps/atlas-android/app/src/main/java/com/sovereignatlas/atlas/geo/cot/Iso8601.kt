// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

/**
 * Minimal ISO-8601 to epoch-millis conversion, in pure Kotlin.
 *
 * WHY THIS EXISTS RATHER THAN `java.time`. The pure tier carries no `java.time`
 * import. It could not use `Instant.parse` even though that would be less code, and
 * `AtakPayloadParser` currently smuggles `java.time` in through a private helper —
 * which is the only reason that class needs a device to test.
 *
 * WHY NOT `SimpleDateFormat`. Same objection, and it is lenient in exactly the
 * places strictness matters: it accepts malformed input silently, so a corrupt
 * `stale` would become a plausible timestamp rather than a rejection.
 *
 * WHAT IS ACCEPTED. `yyyy-MM-ddTHH:mm:ss`, an optional fractional part, and an
 * optional zone designator of `Z`, `+HH:mm`, or `-HH:mm`. A missing designator is
 * read as UTC, because CoT producers emit UTC and assuming local time would put a
 * contact an hour off during daylight saving — a silent, seasonal, wrong answer.
 * A designator that is present but malformed returns null rather than guessing.
 *
 * Returns null for anything unparseable. Callers treat that as "no producer truth
 * available" and fall back to a local TTL, which is the honest response to a
 * timestamp nobody can read (RULES 2.3: unknown beats invented).
 */
object Iso8601 {

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val MILLIS_PER_MINUTE = SECONDS_PER_MINUTE * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = MINUTES_PER_HOUR * MILLIS_PER_MINUTE
    private const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR

    /**
     * Days since 1970-01-01 for a proleptic-Gregorian civil date.
     *
     * This is the standard days-from-civil algorithm: shift the year so that March
     * starts the year, which removes the leap day from the month-length problem,
     * then count 400/100/4/1-year eras. It is exact for any year in range and has
     * no lookup table to get wrong.
     */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val shiftedYear = year - if (month <= 2) 1 else 0
        val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
        val yearOfEra = shiftedYear - era * 400
        val dayOfYear =
            (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365L + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /** Epoch millis for an ISO-8601 instant, or null when it cannot be read. */
    fun parseToEpochMillis(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.length < 19) return null

        val year = text.substring(0, 4).toIntOrNull() ?: return null
        if (text[4] != '-' || text[7] != '-') return null
        if (text[10] != 'T' && text[10] != 't' && text[10] != ' ') return null

        val month = text.substring(5, 7).toIntOrNull() ?: return null
        val day = text.substring(8, 10).toIntOrNull() ?: return null
        if (text[13] != ':' || text[16] != ':') return null

        val hour = text.substring(11, 13).toIntOrNull() ?: return null
        val minute = text.substring(14, 16).toIntOrNull() ?: return null
        val second = text.substring(17, 19).toIntOrNull() ?: return null

        // Reject impossible calendar values. A "2026-13-45" that silently became a
        // date in 2027 would put a track's expiry a year out.
        if (month !in 1..12) return null
        if (day < 1 || day > daysInMonth(year, month)) return null
        // 24:00:00 is the ISO end-of-day form and is legal; 24:00:01 is not.
        if (hour !in 0..23) return null
        if (minute !in 0..59) return null
        if (second !in 0..60) return null

        var index = 19
        var millis = 0L

        // Optional fractional seconds, truncated to milliseconds. Precision finer
        // than a millisecond is not meaningful for track expiry.
        if (index < text.length && (text[index] == '.' || text[index] == ',')) {
            index++
            val start = index
            while (index < text.length && text[index].isDigit()) index++
            if (index == start) return null
            val digits = text.substring(start, index).take(3).padEnd(3, '0')
            millis = digits.toIntOrNull()?.toLong() ?: 0L
        }

        var offsetMillis = 0L
        if (index < text.length) {
            offsetMillis = parseOffset(text, index) ?: return null
        }

        val days = daysFromCivil(year, month, day)
        return days * MILLIS_PER_DAY +
            hour * MILLIS_PER_HOUR +
            minute * MILLIS_PER_MINUTE +
            second * MILLIS_PER_SECOND +
            millis -
            offsetMillis
    }

    /** Zone suffix starting at [index]. Returns null when malformed. */
    private fun parseOffset(text: String, index: Int): Long? {
        return when (text[index]) {
            'Z', 'z' -> if (index == text.length - 1) 0L else null
            '+', '-' -> {
                if (index + 6 != text.length) return null
                if (text[index + 3] != ':') return null
                val offsetHours = text.substring(index + 1, index + 3).toIntOrNull() ?: return null
                val offsetMinutes = text.substring(index + 4, index + 6).toIntOrNull() ?: return null
                if (offsetHours > 18 || offsetMinutes > 59) return null
                val magnitude = offsetHours * MILLIS_PER_HOUR + offsetMinutes * MILLIS_PER_MINUTE
                // A +05:00 zone is five hours AHEAD of UTC, so UTC = local - offset.
                if (text[index] == '-') -magnitude else magnitude
            }
            else -> null
        }
    }
}