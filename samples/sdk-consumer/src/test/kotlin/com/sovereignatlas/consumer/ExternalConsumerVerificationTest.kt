// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.consumer

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.core.knowledge.ConfidenceScore
import com.sovereignatlas.atlas.core.knowledge.EntityType
import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier
import com.sovereignatlas.atlas.core.knowledge.SourceCitation
import com.sovereignatlas.atlas.core.knowledge.TemporalInterval
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.navigation.RoadClass
import com.sovereignatlas.atlas.geo.navigation.RoadNetworkGraph
import com.sovereignatlas.atlas.geo.navigation.RoadRouteRequest
import com.sovereignatlas.atlas.geo.navigation.TravelProfile
import com.sovereignatlas.atlas.layers.compositor.LayerAttribution
import com.sovereignatlas.atlas.layers.compositor.LayerType
import com.sovereignatlas.sdk.AtlasSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalConsumerVerificationTest {

    @Test
    fun externalConsumerCanVerifySdkVersion() {
        assertEquals("1.0.0", AtlasSdk.VERSION)
    }

    @Test
    fun externalConsumerCanComputeSpatialMetrics() {
        val denver = AtlasCoordinate(39.7392, -104.9903)
        val boulder = AtlasCoordinate(40.0150, -105.2705)

        val distanceMeters = AtlasSdk.Spatial.distanceMeters(denver, boulder)
        assertTrue(distanceMeters in 38000.0..42000.0)

        val bearing = AtlasSdk.Spatial.bearingDegrees(denver, boulder)
        assertTrue(bearing in 310.0..330.0)

        val mgrs = AtlasSdk.Spatial.toMgrs(denver)
        assertTrue(mgrs.startsWith("13S"))
    }

    @Test
    fun externalConsumerCanPlanRoadRoute() {
        val graph = RoadNetworkGraph.Builder()
            .addNode(1L, 37.7749, -122.4194)
            .addNode(2L, 37.7755, -122.4180)
            .addEdge(101L, 1L, 2L, 150.0, 50.0, "Market St", RoadClass.PRIMARY)
            .build()

        val engine = AtlasSdk.Navigation.createRoutingEngine(graph)
        val request = RoadRouteRequest(1L, 2L, TravelProfile.DRIVING)
        val result = AtlasSdk.Navigation.planRoute(engine, request)

        assertTrue(result.isSuccess)
        val route = result.getOrNull()
        assertNotNull(route)
        assertEquals(150.0, route!!.totalDistanceMeters, 0.001)
    }

    @Test
    fun externalConsumerCanComposeLayersAndAttribution() {
        val compositor = AtlasSdk.Layers.createCompositor()
        val topo = AtlasSdk.Layers.createDescriptor(
            id = "topo",
            title = "USGS Topo",
            type = LayerType.BASEMAP,
            defaultZIndex = 1,
            attribution = LayerAttribution("USGS", "Public Domain", "USGS National Map"),
        )
        val roads = AtlasSdk.Layers.createDescriptor(
            id = "roads",
            title = "Road Overlay",
            type = LayerType.VECTOR_OVERLAY,
            defaultZIndex = 10,
            attribution = LayerAttribution("OSM", "ODbL", "© OpenStreetMap"),
        )
        compositor.registerLayer(roads)
        compositor.registerLayer(topo)

        val stack = compositor.getRenderStack()
        assertEquals("topo", stack[0].descriptor.id)
        assertEquals("roads", stack[1].descriptor.id)

        val rollup = compositor.getAttributionRollup()
        assertTrue(rollup.contains("USGS"))
        assertTrue(rollup.contains("OpenStreetMap"))
    }

    @Test
    fun externalConsumerCanQueryHistoricalKnowledge() {
        val index = AtlasSdk.Knowledge.createIndex()
        val dossier = HistoricalDossier(
            id = "fort-point",
            name = "Fort Point",
            entityType = EntityType.STRUCTURE,
            temporal = TemporalInterval(1853, 1914),
            latitude = 37.8106,
            longitude = -122.4770,
            description = "Seacoast fortification",
            confidence = ConfidenceScore.CONFIRMED,
            primaryCitation = SourceCitation("NPS-FP", "HABS CA-1230", "NPS", 1934, "NPS", "Public Domain"),
        )
        index.insert(dossier)

        val box = AtlasBoundingBox(37.80, -122.48, 37.82, -122.46)
        val hits = index.querySpatial(box)
        assertEquals(1, hits.size)
        assertEquals("Fort Point", hits.first().name)
    }

    @Test
    fun externalConsumerCanPersistCompositorStateAndEvaluateWorkspaces() {
        val compositor = AtlasSdk.Layers.createCompositor()
        compositor.registerLayer(
            AtlasSdk.Layers.createDescriptor(
                id = "usgs-3dep",
                title = "USGS 3DEP",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.4f,
                defaultZIndex = 2,
            ),
        )
        compositor.setVisibility("usgs-3dep", true)
        compositor.setOpacity("usgs-3dep", 0.7f)
        val snapshot = compositor.exportStateSnapshot()

        val restored = AtlasSdk.Layers.createCompositor()
        restored.registerLayer(
            AtlasSdk.Layers.createDescriptor(
                id = "usgs-3dep",
                title = "USGS 3DEP",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.4f,
                defaultZIndex = 2,
            ),
        )
        assertEquals(1, restored.restoreStateSnapshot(snapshot))
        assertTrue(restored.getLayer("usgs-3dep")!!.isVisible)
        assertEquals(0.7f, restored.getLayer("usgs-3dep")!!.opacity, 0.001f)
        assertTrue(AtlasSdk.Layers.cleanNavigateWorkspace().isCleanNavigateOnly)
        assertTrue(AtlasSdk.Layers.isOverscaled(20.5, 19))
    }

    @Test
    fun externalConsumerCanUseTacticalMeshDtn() {
        val frame = AtlasSdk.Tactical.encodeMeshtasticFrame("TEST".toByteArray())
        val decoded = AtlasSdk.Tactical.decodeMeshtasticStream(frame)
        assertEquals(1, decoded.frames.size)

        val report = com.sovereignatlas.atlas.tactical.mesh.FragmentAckReport(
            transferId = "xfer-ext",
            round = 1,
            totalChunks = 2,
            contiguousHigh = 1,
            windowBase = 1,
            missing = booleanArrayOf(true),
        )
        val encodedReport = AtlasSdk.Tactical.encodeAckReports(listOf(report))
        val decodedReport = AtlasSdk.Tactical.decodeAckReports(encodedReport)
        assertNotNull(decodedReport)
        assertEquals("xfer-ext", decodedReport!!.first().transferId)

        val store = AtlasSdk.Tactical.createFragmentStore()
        val outcome = store.registerInbound(
            transferId = "xfer-ext",
            chunkIndex = 0,
            totalChunks = 2,
            tier = com.sovereignatlas.atlas.tactical.dtn.FragmentTier.PUBLIC,
            counterpartyId = "node-1",
            expiresAt = 0L,
            frame = "CHUNK0".toByteArray(),
        )
        assertEquals(com.sovereignatlas.atlas.tactical.dtn.RegisterOutcome.STORED, outcome)
    }
}

