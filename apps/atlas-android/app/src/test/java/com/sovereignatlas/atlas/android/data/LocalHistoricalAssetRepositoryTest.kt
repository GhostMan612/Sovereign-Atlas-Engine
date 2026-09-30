// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.data

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalHistoricalAssetRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun everyGeojsonFileInTheDirectoryIsParsed() = runTest {
        val repo = repositoryWith("patents.geojson" to DENVER_COLLECTION)
        val assets = repo.getAssets(WORLD)

        assertEquals(listOf("lp-1", "lp-2"), assets.map { it.id })
    }

    @Test
    fun onlyAssetsOverlappingTheRequestedBoundsAreReturned() = runTest {
        val repo = repositoryWith("patents.geojson" to DENVER_COLLECTION)

        val denver = repo.getAssets(AtlasBoundingBox(39.0, -105.5, 40.0, -104.0))
        val samoa = repo.getAssets(AtlasBoundingBox(-14.0, -171.0, -13.0, -170.0))

        assertEquals(listOf("lp-1"), denver.map { it.id })
        assertEquals(listOf("lp-2"), samoa.map { it.id })
    }

    @Test
    fun assetByIdFindsTheMatchingRecord() = runTest {
        val repo = repositoryWith("patents.geojson" to DENVER_COLLECTION)

        assertEquals("lp-2", repo.getAssetById("lp-2")?.id)
    }

    @Test
    fun assetByIdReturnsNullForAnUnrecordedId() = runTest {
        val repo = repositoryWith("patents.geojson" to DENVER_COLLECTION)

        assertNull(repo.getAssetById("lp-does-not-exist"))
    }

    @Test
    fun nonGeojsonAndUnreadableFilesAreSkippedWithoutFailingTheScan() = runTest {
        val dir = temp.newFolder("historical")
        File(dir, "patents.geojson").writeText(DENVER_COLLECTION)
        File(dir, "notes.txt").writeText("not a collection")
        File(dir, "broken.geojson").writeText("{ this is not json")
        val repo = LocalHistoricalAssetRepository { dir }

        assertEquals(listOf("lp-1", "lp-2"), repo.getAssets(WORLD).map { it.id })
    }

    @Test
    fun aMissingDirectoryYieldsAnEmptyCatalogueRatherThanAnError() = runTest {
        val repo = LocalHistoricalAssetRepository { File(temp.root, "absent") }

        assertTrue(repo.getAssets(WORLD).isEmpty())
        assertNull(repo.getAssetById("lp-1"))
    }

    @Test
    fun aNullDirectoryProviderYieldsAnEmptyCatalogue() = runTest {
        val repo = LocalHistoricalAssetRepository { null }

        assertTrue(repo.getAssets(WORLD).isEmpty())
    }

    private fun repositoryWith(vararg files: Pair<String, String>): LocalHistoricalAssetRepository {
        val dir = temp.newFolder("historical")
        for ((name, body) in files) File(dir, name).writeText(body)
        return LocalHistoricalAssetRepository { dir }
    }

    private companion object {
        val WORLD = AtlasBoundingBox(-90.0, -180.0, 90.0, 180.0)

        val DENVER_COLLECTION = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "id": "lp-1",
                  "geometry": { "type": "Point", "coordinates": [-104.99, 39.74] },
                  "properties": { "patent_number": "1880-0042", "issue_date": "1880-03-14" }
                },
                {
                  "type": "Feature",
                  "id": "lp-2",
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[-171.0, -14.0], [-170.0, -14.0], [-170.0, -13.0], [-171.0, -14.0]]]
                  },
                  "properties": { "patent_number": "1891-0007" }
                }
              ]
            }
        """.trimIndent()
    }
}