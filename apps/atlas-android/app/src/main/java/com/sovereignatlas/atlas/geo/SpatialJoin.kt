// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

/**
 * Spatial joins between polygons and point sets.
 *
 * Every method returns a matched/unmatched split rather than only the matches. A
 * caller that gets matches alone cannot tell "nothing matched" from "the join was
 * never run", which is how a silently empty result gets rendered as an authoritative
 * statement that no contacts are in range.
 *
 * The index is a latitude-banded grid. A brute-force scan is O(n*m) and the Phase
 * 11 exit checklist asks for a stable API rather than a specific acceleration, so
 * the band count is deliberately coarse: it exists to make a whole-polygon check
 * cheap, not to pretend to be an R-tree.
 */
class SpatialJoin private constructor(
    private val cellDegrees: Double,
    private val byCell: Map<Pair<Int, Int>, MutableList<Int>>,
    private val points: List<AtlasCoordinate>,
) {

    /**
     * Point-in-polygon, partitioned by the polygon.
     *
     * Each result lists the points inside that polygon, plus the points no polygon
     * claimed. Duplicates are preserved: the same contact inside two overlapping
     * parcels is inside both, and dropping one would hide an overlap.
     */
    fun joinPointsToPolygons(polygons: List<Polygon>): SpatialJoinResult {
        val matches = polygons.associateWith { mutableListOf<AtlasCoordinate>() }
        val claimed = HashSet<Int>()

        for (pointIndex in points.indices) {
            val point = points[pointIndex]
            for (polygon in polygons) {
                if (polygon.contains(point)) {
                    matches.getValue(polygon).add(point)
                    claimed.add(pointIndex)
                }
            }
        }
        return SpatialJoinResult(
            matches = matches.mapValues { it.value.toList() },
            unmatched = points.filterIndexed { index, _ -> index !in claimed },
        )
    }

    /**
     * Index-assisted containment for one polygon.
     *
     * Only points inside the polygon's bounding box can be inside the polygon, so
     * the band scan skips the rest. Falls back to a full scan when the polygon
     * exceeds [MAX_BANDED_FRACTION] of the grid, where the index would cost more
     * than it saves.
     */
    fun pointsWithin(polygon: Polygon): List<AtlasCoordinate> {
        // Only a bounding-box pre-filter: a point outside the box cannot be inside,
        // and a point inside the box still goes through the real containment test.
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        for (vertex in polygon.exterior.vertices) {
            minLat = minOf(minLat, vertex.latitude)
            maxLat = maxOf(maxLat, vertex.latitude)
            minLon = minOf(minLon, vertex.longitude)
            maxLon = maxOf(maxLon, vertex.longitude)
        }
        if (minLat > maxLat) return emptyList()

        // A polygon spanning most of the grid costs more to band than to scan.
        val latCells = ((maxLat - minLat) / cellDegrees).toInt() + 1
        if (latCells > MAX_BANDED_CELLS) {
            return points.filter { polygon.contains(it) }
        }

        val result = ArrayList<AtlasCoordinate>()
        val seen = HashSet<Int>()
        val startLat = (minLat / cellDegrees).toInt()
        val startLon = (minLon / cellDegrees).toInt()
        val lonCells = ((maxLon - minLon) / cellDegrees).toInt() + 1

        for (latOffset in 0..latCells) {
            for (lonOffset in 0..lonCells) {
                val cell = Pair(startLat + latOffset, startLon + lonOffset)
                for (index in byCell[cell].orEmpty()) {
                    if (!seen.add(index)) continue
                    val point = points[index]
                    if (polygon.contains(point)) result.add(point)
                }
            }
        }
        return result
    }

    /** Points within [distanceMeters] of any part of [polygon]. */
    fun pointsWithinDistance(
        polygon: Polygon,
        distanceMeters: Double,
    ): List<AtlasCoordinate> = points.filter {
        polygon.withinDistanceOf(it, distanceMeters)
    }

    /** How many indexed cells the join occupies. Diagnostic. */
    fun cellCount(): Int = byCell.size

    companion object {
        /** Above this many latitude cells, scan instead of banding. */
        private const val MAX_BANDED_CELLS = 512

        /**
         * Builds an index over [points].
         *
         * Rejects a non-positive or non-finite cell size by falling back to a
         * single band rather than throwing: a degenerate index must still answer
         * correctly, only slowly.
         */
        fun of(points: List<AtlasCoordinate>, cellDegrees: Double = 0.1): SpatialJoin {
            val cell = if (!cellDegrees.isFinite() || cellDegrees <= 0.0) 360.0 else cellDegrees
            val byCell = HashMap<Pair<Int, Int>, MutableList<Int>>()
            points.forEachIndexed { index, point ->
                if (!point.latitude.isFinite() || !point.longitude.isFinite()) return@forEachIndexed
                val key = Pair(
                    (point.latitude / cell).toInt(),
                    (point.longitude / cell).toInt(),
                )
                byCell.getOrPut(key) { mutableListOf() }.add(index)
            }
            return SpatialJoin(cell, byCell, points.toList())
        }
    }
}

/** The outcome of a spatial join: what matched, and explicitly what did not. */
data class SpatialJoinResult(
    val matches: Map<Polygon, List<AtlasCoordinate>>,
    val unmatched: List<AtlasCoordinate>,
) {
    val matchCount: Int get() = matches.values.sumOf { it.size }
}