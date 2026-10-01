// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import kotlinx.coroutines.flow.StateFlow

/**
 * The shared shape of every "what is currently on the net" store.
 *
 * Extracted because there were two of them and they had drifted: `PliStore` and
 * `MarkerStore` both held a uid-keyed map of live tracks, both pruned on a TTL,
 * and `MarkerStore` had already been moved out of `android/`-shaped code by
 * necessity while `PliStore` had not. Two stores, one concept, two sets of bugs.
 *
 * WHY `prune` TAKES THE INSTANT. Both stores previously called
 * `System.currentTimeMillis()` internally, which made expiry untestable except by
 * sleeping: `PliStoreTest` slept 120 ms to prove a 50 ms TTL. The clock is now a
 * parameter and the caller supplies it, so a test states the instant it means and
 * a production caller passes the wall clock once. A decision that silently reads
 * the clock cannot be asserted on, and expiry is a decision a tactical operator
 * will eventually disagree with.
 *
 * WHY THE STORES KEEP THEIR OWN TYPES. [T] is unconstrained because the two hold
 * different things — a `CotPli` and a `CotMarker` — and unifying the payload is a
 * separate change with a wider blast radius. What is shared here is the lifecycle,
 * which was the part that had actually diverged.
 *
 * Implementations MUST mutate through `MutableStateFlow.update`, not by assigning
 * `.value`. Direct assignment is a read-modify-write across two atomic operations
 * and loses concurrent upserts; `update` gives a compare-and-swap retry.
 */
interface ActiveTrackStore<T> {

    /** Live tracks keyed by uid. The map is replaced, never mutated in place. */
    fun observe(): StateFlow<Map<String, T>>

    /**
     * Inserts or replaces a track.
     *
     * Implementations may opportunistically drop already-expired entries while
     * upserting, but only against a caller-supplied instant — never an internal
     * clock read.
     */
    fun upsert(track: T)

    /** Drops every track whose effective expiry is at or before [nowMillis]. */
    fun prune(nowMillis: Long)
}

/**
 * The instant a track stops being current.
 *
 * Producer truth first: [expiresAtMillis] is the CoT `stale` attribute converted to
 * epoch millis, because the sender declared when that report goes out of date and
 * that is a fact. The local TTL is a fallback for tracks that arrived without a
 * `stale` — a guess, labelled as one, and never allowed to overrule the producer.
 *
 * An expiry exactly equal to [nowMillis] counts as expired. A report whose stale
 * time has just passed is no longer current, and keeping it for one more tick would
 * make "expired" mean something different in the store than it does on the wire.
 */
internal fun effectiveExpiry(expiresAtMillis: Long?, observedAtMillis: Long, ttlMillis: Long): Long =
    expiresAtMillis ?: (observedAtMillis + ttlMillis)

/** Whether [nowMillis] has reached this track's effective expiry. */
internal fun isExpiredAt(expiresAtMillis: Long?, observedAtMillis: Long, ttlMillis: Long, nowMillis: Long): Boolean =
    nowMillis >= effectiveExpiry(expiresAtMillis, observedAtMillis, ttlMillis)