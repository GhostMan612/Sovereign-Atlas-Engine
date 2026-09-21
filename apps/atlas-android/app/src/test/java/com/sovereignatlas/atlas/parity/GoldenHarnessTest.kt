// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.parity

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

final class GoldenHarnessTest {
    private inline fun <reified T : Throwable> failsWith(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError(
                "expected ${T::class.java.name} but caught $error",
            )
        }
        throw AssertionError("expected ${T::class.java.name} but nothing thrown")
    }
    @Test
    fun loadsCameraSerdeVector() {
        val vector = GoldenHarness.load("camera", "CAM-002_serde.json")
        assertEquals("CAM-002", vector.id)
        assertEquals("ATLAS-CORE-CAM-001", vector.contract)
        assertEquals(GoldenTolerance.Exact, vector.tolerance)
        val serialized = (vector.expected as JsonValue.Obj)
            .entries["serialized"] as JsonValue.Str
        assertEquals("39.83|-98.58|3.0|0.0|0.0", serialized.value)
    }

    @Test
    fun exactMatchPasses() {
        val vector = GoldenHarness.load("layers", "ORDER-001_canonical.json")
        GoldenHarness.assertGoldenEquals(
            vector.expected,
            vector.expected,
            vector.tolerance,
        )
    }

    @Test
    fun tamperedValueFails() {
        val vector = GoldenHarness.load("distance", "DIST-002_equator-1deg.json")
        val tampered = when (val root = vector.expected) {
            is JsonValue.Obj -> root.copy(
                entries = root.entries + ("trap" to JsonValue.Num(1.0)),
            )
            else -> root
        }
        failsWith<AssertionError> {
            GoldenHarness.assertGoldenEquals(
                vector.expected,
                tampered,
                vector.tolerance,
            )
        }
    }

    @Test
    fun declaredRelativeToleranceHonored() {
        val vector = GoldenHarness.load("tactical", "RING-001_steps.json")
        val steps = ((vector.expected as JsonValue.Obj)
            .entries["steps"] as JsonValue.Arr).items
            .map { (it as JsonValue.Num).value }
        val drifted = JsonValue.Arr(
            steps.map { JsonValue.Num(it * 1.005) },
        )
        GoldenHarness.assertGoldenEquals(
            JsonValue.Arr(steps.map { JsonValue.Num(it) }),
            drifted,
            vector.tolerance,
        )
        val broken = JsonValue.Arr(
            steps.map { JsonValue.Num(it * 1.05) },
        )
        failsWith<AssertionError> {
            GoldenHarness.assertGoldenEquals(
                JsonValue.Arr(steps.map { JsonValue.Num(it) }),
                broken,
                vector.tolerance,
            )
        }
    }

    @Test
    fun unitToleranceParsesAndPasses() {
        val vector = GoldenHarness.load("distance", "DIST-002_equator-1deg.json")
        assertEquals(GoldenTolerance.Absolute(0.01, "km"), vector.tolerance)
        GoldenHarness.assertGoldenEquals(
            vector.expected,
            vector.expected,
            vector.tolerance,
        )
    }

    @Test
    fun malformedJsonRejected() {
        failsWith<IllegalArgumentException> {
            GoldenJson.parse("""{"open": [1,}""")
        }
    }

    @Test
    fun missingFixtureRejected() {
        failsWith<IllegalArgumentException> {
            GoldenHarness.load("camera", "CAM-999_missing.json")
        }
    }
}
