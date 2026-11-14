// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.gis

import com.sovereignatlas.atlas.geo.gis.GisFormat
import com.sovereignatlas.atlas.geo.gis.GisSrid
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidGisImporterTest {

    @Test
    fun importKmz_parsesValidStreamingKmzWithCompanionAndClosesStream() {
        val kml = simplePointKml("SAR Command Post", "USFS", -105.2500, 40.1250)
        val zipBytes = buildZip(
            listOf(
                TestZipEntry("images/", ByteArray(0), isDirectory = true),
                TestZipEntry("images/icon.png", ByteArray(64) { 0x5A }),
                TestZipEntry("doc.kml", kml.toByteArray(StandardCharsets.UTF_8)),
            ),
        )
        val stream = TrackingInputStream(ByteArrayInputStream(zipBytes))

        val result = AndroidGisImporter.importKmz(
            stream = stream,
            name = "incident.kmz",
            sourceUri = "content://test/incident.kmz",
        )

        assertTrue(result.errorMessage ?: "expected success", result.isSuccess)
        assertNull(result.errorMessage)
        assertTrue(stream.closed)
        val dataset = result.dataset
        assertNotNull(dataset)
        assertEquals(GisFormat.KMZ, dataset!!.format)
        assertEquals("incident", dataset.name)
        assertEquals("content://test/incident.kmz", dataset.sourcePathOrUri)
        assertEquals(1, dataset.features.size)
        val feature = dataset.features.first()
        assertTrue(feature.geometry is RenderGeometry.Point)
        val point = feature.geometry as RenderGeometry.Point
        assertEquals(-105.2500, point.coordinates.longitude, 1e-4)
        assertEquals(40.1250, point.coordinates.latitude, 1e-4)
        assertEquals(RenderProperty.Text("SAR Command Post"), feature.properties["name"])
        assertEquals(RenderProperty.Text("USFS"), feature.properties["Agency"])
    }

    @Test
    fun importZippedShapefile_parsesNormalizedBundleSkipsCompanionAndClosesStream() {
        val shp = buildSinglePointShp(-105.25, 40.125)
        val dbf = buildSingleRecordDbf("Campground")
        val prj = """PROJCS["WGS 84 / Pseudo-Mercator",AUTHORITY["EPSG","3857"]]"""
            .toByteArray(StandardCharsets.US_ASCII)
        val zipBytes = buildZip(
            listOf(
                TestZipEntry("sub/", ByteArray(0), isDirectory = true),
                TestZipEntry("./sub\\roads.shp", shp),
                TestZipEntry("sub/roads.dbf", dbf),
                TestZipEntry("sub/roads.prj", prj),
                TestZipEntry("docs/readme.txt", ByteArray(16_384) { 'A'.code.toByte() }),
            ),
        )
        val stream = TrackingInputStream(ByteArrayInputStream(zipBytes))

        val result = AndroidGisImporter.importZippedShapefile(
            stream = stream,
            name = "roads.zip",
            sourceUri = "content://test/roads.zip",
        )

        assertTrue(result.errorMessage ?: "expected success", result.isSuccess)
        assertNull(result.errorMessage)
        assertTrue(stream.closed)
        val dataset = result.dataset
        assertNotNull(dataset)
        assertEquals("roads", dataset!!.name)
        assertEquals("content://test/roads.zip", dataset.sourcePathOrUri)
        assertEquals(GisSrid.WEB_MERCATOR, dataset.srid)
        assertEquals(1, dataset.features.size)
        val feature = dataset.features.first()
        assertEquals(RenderProperty.Text("Campground"), feature.properties["NAME"])
        assertTrue(feature.geometry is RenderGeometry.Point)
    }

    @Test
    fun importZippedShapefile_parsesWgs84CoordinatesWithoutPrj() {
        val shp = buildSinglePointShp(-105.25, 40.125)
        val dbf = buildSingleRecordDbf("Basecamp")
        val zipBytes = buildZip(
            listOf(
                TestZipEntry("trail.shp", shp),
                TestZipEntry("trail.dbf", dbf),
            ),
        )
        val stream = TrackingInputStream(ByteArrayInputStream(zipBytes))

        val result = AndroidGisImporter.importZippedShapefile(
            stream = stream,
            name = "trail.zip",
            sourceUri = "content://test/trail.zip",
        )

        assertTrue(result.errorMessage ?: "expected success", result.isSuccess)
        assertTrue(stream.closed)
        val dataset = result.dataset!!
        assertEquals(GisSrid.WGS84, dataset.srid)
        val point = dataset.features.first().geometry as RenderGeometry.Point
        assertEquals(-105.25, point.coordinates.longitude, 1e-4)
        assertEquals(40.125, point.coordinates.latitude, 1e-4)
        assertEquals(RenderProperty.Text("Basecamp"), dataset.features.first().properties["NAME"])
    }

    @Test
    fun importKmz_allowsExactMaxEntryBytesAndRejectsOneByteOver() {
        val kmlBytes = simplePointKml("Boundary", "NPS", -106.0, 39.5)
            .toByteArray(StandardCharsets.UTF_8)
        val zipBytes = buildZip(listOf(TestZipEntry("doc.kml", kmlBytes)))

        val exactLimits = AndroidGisImporter.ArchiveLimits(
            maxEntryBytes = kmlBytes.size.toLong(),
            maxTotalDecompressedBytes = kmlBytes.size.toLong(),
        )
        val exactStream = TrackingInputStream(ByteArrayInputStream(zipBytes))
        val exactResult = AndroidGisImporter.importKmz(
            stream = exactStream,
            name = "exact.kmz",
            sourceUri = "content://test/exact.kmz",
            limits = exactLimits,
        )
        assertTrue(exactResult.errorMessage ?: "expected success", exactResult.isSuccess)
        assertTrue(exactStream.closed)

        val overLimits = exactLimits.copy(
            maxEntryBytes = kmlBytes.size.toLong() - 1L,
        )
        val overStream = TrackingInputStream(ByteArrayInputStream(zipBytes))
        val overResult = AndroidGisImporter.importKmz(
            stream = overStream,
            name = "over.kmz",
            sourceUri = "content://test/over.kmz",
            limits = overLimits,
        )
        assertFalse(overResult.isSuccess)
        assertNull(overResult.dataset)
        assertTrue(overResult.errorMessage!!.contains("exceeds limit"))
        assertTrue(overStream.closed)
    }

    @Test
    fun importKmz_rejectsSpoofedDeclaredSizeBeforeReading() {
        val kmlBytes = simplePointKml("Tiny", "USFS", -105.0, 40.0)
            .toByteArray(StandardCharsets.UTF_8)
        val storedZip = buildZip(
            listOf(TestZipEntry("doc.kml", kmlBytes, stored = true)),
        )
        patchSingleStoredLocalUncompressedSize(storedZip, 200_000_000)
        val stream = TrackingInputStream(ByteArrayInputStream(storedZip))

        val result = AndroidGisImporter.importKmz(
            stream = stream,
            name = "spoofed.kmz",
            sourceUri = "content://test/spoofed.kmz",
        )

        assertFalse(result.isSuccess)
        assertNull(result.dataset)
        assertTrue(result.errorMessage!!.contains("declared size exceeds limit"))
        assertTrue(stream.closed)
    }

    @Test
    fun importZippedShapefile_enforcesSingleEntryLimitsOnShpDbfAndPrj() {
        val shp = buildSinglePointShp(-105.25, 40.125)
        val dbf = buildSingleRecordDbf("Lookout")
        val prj = ByteArray(512) { ' '.code.toByte() }
        val limits = AndroidGisImporter.ArchiveLimits(
            maxEntryBytes = 256L,
            maxPrjBytes = 128L,
            maxTotalDecompressedBytes = 4_096L,
        )

        val hugeShpZip = buildZip(
            listOf(
                TestZipEntry("big.shp", shp + ByteArray(300)),
                TestZipEntry("big.dbf", dbf),
            ),
        )
        val shpStream = TrackingInputStream(ByteArrayInputStream(hugeShpZip))
        val shpResult = AndroidGisImporter.importZippedShapefile(
            stream = shpStream,
            name = "big_shp.zip",
            sourceUri = "content://test/big_shp.zip",
            limits = limits,
        )
        assertFalse(shpResult.isSuccess)
        assertNull(shpResult.dataset)
        assertTrue(shpResult.errorMessage!!.contains("exceeds limit"))
        assertTrue(shpStream.closed)

        val hugeDbfZip = buildZip(
            listOf(
                TestZipEntry("big.shp", shp),
                TestZipEntry("big.dbf", dbf + ByteArray(300)),
            ),
        )
        val dbfStream = TrackingInputStream(ByteArrayInputStream(hugeDbfZip))
        val dbfResult = AndroidGisImporter.importZippedShapefile(
            stream = dbfStream,
            name = "big_dbf.zip",
            sourceUri = "content://test/big_dbf.zip",
            limits = limits,
        )
        assertFalse(dbfResult.isSuccess)
        assertNull(dbfResult.dataset)
        assertTrue(dbfResult.errorMessage!!.contains("exceeds limit"))
        assertTrue(dbfStream.closed)

        val hugePrjZip = buildZip(
            listOf(
                TestZipEntry("big.shp", shp),
                TestZipEntry("big.dbf", dbf),
                TestZipEntry("big.prj", prj),
            ),
        )
        val prjStream = TrackingInputStream(ByteArrayInputStream(hugePrjZip))
        val prjResult = AndroidGisImporter.importZippedShapefile(
            stream = prjStream,
            name = "big_prj.zip",
            sourceUri = "content://test/big_prj.zip",
            limits = limits,
        )
        assertFalse(prjResult.isSuccess)
        assertNull(prjResult.dataset)
        assertTrue(prjResult.errorMessage!!.contains("exceeds limit"))
        assertTrue(prjStream.closed)
    }

    @Test
    fun importKmz_rejectsAggregateDecompressedSizeAcrossEntries() {
        val kmlBytes = simplePointKml("Agg", "BLM", -105.1, 40.1)
            .toByteArray(StandardCharsets.UTF_8)
        val zipBytes = buildZip(
            listOf(
                TestZipEntry("doc.kml", kmlBytes),
                TestZipEntry("a.bin", ByteArray(600) { 1 }),
                TestZipEntry("b.bin", ByteArray(600) { 2 }),
            ),
        )
        val limits = AndroidGisImporter.ArchiveLimits(
            maxEntryBytes = 1_000L,
            maxTotalDecompressedBytes = 1_200L,
        )
        val stream = TrackingInputStream(ByteArrayInputStream(zipBytes))

        val result = AndroidGisImporter.importKmz(
            stream = stream,
            name = "aggregate.kmz",
            sourceUri = "content://test/aggregate.kmz",
            limits = limits,
        )

        assertFalse(result.isSuccess)
        assertNull(result.dataset)
        assertTrue(result.errorMessage!!.contains("decompressed size exceeds limit"))
        assertTrue(stream.closed)
    }

    @Test
    fun importZippedShapefile_rejectsAggregateSizeIncludingSkippedEntries() {
        val shp = buildSinglePointShp(-105.25, 40.125)
        val zipBytes = buildZip(
            listOf(
                TestZipEntry("site.shp", shp),
                TestZipEntry("junk1.txt", ByteArray(600) { 0x41 }),
                TestZipEntry("junk2.txt", ByteArray(600) { 0x42 }),
            ),
        )
        val limits = AndroidGisImporter.ArchiveLimits(
            maxEntryBytes = 800L,
            maxTotalDecompressedBytes = 1_200L,
        )
        val stream = TrackingInputStream(ByteArrayInputStream(zipBytes))

        val result = AndroidGisImporter.importZippedShapefile(
            stream = stream,
            name = "aggregate_shp.zip",
            sourceUri = "content://test/aggregate_shp.zip",
            limits = limits,
        )

        assertFalse(result.isSuccess)
        assertNull(result.dataset)
        assertTrue(result.errorMessage!!.contains("decompressed size exceeds limit"))
        assertTrue(stream.closed)
    }

    @Test
    fun importKmz_rejectsCompressedArchiveSizeOverLimit() {
        val incompressible = ByteArray(1_024).also { java.util.Random(42L).nextBytes(it) }
        val kmlBytes = simplePointKml("Comp", "USFS", -105.0, 40.0)
            .toByteArray(StandardCharsets.UTF_8)
        val zipBytes = buildZip(
            listOf(
                TestZipEntry("doc.kml", kmlBytes),
                TestZipEntry("noise.bin", incompressible),
            ),
        )
        val limits = AndroidGisImporter.ArchiveLimits(
            maxCompressedBytes = 512L,
        )
        val stream = TrackingInputStream(ByteArrayInputStream(zipBytes))

        val result = AndroidGisImporter.importKmz(
            stream = stream,
            name = "compressed_over.kmz",
            sourceUri = "content://test/compressed_over.kmz",
            limits = limits,
        )

        assertFalse(result.isSuccess)
        assertNull(result.dataset)
        assertTrue(result.errorMessage!!.contains("compressed size exceeds limit"))
        assertTrue(stream.closed)
    }

    @Test
    fun importKmz_rejectsDefaultLimitDeflateBombAndAllowsStoredControl() {
        val commentBomb = buildString {
            append("<kml><Placemark><name>Bomb</name>")
            append("<Point><coordinates>-105.0,40.0</coordinates></Point><!--")
            repeat(600_000) { append(' ') }
            append("--></Placemark></kml>")
        }.toByteArray(StandardCharsets.UTF_8)
        val bombZip = buildZip(listOf(TestZipEntry("doc.kml", commentBomb)))
        val bombStream = TrackingInputStream(ByteArrayInputStream(bombZip))

        val bombResult = AndroidGisImporter.importKmz(
            stream = bombStream,
            name = "bomb.kmz",
            sourceUri = "content://test/bomb.kmz",
        )

        assertFalse(bombResult.isSuccess)
        assertNull(bombResult.dataset)
        assertTrue(bombResult.errorMessage!!.contains("compression ratio"))
        assertTrue(bombStream.closed)

        val storedKml = simplePointKml("Stored", "USFS", -105.0, 40.0)
            .toByteArray(StandardCharsets.UTF_8)
        val storedPayload = ByteArray(300_000) { (it and 0xFF).toByte() }
        val storedZip = buildZip(
            listOf(
                TestZipEntry("doc.kml", storedKml),
                TestZipEntry("assets/stored.bin", storedPayload, stored = true),
            ),
        )
        val storedStream = TrackingInputStream(ByteArrayInputStream(storedZip))

        val storedResult = AndroidGisImporter.importKmz(
            stream = storedStream,
            name = "stored.kmz",
            sourceUri = "content://test/stored.kmz",
        )

        assertTrue(storedResult.errorMessage ?: "expected success", storedResult.isSuccess)
        assertTrue(storedStream.closed)
    }

    @Test
    fun importZippedShapefile_rejectsDeflateBombWithDefaultLimits() {
        val shp = buildSinglePointShp(-105.25, 40.125)
        val zeros = ByteArray(600_000)
        val bombZip = buildZip(
            listOf(
                TestZipEntry("valid.shp", shp),
                TestZipEntry("bomb.bin", zeros),
            ),
        )
        val stream = TrackingInputStream(ByteArrayInputStream(bombZip))

        val result = AndroidGisImporter.importZippedShapefile(
            stream = stream,
            name = "bomb_shp.zip",
            sourceUri = "content://test/bomb_shp.zip",
        )

        assertFalse(result.isSuccess)
        assertNull(result.dataset)
        assertTrue(result.errorMessage!!.contains("compression ratio"))
        assertTrue(stream.closed)
    }

    @Test
    fun importArchives_enforceEntryCountIncludingDirectories() {
        val kmlBytes = simplePointKml("Count", "NPS", -105.0, 40.0)
            .toByteArray(StandardCharsets.UTF_8)
        val kmzBytes = buildZip(
            listOf(
                TestZipEntry("d1/", ByteArray(0), isDirectory = true),
                TestZipEntry("d2/", ByteArray(0), isDirectory = true),
                TestZipEntry("doc.kml", kmlBytes),
                TestZipEntry("f1.txt", byteArrayOf(1)),
                TestZipEntry("f2.txt", byteArrayOf(2)),
            ),
        )
        val limits = AndroidGisImporter.ArchiveLimits(maxEntryCount = 4)

        val kmzStream = TrackingInputStream(ByteArrayInputStream(kmzBytes))
        val kmzResult = AndroidGisImporter.importKmz(
            stream = kmzStream,
            name = "many.kmz",
            sourceUri = "content://test/many.kmz",
            limits = limits,
        )
        assertFalse(kmzResult.isSuccess)
        assertNull(kmzResult.dataset)
        assertTrue(kmzResult.errorMessage!!.contains("entry count exceeds limit"))
        assertTrue(kmzStream.closed)

        val shpBytes = buildZip(
            listOf(
                TestZipEntry("d1/", ByteArray(0), isDirectory = true),
                TestZipEntry("site.shp", buildSinglePointShp(-105.25, 40.125)),
                TestZipEntry("a.txt", byteArrayOf(1)),
                TestZipEntry("b.txt", byteArrayOf(2)),
                TestZipEntry("c.txt", byteArrayOf(3)),
            ),
        )
        val shpStream = TrackingInputStream(ByteArrayInputStream(shpBytes))
        val shpResult = AndroidGisImporter.importZippedShapefile(
            stream = shpStream,
            name = "many_shp.zip",
            sourceUri = "content://test/many_shp.zip",
            limits = limits,
        )
        assertFalse(shpResult.isSuccess)
        assertNull(shpResult.dataset)
        assertTrue(shpResult.errorMessage!!.contains("entry count exceeds limit"))
        assertTrue(shpStream.closed)
    }

    @Test
    fun importArchives_rejectPathTraversalAndAbsolutePaths() {
        val kmlBytes = simplePointKml("Evil", "USFS", -105.0, 40.0)
            .toByteArray(StandardCharsets.UTF_8)
        val badPaths = listOf(
            "../evil.kml",
            "sub/../../evil.kml",
            "/abs/doc.kml",
            "\\leading\\doc.kml",
            "C:\\drive\\doc.kml",
            "D:relative.kml",
        )
        for (badPath in badPaths) {
            val kmzBytes = buildZip(listOf(TestZipEntry(badPath, kmlBytes)))
            val stream = TrackingInputStream(ByteArrayInputStream(kmzBytes))
            val result = AndroidGisImporter.importKmz(
                stream = stream,
                name = "bad.kmz",
                sourceUri = "content://test/bad.kmz",
            )
            assertFalse("expected failure for $badPath", result.isSuccess)
            assertNull(result.dataset)
            assertNotNull(result.errorMessage)
            assertTrue(stream.closed)
        }

        val shpBytes = buildSinglePointShp(-105.25, 40.125)
        for (badPath in listOf("../evil.shp", "a/../../evil.shp", "/root/a.shp", "C:\\a.shp")) {
            val zipBytes = buildZip(listOf(TestZipEntry(badPath, shpBytes)))
            val stream = TrackingInputStream(ByteArrayInputStream(zipBytes))
            val result = AndroidGisImporter.importZippedShapefile(
                stream = stream,
                name = "bad_shp.zip",
                sourceUri = "content://test/bad_shp.zip",
            )
            assertFalse("expected failure for $badPath", result.isSuccess)
            assertNull(result.dataset)
            assertNotNull(result.errorMessage)
            assertTrue(stream.closed)
        }
    }

    @Test
    fun importArchives_rejectMultipleKmlAndDuplicateComponents() {
        val kmlBytes = simplePointKml("One", "USFS", -105.0, 40.0)
            .toByteArray(StandardCharsets.UTF_8)

        val twoKmls = buildZip(
            listOf(
                TestZipEntry("doc.kml", kmlBytes),
                TestZipEntry("nested/other.kml", kmlBytes),
            ),
        )
        val twoKmlStream = TrackingInputStream(ByteArrayInputStream(twoKmls))
        val twoKmlResult = AndroidGisImporter.importKmz(
            stream = twoKmlStream,
            name = "two.kmz",
            sourceUri = "content://test/two.kmz",
        )
        assertFalse(twoKmlResult.isSuccess)
        assertNull(twoKmlResult.dataset)
        assertTrue(twoKmlResult.errorMessage!!.contains("multiple .kml"))
        assertTrue(twoKmlStream.closed)

        val dupNormKmz = buildZip(
            listOf(
                TestZipEntry("sub/doc.kml", kmlBytes),
                TestZipEntry("./sub\\doc.kml", kmlBytes),
            ),
        )
        val dupNormStream = TrackingInputStream(ByteArrayInputStream(dupNormKmz))
        val dupNormResult = AndroidGisImporter.importKmz(
            stream = dupNormStream,
            name = "dup.kmz",
            sourceUri = "content://test/dup.kmz",
        )
        assertFalse(dupNormResult.isSuccess)
        assertNull(dupNormResult.dataset)
        assertTrue(dupNormResult.errorMessage!!.contains("Duplicate entry"))
        assertTrue(dupNormStream.closed)

        val shp = buildSinglePointShp(-105.25, 40.125)
        val dbf = buildSingleRecordDbf("Camp")
        val dupComponentBundles = listOf(
            listOf(TestZipEntry("a/roads.shp", shp), TestZipEntry("b/roads.shp", shp)),
            listOf(
                TestZipEntry("a/roads.shp", shp),
                TestZipEntry("a/roads.dbf", dbf),
                TestZipEntry("b/roads.dbf", dbf),
            ),
            listOf(
                TestZipEntry("a/roads.shp", shp),
                TestZipEntry("a/roads.prj", "EPSG:4326".toByteArray()),
                TestZipEntry("b/roads.prj", "EPSG:4326".toByteArray()),
            ),
            listOf(
                TestZipEntry("a/roads.shp", shp),
                TestZipEntry("a/roads.shx", ByteArray(100)),
                TestZipEntry("b/roads.shx", ByteArray(100)),
            ),
        )
        for (bundle in dupComponentBundles) {
            val stream = TrackingInputStream(ByteArrayInputStream(buildZip(bundle)))
            val result = AndroidGisImporter.importZippedShapefile(
                stream = stream,
                name = "dup_comp.zip",
                sourceUri = "content://test/dup_comp.zip",
            )
            assertFalse(result.isSuccess)
            assertNull(result.dataset)
            assertNotNull(result.errorMessage)
            assertTrue(stream.closed)
        }
    }

    @Test
    fun importArchives_rejectMissingPrimaryDocumentEmptyNonZipAndTruncatedStreams() {
        val noKmlZip = buildZip(listOf(TestZipEntry("readme.txt", "hi".toByteArray())))
        val noKmlStream = TrackingInputStream(ByteArrayInputStream(noKmlZip))
        val noKmlResult = AndroidGisImporter.importKmz(
            stream = noKmlStream,
            name = "nokml.kmz",
            sourceUri = "content://test/nokml.kmz",
        )
        assertFalse(noKmlResult.isSuccess)
        assertTrue(noKmlResult.errorMessage!!.contains("no .kml document"))
        assertTrue(noKmlStream.closed)

        val noShpZip = buildZip(listOf(TestZipEntry("readme.txt", "hi".toByteArray())))
        val noShpStream = TrackingInputStream(ByteArrayInputStream(noShpZip))
        val noShpResult = AndroidGisImporter.importZippedShapefile(
            stream = noShpStream,
            name = "noshp.zip",
            sourceUri = "content://test/noshp.zip",
        )
        assertFalse(noShpResult.isSuccess)
        assertTrue(noShpResult.errorMessage!!.contains("no .shp"))
        assertTrue(noShpStream.closed)

        val kmlBytes = simplePointKml("Trunc", "USFS", -105.0, 40.0)
            .toByteArray(StandardCharsets.UTF_8)
        val validKmz = buildZip(
            listOf(
                TestZipEntry("doc.kml", kmlBytes),
                TestZipEntry("extra.txt", ByteArray(256) { 0x33 }),
            ),
        )
        val malformedPayloads = listOf(
            ByteArray(0),
            "not-a-zip".toByteArray(StandardCharsets.US_ASCII),
            byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03),
            validKmz.copyOf(validKmz.size / 2),
        )
        for (payload in malformedPayloads) {
            val kmzStream = TrackingInputStream(ByteArrayInputStream(payload))
            val kmzResult = AndroidGisImporter.importKmz(
                stream = kmzStream,
                name = "corrupt.kmz",
                sourceUri = "content://test/corrupt.kmz",
            )
            assertFalse(kmzResult.isSuccess)
            assertNull(kmzResult.dataset)
            assertFalse(kmzResult.errorMessage.isNullOrBlank())
            assertTrue(kmzStream.closed)

            val shpStream = TrackingInputStream(ByteArrayInputStream(payload))
            val shpResult = AndroidGisImporter.importZippedShapefile(
                stream = shpStream,
                name = "corrupt.zip",
                sourceUri = "content://test/corrupt.zip",
            )
            assertFalse(shpResult.isSuccess)
            assertNull(shpResult.dataset)
            assertFalse(shpResult.errorMessage.isNullOrBlank())
            assertTrue(shpStream.closed)
        }

        val multiShp = buildZip(
            listOf(
                TestZipEntry("a.shp", buildSinglePointShp(-105.0, 40.0)),
                TestZipEntry("a.dbf", buildSingleRecordDbf("Campground")),
            ),
        )
        val truncatedShpStream = TrackingInputStream(
            ByteArrayInputStream(multiShp.copyOf(multiShp.size / 2)),
        )
        val truncatedShpResult = AndroidGisImporter.importZippedShapefile(
            stream = truncatedShpStream,
            name = "trunc_shp.zip",
            sourceUri = "content://test/trunc_shp.zip",
        )
        assertFalse(truncatedShpResult.isSuccess)
        assertNull(truncatedShpResult.dataset)
        assertFalse(truncatedShpResult.errorMessage.isNullOrBlank())
        assertTrue(truncatedShpStream.closed)
    }

    @Test
    fun importKmz_drainsRemainderAfterMaxVerticesEarlyStopAndEnforcesTimeout() {
        val oversizedAfterVertexCapKml = buildString(600_000) {
            append("<kml><Document><Placemark><name>Dense</name><LineString><coordinates>")
            for (i in 0 until 100_005) {
                append("0,0 ")
            }
            append("</coordinates></LineString></Placemark><!--")
            repeat(150_000) { append(' ') }
            append("--></Document></kml>")
        }.toByteArray(StandardCharsets.UTF_8)
        val capZip = buildZip(
            listOf(TestZipEntry("doc.kml", oversizedAfterVertexCapKml, stored = true)),
        )
        val capLimit = AndroidGisImporter.ArchiveLimits(
            maxEntryBytes = 450_000L,
            maxTotalDecompressedBytes = 450_000L,
        )
        val capStream = TrackingInputStream(ByteArrayInputStream(capZip))
        val capResult = AndroidGisImporter.importKmz(
            stream = capStream,
            name = "dense.kmz",
            sourceUri = "content://test/dense.kmz",
            limits = capLimit,
        )
        assertFalse(capResult.isSuccess)
        assertNull(capResult.dataset)
        assertTrue(capResult.errorMessage!!.contains("declared size exceeds limit") || capResult.errorMessage!!.contains("exceeds limit"))
        assertTrue(capStream.closed)

        val deflatedRemainderZip = buildZip(
            listOf(TestZipEntry("doc.kml", oversizedAfterVertexCapKml, stored = false)),
        )
        val deflatedStream = TrackingInputStream(ByteArrayInputStream(deflatedRemainderZip))
        val deflatedResult = AndroidGisImporter.importKmz(
            stream = deflatedStream,
            name = "dense_deflated.kmz",
            sourceUri = "content://test/dense_deflated.kmz",
            limits = AndroidGisImporter.ArchiveLimits(
                maxEntryBytes = 450_000L,
                maxTotalDecompressedBytes = 450_000L,
                minBytesForRatioCheck = 10_000_000L,
            ),
        )
        assertFalse(deflatedResult.isSuccess)
        assertNull(deflatedResult.dataset)
        assertTrue(deflatedResult.errorMessage!!.contains("exceeds limit"))
        assertTrue(deflatedStream.closed)

        val smallKml = simplePointKml("Timeout", "USFS", -105.0, 40.0)
            .toByteArray(StandardCharsets.UTF_8)
        val smallZip = buildZip(listOf(TestZipEntry("doc.kml", smallKml)))
        val ticks = AtomicInteger(0)
        val timeoutStream = TrackingInputStream(ByteArrayInputStream(smallZip))
        val timeoutResult = AndroidGisImporter.importKmz(
            stream = timeoutStream,
            name = "slow.kmz",
            sourceUri = "content://test/slow.kmz",
            limits = AndroidGisImporter.ArchiveLimits(timeoutMs = 1_000L),
            clock = {
                if (ticks.getAndIncrement() == 0) 0L else 5_000L
            },
        )
        assertFalse(timeoutResult.isSuccess)
        assertNull(timeoutResult.dataset)
        assertTrue(timeoutResult.errorMessage!!.contains("timed out"))
        assertTrue(timeoutStream.closed)
    }

    private data class TestZipEntry(
        val name: String,
        val bytes: ByteArray,
        val isDirectory: Boolean = false,
        val stored: Boolean = false,
    )

    private class TrackingInputStream(
        delegate: InputStream,
    ) : FilterInputStream(delegate) {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private fun buildZip(entries: List<TestZipEntry>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.setLevel(Deflater.BEST_COMPRESSION)
            for (item in entries) {
                val entry = ZipEntry(item.name)
                if (item.isDirectory) {
                    entry.method = ZipEntry.STORED
                    entry.size = 0L
                    entry.compressedSize = 0L
                    entry.crc = 0L
                    zos.putNextEntry(entry)
                    zos.closeEntry()
                } else {
                    if (item.stored) {
                        val crc = CRC32().apply { update(item.bytes) }.value
                        entry.method = ZipEntry.STORED
                        entry.size = item.bytes.size.toLong()
                        entry.compressedSize = item.bytes.size.toLong()
                        entry.crc = crc
                    }
                    zos.putNextEntry(entry)
                    zos.write(item.bytes)
                    zos.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    private fun patchSingleStoredLocalUncompressedSize(zipBytes: ByteArray, spoofedSize: Int) {
        val buffer = ByteBuffer.wrap(zipBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(22, spoofedSize)
    }

    private fun simplePointKml(
        name: String,
        agency: String,
        lon: Double,
        lat: Double,
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>$name</name>
                  <ExtendedData>
                    <Data name="Agency"><value>$agency</value></Data>
                  </ExtendedData>
                  <Point>
                    <coordinates>$lon,$lat,1850</coordinates>
                  </Point>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()
    }

    private fun buildSinglePointShp(x: Double, y: Double): ByteArray {
        val buffer = ByteBuffer.allocate(128)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(9994)
        buffer.position(24)
        buffer.putInt(128 / 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1000)
        buffer.putInt(1)
        buffer.putDouble(x)
        buffer.putDouble(y)
        buffer.putDouble(x)
        buffer.putDouble(y)
        buffer.putDouble(0.0)
        buffer.putDouble(0.0)
        buffer.putDouble(0.0)
        buffer.putDouble(0.0)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(1)
        buffer.putInt(10)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1)
        buffer.putDouble(x)
        buffer.putDouble(y)
        return buffer.array()
    }

    private fun buildSingleRecordDbf(value: String): ByteArray {
        val buffer = ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x03.toByte())
        buffer.put(26.toByte())
        buffer.put(11.toByte())
        buffer.put(14.toByte())
        buffer.putInt(1)
        buffer.putShort(65.toShort())
        buffer.putShort(11.toShort())
        buffer.position(32)
        val fieldName = "NAME\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
        buffer.put(fieldName.toByteArray(StandardCharsets.US_ASCII), 0, 11)
        buffer.put('C'.code.toByte())
        buffer.putInt(0)
        buffer.put(10.toByte())
        buffer.put(0.toByte())
        for (i in 0 until 14) buffer.put(0.toByte())
        buffer.put(0x0D.toByte())
        buffer.put(' '.code.toByte())
        val padded = value.padEnd(10, ' ').take(10)
        buffer.put(padded.toByteArray(StandardCharsets.US_ASCII), 0, 10)
        return buffer.array()
    }
}
