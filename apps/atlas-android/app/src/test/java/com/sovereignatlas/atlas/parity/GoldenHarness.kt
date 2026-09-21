// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.parity

import kotlin.math.abs
import kotlin.math.max

sealed interface GoldenTolerance {
    data object Exact : GoldenTolerance
    data class Relative(val value: Double) : GoldenTolerance
    data class Absolute(val value: Double, val unit: String) : GoldenTolerance
    data class Fields(val bounds: Map<String, Double>) : GoldenTolerance
}

data class GoldenVector(
    val id: String,
    val contract: String,
    val inputs: JsonValue.Obj,
    val expected: JsonValue,
    val tolerance: GoldenTolerance,
    val status: String,
    val trace: String,
)

object GoldenHarness {
    fun load(category: String, name: String): GoldenVector {
        val path = "/golden/$category/$name"
        val stream = GoldenHarness::class.java.getResourceAsStream(path)
            ?: throw IllegalArgumentException("golden fixture missing: $path")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = GoldenJson.parse(text)
        if (root !is JsonValue.Obj) {
            throw IllegalArgumentException("golden root must be an object: $path")
        }
        fun field(key: String): JsonValue {
            return root.entries[key]
                ?: throw IllegalArgumentException("golden $path lacks $key")
        }
        val id = (field("id") as? JsonValue.Str)?.value
            ?: throw IllegalArgumentException("golden $path id must be a string")
        val contract = (root.entries["contract"] as? JsonValue.Str)?.value
        val inputs = field("inputs") as? JsonValue.Obj
            ?: throw IllegalArgumentException("golden $path inputs must be an object")
        val status = (root.entries["status"] as? JsonValue.Str)?.value ?: "UNKNOWN"
        val trace = (root.entries["trace"] as? JsonValue.Str)?.value ?: ""
        val toleranceRaw = root.entries["tolerance"] ?: JsonValue.Null
        return GoldenVector(
            id = id,
            contract = contract ?: "",
            inputs = inputs,
            expected = field("expected"),
            tolerance = parseTolerance(toleranceRaw),
            status = status,
            trace = trace,
        )
    }

    fun parseTolerance(raw: JsonValue): GoldenTolerance {
        if (raw is JsonValue.Null) return GoldenTolerance.Exact
        if (raw !is JsonValue.Obj) {
            throw IllegalArgumentException("tolerance must be null or an object")
        }
        val unit = (raw.entries["unit"] as? JsonValue.Str)?.value
        val value = (raw.entries["value"] as? JsonValue.Num)?.value
        if (unit != null && value != null) {
            return when (unit) {
                "relative" -> GoldenTolerance.Relative(value)
                else -> GoldenTolerance.Absolute(value, unit)
            }
        }
        if (unit == null && value == null && raw.entries.isNotEmpty()) {
            val bounds = LinkedHashMap<String, Double>()
            for ((key, bound) in raw.entries) {
                bounds[key] = (bound as? JsonValue.Num)?.value
                    ?: throw IllegalArgumentException(
                        "tolerance field $key must be numeric",
                    )
            }
            return GoldenTolerance.Fields(bounds)
        }
        throw IllegalArgumentException("unrecognized tolerance shape $raw")
    }

    fun assertGoldenEquals(
        expected: JsonValue,
        actual: JsonValue,
        tolerance: GoldenTolerance = GoldenTolerance.Exact,
        path: String = "\$",
        leafKey: String? = null,
    ) {
        if (expected is JsonValue.Num && actual is JsonValue.Num) {
            assertNumbers(expected.value, actual.value, tolerance, path, leafKey)
            return
        }
        if (expected::class != actual::class) {
            throw AssertionError("$path type mismatch expected=$expected actual=$actual")
        }
        when (expected) {
            is JsonValue.Obj -> {
                actual as JsonValue.Obj
                for ((key, want) in expected.entries) {
                    val got = actual.entries[key]
                        ?: throw AssertionError("$path missing key $key")
                    assertGoldenEquals(want, got, tolerance, "$path.$key", key)
                }
                for (key in actual.entries.keys) {
                    if (!expected.entries.containsKey(key)) {
                        throw AssertionError("$path unexpected key $key")
                    }
                }
            }
            is JsonValue.Arr -> {
                actual as JsonValue.Arr
                if (expected.items.size != actual.items.size) {
                    throw AssertionError(
                        "$path length mismatch ${expected.items.size} != ${actual.items.size}",
                    )
                }
                expected.items.forEachIndexed { index, want ->
                    assertGoldenEquals(
                        want,
                        actual.items[index],
                        tolerance,
                        "$path[$index]",
                        leafKey,
                    )
                }
            }
            else -> {
                if (expected != actual) {
                    throw AssertionError("$path value mismatch expected=$expected actual=$actual")
                }
            }
        }
    }

    private fun assertNumbers(
        expected: Double,
        actual: Double,
        tolerance: GoldenTolerance,
        path: String,
        leafKey: String?,
    ) {
        val ok = when (tolerance) {
            is GoldenTolerance.Exact -> expected == actual
            is GoldenTolerance.Absolute -> abs(expected - actual) <= tolerance.value
            is GoldenTolerance.Relative -> {
                if (expected == 0.0 && actual == 0.0) {
                    true
                } else {
                    abs(expected - actual) <=
                        tolerance.value * max(abs(expected), abs(actual))
                }
            }
            is GoldenTolerance.Fields -> {
                val bound = leafKey?.let { tolerance.bounds[it] }
                    ?: throw AssertionError(
                        "$path no tolerance bound declared for field",
                    )
                abs(expected - actual) <= bound
            }
        }
        if (!ok) {
            throw AssertionError("$path numeric mismatch expected=$expected actual=$actual")
        }
    }
}
