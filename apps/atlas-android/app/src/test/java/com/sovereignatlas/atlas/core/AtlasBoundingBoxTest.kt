// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

final class AtlasBoundingBoxTest {
    @Test
    fun plainBoxUnwrapsToItself() {
        val box = AtlasBoundingBox(south = 51.4, west = 5.9, north = 51.6, east = 6.1)
        assertFalse(box.crossesAntimeridian)
        val parts = box.unwrap()
        assertEquals(1, parts.size)
        assertEquals(box, parts[0])
    }

    @Test
    fun antimeridianBoxSplitsAtDatumEdges() {
        val box = AtlasBoundingBox(south = -10.0, west = 179.0, north = 10.0, east = -179.0)
        assertTrue(box.crossesAntimeridian)
        val parts = box.unwrap()
        assertEquals(2, parts.size)
        assertEquals(179.0, parts[0].west, 0.0)
        assertEquals(180.0, parts[0].east, 0.0)
        assertEquals(-180.0, parts[1].west, 0.0)
        assertEquals(-179.0, parts[1].east, 0.0)
        for (part in parts) {
            assertFalse(part.crossesAntimeridian)
            assertTrue(part.validate().isValid)
        }
    }

    @Test
    fun degenerateLatitudeFailsValidation() {
        val box = AtlasBoundingBox(south = 10.0, west = 0.0, north = -10.0, east = 1.0)
        assertFalse(box.validate().isValid)
    }
}
