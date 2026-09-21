// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.core.AtlasRejection
import com.sovereignatlas.atlas.core.AtlasValidation
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class AtlasBoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    fun validate(): AtlasValidation {
        for (edge in listOf(south, west, north, east)) {
            if (!edge.isFinite()) {
                return AtlasValidation.invalid(
                    AtlasRejection("NON_FINITE", "Bounds edges must be finite."),
                )
            }
        }
        if (south < -90.0 || south > 90.0 || north < -90.0 || north > 90.0) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "OUT_OF_RANGE",
                    "Bounds latitudes must be within [-90, 90].",
                ),
            )
        }
        if (west < -180.0 || west > 180.0 || east < -180.0 || east > 180.0) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "OUT_OF_RANGE",
                    "Bounds longitudes must be within [-180, 180] (DEC-004).",
                ),
            )
        }
        if (south > north) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "INVALID_GEOMETRY",
                    "Bounds require south <= north.",
                ),
            )
        }
        return AtlasValidation.valid()
    }

    val crossesAntimeridian: Boolean get() = west > east

    fun contains(point: AtlasCoordinate): Boolean {
        if (crossesAntimeridian) {
            throw AtlasContractException(
                AtlasRejection(
                    "UNRESOLVED_ANTIMERIDIAN",
                    "Containment for antimeridian-crossing boxes is undecided (DEC-005).",
                ),
            )
        }
        return point.latitude >= south &&
            point.latitude <= north &&
            point.longitude >= west &&
            point.longitude <= east
    }
}

data class AtlasGraticule(
    val meridians: List<Double>,
    val parallels: List<Double>,
)

object AtlasGrids {
    val zoomSteps: List<Pair<Int, Double>> = listOf(
        2 to 30.0,
        4 to 10.0,
        6 to 5.0,
        8 to 1.0,
        10 to 0.5,
        12 to 0.1,
        14 to 0.05,
        16 to 0.01,
        99 to 0.005,
    )

    fun intervalForZoom(zoom: Int): Double {
        for ((limit, interval) in zoomSteps) {
            if (zoom <= limit) return interval
        }
        return 0.005
    }

    fun graticuleFor(bounds: AtlasBoundingBox, interval: Double): AtlasGraticule {
        return AtlasGraticule(
            meridians = axis(bounds.west, bounds.east, interval),
            parallels = axis(bounds.south, bounds.north, interval),
        )
    }

    private fun axis(min: Double, max: Double, interval: Double): List<Double> {
        val lines = ArrayList<Double>()
        var value = ceil(min / interval) * interval
        while (value <= max + 1e-9) {
            lines.add(String.format(Locale.US, "%.6f", value).toDouble())
            value += interval
        }
        return lines
    }
}

data class AtlasRingSet(
    val center: AtlasCoordinate,
    val radiusKm: Double,
    val rings: List<List<AtlasCoordinate>>,
    val spokes: List<List<AtlasCoordinate>>,
) {
    val isEmpty: Boolean get() = rings.isEmpty() && spokes.isEmpty()

    companion object {
        fun empty() = AtlasRingSet(
            center = AtlasCoordinate(latitude = 0.0, longitude = 0.0),
            radiusKm = 0.0,
            rings = emptyList(),
            spokes = emptyList(),
        )
    }
}

object AtlasRangeRings {
    val steps: List<Double> = listOf(0.1, 0.25, 0.5, 1.0, 2.0, 5.0)

    const val RINGS_PER_STEP = 4
    const val VERTICES_PER_RING = 65

    val ringFractions: List<Double> = listOf(0.25, 0.5, 0.75, 1.0)

    const val METERS_PER_DEGREE_LATITUDE = 110540.0

    fun radiusForStep(stepIndex: Int): Double? {
        if (stepIndex < 0 || stepIndex >= steps.size) return null
        return steps[stepIndex]
    }

    fun generate(center: AtlasCoordinate?, stepIndex: Int): AtlasRingSet {
        val radiusKm = if (center == null) null else radiusForStep(stepIndex)
        if (center == null || radiusKm == null) return AtlasRingSet.empty()
        val rings = ringFractions.map { fraction -> circle(center, radiusKm * fraction) }
        val spokes = listOf(0.0, 90.0, 180.0, 270.0).map { bearing ->
            listOf(center, destination(center, radiusKm, bearing))
        }
        return AtlasRingSet(
            center = center,
            radiusKm = radiusKm,
            rings = rings,
            spokes = spokes,
        )
    }

    private fun circle(center: AtlasCoordinate, radiusKm: Double): List<AtlasCoordinate> {
        val vertices = ArrayList<AtlasCoordinate>(VERTICES_PER_RING)
        for (i in 0 until VERTICES_PER_RING - 1) {
            val bearing = 360.0 * i / (VERTICES_PER_RING - 1)
            vertices.add(destination(center, radiusKm, bearing))
        }
        vertices.add(vertices.first())
        return vertices
    }

    private fun destination(
        center: AtlasCoordinate,
        radiusKm: Double,
        bearingDeg: Double,
    ): AtlasCoordinate {
        val radiusM = radiusKm * 1000.0
        val latRad = center.latitude * Math.PI / 180.0
        val dLat = radiusM *
            cos(bearingDeg * Math.PI / 180.0) /
            METERS_PER_DEGREE_LATITUDE
        val metersPerDegreeLongitude =
            METERS_PER_DEGREE_LATITUDE * max(cos(latRad), 0.01)
        val dLon = radiusM *
            sin(bearingDeg * Math.PI / 180.0) /
            metersPerDegreeLongitude
        return AtlasCoordinate(
            latitude = center.latitude + dLat,
            longitude = center.longitude + dLon,
        )
    }
}
