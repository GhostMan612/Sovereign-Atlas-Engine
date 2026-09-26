// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import org.junit.Assert.assertEquals
import org.junit.Test

final class OfflineMapTest {
    @Test
    fun formatDefaultsToPbf() {
        val map = OfflineMap(
            name = "region.mbtiles",
            absolutePath = "/data/region.mbtiles",
            sizeBytes = 1024L,
        )
        assertEquals("pbf", map.format)
    }

    @Test
    fun absolutePathIsRawFilesystemPath() {
        val map = OfflineMap(
            name = "region.mbtiles",
            absolutePath = "/data/region.mbtiles",
            sizeBytes = 1024L,
        )
        assertEquals("/data/region.mbtiles", map.absolutePath)
    }

    @Test
    fun copyPreservesValueSemantics() {
        val map = OfflineMap(
            name = "region.mbtiles",
            absolutePath = "/data/region.mbtiles",
            sizeBytes = 1024L,
        )
        assertEquals(map, map.copy())
    }

    @Test
    fun kindDefaultsToVector() {
        val map = OfflineMap(
            name = "region.mbtiles",
            absolutePath = "/data/region.mbtiles",
            sizeBytes = 1024L,
        )
        assertEquals(OfflineMapKind.VECTOR, map.kind)
    }

    @Test
    fun demKindCarriesTerrainFormat() {
        val map = OfflineMap(
            name = "alps_dem.mbtiles",
            absolutePath = "/data/alps_dem.mbtiles",
            sizeBytes = 2048L,
            format = "png",
            kind = OfflineMapKind.DEM,
        )
        assertEquals(OfflineMapKind.DEM, map.kind)
        assertEquals("png", map.format)
    }
}
