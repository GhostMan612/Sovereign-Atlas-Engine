// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import mil.nga.grid.features.Bounds
import mil.nga.mgrs.grid.Grids
import mil.nga.mgrs.gzd.GridZones
import mil.nga.mgrs.utm.UTM
import kotlin.math.max

data class MgrsGridLine(val coordinates: List<Pair<Double, Double>>)

data class MgrsGridLabel(val latitude: Double, val longitude: Double, val text: String)

data class MgrsGrid(val lines: List<MgrsGridLine>, val labels: List<MgrsGridLabel>)

object MgrsEngine {
    const val MAX_GRID_LINES = 500
    const val MAX_GRID_LABELS = 200
    const val SUPPRESS_ZOOM = 16.0

    // Tactical scope is UTM only: UPS polar regions (>84N, <80S) are
    // unhandled by this overlay configuration.
    fun generate(boundingBox: AtlasBoundingBox, zoom: Double): MgrsGrid {
        val zoomInt = zoom.toInt()
        if (zoom >= SUPPRESS_ZOOM) return MgrsGrid(emptyList(), emptyList())
        val grids = Grids.create()
        val zoomGrids = grids.getGrids(zoomInt) ?: return MgrsGrid(emptyList(), emptyList())
        if (!zoomGrids.hasGrids()) return MgrsGrid(emptyList(), emptyList())
        val domainLines = ArrayList<MgrsGridLine>()
        val domainLabels = ArrayList<MgrsGridLabel>()
        for (part in boundingBox.unwrap()) {
            val ngaBounds = Bounds.degrees(part.west, part.south, part.east, part.north)
            val gridRange = GridZones.getGridRange(ngaBounds) ?: continue
            for (zone in gridRange) {
                for (grid in zoomGrids) {
                val lines = grid.getLines(zoomInt, ngaBounds, zone) ?: emptyList()
                for (line in lines) {
                    val p1 = line.point1
                    val p2 = line.point2
                    val utmStart = UTM.from(p1)
                    val utmEnd = UTM.from(p2)
                    if (utmStart.zone != utmEnd.zone) continue
                    // UTM-space densification only: great-circle interpolation
                    // must never replace this loop (see AtlasGeoMath).
                    val lineLengthMeters = AtlasGeoMath.haversine(
                        p1.latitude,
                        p1.longitude,
                        p2.latitude,
                        p2.longitude,
                    )
                    val densifyInterval = when {
                        lineLengthMeters > 50_000 -> 10_000.0
                        lineLengthMeters > 5_000 -> 1_000.0
                        else -> 200.0
                    }
                    val numSteps = max(1, (lineLengthMeters / densifyInterval).toInt())
                    val densified = ArrayList<Pair<Double, Double>>(numSteps + 1)
                    for (i in 0..numSteps) {
                        val fraction = i.toDouble() / numSteps
                        val easting = utmStart.easting +
                            fraction * (utmEnd.easting - utmStart.easting)
                        val northing = utmStart.northing +
                            fraction * (utmEnd.northing - utmStart.northing)
                        val wgs84 = UTM.create(
                            utmStart.zone,
                            utmStart.hemisphere,
                            easting,
                            northing,
                        ).toPoint()
                        densified.add(wgs84.latitude to wgs84.longitude)
                    }
                    domainLines.add(MgrsGridLine(densified))
                    if (domainLines.size > MAX_GRID_LINES) {
                        return MgrsGrid(emptyList(), emptyList())
                    }
                }
                val ngaLabels = grid.getLabels(zoomInt, ngaBounds, zone) ?: emptyList()
                for (label in ngaLabels) {
                    val point = label.center
                    domainLabels.add(MgrsGridLabel(point.latitude, point.longitude, label.name))
                    if (domainLabels.size > MAX_GRID_LABELS) {
                        domainLabels.clear()
                        break
                    }
                }
                }
            }
        }
        return MgrsGrid(domainLines, domainLabels)
    }
}
