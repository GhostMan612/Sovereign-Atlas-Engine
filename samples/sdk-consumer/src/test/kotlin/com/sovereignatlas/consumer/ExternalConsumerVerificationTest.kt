// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.consumer

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.core.LandPatentGeoJsonParser
import com.sovereignatlas.atlas.core.knowledge.ConfidenceScore
import com.sovereignatlas.atlas.core.knowledge.EntityType
import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier
import com.sovereignatlas.atlas.core.knowledge.SourceCitation
import com.sovereignatlas.atlas.core.knowledge.TemporalInterval
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.gis.GeoJsonParser
import com.sovereignatlas.atlas.geo.gis.GisFormat
import com.sovereignatlas.atlas.geo.gis.KmlStreamingParser
import com.sovereignatlas.atlas.geo.navigation.RoadClass
import com.sovereignatlas.atlas.geo.navigation.RoadNetworkGraph
import com.sovereignatlas.atlas.geo.navigation.RoadRouteRequest
import com.sovereignatlas.atlas.geo.navigation.TravelProfile
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import com.sovereignatlas.atlas.layers.compositor.LayerAttribution
import com.sovereignatlas.atlas.layers.compositor.LayerType
import com.sovereignatlas.sdk.AtlasSdk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalConsumerVerificationTest {

    @Test
    fun externalConsumerCanVerifySdkVersion() {
        assertEquals("1.0.0", AtlasSdk.VERSION)
    }

    @Test
    fun externalConsumerLoadsPublishedBinaryJarArtifact() {
        val expectedVersion = System.getProperty("atlas.expectedSdkArtifactVersion") ?: "1.1.0"
        val classesToCheck = listOf(
            AtlasSdk::class.java,
            GeoJsonParser::class.java,
            KmlStreamingParser::class.java,
            LandPatentGeoJsonParser::class.java,
        )
        for (clazz in classesToCheck) {
            val location = clazz.protectionDomain?.codeSource?.location
            assertNotNull("Expected CodeSource location for ${clazz.name}", location)
            val path = File(location!!.toURI()).invariantSeparatorsPath
            assertTrue(
                "Expected published atlas-sdk-$expectedVersion.jar for ${clazz.name}, got $path",
                path.endsWith("/atlas-sdk-$expectedVersion.jar"),
            )
            assertFalse(
                "Expected published JAR rather than in-tree build output for ${clazz.name}: $path",
                path.contains("/apps/atlas-android/atlas-sdk/build/"),
            )
        }
    }

    @Test
    fun externalConsumerExcludesAndroidComposeMapLibreAndFirebaseFromClasspath() {
        val forbiddenClasses = listOf(
            "android.content.Context",
            "android.app.Activity",
            "androidx.compose.runtime.Composer",
            "org.maplibre.android.maps.MapLibreMap",
            "com.google.firebase.FirebaseApp",
        )
        for (className in forbiddenClasses) {
            assertThrows(ClassNotFoundException::class.java) {
                Class.forName(className, false, ExternalConsumerVerificationTest::class.java.classLoader)
            }
        }
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

    @Test
    fun externalConsumerCanParseGeoJsonKmlAndLandPatentsFailClosed() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "name": "Trailhead" },
                  "geometry": { "type": "Point", "coordinates": [-105.25, 40.125] }
                }
              ]
            }
        """.trimIndent()
        val geoDataset = GeoJsonParser.parse(geoJson, datasetName = "Trailhead")
        assertEquals(1, geoDataset.features.size)
        val geoPt = geoDataset.features.first().geometry as RenderGeometry.Point
        assertEquals(-105.25, geoPt.coordinates.longitude, 1e-4)
        assertEquals(40.125, geoPt.coordinates.latitude, 1e-4)

        val kml = """
            <kml><Placemark><name>Lookout</name><Point><coordinates>-105.5,40.25</coordinates></Point></Placemark></kml>
        """.trimIndent()
        val kmlDataset = KmlStreamingParser.parseString(kml, datasetName = "Lookout")
        assertEquals(GisFormat.KML, kmlDataset.format)
        assertEquals(1, kmlDataset.features.size)
        assertEquals(RenderProperty.Text("Lookout"), kmlDataset.features.first().properties["name"])

        val patentJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "id": "pat-1",
                  "properties": { "patent_number": "CO-1889", "issue_date": "1889-07-04" },
                  "geometry": { "type": "Point", "coordinates": [-105.1, 40.2] }
                }
              ]
            }
        """.trimIndent()
        val patents = LandPatentGeoJsonParser.parse(patentJson)
        assertEquals(1, patents.size)
        assertEquals("CO-1889", patents.first().patentNumber)
        assertEquals(1889, patents.first().year)
        assertEquals("BLM GLO", patents.first().attribution)

        assertTrue(LandPatentGeoJsonParser.parse("{malformed").isEmpty())
    }

    @Test
    fun externalConsumerCanEncodeAndTrackOfficialMeshtasticProtobufs() {
        val session = com.sovereignatlas.atlas.tactical.mesh.MeshtasticProtocolSession()
        val adapter = com.sovereignatlas.atlas.tactical.mesh.MeshtasticWireAdapter()
        assertEquals(72, com.sovereignatlas.atlas.tactical.mesh.PortNum.PORTNUM_ATAK_PLUGIN)
        assertEquals(5, com.sovereignatlas.atlas.tactical.mesh.PortNum.PORTNUM_ROUTING_APP)

        session.beginConnecting()
        val wantBytes = session.beginHandshake(isUsb = false, configNonce = 456L)
        assertEquals(456, org.meshtastic.proto.ToRadio.ADAPTER.decode(wantBytes).want_config_id)
        session.onFromRadioBytes(
            org.meshtastic.proto.FromRadio.ADAPTER.encode(
                org.meshtastic.proto.FromRadio(
                    id = 1,
                    my_info = org.meshtastic.proto.MyNodeInfo(my_node_num = 0x01020304),
                )
            )
        )
        session.onFromRadioBytes(
            org.meshtastic.proto.FromRadio.ADAPTER.encode(
                org.meshtastic.proto.FromRadio(id = 2, config_complete_id = 456)
            )
        )
        assertTrue(session.isReady.value)
        val encoded = adapter.encodePacket(
            com.sovereignatlas.atlas.tactical.mesh.MeshPacket(
                from = 0x01020304L,
                to = 0xFFFFFFFFL,
                portNum = com.sovereignatlas.atlas.tactical.mesh.PortNum.PORTNUM_ATAK_PLUGIN,
                payload = byteArrayOf(4, 5, 6),
                id = 9L,
            )
        )
        assertTrue(org.meshtastic.proto.ToRadio.ADAPTER.decode(encoded).packet != null)
    }
}
