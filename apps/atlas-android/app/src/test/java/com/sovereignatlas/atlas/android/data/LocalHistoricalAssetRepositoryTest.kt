// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.data

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.core.HistoricalLicense
import com.sovereignatlas.atlas.core.LandPatent
import com.sovereignatlas.atlas.core.SanbornBlueprint
import com.sovereignatlas.atlas.offline.mbtiles.MetadataReader
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Stands in for the Android SQLite reader. Records every path it was asked about,
 * so a test can prove the adapter routed the right file rather than guessing.
 */
private class FakeMetadataReader(
    private val fn: (File) -> Map<String, String>? = { null },
) : MetadataReader {
    val asked: MutableList<String> = mutableListOf()
    override fun readAll(file: File): Map<String, String>? {
        asked += file.absolutePath
        return fn(file)
    }

    override fun read(file: File): Pair<String, String>? =
        readAll(file)?.let { (it["name"] ?: "") to it["description"].orEmpty() }
}

class LocalHistoricalAssetRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun everyGeojsonFileInTheDirectoryIsParsed() = runTest {
        val repo = repositoryWith("patents.geojson" to DENVER_COLLECTION)

        assertEquals(listOf("lp-1", "lp-2"), repo.getAssets(WORLD).map { it.id })
    }

    @Test
    fun onlyAssetsOverlappingTheRequestedBoundsAreReturned() = runTest {
        val repo = repositoryWith("patents.geojson" to DENVER_COLLECTION)

        assertEquals(listOf("lp-1"), repo.getAssets(DENVER_VIEWPORT).map { it.id })
        assertEquals(listOf("lp-2"), repo.getAssets(SAMOA_VIEWPORT).map { it.id })
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
        val repo = LocalHistoricalAssetRepository({ dir }, FakeMetadataReader())

        assertEquals(listOf("lp-1", "lp-2"), repo.getAssets(WORLD).map { it.id })
    }

    @Test
    fun aMissingDirectoryYieldsAnEmptyCatalogueRatherThanAnError() = runTest {
        val repo = LocalHistoricalAssetRepository({ File(temp.root, "absent") }, FakeMetadataReader())

        assertTrue(repo.getAssets(WORLD).isEmpty())
        assertNull(repo.getAssetById("lp-1"))
    }

    @Test
    fun aNullDirectoryProviderYieldsAnEmptyCatalogue() = runTest {
        val repo = LocalHistoricalAssetRepository({ null }, FakeMetadataReader())

        assertTrue(repo.getAssets(WORLD).isEmpty())
    }

    @Test
    fun anMbtilesFileIsScannedAndMappedToABlueprint() = runTest {
        val reader = FakeMetadataReader { SANBORN_METADATA }
        val repo = repositoryWith("sanborn-denver.mbtiles" to "not really sqlite", reader = reader)

        val assets = repo.getAssets(WORLD)
        val blueprint = assets.single() as SanbornBlueprint

        assertEquals("Denver Sanborn 1897", blueprint.title)
        assertEquals("denver-1897", blueprint.id)
        assertEquals("12", blueprint.sheet)
        assertEquals(3, blueprint.edition)
        assertEquals(12.0f, blueprint.minZoom!!, 1e-6f)
        assertEquals(17.0f, blueprint.maxZoom!!, 1e-6f)
        assertEquals("png", blueprint.format)
        assertEquals(AtlasBoundingBox(39.5, -105.1, 40.1, -104.5), blueprint.boundingBox)
        assertTrue(blueprint.filePath.endsWith("sanborn-denver.mbtiles"))
        assertEquals(1, reader.asked.size)
    }

    @Test
    fun aBlueprintWithoutBoundsIsSkippedRatherThanCentredOnNullIsland() = runTest {
        val repo = repositoryWith(
            "no-bounds.mbtiles" to "x",
            reader = FakeMetadataReader { mapOf("name" to "No Extent") },
        )

        assertTrue(repo.getAssets(WORLD).isEmpty())
    }

    @Test
    fun aMalformedBoundsStringIsSkipped() = runTest {
        val repo = repositoryWith(
            "bad-bounds.mbtiles" to "x",
            reader = FakeMetadataReader {
                mapOf("name" to "Bad", "bounds" to "west,south,east,not-a-number")
            },
        )

        assertTrue(repo.getAssets(WORLD).isEmpty())
    }

    @Test
    fun anUnreadableMbtilesFileIsSkippedWithoutFailingTheScan() = runTest {
        val repo = repositoryWith(
            "good.geojson" to DENVER_COLLECTION,
            "not-sqlite.mbtiles" to "x",
            reader = FakeMetadataReader { null },
        )

        assertEquals(listOf("lp-1", "lp-2"), repo.getAssets(WORLD).map { it.id })
    }

    @Test
    fun aPackWithNoLicenceMetadataStaysUnverified() = runTest {
        val repo = repositoryWith(
            "sanborn.mbtiles" to "x",
            reader = FakeMetadataReader { SANBORN_METADATA },
        )

        val license = repo.getAssets(WORLD).single().license
        assertTrue("expected Unverified, got $license", license is HistoricalLicense.Unverified)
        assertFalse(license.isRedistributable)
    }

    @Test
    fun anAbsentZoomRangeStaysNullRatherThanBecomingZero() = runTest {
        val repo = repositoryWith(
            "no-zoom.mbtiles" to "x",
            reader = FakeMetadataReader {
                mapOf("name" to "No Zoom", "bounds" to "-105.1,39.5,-104.5,40.1")
            },
        )

        val blueprint = repo.getAssets(WORLD).single() as SanbornBlueprint
        assertNull(blueprint.minZoom)
        assertNull(blueprint.maxZoom)
    }

    @Test
    fun aBlueprintWithNoYearRecordsZeroRatherThanAGuess() = runTest {
        val repo = repositoryWith(
            "no-year.mbtiles" to "x",
            reader = FakeMetadataReader {
                mapOf("name" to "No Year", "bounds" to "-105.1,39.5,-104.5,40.1")
            },
        )

        assertEquals(0, repo.getAssets(WORLD).single().year)
    }

    @Test
    fun geojsonAndMbtilesInOneDirectoryAreBothReturned() = runTest {
        val repo = repositoryWith(
            "patents.geojson" to DENVER_COLLECTION,
            "sanborn.mbtiles" to "x",
            reader = FakeMetadataReader { SANBORN_METADATA },
        )

        val assets = repo.getAssets(WORLD)
        assertEquals(3, assets.size)
        assertTrue(assets.any { it is LandPatent })
        assertTrue(assets.any { it is SanbornBlueprint })
    }

    @Test
    fun aBlueprintWithNoRecordedIdStillGetsAStableAddressableId() = runTest {
        val repo = repositoryWith(
            "no-id.mbtiles" to "x",
            reader = FakeMetadataReader {
                mapOf("name" to "Generated", "bounds" to "-105.1,39.5,-104.5,40.1")
            },
        )

        val first = repo.getAssets(WORLD).single().id
        val second = repo.getAssets(WORLD).single().id
        // Two independent scans must agree, or getAssetById can never resolve it.
        assertEquals(first, second)
        assertEquals(first, repo.getAssetById(first)?.id)
    }

    private fun repositoryWith(
        vararg files: Pair<String, String>,
        reader: MetadataReader = FakeMetadataReader(),
    ): LocalHistoricalAssetRepository {
        val dir = temp.newFolder("historical")
        for ((name, body) in files) File(dir, name).writeText(body)
        return LocalHistoricalAssetRepository({ dir }, reader)
    }

    private companion object {
        val WORLD = AtlasBoundingBox(-90.0, -180.0, 90.0, 180.0)
        val DENVER_VIEWPORT = AtlasBoundingBox(39.0, -105.5, 40.0, -104.0)
        val SAMOA_VIEWPORT = AtlasBoundingBox(-14.0, -171.0, -13.0, -170.0)

        val SANBORN_METADATA = mapOf(
            "id" to "denver-1897",
            "name" to "Denver Sanborn 1897",
            "sheet" to "12",
            "edition" to "3",
            "bounds" to "-105.1,39.5,-104.5,40.1",
            "minzoom" to "12",
            "maxzoom" to "17",
            "format" to "png",
        )

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