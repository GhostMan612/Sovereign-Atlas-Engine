// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live hostile/marker tracks, keyed by uid.
 *
 * RELOCATED from `android/comms/` to the pure tier. The code never imported
 * anything Android — only `kotlinx.coroutines` and the pure [CotMarker] — so it was
 * pure Kotlin living in an Android package, and the purity scan could not see it.
 * Moving it makes the boundary claim true rather than merely intended.
 *
 * It now implements [ActiveTrackStore] alongside [PliStore]. The two stores had
 * drifted: this one used `update { }` (correct) while `PliStore` assigned `.value`
 * (a lost-update race), and this one pruned on a 24-hour TTL with no producer
 * truth. Both defects are gone.
 *
 * The self-pruning ticker stays. It is scheduling, not policy, so it belongs here
 * rather than in the interface — and it supplies the wall clock to [prune], which
 * is what keeps the decision itself testable.
 */
class MarkerStore(private val scope: CoroutineScope) : ActiveTrackStore<CotMarker> {

    private val _tracks = MutableStateFlow<Map<String, CotMarker>>(emptyMap())

    /** Retained as a named property; [observe] is the interface form. */
    val markerStream: StateFlow<Map<String, CotMarker>> = _tracks.asStateFlow()

    private val pruneJob = scope.launch {
        while (isActive) {
            delay(PRUNE_INTERVAL_MILLIS)
            prune(System.currentTimeMillis())
        }
    }

    override fun observe(): StateFlow<Map<String, CotMarker>> = _tracks.asStateFlow()

    override fun upsert(track: CotMarker) {
        _tracks.update { current -> current + (track.uid to track) }
    }

    override fun prune(nowMillis: Long) {
        _tracks.update { current ->
            current.filterValues { !isExpiredAt(it.expiresAtMillis, it.timestampMillis, TTL_MILLIS, nowMillis) }
        }
    }

    fun shutdown() {
        pruneJob.cancel()
    }

    internal companion object {
        /** Fallback lifetime when a marker arrived without a readable `stale`. */
        const val TTL_MILLIS = 24 * 60 * 60 * 1000L
        const val PRUNE_INTERVAL_MILLIS = 60_000L
    }
}