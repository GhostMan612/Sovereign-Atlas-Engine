// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

/**
 * A Cursor-on-Target event as received, before any app-level interpretation.
 *
 * This is deliberately RICHER than [CotPli] and [CotMarker]. Those two carry only
 * what the map needs to draw and expire a symbol; this carries what a tactical C2
 * node needs to reason about a report. In particular it keeps, and they discard:
 *
 * - `ce` (circular error) and `le` (linear error) — the producer's own statement
 *   of positional uncertainty, in metres. A renderer that ignores these draws a
 *   hostile contact with the same confidence as a confirmed one.
 * - `how` — how the position was obtained. "m-g" (GPS) and "h-e" (estimated) are
 *   not the same claim.
 * - `start` and `stale` — the validity window. A contact last seen twenty minutes
 *   ago with an expired `stale` time is not a current position.
 * - `version` — wire version, needed to decode correctly before trusting anything.
 *
 * It is an INPUT DTO, not a replacement for `CotPli`. Conversion happens in the
 * adapter that feeds the map; keeping the two separate is what lets the tactical
 * layer see the full report without widening the symbol model.
 *
 * Timestamps are [String] rather than `java.time` types on purpose. CoT carries
 * ISO-8601 with variable fractional-second precision and a variable zone designator,
 * and the pure tier carries no `java.time` import. The strings are the wire truth;
 * parsing them is the caller's decision, made where a clock exists.
 */
data class CotEvent(
    /** Wire format version, e.g. "2.0". */
    val version: String,
    /** Producer-assigned unique identifier for this object. */
    val uid: String,
    /** CoT type string, e.g. "a-f-G-U-C" for a friendly PLI. */
    val type: String,
    /** How the position was derived, e.g. "m-g" GPS or "h-e" estimated. */
    val how: String,
    /** Time the event was generated (ISO-8601, verbatim). */
    val time: String,
    /** Time the object's validity begins (ISO-8601, verbatim). */
    val start: String,
    /** Time after which the report is stale (ISO-8601, verbatim). */
    val stale: String,
    /** Latitude in decimal degrees. 0.0 is a real coordinate and is NOT rejected. */
    val lat: Double,
    /** Longitude in decimal degrees. 0.0 is a real coordinate and is NOT rejected. */
    val lon: Double,
    /** Height above ellipsoid in metres, when reported. */
    val hae: Double? = null,
    /** Circular error (positional uncertainty) in metres, when reported. */
    val ce: Double? = null,
    /** Linear error (positional uncertainty) in metres, when reported. */
    val le: Double? = null,
    /** Contact callsign, when a `<contact>` element supplied one. */
    val callsign: String? = null,
) {
    /**
     * Whether the report has expired by the given stale instant.
     *
     * Takes the comparison instant as a parameter rather than reading a clock:
     * this type is pure, and a decision that silently depends on wall time is not
     * testable. Comparison stays on [String] because the wire form is ISO-8601 with
     * a fixed "Z" designator in practice, which orders correctly lexicographically —
     * and when it does not, a wrong ordering is a display problem, not a safety one.
     */
    fun isStaleAsOf(nowIso8601: String): Boolean = stale < nowIso8601
}