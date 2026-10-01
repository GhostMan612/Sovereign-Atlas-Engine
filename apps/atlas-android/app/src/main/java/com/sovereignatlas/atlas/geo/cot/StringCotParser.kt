// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

/**
 * Pure Kotlin Cursor-on-Target XML reader.
 *
 * WHY THIS EXISTS. The shipped CoT reader, `AtakPayloadParser`, parses with
 * `android.util.Xml`'s pull parser. That is correct for its job but untestable off
 * a device, so none of its behaviour can be proven by the host gate. This parser
 * reads the same wire format with string scanning and zero imports, so every rule
 * below is covered by a JVM unit test.
 *
 * IT IS STANDALONE ON PURPOSE. There is already a `CotParser` interface in this
 * package, and it is the byte-oriented UDP boundary — `parse(packetData:
 * ByteArray)`. This parser takes a [String] and has a different failure surface,
 * so widening that interface to cover it would force the UDP boundary to depend on
 * something it does not need. They meet at the adapter, not here.
 *
 * WHY NOT REGEX FOR THE WHOLE DOCUMENT. Tag BOUNDS are located with [indexOf] and
 * sliced, because a whole-document regex cannot tell `<point>` from a mention of
 * the word inside an attribute value, and cannot bound an unterminated tag. Only
 * attribute EXTRACTION within an already-isolated tag body uses a matcher, which is
 * the part where quoting variation is genuinely the hard part.
 *
 * NULL IS A CONTRACT, NOT AN ERROR. [parse] never throws. It returns null for a
 * document that is not CoT, is truncated mid-tag, or lacks a mandatory attribute.
 * A C2 receiver is fed bytes off a socket by strangers; one malformed message must
 * not be able to take down the node.
 *
 * NULL ISLAND IS VALID. `lat="0.0" lon="0.0"` parses successfully. The older
 * reader in `AtakPayloadParser` rejects any point with a zero coordinate, which
 * discards a real (if rare) report and, worse, teaches operators that a southern
 * ocean position cannot be a contact. That rejection is ended here and is
 * deliberately NOT copied.
 */
object StringCotParser {

    /**
     * Attribute matchers, precompiled once. Built per call instead, a chatty
     * receiver would recompile twelve patterns for every message.
     *
     * `\b` before the name stops `uid` matching inside `guid`, and stops `ce`
     * matching inside a hypothetical `force=`. Both delimiters are accepted
     * because producers in the field emit either.
     */
    private val ATTRIBUTE: Map<String, Regex> = buildMap {
        fun put(name: String) {
            put(name, Regex("\\b$name\\s*=\\s*[\"']([^\"']*)[\"']"))
        }
        listOf("version", "uid", "type", "how", "time", "start", "stale", "lat", "lon", "hae", "ce", "le", "callsign")
            .forEach(::put)
    }

    /** Parses [xml], or returns null when it is not a usable CoT event. */
    fun parse(xml: String): CotEvent? = runCatching { parseUnsafe(xml) }.getOrNull()

    private fun parseUnsafe(xml: String): CotEvent? {
        val eventTag = openTagBody(xml, "event") ?: return null

        // Mandatory per the CoT schema. An attribute present but blank is treated
        // as absent: uid="" identifies nothing, and defaulting it would let a
        // malformed message through as if it were well formed.
        val version = text(eventTag, "version") ?: return null
        val uid = text(eventTag, "uid") ?: return null
        val type = text(eventTag, "type") ?: return null
        val how = text(eventTag, "how") ?: return null
        val time = text(eventTag, "time") ?: return null
        val start = text(eventTag, "start") ?: return null
        val stale = text(eventTag, "stale") ?: return null

        val pointTag = openTagBody(xml, "point") ?: return null
        val lat = number(pointTag, "lat") ?: return null
        val lon = number(pointTag, "lon") ?: return null

        // A coordinate outside these ranges is not a coordinate at all. This is
        // range checking, not a Null Island rule: 0.0 passes, 999.0 does not.
        if (lat < -90.0 || lat > 90.0) return null
        if (lon < -180.0 || lon > 180.0) return null

        // Optional. ce/le/hae that are present but non-numeric are dropped rather
        // than failing the whole event: a bad uncertainty figure is worse than no
        // uncertainty figure, but it says nothing about the position itself.
        val callsign = openTagBody(xml, "contact")?.let { text(it, "callsign") }

        return CotEvent(
            version = version,
            uid = uid,
            type = type,
            how = how,
            time = time,
            start = start,
            stale = stale,
            lat = lat,
            lon = lon,
            hae = number(pointTag, "hae"),
            ce = number(pointTag, "ce"),
            le = number(pointTag, "le"),
            callsign = callsign,
        )
    }

    /**
     * Returns the text between `<name` and its closing `>`, or null when the tag is
     * absent or unterminated.
     *
     * The character after the name must be whitespace, `/` or `>`, so `<event>`
     * is found but `<eventual>` is not. That guard is the whole reason this is not
     * a plain `indexOf("<event")`.
     */
    private fun openTagBody(xml: String, name: String): String? {
        var searchFrom = 0
        while (true) {
            val open = xml.indexOf("<$name", searchFrom)
            if (open < 0) return null
            val after = xml.getOrNull(open + 1 + name.length)
            // A closing tag (</event) never matches here because the search string
            // starts at '<' and the second character of "</event" is '/', not the
            // tag name, so "</event".indexOf("<event") is -1 for that position.
            if (after == null || !(after.isWhitespace() || after == '>' || after == '/')) {
                searchFrom = open + 1
                continue
            }
            val close = xml.indexOf('>', open)
            if (close < 0) return null
            return xml.substring(open + 1 + name.length, close)
        }
    }

    /** Attribute value, or null when absent or blank. */
    private fun text(tagBody: String, name: String): String? =
        ATTRIBUTE[name]?.find(tagBody)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Attribute value as a finite double, or null when absent, blank, non-numeric,
     * or infinite/NaN.
     *
     * The finiteness check matters more than it looks: "NaN" parses successfully as
     * a Double, and a NaN latitude reaching a map renderer is a crash on some
     * backends and a silently dropped marker on others.
     */
    private fun number(tagBody: String, name: String): Double? {
        val raw = text(tagBody, name) ?: return null
        val parsed = raw.trim().toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() }
    }
}