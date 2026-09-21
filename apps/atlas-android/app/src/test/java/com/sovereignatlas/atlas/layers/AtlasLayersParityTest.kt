// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.layers

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.core.AtlasId
import com.sovereignatlas.atlas.parity.GoldenHarness
import com.sovereignatlas.atlas.parity.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

final class AtlasLayersParityTest {
    private fun definition(
        id: String,
        attribution: String? = null,
        isPrivate: Boolean = false,
    ): AtlasLayerDefinition {
        return AtlasLayerDefinition(
            id = AtlasId(id),
            kind = AtlasLayerKind.vector,
            providerId = "test-provider",
            category = AtlasLayerCategory.overlay,
            attribution = attribution,
            isPrivate = isPrivate,
        )
    }

    @Test
    fun baselineRanksMatchCanonicalOrder() {
        val vector = GoldenHarness.load("layers", "ORDER-001_canonical.json")
        val expected = vector.expected as JsonValue.Obj
        val ranks = (expected.entries["ranks"] as JsonValue.Arr).items
            .map { (it as JsonValue.Str).value }
        assertEquals(ranks, AtlasBaselineRanks.ranks)
        ranks.forEachIndexed { index, id ->
            assertEquals(index, AtlasBaselineRanks.rankOf(id))
        }
        assertEquals(null, AtlasBaselineRanks.rankOf("not-a-layer"))
    }

    @Test
    fun attributionFollowsVisibleNonPrivate() {
        val vector = GoldenHarness.load("layers", "ATTR-001_visible-set.json")
        val expected = vector.expected as JsonValue.Obj
        val attributed = (expected.entries["attributed"] as JsonValue.Arr).items
            .map { (it as JsonValue.Str).value }.toSet()
        val excluded = (expected.entries["excluded_private"] as JsonValue.Arr).items
            .map { (it as JsonValue.Str).value }
        val states = attributed.map {
            AtlasLayerState(definition = definition(it, attribution = it))
        } + excluded.map {
            AtlasLayerState(
                definition = definition(it, attribution = it, isPrivate = true),
            )
        }
        val stack = AtlasLayerStack(states)
        assertTrue(stack.validate().isValid)
        assertEquals(attributed, AtlasAttribution.forVisible(stack))
    }

    @Test
    fun duplicateIdsRejected() {
        val stack = AtlasLayerStack(
            listOf(
                AtlasLayerState(definition = definition("dup")),
                AtlasLayerState(definition = definition("dup")),
            ),
        )
        val validation = stack.validate()
        assertFalse(validation.isValid)
        assertEquals("DUPLICATE_IDENTITY", validation.rejection?.category)
    }

    @Test
    fun emptyIdRejected() {
        val stack = AtlasLayerStack(
            listOf(AtlasLayerState(definition = definition(""))),
        )
        val validation = stack.validate()
        assertFalse(validation.isValid)
        assertEquals("INVALID_IDENTITY", validation.rejection?.category)
    }

    @Test
    fun toggleAndOpacityBehave() {
        val state = AtlasLayerState(definition = definition("overlay"))
        assertTrue(state.visible)
        assertFalse(state.toggled().visible)
        assertEquals(0.25, state.withOpacity(0.25).opacity, 0.0)
        try {
            state.withOpacity(2.0)
            throw AssertionError("opacity above one must be rejected")
        } catch (error: AtlasContractException) {
            assertEquals("INVALID_LAYER_STATE", error.rejection.category)
        }
    }

    @Test
    fun baselineConformanceDetectsInversion() {
        fun stackOf(vararg ids: String): AtlasLayerStack {
            return AtlasLayerStack(
                ids.map { AtlasLayerState(definition = definition(it)) },
            )
        }
        assertTrue(
            stackOf("raster-sources", "markers", "measurement-overlay-topmost")
                .conformsToBaseline(AtlasBaselineRanks::rankOf),
        )
        assertFalse(
            stackOf("markers", "raster-sources")
                .conformsToBaseline(AtlasBaselineRanks::rankOf),
        )
    }
}
