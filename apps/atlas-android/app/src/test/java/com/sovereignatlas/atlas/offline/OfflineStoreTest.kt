// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun tempDir(): File = Files.createTempDirectory("atlas-offline").toFile()

private fun store(dir: File): OfflineStore = OfflineStore(directoryProvider = { dir })

final class OfflineProvidersTest {
    @Test
    fun osmUrlResolvesXYZ() {
        assertEquals(
            "https://tile.openstreetmap.org/10/1/2.png",
            resolveTileUrl(OfflineBuiltinProviders.osmStandard, 10, 1, 2),
        )
    }

    @Test
    fun esriUrlUsesZYXOrder() {
        assertEquals(
            "https://server.arcgisonline.com/ArcGIS/rest/services/" +
                "World_Imagery/MapServer/tile/10/2/1",
            resolveTileUrl(OfflineBuiltinProviders.esriImagery, 10, 1, 2),
        )
    }

    @Test
    fun openTopoMapFillsSubdomain() {
        assertEquals(
            "https://a.tile.opentopomap.org/10/1/2.png",
            resolveTileUrl(OfflineBuiltinProviders.openTopoMap, 10, 1, 2),
        )
    }

    @Test
    fun localBundleHasNoUrl() {
        assertEquals(
            null,
            resolveTileUrl(OfflineBuiltinProviders.localBundle, 10, 1, 2),
        )
    }

    @Test
    fun cartoPositronResolvesWithSubdomain() {
        assertEquals(
            "https://a.basemaps.cartocdn.com/light_all/10/1/2.png",
            resolveTileUrl(OfflineBuiltinProviders.cartoPositron, 10, 1, 2),
        )
    }
}

final class OfflineStoreTest {
    @Test
    fun planSingleTilePack() {
        val target = store(tempDir())
        val outcome = target.plan(
            providerId = "esri-imagery",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 1,
            yMin = 2,
            yMax = 2,
            approvedBulk = false,
            isPrefetch = false,
        )
        assertTrue(outcome is PlanOutcome.Planned)
        val record = (outcome as PlanOutcome.Planned).record
        assertEquals("pack-000001", record.packId)
        assertEquals(1, record.tileCount)
        assertEquals(OfflinePackLifecycle.planned, record.lifecycle)
        assertTrue(target.events().isNotEmpty())
    }

    @Test
    fun osmWithoutApprovalHitsBulkGuard() {
        val target = store(tempDir())
        val outcome = target.plan(
            providerId = "osm-standard",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 1,
            yMin = 2,
            yMax = 2,
            approvedBulk = false,
            isPrefetch = false,
        )
        assertTrue(outcome is PlanOutcome.Refused)
        assertTrue((outcome as PlanOutcome.Refused).reason.startsWith("BULK_GUARD"))
    }

    @Test
    fun osmWithApprovalPlans() {
        val target = store(tempDir())
        val outcome = target.plan(
            providerId = "osm-standard",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 1,
            yMin = 2,
            yMax = 2,
            approvedBulk = true,
            isPrefetch = false,
        )
        assertTrue(outcome is PlanOutcome.Planned)
    }

    @Test
    fun unknownProviderRefused() {
        val target = store(tempDir())
        val outcome = target.plan(
            providerId = "nope",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 1,
            yMin = 2,
            yMax = 2,
            approvedBulk = true,
            isPrefetch = false,
        )
        assertTrue(outcome is PlanOutcome.Refused)
    }

    @Test
    fun overQuotaRefused() {
        val target = store(tempDir())
        val outcome = target.plan(
            providerId = "esri-imagery",
            zMin = 0,
            zMax = 19,
            xMin = 0,
            xMax = 524287,
            yMin = 0,
            yMax = 524287,
            approvedBulk = true,
            isPrefetch = false,
        )
        assertTrue(outcome is PlanOutcome.Refused)
    }

    @Test
    fun localBundleBlocked() {
        val target = store(tempDir())
        val outcome = target.plan(
            providerId = "local-bundle",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 1,
            yMin = 2,
            yMax = 2,
            approvedBulk = true,
            isPrefetch = false,
        )
        assertTrue(outcome is PlanOutcome.Blocked)
    }

    @Test
    fun roundTripPersistsIndex() {
        val dir = tempDir()
        val first = store(dir)
        first.plan(
            providerId = "esri-imagery",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 1,
            yMin = 2,
            yMax = 2,
            approvedBulk = false,
            isPrefetch = false,
        )
        val second = store(dir)
        second.restore()
        assertEquals(1, second.packs().size)
        assertEquals("pack-000001", second.packs().single().packId)
        assertEquals(null, second.lastErrorOrNull())
    }

    @Test
    fun removeDeletesPack() {
        val target = store(dir = tempDir())
        target.plan(
            providerId = "esri-imagery",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 1,
            yMin = 2,
            yMax = 2,
            approvedBulk = false,
            isPrefetch = false,
        )
        assertTrue(target.removePack("pack-000001"))
        assertFalse(target.removePack("pack-000001"))
        assertTrue(target.packs().isEmpty())
    }

    @Test
    fun downloadWritesTilesAndManifest() {
        val dir = tempDir()
        val target = store(dir)
        val outcome = target.plan(
            providerId = "esri-imagery",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 2,
            yMin = 2,
            yMax = 2,
            approvedBulk = false,
            isPrefetch = false,
        ) as PlanOutcome.Planned
        val downloader = OfflineDownloader { _, _, _, _ -> byteArrayOf(1, 2, 3) }
        var progress = 0
        val result = downloader.download(
            record = outcome.record,
            descriptor = OfflineBuiltinProviders.esriImagery,
            dir = target.packDir(outcome.record.packId),
            onProgress = { tiles, _ -> progress = tiles },
            isCancelled = { false },
        )
        assertTrue(result is DownloadResult.Complete)
        assertEquals(2, progress)
        assertEquals(OfflinePackLifecycle.complete, outcome.record.lifecycle)
        assertTrue(File(target.packDir(outcome.record.packId), "10/1/2.png").exists())
        assertTrue(File(target.packDir(outcome.record.packId), "manifest.json").exists())
    }

    @Test
    fun downloadCancelStopsEarly() {
        val dir = tempDir()
        val target = store(dir)
        val outcome = target.plan(
            providerId = "esri-imagery",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 5,
            yMin = 2,
            yMax = 2,
            approvedBulk = false,
            isPrefetch = false,
        ) as PlanOutcome.Planned
        val downloader = OfflineDownloader { _, _, _, _ -> byteArrayOf(1) }
        var calls = 0
        val result = downloader.download(
            record = outcome.record,
            descriptor = OfflineBuiltinProviders.esriImagery,
            dir = target.packDir(outcome.record.packId),
            onProgress = { _, _ -> },
            isCancelled = {
                calls += 1
                calls > 2
            },
        )
        assertTrue(result is DownloadResult.Cancelled)
        assertEquals(OfflinePackLifecycle.cancelled, outcome.record.lifecycle)
    }

    @Test
    fun downloadFailureMarksRecord() {
        val dir = tempDir()
        val target = store(dir)
        val outcome = target.plan(
            providerId = "esri-imagery",
            zMin = 10,
            zMax = 10,
            xMin = 1,
            xMax = 1,
            yMin = 2,
            yMax = 2,
            approvedBulk = false,
            isPrefetch = false,
        ) as PlanOutcome.Planned
        val downloader = OfflineDownloader { _, _, _, _ -> null }
        val result = downloader.download(
            record = outcome.record,
            descriptor = OfflineBuiltinProviders.esriImagery,
            dir = target.packDir(outcome.record.packId),
            onProgress = { _, _ -> },
            isCancelled = { false },
        )
        assertTrue(result is DownloadResult.Failed)
        assertEquals(OfflinePackLifecycle.failed, outcome.record.lifecycle)
        assertTrue(outcome.record.failureDetail.isNotEmpty())
    }
}
