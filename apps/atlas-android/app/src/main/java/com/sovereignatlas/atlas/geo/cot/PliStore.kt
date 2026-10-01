// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CotPli(
    val uid: String,
    val type: String,
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val altitude: Double? = null,
    /**
     * Epoch millis at which the producer declared this report stale, from the CoT
     * `stale` attribute. Null when the message carried no readable `stale`, in
     * which case the store falls back to `timestamp + ttlMillis`.
     *
     * Producer truth first: a sender knows when its own report expires and a local
     * TTL is only a guess about someone else's data.
     */
    val expiresAtMillis: Long? = null,
)

interface CotParser {
    fun parse(packetData: ByteArray): ParsedCot?
}

sealed interface ParsedCot {
    data class Pli(val pli: CotPli) : ParsedCot
    data class Chat(val message: ChatMessage) : ParsedCot
    data class Marker(val marker: CotMarker) : ParsedCot
}

/**
 * Live PLI tracks, keyed by uid.
 *
 * Implements [ActiveTrackStore]. Two defects were fixed in the process:
 *
 * 1. Mutations assigned `_activePlis.value = ...`, which is a read-modify-write
 *    across two atomic operations. Two multicast packets arriving on different
 *    threads could interleave and lose one. `update` is a compare-and-swap retry.
 * 2. Expiry read `System.currentTimeMillis()` internally, so it could only be
 *    tested by sleeping. The instant is now a parameter.
 *
 * The `update` filter inside [upsert] is retained deliberately: a receiver that
 * only prunes on its 30 s ticker would hold dead tracks for up to 30 s. It is
 * given the caller's clock so it stays deterministic.
 */
class PliStore(
    private val localDeviceUid: String,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    /**
     * Wall clock, injected so the filter inside [upsert] is testable without
     * sleeping. Defaults to the real clock; production wiring in `AppServices` does
     * not pass it. [prune] takes its instant as an argument instead.
     */
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ActiveTrackStore<CotPli> {

    private val _tracks = MutableStateFlow<Map<String, CotPli>>(emptyMap())

    /** Retained as a named property; [observe] is the interface form. */
    val activePlis: StateFlow<Map<String, CotPli>> = _tracks.asStateFlow()

    override fun observe(): StateFlow<Map<String, CotPli>> = _tracks.asStateFlow()

    override fun upsert(track: CotPli) {
        // Self-echo is dropped before anything else: a node that renders its own
        // track as a friendly is reporting on itself, not on the net.
        if (track.uid == localDeviceUid) return
        val now = clock()
        _tracks.update { current ->
            current.filterValues { !isExpiredAt(it.expiresAtMillis, it.timestamp, ttlMillis, now) } +
                (track.uid to track)
        }
    }

    override fun prune(nowMillis: Long) {
        _tracks.update { current ->
            current.filterValues { !isExpiredAt(it.expiresAtMillis, it.timestamp, ttlMillis, nowMillis) }
        }
    }

    internal companion object {
        const val DEFAULT_TTL_MILLIS = 15 * 60 * 1000L
    }
}