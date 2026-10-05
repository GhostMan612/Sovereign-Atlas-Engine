// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.geo.los.LoSStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The LoS colour mapping, tested as a safety property rather than as a style choice.
 *
 * The defect this replaces collapsed four statuses into one amber. A test that
 * asserted "the colours are the expected hex values" would have passed against the
 * broken three-arm `when` for the two statuses it did handle, and would only have
 * caught the bug by restating the fix. These tests assert the SHAPE of the mapping —
 * that every status is distinguishable from every other, that no unmeasured status
 * borrows a measured status's colour, and that dash is used as a second channel — so
 * they fail against the old code and keep failing if a future edit re-collapses it.
 */
final class LosRenderStyleTest {

    // ------------------------------------------------------------------
    // The defect
    // ------------------------------------------------------------------

    @Test
    fun everyMeasurabilityClassGetsItsOwnColour() {
        // THE assertion that catches the original bug. Four statuses rendered amber;
        // an operator could not tell no-data from a broken provider from a gap in the
        // DEM, and each of those implies a different next action.
        //
        // Distinct per CLASS, not per status: DegenerateGeometry and ProviderError
        // deliberately share UNRELIABLE because neither is a statement about terrain,
        // and captionFor is what separates them. Asserting six distinct colours here
        // would contradict degenerateGeometryAndProviderErrorShareTheUnreliableStyle
        // and would push the code toward giving a broken provider a colour that
        // claims a terrain finding.
        val classes = listOf(
            LoSStatus.Clear,
            LoSStatus.BlockedTerrain,
            LoSStatus.IncompleteTerrain,
            LoSStatus.NoTerrainData,
            LoSStatus.ProviderError,
        )
        val colours = classes.map { LosRenderStyle.forStatus(it).lineColor }
        assertEquals(
            "each measurability class needs its own colour",
            classes.size,
            colours.toSet().size,
        )
    }

    @Test
    fun everyStatusIsDistinguishableByStylePlusCaption() {
        // The complete invariant, and the one that actually holds: what the operator
        // perceives is the line AND its caption together. Every status must be
        // unique on that pair, or two verdicts are indistinguishable and the split is
        // decorative.
        val pairs = LoSStatus.entries.map { status ->
            LosRenderStyle.forStatus(status) to LosRenderStyle.captionFor(status)
        }
        assertEquals(
            "every status must be unique by style + caption",
            pairs.size,
            pairs.toSet().size,
        )
    }

    @Test
    fun theFiveVisualClassesAreDistinguishable() {
        // Clear, Blocked, Incomplete, NoTerrainData, Unreliable, Unknown. Six entries
        // in the enum; every one must paint differently from every other, including
        // the null-verdict placeholder.
        // Six statuses, but only five get a verdict style: DegenerateGeometry and
        // ProviderError deliberately share UNRELIABLE. Plus UNKNOWN for the null
        // verdict. So six distinct painted entries in total.
        val painted = LoSStatus.entries.map { LosRenderStyle.forStatus(it) }.toSet() +
            LosRenderStyle.forStatus(null)
        assertEquals("the mapping must paint six distinct classes", 6, painted.size)
    }

    @Test
    fun anUncalculatedRayIsDistinctFromEveryVerdict() {
        // The old code reached amber for null verdict via the same `else` arm as the
        // four error statuses. "Not asked yet" and "asked, no data" are different
        // facts with different operator responses.
        val unknown = LosRenderStyle.forStatus(null)

        for (status in LoSStatus.entries) {
            assertNotEquals(
                "null verdict must not look like $status",
                unknown,
                LosRenderStyle.forStatus(status),
            )
        }
    }

    // ------------------------------------------------------------------
    // The safety direction: unmeasured must never borrow a measured colour
    // ------------------------------------------------------------------

    @Test
    fun onlyClearIsGreen() {
        // Green is the one unqualified "you can see it" claim on this map. If an
        // error path could reach it, that is the failure this whole mapping exists to
        // prevent.
        val green = LosRenderStyle.forStatus(LoSStatus.Clear).lineColor
        for (status in LoSStatus.entries.filter { it != LoSStatus.Clear }) {
            assertNotEquals(
                "$status must not render as the Clear green",
                green,
                LosRenderStyle.forStatus(status).lineColor,
            )
        }
    }

    @Test
    fun onlyBlockedIsRed() {
        // Red asserts terrain blocked the ray. Painting unmeasured ground red is
        // exactly what Phase 9 §9.5 forbids: a terrain finding nobody made.
        val red = LosRenderStyle.forStatus(LoSStatus.BlockedTerrain).lineColor
        for (status in LoSStatus.entries.filter { it != LoSStatus.BlockedTerrain }) {
            assertNotEquals(
                "$status must not render as the Blocked red",
                red,
                LosRenderStyle.forStatus(status).lineColor,
            )
        }
    }

    @Test
    fun amberMeansOnlyNoTerrainData() {
        // Amber is reserved because it is the one status carrying an operator
        // action: mount a DEM pack. Sharing it with a provider error would hide that.
        // DegenerateGeometry shares UNRELIABLE, so it is excluded by style rather
        // than by name.
        val amber = LosRenderStyle.forStatus(LoSStatus.NoTerrainData).lineColor
        for (status in LoSStatus.entries.filter { it != LoSStatus.NoTerrainData }) {
            assertNotEquals(
                "$status must not render as the NoTerrainData amber",
                amber,
                LosRenderStyle.forStatus(status).lineColor,
            )
        }
    }

    @Test
    fun aProviderErrorLooksLikeNeitherABlockNorAClear() {
        // A broken provider is not a statement about terrain. It must read as neither
        // "blocked" nor "shootable".
        val unreliable = LosRenderStyle.forStatus(LoSStatus.ProviderError)

        assertNotEquals(LosRenderStyle.BLOCKED.lineColor, unreliable.lineColor)
        assertNotEquals(LosRenderStyle.CLEAR.lineColor, unreliable.lineColor)
    }

    @Test
    fun degenerateGeometryAndProviderErrorShareTheUnreliableStyle() {
        // Deliberate. Neither produced a terrain finding, so neither is given a
        // colour that claims one; their difference is in captionFor, which the
        // operator reads, not in the line they are squinting at.
        assertEquals(
            LosRenderStyle.forStatus(LoSStatus.ProviderError),
            LosRenderStyle.forStatus(LoSStatus.DegenerateGeometry),
        )
    }

    // ------------------------------------------------------------------
    // Dash as the second channel
    // ------------------------------------------------------------------

    @Test
    fun measuredVerdictsAreSolid() {
        // A measured answer gets an unbroken line. If Clear were dashed it would
        // read as provisional, and it is the opposite: it is the one answer backed
        // by complete terrain measurement.
        assertNull(LosRenderStyle.forStatus(LoSStatus.Clear).lineDashArray)
        assertNull(LosRenderStyle.forStatus(LoSStatus.BlockedTerrain).lineDashArray)
    }

    @Test
    fun incompleteTerrainIsDashed() {
        // The single most important visual: a gap in the DEM must not look like a
        // measured answer. The engine ABORTED over the gap; a solid line would
        // contradict that.
        val dash = LosRenderStyle.forStatus(LoSStatus.IncompleteTerrain).lineDashArray

        assertTrue("IncompleteTerrain must be dashed", dash != null)
        assertEquals(2, dash!!.size)
    }

    @Test
    fun everyUnmeasuredStatusIsDashed() {
        // Colour alone is a weak channel on a small phone in sunlight, and some
        // colour-vision differences collapse amber and grey toward each other. Dash
        // survives when hue does not.
        for (status in listOf(
            LoSStatus.IncompleteTerrain,
            LoSStatus.NoTerrainData,
            LoSStatus.DegenerateGeometry,
            LoSStatus.ProviderError,
        )) {
            assertTrue("$status must be dashed", LosRenderStyle.forStatus(status).lineDashArray != null)
        }
    }

    @Test
    fun aDashArrayIsWellFormed() {
        // Odd-length dash arrays are rejected by the style spec and silently drop the
        // layer's paint, which would reproduce the invisible-ray bug in a new place.
        for (style in LosRenderStyle.entries) {
            val dash = style.lineDashArray ?: continue
            assertEquals(
                "${style.name} dash array must have even length",
                0,
                dash.size % 2,
            )
            assertTrue(
                "${style.name} dash array must be positive",
                dash.all { it > 0f },
            )
        }
    }

    @Test
    fun theDashArrayTypeIsTheBoxedOneMapLibreActuallyTakes() {
        // `PropertyFactory.lineDasharray` takes `java.lang.Float[]` - boxed. A
        // primitive FloatArray does not match and the compiler says only "none of the
        // candidates is applicable". Pinned so a future edit does not reintroduce it.
        val dash: Array<Float> = LosRenderStyle.forStatus(LoSStatus.IncompleteTerrain)
            .lineDashArray!!
        assertTrue(dash.isNotEmpty())
        assertTrue(dash.all { it > 0f })
    }

    @Test
    fun aDashArrayFitsInFourSlots() {
        // MapLibre accepts a dash array of arbitrary length, but a long one on a 3px
        // line turns into a solid smear at low zoom, which defeats the purpose.
        for (style in LosRenderStyle.entries) {
            val dash = style.lineDashArray ?: continue
            assertTrue("${style.name} dash array is too long", dash.size <= 4)
        }
    }

    // ------------------------------------------------------------------
    // Captions
    // ------------------------------------------------------------------

    @Test
    fun everyStatusHasACaptionThatIsNotBlank() {
        assertTrue(LosRenderStyle.captionFor(null).isNotBlank())
        for (status in LoSStatus.entries) {
            assertTrue("$status caption is blank", LosRenderStyle.captionFor(status).isNotBlank())
        }
    }

    @Test
    fun measuredCaptionsSayMeasured() {
        // "Clear" alone would not distinguish "measured clear" from "presumed clear",
        // which is the distinction this mapping exists to protect.
        assertTrue(LosRenderStyle.captionFor(LoSStatus.Clear).contains("measured"))
        assertTrue(LosRenderStyle.captionFor(LoSStatus.BlockedTerrain).contains("measured"))
    }

    @Test
    fun noTerrainDataNamesTheOperatorAction() {
        val caption = LosRenderStyle.captionFor(LoSStatus.NoTerrainData)

        assertTrue("must tell the operator what to do", caption.contains("DEM"))
    }

    @Test
    fun incompleteTerrainSaysThereIsAGap() {
        assertTrue(
            LosRenderStyle.captionFor(LoSStatus.IncompleteTerrain).contains("unmeasured"),
        )
    }

    @Test
    fun anUncalculatedRaySaysSo() {
        assertEquals("Not calculated", LosRenderStyle.captionFor(null))
    }

    @Test
    fun degenerateGeometryExplainsItself() {
        // "same point" — an operator who sees this needs to know the fix is to move
        // one of the two taps, not to suspect the engine.
        assertTrue(
            LosRenderStyle.captionFor(LoSStatus.DegenerateGeometry).contains("same point"),
        )
    }

    @Test
    fun captionsAreDistinctForEveryStatus() {
        val captions = LoSStatus.entries.map { LosRenderStyle.captionFor(it) }
        assertEquals(captions.size, captions.toSet().size)
    }

    // ------------------------------------------------------------------
    // Coverage of the enum
    // ------------------------------------------------------------------

    @Test
    fun everyStatusHasACaptionAndAStyle() {
        // forStatus is exhaustive by construction (no else branch), so this is really
        // a check that the enum has not gained a value whose rendering was never
        // considered. It fails loudly rather than silently defaulting to something.
        for (status in LoSStatus.entries) {
            assertTrue(
                "$status has no render style",
                LosRenderStyle.forStatus(status) != LosRenderStyle.UNKNOWN,
            )
            assertTrue(
                "$status has no caption",
                LosRenderStyle.captionFor(status).isNotBlank(),
            )
        }
    }

    @Test
    fun noStatusFallsThroughToTheUnknownPlaceholder() {
        // A null verdict legitimately renders UNKNOWN. A real verdict doing so would
        // mean forStatus had started defaulting, which is the silent-collapse bug in
        // a new coat.
        for (status in LoSStatus.entries) {
            assertNotEquals(
                "$status must not render as the uncalculated placeholder",
                LosRenderStyle.UNKNOWN,
                LosRenderStyle.forStatus(status),
            )
        }
    }
}
