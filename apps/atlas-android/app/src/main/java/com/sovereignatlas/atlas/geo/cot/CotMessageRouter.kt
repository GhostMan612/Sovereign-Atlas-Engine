// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

/**
 * Decodes the two things a pure Kotlin string scanner cannot decode.
 *
 * Both require machinery that is not pure: wire-format protobuf needs the ATAK
 * codec, and GeoChat lives in `__chat` / `chatgrp` / `remarks` detail elements
 * that [StringCotParser] does not model. The escape hatch is an injected function
 * rather than a dependency, so this file keeps its purity and a JVM test can
 * substitute a fake and assert the fallback was actually taken.
 *
 * Implementations return null for a payload they cannot read. A null here means
 * "dropped", never "thrown": this is fed bytes off a socket by strangers.
 */
fun interface LegacyCotReader {
    fun parseUnreadable(packetData: ByteArray): ParsedCot?
}

/**
 * Routes received CoT datagrams to the right store.
 *
 * WHY THIS EXISTS. The routing was correct but lived inside
 * `AtakMulticastListener`, whose constructor takes `WifiManager` and
 * `PowerManager`. That made it impossible to prove on the JVM that a hostile
 * contact lands in the marker store and a friendly lands in the PLI store — the
 * one fact a tactical map most needs to be right. Moving the decision here makes
 * it provable, and leaves the listener owning only sockets and locks.
 *
 * Routing rules, and why they are not "a- is friendly":
 *
 * - `a-f-*` friendly -> PLI store
 * - `a-h-*` hostile  -> MARKER store. Filing a hostile as a friendly is the error
 *   this class exists to make impossible.
 * - `a-n-*` / `a-u-*` neutral, unknown -> MARKER store
 * - `b-m-*` PIR / SAR -> MARKER store
 * - `b-t-f` GeoChat -> NOT a track; delegated, never a map symbol
 *
 * Protobuf-framed payloads are delegated wholesale, and the result is routed by
 * this class too, so both wire formats obey the same rules instead of drifting
 * apart as they did when the classification was written twice.
 *
 * Pure by construction: no Android imports, and the clock is injected.
 */
class CotMessageRouter(
    private val pliStore: ActiveTrackStore<CotPli>,
    private val markerStore: ActiveTrackStore<CotMarker>,
    private val messageStore: MessageStore,
    private val legacyReader: LegacyCotReader,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Routes one received datagram. Never throws.
     *
     * A malformed or unrecognised payload is dropped. Silence is correct here: on
     * a live mesh the great majority of traffic is not this node's business, and a
     * log line per packet would drown the radio.
     */
    fun route(packetData: ByteArray) {
        if (isProtobufFramed(packetData)) {
            routeParsed(legacyReader.parseUnreadable(packetData))
            return
        }

        val event = StringCotParser.parse(String(packetData, Charsets.UTF_8)) ?: return
        when (val decision = decideIngest(event, clock())) {
            is CotIngestDecision.UpsertPli -> pliStore.upsert(decision.pli)
            is CotIngestDecision.UpsertMarker -> markerStore.upsert(decision.marker)
            // GeoChat detail is not modelled by the pure reader, so this one case
            // costs a second parse. Worth it: chat is not a map symbol.
            is CotIngestDecision.DelegateToLegacyChat ->
                routeParsed(legacyReader.parseUnreadable(packetData))
            is CotIngestDecision.Ignore -> Unit
        }
    }

    /**
     * Drops expired tracks in both stores.
     *
     * Owned here rather than left to each listener so a node with two stores needs
     * one ticker, and so the pruning policy is asserted in one place.
     */
    fun prune(nowMillis: Long) {
        pliStore.prune(nowMillis)
        markerStore.prune(nowMillis)
    }

    private fun routeParsed(parsed: ParsedCot?) {
        when (parsed) {
            is ParsedCot.Pli -> pliStore.upsert(parsed.pli)
            is ParsedCot.Chat -> messageStore.addMessage(parsed.message)
            is ParsedCot.Marker -> markerStore.upsert(parsed.marker)
            null -> Unit
        }
    }

    /**
     * True for the `0xBF 0x01 0xBF` framing that precedes a protobuf payload.
     *
     * Only that one case is diverted. An XML-framed or bare payload goes to the
     * pure reader, which locates `<event` and so ignores any leading header bytes
     * whatever they decode to.
     */
    private fun isProtobufFramed(data: ByteArray): Boolean =
        data.size >= 4 &&
            data[0] == PROTOBUF_MAGIC &&
            data[1] == PROTOBUF_VERSION &&
            data[2] == PROTOBUF_MAGIC

    private companion object {
        const val PROTOBUF_MAGIC = 0xBF.toByte()
        const val PROTOBUF_VERSION = 0x01.toByte()
    }
}