// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.cot

import com.sovereignatlas.atlas.geo.cot.CotMarker
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun marker(
    uid: String = "m-1",
    type: String = "a-h-G",
    callsign: String = "Hostile",
    latitude: Double = 44.9,
    longitude: Double = -93.1,
): CotMarker = CotMarker(
    uid = uid,
    type = type,
    callsign = callsign,
    latitude = latitude,
    longitude = longitude,
    altitude = null,
    timestampMillis = 1000L,
)

/**
 * Mesh marker mapping, asserted against the pure types it now emits.
 *
 * The affiliation assertions are the safety-relevant ones. The taxonomy is the third
 * letter onward, not "a- is friendly": filing a hostile as a friendly is the error
 * the entire classification exists to prevent, and it shows up on the operator's map
 * as a wrong icon. An unrecognised type must therefore land on `unknown`, never on
 * `friendly`.
 */
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
    fun noCotTypeEverDerivesFriendlyAccidentally() {
        // The one property that must hold absolutely: nothing that is not explicitly
        // friendly can reach the friendly branch.
        val safe = listOf("a-h-G", "a-n-G", "a-u-G", "b-m-p-w", "", "x", "a-", "a-f")
        for (type in safe) {
            val affiliation = CotGeoJsonMapper.deriveAffiliation(type)
            if (type != "a-f") {
                assertTrue(
                    "'$type' must not derive friendly (got $affiliation)",
                    affiliation != "friendly",
                )
            }
        }
    }

    @Test
    fun pointUsesLongitudeAsXAndLatitudeAsY() {
        val collection = CotGeoJsonMapper.toRenderFeatures(
            listOf(marker(latitude = 10.5, longitude = -20.25)),
        )

        val point = collection.features[0].geometry as RenderGeometry.Point
        assertEquals(-20.25, point.coordinates.longitude, 0.0)
        assertEquals(10.5, point.coordinates.latitude, 0.0)
    }

    @Test
    fun theUidIsTheFeatureIdNotAProperty() {
        // Hit-testing recovers an id to act on. It is a constructor parameter now, so
        // a mapper cannot produce a marker the app is unable to select.
        val collection = CotGeoJsonMapper.toRenderFeatures(
            listOf(marker(uid = "m-1"), marker(uid = "m-2", type = "a-f-G")),
        )

        assertEquals(listOf("m-1", "m-2"), collection.features.map { it.id })
    }

    @Test
    fun featuresCarryIdentityAndAffiliation() {
        val collection = CotGeoJsonMapper.toRenderFeatures(
            listOf(
                marker(uid = "m-1", type = "a-f-G", callsign = "ALPHA"),
                marker(uid = "m-2", type = "a-u-G", callsign = "BRAVO"),
            ),
        )

        val features = collection.features
        assertEquals(2, features.size)
        assertEquals(RenderProperty.Text("ALPHA"), features[0].properties["callsign"])
        assertEquals(RenderProperty.Text("friendly"), features[0].properties["affiliation"])
        assertEquals(RenderProperty.Text("unknown"), features[1].properties["affiliation"])
    }

    @Test
    fun theAffiliationIsTheTextTheStyleLooksUpAsAnIconName() {
        // The style does iconImage(Expression.get("affiliation")), so this property
        // must be a string. The RenderProperty union is what makes that checkable.
        val collection = CotGeoJsonMapper.toRenderFeatures(listOf(marker(type = "a-h-G")))

        val affiliation = collection.features[0].properties["affiliation"]
        assertTrue("affiliation must be Text", affiliation is RenderProperty.Text)
        assertEquals("hostile", (affiliation as RenderProperty.Text).value)
    }

    @Test
    fun emptyListYieldsEmptyCollection() {
        val collection = CotGeoJsonMapper.toRenderFeatures(emptyList())

        assertTrue(collection.isEmpty)
    }
}
