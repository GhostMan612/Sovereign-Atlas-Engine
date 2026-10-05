// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seam's own invariants — the properties a plugin author relies on, and the ones
 * the audit's findings demand.
 *
 * This is the first file in `geo/` that has no Android, MapLibre, or Compose
 * dependency at all, which is the entire point: Phase 12's Plugin SDK needs a renderer
 * contract a third party can compile against without MapLibre on the classpath.
 */
final class FeatureSinkTest {

    // ------------------------------------------------------------------
    // AtlasLayer is closed
    // ------------------------------------------------------------------

    @Test
    fun everyLayerHasANonBlankSourceAndLayerId() {
        // A blank id would push features at a source that does not exist. The audit
        // found that failure was silent in two of three call paths.
        for (layer in allLayers()) {
            assertTrue("${layer.layerId} has a blank layerId", layer.layerId.isNotBlank())
            assertTrue("${layer.layerId} has a blank sourceId", layer.sourceId.isNotBlank())
        }
    }

    @Test
    fun noTwoLayersShareASource() {
        // Two layers writing one source would overwrite each other on every push, and
        // the symptom — one of them flickering or missing — is very hard to trace.
        val sources = allLayers().map { it.sourceId }
        assertEquals(
            "each layer must own its source",
            sources.size,
            sources.toSet().size,
        )
    }

    @Test
    fun noTwoLayersShareALayerId() {
        val ids = allLayers().map { it.layerId }
        assertEquals("each layer must have its own id", ids.size, ids.toSet().size)
    }

    @Test
    fun theLayerSetCoversEverySourceThatWasEverPushedTo() {
        // The audit counted 28 push sites against 22 sources. Every one of those
        // sources must be reachable through the sealed set, or a live feature set
        // would have no way to be written at all.
        val expected = setOf(
            "atlas-waypoints", "atlas-track", "atlas-scrub", "atlas-measure",
            "atlas-measure-dots", "atlas-graticule", "atlas-rings", "atlas-position",
            "atlas-goto", "atlas-fence", "atlas-mgrs-lines", "atlas-mgrs-labels",
            "atlas-tactical-route", "atlas-tactical-pli", "cot-marker-source",
            "mesh-track-source", "ops-graphics-source", "atlas-los-observer",
            "atlas-los-target", "atlas-los-ray", "atlas-los-block",
            // Still written with MapLibre geometry via replaceRaw; its mapper is the
            // one that has not been converted to pure types yet.
            "historical-patents-source",
        )
        assertEquals(expected, allLayers().map { it.sourceId }.toSet())
    }

    @Test
    fun theSealedSetIsClosedAgainstAccidentalGrowth() {
        // if any (layer: AtlasLayer) branch is added here, the set grew and this
        // fails. That is the mechanism by which "one more layer" stops being free.
        assertEquals(22, allLayers().size)
    }

    // ------------------------------------------------------------------
    // Geometry invariants
    // ------------------------------------------------------------------

    @Test
    fun aLineStringNeedsAtLeastTwoPositions() {
        // The old code could construct a one-point LineString, which the style drops
        // silently. Rejecting it at construction makes the malformed case
        // unrepresentable instead of merely unlikely.
        val error = runCatching {
            RenderGeometry.LineString(listOf(LngLatFixture.ONE))
        }.exceptionOrNull()
        assertTrue("a one-point LineString must be rejected", error is IllegalArgumentException)
    }

    @Test
    fun aTwoPointLineStringIsAccepted() {
        val line = RenderGeometry.LineString(listOf(LngLatFixture.ONE, LngLatFixture.TWO))
        assertEquals(2, line.coordinates.size)
    }

    @Test
    fun aPolygonNeedsAnExteriorRing() {
        val error = runCatching { RenderGeometry.Polygon(emptyList()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun allCoordinatesTraversesEveryShape() {
        // Bounds derivation and hit-testing both need this without caring which shape
        // it is. Re-implementing the when at each call site is how traversal order
        // ends up inconsistent between them.
        assertEquals(1, RenderGeometry.Point(LngLatFixture.ONE).allCoordinates().size)
        assertEquals(
            2,
            RenderGeometry.LineString(listOf(LngLatFixture.ONE, LngLatFixture.TWO))
                .allCoordinates().size,
        )
        assertEquals(
            3,
            RenderGeometry.Polygon(
                listOf(
                    listOf(LngLatFixture.ONE, LngLatFixture.TWO, LngLatFixture.ONE),
                ),
            ).allCoordinates().size,
        )
        assertEquals(
            3,
            RenderGeometry.MultiPolygon(
                listOf(
                    listOf(
                        listOf(LngLatFixture.ONE, LngLatFixture.TWO, LngLatFixture.ONE),
                    ),
                ),
            ).allCoordinates().size,
        )
    }

    @Test
    fun longitudeIsFirstBecauseRfc7946FixesThatOrder() {
        // A reversed pair is a silent, geographically wrong read rather than a
        // compile error. Asserted on the pure type so the conversion cannot drift.
        val at = LngLatFixture.WEST
        assertEquals(-93.265, at.longitude, 0.0)
        assertEquals(44.9778, at.latitude, 0.0)
    }

    // ------------------------------------------------------------------
    // Features and collections
    // ------------------------------------------------------------------

    @Test
    fun anEmptyCollectionIsTheSupportedWayToClearALayer() {
        // Several call sites clear by pushing empty. A sink that treated empty as a
        // no-op would leave a stale layer on screen.
        assertTrue(RenderFeatureCollection.EMPTY.isEmpty)
        assertEquals(0, RenderFeatureCollection.EMPTY.size)
    }

    @Test
    fun pointsHelperBuildsOneFeaturePerItem() {
        val collection = RenderFeatureCollection.points(
            listOf("a" to LngLatFixture.ONE, "b" to LngLatFixture.TWO),
        )

        assertEquals(2, collection.size)
        assertEquals(listOf("a", "b"), collection.features.map { it.id })
    }

    @Test
    fun aFeatureDefaultsToNoProperties() {
        val feature = RenderFeature(RenderGeometry.Point(LngLatFixture.ONE), "id")
        assertTrue(feature.properties.isEmpty())
    }

    @Test
    fun replaceIsASingleMethodAndThatIsTheWholeSeam() {
        // The audit's finding was that 28 call sites each reached for Style to do the
        // same thing. If this interface grows a second method for style installation
        // or paint, the abstraction stops paying for itself.
        val sink = FeatureSink { _, _ -> }
        assertEquals(1, sink::class.java.declaredMethods.count { it.name == "replace" })
    }

    @Test
    fun aSinkCanBeImplementedWithoutAnyMapLibreTypeInScope() {
        // The acceptance criterion for Phase 12. A recording sink is all a plugin
        // needs to assert on what the app wanted to draw, with no renderer present.
        val seen = mutableListOf<Pair<AtlasLayer, Int>>()
        val sink = FeatureSink { layer, features -> seen += layer to features.size }

        sink.replace(AtlasLayer.Waypoints, RenderFeatureCollection.EMPTY)
        sink.replace(AtlasLayer.LosRay, RenderFeatureCollection.points(listOf("r" to LngLatFixture.ONE)))

        assertEquals(listOf(AtlasLayer.Waypoints to 0, AtlasLayer.LosRay to 1), seen)
    }

    private fun allLayers(): List<AtlasLayer> = listOf(
        AtlasLayer.Waypoints, AtlasLayer.Track, AtlasLayer.Scrub, AtlasLayer.Measure,
        AtlasLayer.MeasureDots, AtlasLayer.Graticule, AtlasLayer.RangeRings,
        AtlasLayer.Position, AtlasLayer.GoTo, AtlasLayer.Fence, AtlasLayer.MgrsLines,
        AtlasLayer.MgrsLabels, AtlasLayer.Route, AtlasLayer.Pli, AtlasLayer.Marker,
        AtlasLayer.MeshTrack, AtlasLayer.OpsGraphics, AtlasLayer.AssetParcels,
        AtlasLayer.LosObserver, AtlasLayer.LosTarget, AtlasLayer.LosRay,
        AtlasLayer.LosBlock,
    )
}

/** Named constants so a longitude/latitude swap is visible in a failure message. */
private object LngLatFixture {
    val ONE = com.sovereignatlas.atlas.core.LngLat(longitude = -93.1, latitude = 44.9)
    val TWO = com.sovereignatlas.atlas.core.LngLat(longitude = -93.2, latitude = 45.0)
    val WEST = com.sovereignatlas.atlas.core.LngLat(longitude = -93.265, latitude = 44.9778)
}
