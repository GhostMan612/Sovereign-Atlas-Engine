// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.measure

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.parity.GoldenHarness
import com.sovereignatlas.atlas.parity.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun coord(latitude: Double, longitude: Double): AtlasCoordinate {
    return AtlasCoordinate(latitude = latitude, longitude = longitude)
}

final class MeasureStateTest {
    @Test
    fun beginWithFixLabelsGps() {
        val state = MeasureState()
        state.begin(fixA = coord(10.0, 20.0), center = coord(0.0, 0.0))
        assertTrue(state.isActive())
        assertFalse(state.isComplete())
        assertEquals(MeasurePointSource.fix, state.pointASourceOrNull())
        assertEquals("A (GPS): 10.0000, 20.0000", state.snapshot().pointALabel)
    }

    @Test
    fun beginWithoutFixLabelsMapCenter() {
        val state = MeasureState()
        state.begin(fixA = null, center = coord(1.0, 2.0))
        assertEquals(MeasurePointSource.mapCenter, state.pointASourceOrNull())
        assertEquals("A (map center): 1.0000, 2.0000", state.snapshot().pointALabel)
        assertEquals("B: tap the map to set point B", state.snapshot().pointBLabel)
    }

    @Test
    fun setBCompletesWithDistanceAndBearing() {
        val state = MeasureState()
        state.begin(fixA = null, center = coord(0.0, 0.0))
        state.setB(coord(0.0, 1.0))
        assertTrue(state.isComplete())
        assertEquals(111.19508, state.distanceKm()!!, 0.01)
        assertEquals(90.0, state.bearingDeg()!!, 0.5)
        assertEquals("B: 0.0000, 1.0000", state.snapshot().pointBLabel)
    }

    @Test
    fun coincidentBearingIsNull() {
        val state = MeasureState()
        state.begin(fixA = null, center = coord(5.0, 5.0))
        state.setB(coord(5.0, 5.0))
        assertEquals(0.0, state.distanceKm()!!, 0.0)
        assertNull(state.bearingDeg())
        assertEquals("Bearing: undefined", state.snapshot().bearingText)
    }

    @Test
    fun unitsConvertLikeGolden() {
        val vector = GoldenHarness.load("measure", "GEO2-004_units.json")
        val inputs = vector.inputs
        fun input(key: String): Double {
            return (inputs.entries[key] as JsonValue.Num).value
        }
        val expected = vector.expected as JsonValue.Obj
        fun want(key: String): Double {
            return (expected.entries[key] as JsonValue.Num).value
        }
        GoldenHarness.assertGoldenEquals(
            JsonValue.Num(want("feet")),
            JsonValue.Num(input("feet_in") / 0.3048),
            vector.tolerance,
        )
        GoldenHarness.assertGoldenEquals(
            JsonValue.Num(want("miles")),
            JsonValue.Num(input("miles_in") / 1609.344),
            vector.tolerance,
        )
        GoldenHarness.assertGoldenEquals(
            JsonValue.Num(want("nmi")),
            JsonValue.Num(input("nmi_in") / 1852.0),
            vector.tolerance,
        )
        val state = MeasureState()
        state.begin(fixA = null, center = coord(0.0, 0.0))
        state.setB(coord(0.0, 1.0))
        state.setUnit(MeasureUnit.feet)
        val expectedFeet = 111.19508 * 1000.0 / 0.3048
        assertEquals(expectedFeet, state.displayDistance()!!, expectedFeet * 0.001)
    }

    @Test
    fun nauticalMileMatchesGolden() {
        val state = MeasureState()
        state.begin(fixA = null, center = coord(0.0, 0.0))
        state.setB(coord(0.0, 0.016655))
        state.setUnit(MeasureUnit.nauticalMiles)
        assertEquals("nmi", state.unitLabel())
    }

    @Test
    fun formatStringsMatchFlutter() {
        assertEquals("12.5 m", formatMeasure(12.45, MeasureUnit.meters))
        assertEquals("1.11 km", formatMeasure(1.11195, MeasureUnit.kilometers))
        assertEquals("1.00 mi", formatMeasure(1.0, MeasureUnit.miles))
        assertEquals("5280.0 ft", formatMeasure(5280.04, MeasureUnit.feet))
        assertEquals("1.00 nmi", formatMeasure(1.0, MeasureUnit.nauticalMiles))
        assertEquals("BRG 090°", formatBearing(90.0))
        assertEquals("BRG 005°", formatBearing(5.0))
    }

    @Test
    fun labelsMatchFlutter() {
        assertEquals("m", labelOf(MeasureUnit.meters))
        assertEquals("km", labelOf(MeasureUnit.kilometers))
        assertEquals("mi", labelOf(MeasureUnit.miles))
        assertEquals("ft", labelOf(MeasureUnit.feet))
        assertEquals("nmi", labelOf(MeasureUnit.nauticalMiles))
    }

    @Test
    fun clearResets() {
        val state = MeasureState()
        state.begin(fixA = coord(1.0, 1.0), center = coord(0.0, 0.0))
        state.setB(coord(2.0, 2.0))
        state.clear()
        assertFalse(state.isActive())
        assertFalse(state.isComplete())
        assertNull(state.distanceKm())
        assertEquals("A: waiting", state.snapshot().pointALabel)
        assertEquals("Distance: —", state.snapshot().distanceText)
    }

    @Test
    fun listenersNotified() {
        val state = MeasureState()
        var count = 0
        state.addListener { count++ }
        state.begin(fixA = null, center = coord(0.0, 0.0))
        state.setB(coord(0.0, 1.0))
        state.setUnit(MeasureUnit.kilometers)
        state.clear()
        assertEquals(4, count)
    }
}
