// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import org.junit.Assert.assertEquals
import org.junit.Test

final class MapNameFormatterTest {
    @Test
    fun stripsExtensionAndTitleCases() {
        assertEquals("Baghdad Sector 4 1991", MapNameFormatter.format("baghdad_sector_4_1991.mbtiles"))
        assertEquals("Tokyo Historical 1945", MapNameFormatter.format("tokyo_historical-1945.mbtiles"))
    }

    @Test
    fun extensionStripIsCaseInsensitive() {
        assertEquals("Kyiv North", MapNameFormatter.format("kyiv_north.MBTILES"))
        assertEquals("Kyiv North", MapNameFormatter.format("kyiv_north.Mbtiles"))
    }

    @Test
    fun onlyATrailingExtensionIsStripped() {
        assertEquals("Atlas.mbtiles Backup", MapNameFormatter.format("atlas.mbtiles backup.mbtiles"))
    }

    @Test
    fun missingExtensionIsLeftIntact() {
        assertEquals("Plain Name", MapNameFormatter.format("plain_name"))
    }

    @Test
    fun repeatedAndEdgeDelimitersCollapse() {
        assertEquals("A B", MapNameFormatter.format("__a___b__.mbtiles"))
        assertEquals("A B", MapNameFormatter.format("-a--b-.mbtiles"))
    }

    @Test
    fun emptyAndDelimiterOnlyInputsAreSafe() {
        assertEquals("", MapNameFormatter.format(""))
        assertEquals("", MapNameFormatter.format("__--__.mbtiles"))
        assertEquals("", MapNameFormatter.format(".mbtiles"))
    }

    @Test
    fun innerCasingIsPreservedForAcronyms() {
        // Only the first character is uppercased, so acronyms survive intact.
        assertEquals("NATO Grid 7", MapNameFormatter.format("NATO_grid-7.mbtiles"))
        assertEquals("City MAP", MapNameFormatter.format("city_MAP.mbtiles"))
    }

    @Test
    fun digitsAreNotAltered() {
        assertEquals("Sector 42 1991", MapNameFormatter.format("sector_42_1991.mbtiles"))
    }

    @Test
    fun formattingIsDeterministicAndIdempotent() {
        val input = "baghdad_sector_4_1991.mbtiles"
        val once = MapNameFormatter.format(input)
        assertEquals(once, MapNameFormatter.format(input))
        assertEquals(once, MapNameFormatter.format(once))
    }
}
