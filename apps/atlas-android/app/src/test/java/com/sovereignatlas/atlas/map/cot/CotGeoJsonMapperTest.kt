// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.cot

import com.sovereignatlas.atlas.geo.cot.CotMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point

private fun marker(
    uid: String = "m-1",
    type: String = "a-h-G",
    callsign: String = "Hostile",
    latitude: Double = 44.9,
    longitude: Double = -93.1,
): CotMarker {
    return CotMarker(
        uid = uid,
        type = type,
        callsign = callsign,
        latitude = latitude,
        longitude = longitude,
        altitude = null,
        timestampMillis = 1000L,
    )
}

final class CotGeoJsonMapperTest {
    @Test
    fun affiliationDerivesFromCotType() {
        assertEquals("friendly", CotGeoJsonMapper.deriveAffiliation("a-f-G"))
        assertEquals("hostile", CotGeoJsonMapper.deriveAffiliation("a-h-X"))
        assertEquals("neutral", CotGeoJsonMapper.deriveAffiliation("a-n-X"))
    }

    @Test
    fun unknownTypesFallBackSafely() {
        assertEquals("unknown", CotGeoJsonMapper.deriveAffiliation("a-u-X"))
        assertEquals("unknown", CotGeoJsonMapper.deriveAffiliation("b-m-p-w"))
        assertEquals("unknown", CotGeoJsonMapper.deriveAffiliation(""))
    }

    @Test
    fun pointUsesLongitudeAsXAndLatitudeAsY() {
        val collection = CotGeoJsonMapper.toFeatureCollection(
            listOf(marker(latitude = 10.5, longitude = -20.25)),
        )
        val point = collection.features()!![0].geometry() as Point
        assertEquals(-20.25, point.longitude(), 0.0)
        assertEquals(10.5, point.latitude(), 0.0)
    }

    @Test
    fun featuresCarryIdentityAndAffiliation() {
        val collection = CotGeoJsonMapper.toFeatureCollection(
            listOf(
                marker(uid = "m-1", type = "a-f-G", callsign = "ALPHA"),
                marker(uid = "m-2", type = "a-u-G", callsign = "BRAVO"),
            ),
        )
        val features = collection.features()!!
        assertEquals(2, features.size)
        assertEquals("m-1", features[0].getStringProperty("uid"))
        assertEquals("ALPHA", features[0].getStringProperty("callsign"))
        assertEquals("friendly", features[0].getStringProperty("affiliation"))
        assertEquals("m-2", features[1].getStringProperty("uid"))
        assertEquals("unknown", features[1].getStringProperty("affiliation"))
    }

    @Test
    fun emptyListYieldsEmptyCollection() {
        val collection = CotGeoJsonMapper.toFeatureCollection(emptyList())
        assertTrue(collection.features()!!.isEmpty())
    }
}
