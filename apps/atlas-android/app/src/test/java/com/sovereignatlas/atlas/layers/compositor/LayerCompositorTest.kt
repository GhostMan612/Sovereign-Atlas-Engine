// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.layers.compositor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerCompositorTest {

    @Test
    fun layerRegistrationAndOpacityClamping() {
        val compositor = LayerCompositor()
        val desc = LayerDescriptor(
            id = "layer-1",
            title = "Topographic Overlay",
            type = LayerType.RASTER_OVERLAY,
            defaultOpacity = 1.5f,
        )

        val state = compositor.registerLayer(desc)
        assertEquals(1.0f, state.opacity, 0.001f)

        compositor.setOpacity("layer-1", -0.5f)
        assertEquals(0.0f, compositor.getLayer("layer-1")!!.opacity, 0.001f)
    }

    @Test
    fun renderStackSortedByZIndex() {
        val compositor = LayerCompositor()
        compositor.registerLayer(
            LayerDescriptor(
                id = "high-z",
                title = "Waypoints",
                type = LayerType.VECTOR_OVERLAY,
                defaultZIndex = 10,
            ),
        )
        compositor.registerLayer(
            LayerDescriptor(
                id = "low-z",
                title = "Basemap",
                type = LayerType.BASEMAP,
                defaultZIndex = 0,
            ),
        )
        compositor.registerLayer(
            LayerDescriptor(
                id = "mid-z",
                title = "Parcels",
                type = LayerType.GEOJSON_OVERLAY,
                defaultZIndex = 5,
            ),
        )

        val stack = compositor.getRenderStack()
        assertEquals(3, stack.size)
        assertEquals("low-z", stack[0].descriptor.id)
        assertEquals("mid-z", stack[1].descriptor.id)
        assertEquals("high-z", stack[2].descriptor.id)
    }

    @Test
    fun zoomAndTemporalFilteringGovernsEffectiveVisibility() {
        val compositor = LayerCompositor()
        compositor.registerLayer(
            LayerDescriptor(
                id = "detailed-parcels",
                title = "Parcels",
                type = LayerType.GEOJSON_OVERLAY,
                zoomRange = ZoomRange(minZoom = 14.0, maxZoom = 22.0),
            ),
        )
        compositor.registerLayer(
            LayerDescriptor(
                id = "historical-1910",
                title = "1910 Survey",
                type = LayerType.HISTORICAL_OVERLAY,
                timeRange = TimeRange(
                    startEpochMs = -1893456000000L,
                    endEpochMs = -1577923200000L,
                ),
            ),
        )

        val lowZoomStack = compositor.getRenderStack(zoom = 10.0)
        assertFalse(lowZoomStack.any { it.descriptor.id == "detailed-parcels" })

        val highZoomStack = compositor.getRenderStack(zoom = 15.0)
        assertTrue(highZoomStack.any { it.descriptor.id == "detailed-parcels" })

        val epoch1905 = -2051222400000L
        val outsideTimeStack = compositor.getRenderStack(currentEpochMs = epoch1905)
        assertFalse(outsideTimeStack.any { it.descriptor.id == "historical-1910" })

        val epoch1915 = -1735689600000L
        val insideTimeStack = compositor.getRenderStack(currentEpochMs = epoch1915)
        assertTrue(insideTimeStack.any { it.descriptor.id == "historical-1910" })
    }

    @Test
    fun attributionRollupAggregatesUniqueSources() {
        val compositor = LayerCompositor()
        compositor.registerLayer(
            LayerDescriptor(
                id = "osm-base",
                title = "OpenStreetMap",
                type = LayerType.BASEMAP,
                attribution = LayerAttribution("OpenStreetMap", "ODbL", "© OpenStreetMap contributors"),
            ),
        )
        compositor.registerLayer(
            LayerDescriptor(
                id = "osm-transport",
                title = "OSM Transport",
                type = LayerType.RASTER_OVERLAY,
                attribution = LayerAttribution("OpenStreetMap", "ODbL", "© OpenStreetMap contributors"),
            ),
        )
        compositor.registerLayer(
            LayerDescriptor(
                id = "usgs-topo",
                title = "USGS Topo",
                type = LayerType.RASTER_OVERLAY,
                attribution = LayerAttribution("USGS", "Public Domain", "USGS National Map"),
            ),
        )

        val rollup = compositor.getAttributionRollup()
        assertTrue(rollup.contains("© OpenStreetMap contributors"))
        assertTrue(rollup.contains("USGS National Map"))
        assertEquals(1, rollup.split("© OpenStreetMap contributors").size - 1)
    }

    @Test
    fun combinationPresetsConfigureSimultaneousMultiLayerStacks() {
        val compositor = LayerCompositor()
        for (id in listOf(
            "blend-usgs-topo",
            "blend-usgs-hillshade",
            "blend-usgs-hydrography",
            "blend-usgs-imagery-topo",
            "blend-openseamap-nautical",
            "blend-osm-standard",
            "blend-carto-positron",
        )) {
            compositor.registerLayer(
                LayerDescriptor(
                    id = id,
                    title = id,
                    type = LayerType.RASTER_OVERLAY,
                    defaultVisibility = false,
                ),
            )
        }
        for (id in listOf("graticule", "mgrs", "rings")) {
            compositor.registerLayer(
                LayerDescriptor(
                    id = id,
                    title = id,
                    type = LayerType.VECTOR_OVERLAY,
                    defaultVisibility = false,
                ),
            )
        }
        for (id in listOf("historical-dossiers", "historical-patents", "historical-blueprints")) {
            compositor.registerLayer(
                LayerDescriptor(
                    id = id,
                    title = id,
                    type = LayerType.HISTORICAL_OVERLAY,
                    defaultVisibility = true,
                ),
            )
        }
        for (id in listOf("waypoints", "track", "measure", "route", "cot-markers", "pli-mesh", "ops-graphics", "fence")) {
            compositor.registerLayer(
                LayerDescriptor(
                    id = id,
                    title = id,
                    type = LayerType.GEOJSON_OVERLAY,
                    defaultVisibility = true,
                ),
            )
        }

        assertEquals(6, AtlasLayerPresets.all.size)
        assertNotNull(AtlasLayerPresets.lookup("satellite-topo-relief"))

        compositor.applyCombinationPreset(AtlasLayerPresets.satelliteTopoRelief)
        assertTrue(compositor.getLayer("blend-usgs-topo")!!.isVisible)
        assertEquals(0.45f, compositor.getLayer("blend-usgs-topo")!!.opacity, 0.001f)
        assertTrue(compositor.getLayer("blend-usgs-hillshade")!!.isVisible)
        assertEquals(0.35f, compositor.getLayer("blend-usgs-hillshade")!!.opacity, 0.001f)
        assertFalse(compositor.getLayer("blend-usgs-hydrography")!!.isVisible)

        compositor.applyCombinationPreset(AtlasLayerPresets.tacticalFullSpectrum)
        assertTrue(compositor.getLayer("blend-openseamap-nautical")!!.isVisible)
        assertTrue(compositor.getLayer("mgrs")!!.isVisible)
        assertTrue(compositor.getLayer("rings")!!.isVisible)
        assertFalse(compositor.getLayer("blend-usgs-topo")!!.isVisible)

        compositor.applyCombinationPreset(AtlasLayerPresets.standardNavigation)
        assertFalse(compositor.getLayer("blend-openseamap-nautical")!!.isVisible)
        assertFalse(compositor.getLayer("mgrs")!!.isVisible)
        assertTrue(compositor.getLayer("waypoints")!!.isVisible)
    }

    @Test
    fun stateSnapshotRoundTripRestoresVisibilityOpacityAndZIndex() {
        val original = LayerCompositor()
        original.registerLayer(
            LayerDescriptor(
                id = "blend-usgs-topo",
                title = "USGS Topo",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.45f,
                defaultZIndex = 4,
            ),
        )
        original.registerLayer(
            LayerDescriptor(
                id = "mgrs",
                title = "MGRS Grid",
                type = LayerType.VECTOR_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.80f,
                defaultZIndex = 20,
            ),
        )
        original.setVisibility("blend-usgs-topo", true)
        original.setOpacity("blend-usgs-topo", 0.65f)
        original.setZIndex("blend-usgs-topo", 12)
        original.setVisibility("mgrs", true)
        original.setOpacity("mgrs", 0.50f)

        val snapshot = original.exportStateSnapshot()

        val restored = LayerCompositor()
        restored.registerLayer(
            LayerDescriptor(
                id = "blend-usgs-topo",
                title = "USGS Topo",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.45f,
                defaultZIndex = 4,
            ),
        )
        restored.registerLayer(
            LayerDescriptor(
                id = "mgrs",
                title = "MGRS Grid",
                type = LayerType.VECTOR_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.80f,
                defaultZIndex = 20,
            ),
        )
        val count = restored.restoreStateSnapshot(snapshot)
        assertEquals(2, count)
        assertTrue(restored.getLayer("blend-usgs-topo")!!.isVisible)
        assertEquals(0.65f, restored.getLayer("blend-usgs-topo")!!.opacity, 0.001f)
        assertEquals(12, restored.getLayer("blend-usgs-topo")!!.zIndex)
        assertTrue(restored.getLayer("mgrs")!!.isVisible)
        assertEquals(0.50f, restored.getLayer("mgrs")!!.opacity, 0.001f)
    }

    @Test
    fun workspaceConfigAndOverscalePolicyEvaluateDeterministically() {
        assertFalse(AtlasWorkspaceConfig.ALL_WORKSPACES.isCleanNavigateOnly)
        assertTrue(AtlasWorkspaceConfig.CLEAN_NAVIGATE.isCleanNavigateOnly)

        assertFalse(OverscalePolicy.isOverscaled(19.0, 19))
        assertTrue(OverscalePolicy.isOverscaled(19.2, 19))
        assertEquals("OVERSCALED (z21.4 > native z19)", OverscalePolicy.formatBadge(21.4, 19))
    }
}

