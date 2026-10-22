// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core.knowledge

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeBundleSerializerTest {

    @Test
    fun sha256ComputesAndVerifiesDeterministically() {
        val payload = "Sovereign Atlas Knowledge Bundle v1".toByteArray(Charsets.UTF_8)
        val hash = KnowledgeBundleSerializer.computeSha256(payload)

        assertEquals(64, hash.length)
        assertTrue(KnowledgeBundleSerializer.verifyChecksum(payload, hash))

        val tampered = "Sovereign Atlas Knowledge Bundle v2".toByteArray(Charsets.UTF_8)
        assertFalse(KnowledgeBundleSerializer.verifyChecksum(tampered, hash))
    }

    @Test
    fun atomicWriteVerifiedCommitsValidBundleAndPreservesOriginalOnTamperedUpdate() {
        val dir = Files.createTempDirectory("atlas-bundle-test").toFile()
        val target = File(dir, "knowledge-v1.json")
        val initialBytes = "{\"version\":1}".toByteArray(Charsets.UTF_8)
        val initialSha = KnowledgeBundleSerializer.computeSha256(initialBytes)

        assertTrue(KnowledgeBundleSerializer.atomicWriteVerified(target, initialBytes, initialSha))
        assertTrue(target.exists())
        assertTrue(target.readBytes().contentEquals(initialBytes))

        val tamperedBytes = "{\"version\":2,\"corrupt\":true}".toByteArray(Charsets.UTF_8)
        assertFalse(KnowledgeBundleSerializer.atomicWriteVerified(target, tamperedBytes, initialSha))
        assertFalse(File(dir, "knowledge-v1.json.tmp").exists())
        assertTrue(target.readBytes().contentEquals(initialBytes))
    }

    @Test
    fun bundleRendersValidJson() {
        val dossier = HistoricalDossier(
            id = "dossier-001",
            name = "Territorial Land Office",
            entityType = EntityType.STRUCTURE,
            temporal = TemporalInterval(1849, 1858),
            latitude = 44.95,
            longitude = -93.10,
            description = "Original government land sales office",
            confidence = ConfidenceScore.CONFIRMED,
            primaryCitation = SourceCitation(
                citationId = "CIT-01",
                title = "Territorial Annals",
                authorOrArchive = "Historical Society",
                publicationYear = 1880,
                urlOrShelfmark = "SHELF-44",
                license = "CC-BY-4.0",
            ),
        )

        val bundle = KnowledgeBundle(
            bundleId = "bundle-us-territorial",
            title = "US Territorial Era Sites",
            version = 1,
            license = "Open Database License (ODbL)",
            dossiers = listOf(dossier),
        )

        val json = KnowledgeBundleSerializer.renderJson(bundle)

        assertTrue(json.contains("\"bundleId\": \"bundle-us-territorial\""))
        assertTrue(json.contains("\"name\": \"Territorial Land Office\""))
        assertTrue(json.contains("\"validFromYear\": 1849"))
        assertTrue(json.contains("\"validToYear\": 1858"))
        assertTrue(json.contains("\"confidence\": \"CONFIRMED\""))
    }

    @Test
    fun loadVerifiedDirectoryIngestsGoldenPresidioPackAndEnforcesProvenance() {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden/historical_dossiers_presidio.pack")
        assertNotNull(stream)
        val packBytes = stream!!.readBytes()
        val sha256 = KnowledgeBundleSerializer.computeSha256(packBytes)

        val dir = Files.createTempDirectory("atlas-knowledge-pack").toFile()
        val packFile = File(dir, "historical_dossiers_presidio.pack")
        val sidecarFile = File(dir, "historical_dossiers_presidio.pack.sha256")
        packFile.writeBytes(packBytes)
        sidecarFile.writeText("$sha256  historical_dossiers_presidio.pack\n", Charsets.UTF_8)

        val index = HistoricalKnowledgeIndex()
        val count = KnowledgeBundleSerializer.loadVerifiedDirectory(dir, index)
        assertEquals(3, count)

        val d1 = index.get("hd-presidio-001")
        assertNotNull(d1)
        assertEquals("HABS-CA-1230", d1!!.primaryCitation.citationId)
        assertEquals("Public Domain", d1.primaryCitation.license)

        val activeIn1910 = index.queryTemporal(1910)
        assertEquals(3, activeIn1910.size)
    }

    @Test
    fun loadVerifiedDirectoryRejectsTamperedPackAndMissingProvenance() {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden/historical_dossiers_presidio.pack")
        val packBytes = stream!!.readBytes()

        val dir = Files.createTempDirectory("atlas-knowledge-tamper").toFile()
        val packFile = File(dir, "historical_dossiers_presidio.pack")
        val sidecarFile = File(dir, "historical_dossiers_presidio.pack.sha256")
        packFile.writeBytes(packBytes)
        sidecarFile.writeText("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", Charsets.UTF_8)

        val index = HistoricalKnowledgeIndex()
        val count = KnowledgeBundleSerializer.loadVerifiedDirectory(dir, index)
        assertEquals(0, count)

        val missingCitationRaw = """
            BUNDLE|bad-bundle|Invalid Bundle|1|Public Domain
            DOSSIER|hd-bad|Mystery Site|PLACE|1900|1950|37.8|-122.4|Desc|UNCERTAIN||||1900||Public Domain|tag
        """.trimIndent()
        assertNull(KnowledgeBundleSerializer.parsePack(missingCitationRaw))
    }
}
