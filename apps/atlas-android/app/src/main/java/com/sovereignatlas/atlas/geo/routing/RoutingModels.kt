// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.routing

enum class TrailType(val speedFactor: Double) {
    ROAD(1.00),
    TRAIL(0.90),
    CROSS_COUNTRY(0.65),
    STEEP_OFF_TRAIL(0.45),
}

enum class LoadProfile(val speedMultiplier: Double, val ascentPenaltyMultiplier: Double) {
    SLICK(1.00, 1.00),
    PATROL(1.15, 1.20),
    HEAVY(1.35, 1.50),
    OVERLOAD(1.60, 1.90),
}

data class RoutingRequest(val startNodeId: Int, val endNodeId: Int, val loadProfile: LoadProfile)

data class RoutingResult(
    val path: List<RoutingNode>,
    val estimatedHours: Double,
    val totalDistanceMeters: Double,
    val totalGainMeters: Double,
    val totalDescentMeters: Double,
)
