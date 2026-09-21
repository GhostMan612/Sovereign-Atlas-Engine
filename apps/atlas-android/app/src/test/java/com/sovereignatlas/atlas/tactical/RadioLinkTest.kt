// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class RadioLinkTest {
    @Test
    fun fsplMatchesTac2004() {
        assertEquals(
            100.2,
            AtlasRadioLink.freeSpaceLossDb(1000.0, 2442.0),
            0.05,
        )
    }

    @Test
    fun marginMatchesTac2004() {
        assertEquals(
            9.8,
            AtlasRadioLink.linkMarginDb(
                distanceMeters = 1000.0,
                frequencyMHz = 2442.0,
                txPowerDbm = 20.0,
                rxSensitivityDbm = -90.0,
                antennaGainDbi = 0.0,
                extraLossDb = 0.0,
            ),
            0.05,
        )
    }

    @Test
    fun nonPositiveInputsThrow() {
        try {
            AtlasRadioLink.freeSpaceLossDb(0.0, 2442.0)
            throw AssertionError("zero distance must throw")
        } catch (error: IllegalArgumentException) {
            Unit
        }
        try {
            AtlasRadioLink.freeSpaceLossDb(1000.0, -1.0)
            throw AssertionError("negative frequency must throw")
        } catch (error: IllegalArgumentException) {
            Unit
        }
    }

    @Test
    fun rangeIsHaversineMeters() {
        val range = AtlasRadioLink.rangeMeters(
            AtlasCoordinate(latitude = 0.0, longitude = 0.0),
            AtlasCoordinate(latitude = 0.0, longitude = 1.0),
        )
        assertTrue(range > 111000.0 && range < 112000.0)
    }
}
