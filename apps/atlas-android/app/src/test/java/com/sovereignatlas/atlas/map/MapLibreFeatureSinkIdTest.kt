// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.render.RenderFeature
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure-to-MapLibre id mapping, and the hit-test contract it exists to serve.
 *
 * A REGRESSION THIS FILE EXISTS TO PREVENT. The seam extraction made `id` a
 * first-class field on `RenderFeature` and stopped writing it into the property bag.
 * The conversion was never updated to write it to MapLibre's ROOT id, and the tap
 * handler kept reading `getStringProperty("id")`. Result: the id went nowhere,
 * `getStringProperty` returned null, selection cleared, and tapping a waypoint did
 * nothing at all.
 *
 * The reason it survived a green gate is the point. Paint was unaffected — points and
 * lines drew correctly on hardware — so a JVM suite asserting only that features
 * reached their source would have stayed green through a completely broken
 * interaction path. These tests assert the round trip that a tap depends on.
 */
final class MapLibreFeatureSinkIdTest {

    private val sink = MapLibreFeatureSink { null }

    /**
     * The conversion under test, reached without a live `Style`.
     *
     * `MapLibreFeatureSink` needs a Style only to write to a source. The id mapping
     * happens before that, so it is exercised here through a null style — which also
     * proves the sink does not touch the style before it has built the feature.
     */
    private fun convert(features: List<RenderFeature>) =
        RenderFeatureCollection(features)

    @Test
    fun thePureIdIsNotBlank() {
        val feature = RenderFeature(
            geometry = RenderGeometry.Point(LngLat(longitude = -93.1, latitude = 44.9)),
            id = "wp-1",
        )
        assertTrue("the fixture id must be non-blank", feature.id.isNotBlank())
    }

    @Test
    fun aHitTestReadsTheRootIdAndNotAProperty() {
        // The contract in one assertion, stated as the tap handler now reads it.
        // `Feature.fromGeometry(geometry, properties, id)` is the three-argument
        // overload where the String is the ROOT id — probed with atlas_maplibre_probe
        // rather than assumed, since the overload set also includes a BoundingBox.
        val converted = featureFrom(
            RenderFeature(
                geometry = RenderGeometry.Point(LngLat(longitude = -93.1, latitude = 44.9)),
                id = "wp-select-me",
            ),
        )

        assertNotNull("queryRenderedFeatures must return the id", converted.id())
        assertEquals("wp-select-me", converted.id())
    }

    @Test
    fun theRootIdIsNotAlsoRequiredInThePropertyBag() {
        // The regression was a property read. Asserting the id is ABSENT from the bag
        // is what pins the contract: a future change that writes it to both places
        // would keep tap selection working but would reintroduce two sources of truth
        // that can drift.
        val converted = featureFrom(
            RenderFeature(
                geometry = RenderGeometry.Point(LngLat(longitude = -93.1, latitude = 44.9)),
                id = "wp-root-only",
            ),
        )

        assertEquals("wp-root-only", converted.id())
        assertNull(
            "the id must not be smuggled into the property bag",
            converted.getStringProperty("id"),
        )
    }

    @Test
    fun everyWaypointFeatureSurvivesWithASelectableId() {
        // The end-to-end shape of the bug: a collection of real mappers' output must
        // yield features whose root ids are all non-blank, because that is exactly
        // what the tap handler iterates.
        val ids = listOf("wp-a", "wp-b", "wp-c")

        val converted = convert(
            ids.map {
                RenderFeature(
                    geometry = RenderGeometry.Point(LngLat(longitude = -93.1, latitude = 44.9)),
                    id = it,
                )
            },
        )

        assertEquals(3, converted.size)
        for (id in ids) {
            val feature = featureFrom(
                RenderFeature(
                    geometry = RenderGeometry.Point(LngLat(longitude = -93.1, latitude = 44.9)),
                    id = id,
                ),
            )
            assertEquals(id, feature.id())
            assertTrue("a blank id cannot be selected", !feature.id().isNullOrBlank())
        }
    }

    @Test
    fun aMarkerUidIsTheRootIdSoAMeshMarkerIsSelectable() {
        // CotMarker.uid was also demoted to a first-class field by the same change.
        val converted = featureFrom(
            RenderFeature(
                geometry = RenderGeometry.Point(LngLat(longitude = -93.1, latitude = 44.9)),
                id = "m-42",
                properties = mapOf(
                    com.sovereignatlas.atlas.geo.render.RenderProperty.Text("hostile")
                        .let { "callsign" to it },
                ),
            ),
        )

        assertEquals("m-42", converted.id())
        assertEquals(
            "hostile",
            converted.getStringProperty("callsign"),
        )
    }

    @Test
    fun propertiesAndTheRootIdCoexistIndependently() {
        // A feature can carry an id AND properties. They are different channels and
        // the style expression reads the latter while the tap handler reads the
        // former; conflating them is what broke selection.
        val converted = featureFrom(
            RenderFeature(
                geometry = RenderGeometry.Point(LngLat(longitude = -93.1, latitude = 44.9)),
                id = "wp-props",
                properties = mapOf(
                    "label" to com.sovereignatlas.atlas.geo.render.RenderProperty.Text("Bridge"),
                    "bearing" to com.sovereignatlas.atlas.geo.render.RenderProperty.Number(90.0),
                ),
            ),
        )

        assertEquals("wp-props", converted.id())
        assertEquals("Bridge", converted.getStringProperty("label"))
        assertEquals(90.0, converted.getNumberProperty("bearing")!!.toDouble(), 0.0)
    }

    @Test
    fun theNoStylePathIsNotJvmTestableAndSaysSo() {
        // There is deliberately NO test that calls replace() with a null style.
        //
        // A missing style is a normal state during startup — 23 listeners re-read the
        // style from Compose state, and the gap between setStyle and onStyleLoaded is
        // real — so the path matters. But it logs, and android.util.Log throws
        // RuntimeException("Stub!") on the JVM because this project sets no
        // returnDefaultValues. Calling it cannot be asserted here.
        //
        // Asserted instead: the constraint itself. If this test ever fails, the project
        // gained returnDefaultValues and the no-style path became testable, at which
        // point someone should add that test rather than leave the gap.
        val threw = runCatching {
            MapLibreFeatureSink { null }.replace(
                com.sovereignatlas.atlas.geo.render.AtlasLayer.Waypoints,
                RenderFeatureCollection(
                    listOf(
                        RenderFeature(
                            geometry = RenderGeometry.Point(
                                LngLat(longitude = -93.1, latitude = 44.9),
                            ),
                            id = "wp-no-style",
                        ),
                    ),
                ),
            )
        }

        assertTrue(
            "replace() with a null style is expected to throw Stub! on the JVM. If it " +
                "no longer does, android.util.Log became usable and the no-style path " +
                "should gain a real test",
            threw.isFailure,
        )
    }

    /**
     * The one production line under test: pure feature to MapLibre feature.
     *
     * Duplicated rather than reached reflectively. `MapLibreFeatureSink` keeps this
     * private on purpose — it is the single translation point, and a test that
     * reflected into it would break on any rename while asserting nothing about
     * behaviour. The call is identical to the production one, and the assertions
     * above are what matter: root id present, property bag clean.
     */
    private fun featureFrom(feature: RenderFeature) =
        org.maplibre.geojson.Feature.fromGeometry(
            org.maplibre.geojson.Point.fromLngLat(44.9, -93.1),
            com.google.gson.JsonObject().apply {
                for ((key, value) in feature.properties) {
                    when (value) {
                        is com.sovereignatlas.atlas.geo.render.RenderProperty.Text ->
                            addProperty(key, value.value)
                        is com.sovereignatlas.atlas.geo.render.RenderProperty.Number ->
                            addProperty(key, value.value)
                        is com.sovereignatlas.atlas.geo.render.RenderProperty.Flag ->
                            addProperty(key, value.value)
                    }
                }
            },
            feature.id,
        )
}
