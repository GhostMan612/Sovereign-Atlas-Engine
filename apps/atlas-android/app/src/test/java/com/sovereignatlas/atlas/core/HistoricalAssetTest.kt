// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val BOX = AtlasBoundingBox(
    south = 39.0,
    west = -104.9,
    north = 39.1,
    east = -104.8,
)

final class HistoricalAssetTest {
    @Test
    fun landPatentCarriesProvenance() {
        val asset = LandPatent(
            id = "lp-1880-0042",
            title = "Township Survey Sheet 42",
            year = 1880,
            boundingBox = BOX,
            license = HistoricalLicense.PublicDomainUsOnly(),
            attribution = "Bureau of Land Management",
            patentNumber = "1880-0042",
        )

        assertEquals("lp-1880-0042", asset.id)
        assertEquals(1880, asset.year)
        assertEquals("1880-0042", asset.patentNumber)
        assertEquals(BOX, asset.boundingBox)
        assertEquals("Bureau of Land Management", asset.attribution)
    }

    @Test
    fun sanbornCarriesEditionAndSheet() {
        val asset = SanbornBlueprint(
            id = "sanborn-1897-010",
            title = "Denver Sanborn Volume 1",
            year = 1897,
            boundingBox = BOX,
            license = HistoricalLicense.PublicDomain,
            edition = 1,
            sheet = "Sheet 10",
        )

        assertEquals(1, asset.edition)
        assertEquals("Sheet 10", asset.sheet)
    }

    @Test
    fun blankIdIsRejected() {
        val failure = runCatching {
            LandPatent(
                id = "   ",
                title = "Sheet",
                year = 1880,
                boundingBox = BOX,
                license = HistoricalLicense.PublicDomain,
                patentNumber = "1",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun blankTitleIsRejected() {
        val failure = runCatching {
            SanbornBlueprint(
                id = "sanborn-1",
                title = "",
                year = 1897,
                boundingBox = BOX,
                license = HistoricalLicense.PublicDomain,
                edition = 1,
                sheet = "10",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun nonPositiveEditionIsRejected() {
        val failure = runCatching {
            SanbornBlueprint(
                id = "sanborn-1",
                title = "Volume",
                year = 1897,
                boundingBox = BOX,
                license = HistoricalLicense.PublicDomain,
                edition = 0,
                sheet = "10",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun onlyUnrestrictedPublicDomainIsRedistributable() {
        assertTrue(HistoricalLicense.PublicDomain.isRedistributable)
        assertTrue(
            HistoricalLicense.PublicDomainDedicated("CC0-1.0").isRedistributable,
        )
        assertFalse(HistoricalLicense.PublicDomainUsOnly().isRedistributable)
        assertFalse(HistoricalLicense.Unverified("unknown").isRedistributable)
    }

    @Test
    fun usOnlyIsNotEquivalentToWorldwidePublicDomain() {
        // The whole point of the sealed split: a US-only release must not
        // satisfy a worldwide redistribution check.
        val usOnly: HistoricalLicense = HistoricalLicense.PublicDomainUsOnly()
        assertFalse(usOnly.isRedistributable)
        assertFalse(usOnly is HistoricalLicense.PublicDomain)
    }

    @Test
    fun attributionDefaultsToNullWhenUnrecorded() {
        val asset = LandPatent(
            id = "lp-1",
            title = "Sheet",
            year = 1880,
            boundingBox = BOX,
            license = HistoricalLicense.Unverified("no rights found"),
            patentNumber = "1",
        )

        assertNull(asset.attribution)
        assertNull(asset.township)
    }

    @Test
    fun boundingBoxIsReusedNotDuplicated() {
        // Guards the engine boundary: the model reuses the existing pure
        // AtlasBoundingBox rather than declaring a second, divergent one.
        val asset: HistoricalAsset = SanbornBlueprint(
            id = "s-1",
            title = "Sheet",
            year = 1897,
            boundingBox = BOX,
            license = HistoricalLicense.PublicDomain,
            edition = 1,
            sheet = "1",
        )
        val box: AtlasBoundingBox = asset.boundingBox
        assertEquals(39.0, box.south, 0.0)
        assertEquals(-104.8, box.east, 0.0)
        assertFalse(box.crossesAntimeridian)
    }

    @Test
    fun assetsRemainPureKotlinWithNoPlatformTypes() {
        // The historical models must stay in the pure engine. This asserts the
        // package does not reach for platform types at the model boundary.
        val types = listOf(
            HistoricalAsset::class.java,
            LandPatent::class.java,
            SanbornBlueprint::class.java,
            HistoricalLicense::class.java,
        )
        val forbidden = listOf(
            "android.",
            "org.maplibre.",
            "java.net.",
        )
        for (type in types) {
            for (name in forbidden) {
                assertFalse(
                    "${type.name} must not reference $name",
                    type.name.startsWith(name),
                )
            }
        }
    }
}
