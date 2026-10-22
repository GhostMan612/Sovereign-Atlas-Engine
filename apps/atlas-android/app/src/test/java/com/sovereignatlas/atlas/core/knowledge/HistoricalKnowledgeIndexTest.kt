// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core.knowledge

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalKnowledgeIndexTest {

    private fun sampleDossier(
        id: String,
        name: String,
        lat: Double,
        lon: Double,
        fromYear: Int?,
        toYear: Int?,
        type: EntityType = EntityType.STRUCTURE,
    ): HistoricalDossier {
        return HistoricalDossier(
            id = id,
            name = name,
            entityType = type,
            temporal = TemporalInterval(fromYear, toYear),
            latitude = lat,
            longitude = lon,
            description = "Historical site for test purposes",
            confidence = ConfidenceScore.CONFIRMED,
            primaryCitation = SourceCitation(
                citationId = "CIT-001",
                title = "State Historical Survey",
                authorOrArchive = "State Archives",
                publicationYear = 1935,
                urlOrShelfmark = "ARC-1935-A",
                license = "Public Domain",
            ),
            tags = listOf("historical", "heritage"),
        )
    }

    @Test
    fun spatialQueryReturnsEntitiesInsideBoundingBox() {
        val index = HistoricalKnowledgeIndex()
        index.insert(sampleDossier("d1", "Fort Snelling", 44.893, -93.181, 1820, null))
        index.insert(sampleDossier("d2", "Stone Arch Bridge", 44.981, -93.253, 1883, null))
        index.insert(sampleDossier("d3", "Duluth Harbor", 46.781, -92.093, 1871, null))

        val twinCitiesBox = AtlasBoundingBox(
            south = 44.8,
            west = -93.4,
            north = 45.1,
            east = -93.1,
        )

        val results = index.querySpatial(twinCitiesBox)
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "d1" })
        assertTrue(results.any { it.id == "d2" })
    }

    @Test
    fun temporalQueryReturnsEntitiesActiveInTargetYear() {
        val index = HistoricalKnowledgeIndex()
        index.insert(sampleDossier("d1", "Early Trading Post", 45.0, -93.0, 1810, 1845))
        index.insert(sampleDossier("d2", "Mill District", 45.0, -93.0, 1860, 1920))
        index.insert(sampleDossier("d3", "Modern Highway", 45.0, -93.0, 1955, null))

        val year1830 = index.queryTemporal(1830)
        assertEquals(1, year1830.size)
        assertEquals("d1", year1830[0].id)

        val year1900 = index.queryTemporal(1900)
        assertEquals(1, year1900.size)
        assertEquals("d2", year1900[0].id)

        val year2020 = index.queryTemporal(2020)
        assertEquals(1, year2020.size)
        assertEquals("d3", year2020[0].id)
    }

    @Test
    fun searchFindsEntitiesByKeywordAndCitation() {
        val index = HistoricalKnowledgeIndex()
        index.insert(sampleDossier("d1", "St. Anthony Falls Flouring Mill", 44.98, -93.25, 1850, 1930))

        val nameSearch = index.search("flouring")
        assertEquals(1, nameSearch.size)

        val citationSearch = index.search("state archives")
        assertEquals(1, citationSearch.size)
    }
}
