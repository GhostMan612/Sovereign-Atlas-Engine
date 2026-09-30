// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.map.AtlasLayerIds.HISTORICAL_RASTER_LAYER_PREFIX
import com.sovereignatlas.atlas.map.AtlasLayerIds.HISTORICAL_RASTER_SOURCE_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class HistoricalRasterLayerIdTest {

    @Test
    fun idsCarryTheExpectedPrefixes() {
        assertEquals(
            HISTORICAL_RASTER_SOURCE_PREFIX + "denver-1897",
            historicalRasterSourceId("denver-1897"),
        )
        assertEquals(
            HISTORICAL_RASTER_LAYER_PREFIX + "denver-1897",
            historicalRasterLayerId("denver-1897"),
        )
    }

    @Test
    fun aPathTraversalInAnIdCannotEscapeIntoTheStyle() {
        // A blueprint id can come from pack metadata, which is untrusted input. A
        // slash in it would produce a layer id that style.getLayer never matches,
        // and a "../" would name a layer outside the historical namespace.
        val id = historicalRasterLayerId("../../../etc/passwd")

        assertTrue(id.startsWith(HISTORICAL_RASTER_LAYER_PREFIX))
        assertNotEquals(HISTORICAL_RASTER_LAYER_PREFIX + "../../../etc/passwd", id)
        assertTrue(id.none { it == '/' })
        assertTrue(id.none { it == '.' })
    }

    @Test
    fun distinctIdsProduceDistinctLayerIds() {
        assertNotEquals(historicalRasterLayerId("a"), historicalRasterLayerId("b"))
        assertNotEquals(historicalRasterSourceId("a"), historicalRasterSourceId("b"))
    }
}
