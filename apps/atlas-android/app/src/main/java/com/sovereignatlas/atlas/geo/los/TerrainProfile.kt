// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.los

import com.sovereignatlas.atlas.geo.GeoPoint

/**
 * The outcome of a line-of-sight calculation.
 *
 * WHY THIS IS AN ENUM AND NOT A BOOLEAN. Two engines previously coexisted, and
 * both reported `isVisible: Boolean` plus a nullable `errorMessage`. That shape
 * cannot distinguish "terrain blocks this ray" from "we have no terrain here" —
 * both arrive as `false`. A caller had to string-match an error message to tell a
 * blocked ridge from an absent DEM, and any caller that failed to do so drew a
 * confident "blocked" verdict over ground it had never measured. Phase 9 §9.5
 * forbids exactly that, and it is impossible to honour through a boolean.
 *
 * [BlockedTerrain] and [NoTerrainData] are the pair a boolean collapses.
 */
enum class LoSStatus {
    /** Terrain was measured everywhere and the ray clears it. */
    Clear,

    /** Terrain was measured everywhere and something blocks the ray. */
    BlockedTerrain,

    /** No elevation source is active. Nothing was measured, so nothing is claimed. */
    NoTerrainData,

    /**
     * An elevation source answered, but not everywhere along the path.
     *
     * The calculation ABORTS rather than completing over the gap. The previous
     * engine skipped unmeasured samples via `mapIndexedNotNull`, so a ridge inside
     * a DEM gap was never examined and the result reported CLEAR — the most
     * dangerous possible failure, because it looked like a real answer.
     */
    IncompleteTerrain,

    /** Observer and target are the same point; there is no ray to cast. */
    DegenerateGeometry,

    /** The elevation provider violated its contract. */
    ProviderError,
}

/** Whether the ray is clear, and what blocked it if not. */
data class LineOfSight(
    val status: LoSStatus,
    val blockingPoint: GeoPoint? = null,
    val blockingDistanceMeters: Double? = null,
) {
    /**
     * True only when the ray was measured and found clear.
     *
     * Every other status is false. There is deliberately no `isBlocked`
     * counterpart: the interesting question is never "is it blocked" but "is this
     * answer real", and that is [status].
     */
    val isVisible: Boolean get() = status == LoSStatus.Clear

    /** True when the verdict rests on complete terrain measurement. */
    val isMeasured: Boolean
        get() = status == LoSStatus.Clear || status == LoSStatus.BlockedTerrain
}

/**
 * One sample along the ray.
 *
 * Carries BOTH the terrain and the ray height, which is what makes an obstruction
 * legible: [obstructionMeters] is the vertical gap between them, positive when the
 * terrain is above the ray.
 */
data class ProfilePoint(
    val location: GeoPoint,
    val distanceFromStartMeters: Double,
    val terrainElevationMeters: Double,
    /** Straight chord from observer eye to target eye at this distance. */
    val rayElevationMeters: Double,
    /** Terrain elevation corrected for earth curvature. */
    val correctedTerrainElevationMeters: Double,
    /** Corrected terrain minus ray. Positive means the terrain is in the way. */
    val obstructionMeters: Double,
    /** False when this point, or an earlier one, sits above the ray. */
    val isVisible: Boolean,
)

/** A measured ray, plus the verdict on it. */
data class TerrainProfile(
    val observerLocation: GeoPoint,
    val targetLocation: GeoPoint,
    val observerElevationMeters: Double,
    val targetElevationMeters: Double,
    val points: List<ProfilePoint>,
    val lineOfSight: LineOfSight,
) {
    /** Convenience for callers that only want the boolean. */
    val hasLineOfSight: Boolean get() = lineOfSight.isVisible

    /**
     * Null when the result is not a failure.
     *
     * Retained so a caller can still show a message, but a NON-NULL value always
     * means the verdict is not a terrain finding. Never use it to distinguish
     * outcomes; use [lineOfSight].
     */
    val errorMessage: String? get() = messageFor(lineOfSight.status)
}

/** Human-facing text for a status that is not a terrain finding. */
fun messageFor(status: LoSStatus): String? = when (status) {
    LoSStatus.Clear -> null
    LoSStatus.BlockedTerrain -> null
    LoSStatus.NoTerrainData ->
        "No terrain data — activate a relief (.mbtiles DEM) map, then retry."
    LoSStatus.IncompleteTerrain ->
        "Terrain data does not cover the whole path, so line of sight is undetermined."
    LoSStatus.DegenerateGeometry -> "Observer and target are at the same location."
    LoSStatus.ProviderError -> "The elevation source returned an unusable response."
}

/** Supplies terrain elevation for a list of coordinates; null means "not covered". */
interface ElevationProvider {
    suspend fun getElevations(points: List<Pair<Double, Double>>): List<Double?>
}