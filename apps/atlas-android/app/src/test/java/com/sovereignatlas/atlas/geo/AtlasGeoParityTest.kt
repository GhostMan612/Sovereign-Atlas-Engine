// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.parity.GoldenHarness
import com.sovereignatlas.atlas.parity.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class AtlasGeoParityTest {
    private fun coord(pair: JsonValue): AtlasCoordinate {
        val items = (pair as JsonValue.Arr).items
        return AtlasCoordinate(
            latitude = (items[0] as JsonValue.Num).value,
            longitude = (items[1] as JsonValue.Num).value,
        )
    }

    @Test
    fun distanceMatchesEquatorialDegree() {
        val vector = GoldenHarness.load("distance", "DIST-002_equator-1deg.json")
        val expected = vector.expected as JsonValue.Obj
        val valueKm = (expected.entries["value_km"] as JsonValue.Num).value
        val got = AtlasGeoMath.haversineKm(
            AtlasCoordinate(latitude = 0.0, longitude = 0.0),
            AtlasCoordinate(latitude = 0.0, longitude = 1.0),
        )
        GoldenHarness.assertGoldenEquals(
            JsonValue.Num(valueKm),
            JsonValue.Num(got),
            vector.tolerance,
        )
    }

    @Test
    fun bearingsMatchCardinals() {
        val vector = GoldenHarness.load("bearing", "BRG-001_cardinal.json")
        val expected = vector.expected as JsonValue.Obj
        val cases = (expected.entries["cases"] as JsonValue.Arr).items
        assertEquals(4, cases.size)
        for (raw in cases) {
            val kase = raw as JsonValue.Obj
            val from = coord(kase.entries["from"]!!)
            val to = coord(kase.entries["to"]!!)
            val want = (kase.entries["value_deg"] as JsonValue.Num).value
            val got = AtlasGeoMath.initialBearingDeg(from, to)
            GoldenHarness.assertGoldenEquals(
                JsonValue.Num(want),
                JsonValue.Num(got),
                vector.tolerance,
            )
        }
    }

    @Test
    fun normalizationMatchesCases() {
        val vector = GoldenHarness.load("angles", "NORM-001_bearing.json")
        val expected = vector.expected as JsonValue.Obj
        val cases = (expected.entries["cases"] as JsonValue.Arr).items
        for (raw in cases) {
            val kase = raw as JsonValue.Obj
            val input = (kase.entries["in"] as JsonValue.Num).value
            val want = (kase.entries["out"] as JsonValue.Num).value
            assertEquals(want, AtlasAngles.normalizeBearingDeg(input), 0.0)
        }
    }

    @Test
    fun graticuleMatchesUnitBox() {
        val vector = GoldenHarness.load("measure", "GEO2-010_graticule.json")
        val inputs = vector.inputs
        val boxRaw = inputs.entries["box"] as JsonValue.Obj
        val box = AtlasBoundingBox(
            south = (boxRaw.entries["south"] as JsonValue.Num).value,
            west = (boxRaw.entries["west"] as JsonValue.Num).value,
            north = (boxRaw.entries["north"] as JsonValue.Num).value,
            east = (boxRaw.entries["east"] as JsonValue.Num).value,
        )
        assertTrue(box.validate().isValid)
        val interval = (inputs.entries["interval"] as JsonValue.Num).value
        val grid = AtlasGrids.graticuleFor(box, interval)
        val expected = vector.expected as JsonValue.Obj
        GoldenHarness.assertGoldenEquals(
            expected.entries["meridians"]!!,
            JsonValue.Arr(grid.meridians.map { JsonValue.Num(it) }),
            vector.tolerance,
        )
        GoldenHarness.assertGoldenEquals(
            expected.entries["parallels"]!!,
            JsonValue.Arr(grid.parallels.map { JsonValue.Num(it) }),
            vector.tolerance,
        )
    }

    @Test
    fun ringStepsAndCountsMatch() {
        val vector = GoldenHarness.load("tactical", "RING-001_steps.json")
        val expected = vector.expected as JsonValue.Obj
        GoldenHarness.assertGoldenEquals(
            expected.entries["steps"]!!,
            JsonValue.Arr(AtlasRangeRings.steps.map { JsonValue.Num(it) }),
            vector.tolerance,
        )
        val set = AtlasRangeRings.generate(
            AtlasCoordinate(latitude = 44.9778, longitude = -93.265),
            3,
        )
        val ringsPerStep = (expected.entries["rings_per_step"] as JsonValue.Num).value
        val spokes = (expected.entries["spokes"] as JsonValue.Num).value
        assertEquals(ringsPerStep.toInt(), set.rings.size)
        assertEquals(spokes.toInt(), set.spokes.size)
        assertEquals(AtlasRangeRings.VERTICES_PER_RING, set.rings.first().size)
    }

    @Test
    fun nullCenterYieldsEmpty() {
        val vector = GoldenHarness.load("tactical", "RING-002_empty.json")
        assertTrue(AtlasRangeRings.generate(null, -1).isEmpty)
        assertTrue(
            AtlasRangeRings.generate(
                AtlasCoordinate(latitude = 0.0, longitude = 0.0),
                -1,
            ).isEmpty,
        )
        assertEquals("empty", ((vector.expected as JsonValue.Obj).entries["geometry"] as JsonValue.Str).value)
    }

    @Test
    fun earthRadiusPreserved() {
        assertEquals(6371.0088, AtlasGeoMath.REFERENCE_RADIUS_KM, 0.0)
    }
}
