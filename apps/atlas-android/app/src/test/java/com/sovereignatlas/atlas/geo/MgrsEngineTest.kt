// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class MgrsEngineTest {
    private val straddle = AtlasBoundingBox(
        south = 51.4,
        west = 5.9,
        north = 51.6,
        east = 6.1,
    )

    @Test
    fun zoneBoundaryServesBothSides() {
        val grid = MgrsEngine.generate(straddle, 10.0)
        assertTrue(grid.lines.isNotEmpty())
        val west = grid.lines.any { line -> line.coordinates.any { it.second < 6.0 } }
        val east = grid.lines.any { line -> line.coordinates.any { it.second > 6.0 } }
        assertTrue("lines west of 6E", west)
        assertTrue("lines east of 6E", east)
    }

    @Test
    fun noLineInterpolatesAcrossTheBoundaryGap() {
        val grid = MgrsEngine.generate(straddle, 10.0)
        for (line in grid.lines) {
            val points = line.coordinates
            for (index in 1 until points.size) {
                val gapKm = AtlasGeoMath.haversineKm(
                    AtlasCoordinate(points[index - 1].first, points[index - 1].second),
                    AtlasCoordinate(points[index].first, points[index].second),
                )
                assertTrue("gap $gapKm km within densify step", gapKm < 15.0)
            }
        }
    }

    @Test
    fun denseZoomSuppressesGrid() {
        val grid = MgrsEngine.generate(straddle, 16.0)
        assertEquals(0, grid.lines.size)
        assertEquals(0, grid.labels.size)
    }

    @Test
    fun labelsAreBounded() {
        val grid = MgrsEngine.generate(straddle, 10.0)
        assertTrue(grid.labels.size <= MgrsEngine.MAX_GRID_LABELS)
        for (label in grid.labels) {
            assertTrue(label.text.isNotEmpty())
        }
    }
}
