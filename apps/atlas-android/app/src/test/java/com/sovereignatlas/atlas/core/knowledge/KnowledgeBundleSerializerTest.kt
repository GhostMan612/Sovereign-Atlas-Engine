// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core.knowledge

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
