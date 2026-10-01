// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

/**
 * What a parsed CoT event should become, decided in the pure tier.
 *
 * Kept separate from the socket so the classification is testable without a
 * device. `AtakMulticastListener` becomes a thin dispatcher: ask this what the
 * message is, then hand it to the matching store.
 */
sealed interface CotIngestDecision {
    data class UpsertPli(val pli: CotPli) : CotIngestDecision
    data class UpsertMarker(val marker: CotMarker) : CotIngestDecision

    /**
     * GeoChat. Its payload lives in `__chat` / `chatgrp` / `remarks` detail, which
     * [StringCotParser] does not model, so the adapter hands the original bytes to
     * the legacy byte reader for this one case.
     */
    data object DelegateToLegacyChat : CotIngestDecision

    /** Nothing this node tracks. Dropped silently; an unknown type is not an error. */
    data object Ignore : CotIngestDecision
}

/** The CoT chat type. GeoChat rides the same wire format as tracks but is not one. */
const val GEOCHAT_TYPE = "b-t-f"

/**
 * Classifies a CoT type string.
 *
 * THE TAXONOMY IS NOT "a- IS FRIENDLY". CoT's first letter is AFFILIATION and the
 * second is DIMENSION, and the letters that matter here are the third onward:
 *
 * - `a-f-*` friendly    -> PLI
 * - `a-h-*` hostile     -> MARKER, not a PLI
 * - `a-n-*` neutral     -> MARKER
 * - `a-u-*` unknown     -> MARKER
 * - `b-m-*` (PIR/SAR)   -> MARKER
 * - `b-t-f`             -> GeoChat, not a track at all
 *
 * `a-h-G` is a hostile ground contact. Routing anything starting `a-` into the
 * friendly PLI store would file a hostile as a friendly on the operator's map,
 * which is the kind of error this whole taxonomy exists to prevent. This matches
 * the classification the shipped `AtakPayloadParser` already applies to protobuf,
 * so the XML and protobuf paths now agree instead of drifting.
 */
enum class CotTrackKind { PLI, MARKER, CHAT, UNKNOWN }

/** Classifies this event by its CoT type. */
fun CotEvent.trackKind(): CotTrackKind = when {
    type.startsWith("a-f-") -> CotTrackKind.PLI
    type.startsWith("a-h-") ||
        type.startsWith("a-n-") ||
        type.startsWith("a-u-") ||
        type.startsWith("b-m-") -> CotTrackKind.MARKER
    type == GEOCHAT_TYPE -> CotTrackKind.CHAT
    else -> CotTrackKind.UNKNOWN
}

/**
 * Decides what to do with a parsed event, converting it to the symbol model here
 * so the adapter only routes.
 *
 * [observedAtMillis] is passed rather than read: this is pure, and an ingest path
 * that quietly stamps the wall clock is one that cannot be asserted on.
 */
fun decideIngest(event: CotEvent, observedAtMillis: Long): CotIngestDecision =
    when (event.trackKind()) {
        CotTrackKind.PLI -> CotIngestDecision.UpsertPli(event.toCotPli(observedAtMillis))
        CotTrackKind.MARKER -> CotIngestDecision.UpsertMarker(event.toCotMarker(observedAtMillis))
        CotTrackKind.CHAT -> CotIngestDecision.DelegateToLegacyChat
        CotTrackKind.UNKNOWN -> CotIngestDecision.Ignore
    }