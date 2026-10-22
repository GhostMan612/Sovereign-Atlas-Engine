// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.sdk

import com.sovereignatlas.atlas.core.knowledge.ConfidenceScore
import com.sovereignatlas.atlas.core.knowledge.EntityType
import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier
import com.sovereignatlas.atlas.core.knowledge.KnowledgeBundle
import com.sovereignatlas.atlas.core.knowledge.KnowledgeBundleSerializer
import com.sovereignatlas.atlas.core.knowledge.SourceCitation
import com.sovereignatlas.atlas.core.knowledge.TemporalInterval
import com.sovereignatlas.atlas.geo.navigation.RestrictionType
import com.sovereignatlas.atlas.geo.navigation.RoadClass
import com.sovereignatlas.atlas.geo.navigation.RoadGraphBundle
import com.sovereignatlas.atlas.geo.navigation.RoadGraphBundleSerializer
import com.sovereignatlas.atlas.geo.navigation.RoadNetworkGraph
import com.sovereignatlas.atlas.geo.navigation.SurfaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoadGraphBundleSerializerTest {

    @Test
    fun roadGraphBundleRendersAndParsesDeterministicallyWithTurnRestrictions() {
        val builder = RoadNetworkGraph.Builder()
        builder.addNode(10L, 37.8030, -122.4735)
        builder.addNode(20L, 37.8005, -122.4730)
        builder.addNode(30L, 37.7985, -122.4750)
        builder.addEdge(
            id = 100L,
            fromNodeId = 10L,
            toNodeId = 20L,
            lengthMeters = 280.0,
            speedKmh = 40.0,
            name = "Lincoln Boulevard",
            roadClass = RoadClass.PRIMARY,
            isOneWay = false,
            surface = SurfaceType.PAVED,
        )
        builder.addEdge(
            id = 200L,
            fromNodeId = 20L,
            toNodeId = 30L,
            lengthMeters = 290.0,
            speedKmh = 40.0,
            name = "Lincoln Boulevard",
            roadClass = RoadClass.PRIMARY,
            isOneWay = false,
            surface = SurfaceType.PAVED,
        )
        builder.addTurnRestriction(
            fromEdgeId = 100L,
            viaNodeId = 20L,
            toEdgeId = 200L,
            type = RestrictionType.NO_LEFT_TURN,
        )
        val original = RoadGraphBundle(
            bundleId = "presidio-road-test",
            license = "Public Domain",
            attribution = "US Census TIGER/Line",
            graph = builder.build(),
        )

        val rendered = RoadGraphBundleSerializer.renderPack(original)
        val parsed = RoadGraphBundleSerializer.parsePack(rendered)
        assertNotNull(parsed)
        assertEquals("presidio-road-test", parsed!!.bundleId)
        assertEquals(3, parsed.graph.nodes.size)
        assertEquals(1, parsed.graph.turnRestrictions.size)
        assertEquals(
            false,
            parsed.graph.isTurnAllowed(
                parsed.graph.edges[100L],
                parsed.graph.edges[200L]!!,
            ),
        )
    }

    @Test
    fun knowledgeBundleRendersAndParsesPackDeterministically() {
        val dossier = HistoricalDossier(
            id = "hd-sdk-01",
            name = "Fort Point",
            entityType = EntityType.STRUCTURE,
            temporal = TemporalInterval(1853, 1914),
            latitude = 37.8106,
            longitude = -122.4770,
            description = "Coastal brick fortification",
            confidence = ConfidenceScore.CONFIRMED,
            primaryCitation = SourceCitation(
                citationId = "HABS-CA-1230",
                title = "Fort Point Survey",
                authorOrArchive = "National Park Service",
                publicationYear = 1934,
                urlOrShelfmark = "HABS CA-1230",
                license = "Public Domain",
            ),
            tags = listOf("fort", "harbor"),
        )
        val bundle = KnowledgeBundle(
            bundleId = "presidio-knowledge-test",
            title = "Presidio Dossiers",
            version = 1,
            license = "Public Domain",
            dossiers = listOf(dossier),
        )

        val rendered = KnowledgeBundleSerializer.renderPack(bundle)
        val parsed = KnowledgeBundleSerializer.parsePack(rendered)
        assertNotNull(parsed)
        assertEquals("presidio-knowledge-test", parsed!!.bundleId)
        assertEquals(1, parsed.dossiers.size)
        assertEquals("HABS-CA-1230", parsed.dossiers[0].primaryCitation.citationId)
    }
}
