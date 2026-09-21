// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.measure

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.AtlasLengthUnits
import java.util.Locale

enum class MeasurePointSource { fix, mapCenter }

enum class MeasureUnit { meters, kilometers, miles, feet, nauticalMiles }

fun labelOf(unit: MeasureUnit): String {
    return when (unit) {
        MeasureUnit.meters -> "m"
        MeasureUnit.kilometers -> "km"
        MeasureUnit.miles -> "mi"
        MeasureUnit.feet -> "ft"
        MeasureUnit.nauticalMiles -> "nmi"
    }
}

fun formatMeasure(value: Double, unit: MeasureUnit): String {
    return when (unit) {
        MeasureUnit.meters -> String.format(Locale.US, "%.1f m", value)
        MeasureUnit.kilometers -> String.format(Locale.US, "%.2f km", value)
        MeasureUnit.miles -> String.format(Locale.US, "%.2f mi", value)
        MeasureUnit.feet -> String.format(Locale.US, "%.1f ft", value)
        MeasureUnit.nauticalMiles -> String.format(Locale.US, "%.2f nmi", value)
    }
}

fun formatBearing(degrees: Double): String {
    val rounded = kotlin.math.floor(degrees + 0.5).toLong()
    return "BRG " + rounded.toString().padStart(3, '0') + "°"
}

data class MeasureSnapshot(
    val pointALabel: String?,
    val pointBLabel: String?,
    val distanceText: String?,
    val bearingText: String?,
    val unitLabel: String,
    val isComplete: Boolean,
)

class MeasureState {
    private var pointA: AtlasCoordinate? = null
    private var pointASource: MeasurePointSource? = null
    private var pointB: AtlasCoordinate? = null
    private var unit: MeasureUnit = MeasureUnit.meters
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun pointAOrNull(): AtlasCoordinate? = pointA

    fun pointASourceOrNull(): MeasurePointSource? = pointASource

    fun pointBOrNull(): AtlasCoordinate? = pointB

    fun unit(): MeasureUnit = unit

    fun isActive(): Boolean = pointA != null

    fun isComplete(): Boolean = pointA != null && pointB != null

    fun distanceKm(): Double? {
        val a = pointA ?: return null
        val b = pointB ?: return null
        return AtlasGeoMath.haversineKm(a, b)
    }

    fun bearingDeg(): Double? {
        val a = pointA ?: return null
        val b = pointB ?: return null
        return try {
            AtlasGeoMath.initialBearingDeg(a, b)
        } catch (error: AtlasContractException) {
            null
        }
    }

    fun displayDistance(): Double? {
        val km = distanceKm() ?: return null
        val meters = km * 1000.0
        return when (unit) {
            MeasureUnit.meters -> meters
            MeasureUnit.kilometers -> km
            MeasureUnit.miles -> AtlasLengthUnits.toMiles(meters)
            MeasureUnit.feet -> AtlasLengthUnits.toFeet(meters)
            MeasureUnit.nauticalMiles -> AtlasLengthUnits.toNauticalMiles(meters)
        }
    }

    fun unitLabel(): String = labelOf(unit)

    fun snapshot(): MeasureSnapshot {
        val a = pointA
        val b = pointB
        val distance = displayDistance()
        val bearing = bearingDeg()
        return MeasureSnapshot(
            pointALabel = if (a == null) {
                "A: waiting"
            } else {
                val source = if (pointASource == MeasurePointSource.fix) "GPS" else "map center"
                "A ($source): ${decimalOf(a)}"
            },
            pointBLabel = if (b == null) {
                "B: tap the map to set point B"
            } else {
                "B: ${decimalOf(b)}"
            },
            distanceText = if (distance == null) {
                "Distance: —"
            } else {
                "Distance: ${formatMeasure(distance, unit)}"
            },
            bearingText = if (bearing == null) {
                "Bearing: undefined"
            } else {
                "Bearing: ${formatBearing(bearing)}"
            },
            unitLabel = unitLabel(),
            isComplete = isComplete(),
        )
    }

    fun begin(fixA: AtlasCoordinate?, center: AtlasCoordinate) {
        if (fixA != null) {
            pointA = fixA
            pointASource = MeasurePointSource.fix
        } else {
            pointA = center
            pointASource = MeasurePointSource.mapCenter
        }
        pointB = null
        notifyListeners()
    }

    fun setB(point: AtlasCoordinate) {
        pointB = point
        notifyListeners()
    }

    fun setUnit(next: MeasureUnit) {
        unit = next
        notifyListeners()
    }

    fun clear() {
        pointA = null
        pointASource = null
        pointB = null
        notifyListeners()
    }

    private fun decimalOf(point: AtlasCoordinate): String {
        return String.format(
            Locale.US,
            "%.4f, %.4f",
            point.latitude,
            point.longitude,
        )
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener()
        }
    }
}
