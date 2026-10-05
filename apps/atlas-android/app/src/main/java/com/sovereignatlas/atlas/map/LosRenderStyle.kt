// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.geo.los.LoSStatus

/**
 * How each [LoSStatus] is painted on the map, decided in Kotlin rather than in a
 * style expression.
 *
 * WHY THIS IS NOT A `when` INSIDE `pushLosState`. The previous version was a
 * three-arm `when` collapsing four statuses into one amber. That was the actual
 * defect the Phase 9 work existed to prevent: `LoSStatus` was deliberately split
 * from a boolean precisely so "terrain blocked the ray" could stop meaning the same
 * thing as "nothing was measured", and then the renderer put them back together.
 * A colour mapping that loses a distinction is not a styling preference — it is a
 * false-certainty generator pointed at an operator making a fire mission.
 *
 * THE ORDER OF CERTAINTY IS THE ORDER OF THE TABLE. A map reader scanning these
 * colours should never have to consult a legend to know which end is a fact:
 *
 * - measured and clear        -> green
 * - measured and blocked      -> red
 * - partially measured        -> grey, and DASHED, because the gap is the message
 * - nothing measured at all   -> amber, and it is the only status that says
 *   "go install an offline pack"
 * - the answer is broken      -> muted, because a broken provider is not a finding
 *   about the terrain
 *
 * [UNKNOWN] covers "no calculation has run yet", which is what the ray looks like
 * the moment observer and target are set. It is deliberately distinct from
 * [LoSStatus.NoTerrainData]: "not asked yet" and "asked, and there is no data" are
 * different facts and the operator's next action differs.
 */
enum class LosRenderStyle(
    val lineColor: String,
    val lineWidth: Float,
    /**
     * Dash pattern, or null for a solid line.
     *
     * [Array] of boxed [Float], NOT [FloatArray]. `PropertyFactory.lineDasharray`
     * takes `java.lang.Float[]` — probed, not assumed — and boxed `Float[]` is
     * `Array<Float>` in Kotlin. `FloatArray` is the PRIMITIVE `float[]` and does not
     * match, which the compiler rejects with a bare "none of the candidates is
     * applicable" that says nothing about which of the two array types was meant.
     * Worth recording so the next edit does not repeat it.
     */
    val lineDashArray: Array<Float>?,
) {
    /** Measured everywhere, ray clears. The only unqualified green on this map. */
    CLEAR("#39FF14", 3.0f, null),

    /** Measured everywhere, terrain blocks. Red asserts a terrain finding. */
    BLOCKED("#FF0000", 3.0f, null),

    /**
     * A DEM answered but not everywhere along the path.
     *
     * DASHED, which is the whole point. A solid grey line reads as "measured and
     * nothing there"; a dashed one reads as "this line is partly an assumption". The
     * engine ABORTED over the gap rather than guessing, and the paint says the same
     * thing the verdict said.
     */
    INCOMPLETE("#9E9E9E", 3.0f, arrayOf(6.0f, 4.0f)),

    /**
     * No elevation source. Amber, and deliberately the ONLY amber.
     *
     * This is the status that means an action is available: mount an offline DEM.
     * Sharing a colour with the error states would hide that.
     */
    NO_TERRAIN_DATA("#FFA500", 3.0f, arrayOf(2.0f, 3.0f)),

    /**
     * The geometry was degenerate, or the provider broke its contract.
     *
     * Muted purple-grey, dashed, and thin. Neither red nor amber: a broken provider
     * is not a statement about terrain, and painting it as a terrain finding is the
     * error Phase 9 §9.5 forbids. It must not read as "blocked" and must not read
     * as "you could shoot through this".
     */
    UNRELIABLE("#7A6E9E", 2.0f, arrayOf(3.0f, 3.0f)),

    /** No calculation has run. Thin, so it is visibly a placeholder. */
    UNKNOWN("#4A4A4A", 1.5f, arrayOf(1.0f, 4.0f)),
    ;

    companion object {
        /**
         * The style for a status.
         *
         * [LineOfSight] is null when no profile exists yet, which maps to [UNKNOWN]
         * rather than to a verdict colour. The `when` is written out with no `else`
         * so that ADDING A STATUS TO [LoSStatus] FAILS THIS TO COMPILE. A renderer
         * that silently defaulted a new status would reintroduce exactly the collapse
         * this file was written to end.
         */
        fun forStatus(status: LoSStatus?): LosRenderStyle = when (status) {
            null -> UNKNOWN
            LoSStatus.Clear -> CLEAR
            LoSStatus.BlockedTerrain -> BLOCKED
            LoSStatus.IncompleteTerrain -> INCOMPLETE
            LoSStatus.NoTerrainData -> NO_TERRAIN_DATA
            LoSStatus.ProviderError -> UNRELIABLE
            LoSStatus.DegenerateGeometry -> UNRELIABLE
        }

        /** The human-readable legend for the current verdict. */
        fun captionFor(status: LoSStatus?): String = when (status) {
            null -> "Not calculated"
            LoSStatus.Clear -> "Clear — measured"
            LoSStatus.BlockedTerrain -> "Blocked — measured"
            LoSStatus.IncompleteTerrain -> "Partly unmeasured — gap in DEM"
            LoSStatus.NoTerrainData -> "No terrain data — mount a DEM pack"
            LoSStatus.DegenerateGeometry -> "No ray to cast — same point"
            LoSStatus.ProviderError -> "Elevation provider error"
        }
    }
}
