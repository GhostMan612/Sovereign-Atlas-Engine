// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.graphics

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.graphics.OperationalGraphic
import com.sovereignatlas.atlas.geo.graphics.ZoneType
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun at(latitude: Double, longitude: Double) =
    AtlasCoordinate(latitude = latitude, longitude = longitude)

private fun triangle() = listOf(at(44.0, -93.0), at(45.0, -93.0), at(45.0, -92.0))

/**
 * Tactical drawings, asserted against the pure geometry the mapper now emits.
 *
 * The ring-closure tests are the ones that matter most here. RFC 7946 requires a
 * linear ring's first and last position to be identical, and the MapLibre style
 * silently drops a polygon that violates it — a zone that simply never appears, with
 * no error. That rule is a domain decision about the operator's drawing, so it lives
 * in the mapper rather than in the generic converter, and it is asserted here.
 */
final class GraphicsGeoJsonMapperTest {

    @Test
    fun lineBecomesLineStringWithColorAndWidth() {
        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(
                OperationalGraphic.TacticalLine(
                    id = "phase-line",
                    points = listOf(at(44.0, -93.0), at(44.5, -92.5)),
                    colorHex = "#FFFF00",
                    width = 4.0f,
                ),
            ),
        )

        val features = collection.features
        assertEquals(1, features.size)
        val line = features[0].geometry as RenderGeometry.LineString
        assertEquals(2, line.coordinates.size)
        assertEquals("phase-line", features[0].id)
        assertEquals(RenderProperty.Text("#FFFF00"), features[0].properties["color"])
        assertEquals(4.0, (features[0].properties["width"] as RenderProperty.Number).value, 0.0001)
    }

    @Test
    fun lineUsesLongitudeAsXAndLatitudeAsY() {
        // RFC 7946 fixes longitude first. A reversed pair is a silent, geographically
        // wrong read rather than a compile error, so this is asserted directly.
        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(
                OperationalGraphic.TacticalLine(
                    id = "l",
                    points = listOf(at(10.0, 20.0), at(11.0, 21.0)),
                ),
            ),
        )

        val line = collection.features[0].geometry as RenderGeometry.LineString
        assertEquals(20.0, line.coordinates[0].longitude, 0.0)
        assertEquals(10.0, line.coordinates[0].latitude, 0.0)
    }

    @Test
    fun widthIsANumberNotAString() {
        // The style reads `width` through an expression expecting a number. A string
        // there fails to paint with nothing in the log — the reason the property type
        // is a closed union rather than a raw map.
        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(OperationalGraphic.TacticalLine("d", listOf(at(0.0, 0.0), at(1.0, 1.0)))),
        )

        val width = collection.features[0].properties["width"]
        assertTrue("width must be a Number", width is RenderProperty.Number)
    }

    @Test
    fun zoneBecomesClosedPolygonWithFillProperties() {
        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(
                OperationalGraphic.TacticalZone(
                    id = "medevac",
                    points = triangle(),
                    type = ZoneType.MEDEVAC,
                ),
            ),
        )

        val feature = collection.features[0]
        val ring = (feature.geometry as RenderGeometry.Polygon).coordinates[0]
        assertEquals(4, ring.size)
        assertEquals(ring.first(), ring.last())
        assertEquals("medevac", feature.id)
        assertEquals(RenderProperty.Text("#00FF00"), feature.properties["fillColor"])
        // Delta, not exact equality. ZoneType.alpha is a Float, and 0.3f widened to
        // Double is 0.30000001192092896. The previous version of this test compared
        // against a MapLibre property bag, which also rounded, so an exact match here
        // would be asserting an arithmetic accident rather than a mapping decision.
        val opacity = feature.properties["fillOpacity"] as RenderProperty.Number
        assertEquals(0.3, opacity.value, 0.0001)
    }

    @Test
    fun anOpenRingIsClosedByTheMapper() {
        val ring = (
            GraphicsGeoJsonMapper.toRenderFeatures(
                listOf(OperationalGraphic.TacticalZone("z", triangle(), ZoneType.OBJECTIVE)),
            ).features[0].geometry as RenderGeometry.Polygon
            ).coordinates[0]

        assertEquals("the ring must start and end at the same position", ring.first(), ring.last())
    }

    @Test
    fun alreadyClosedZoneIsNotDuplicated() {
        val closed = triangle() + at(44.0, -93.0)

        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(
                OperationalGraphic.TacticalZone(
                    id = "closed",
                    points = closed,
                    type = ZoneType.OBJECTIVE,
                ),
            ),
        )

        val ring = (collection.features[0].geometry as RenderGeometry.Polygon).coordinates[0]
        assertEquals(4, ring.size)
    }

    @Test
    fun degenerateGeometriesAreDropped() {
        // Fewer than two points is not a line; fewer than three is not an area.
        // Emitting either would make the style drop the feature with no log.
        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(
                OperationalGraphic.TacticalLine("one-point", listOf(at(1.0, 2.0))),
                OperationalGraphic.TacticalZone(
                    "two-points",
                    listOf(at(1.0, 2.0), at(3.0, 4.0)),
                    ZoneType.RESTRICTED,
                ),
            ),
        )

        assertTrue(collection.features.isEmpty())
    }

    @Test
    fun zoneCarriesItsOwnPalette() {
        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(
                OperationalGraphic.TacticalZone("z", triangle(), ZoneType.RESTRICTED),
                OperationalGraphic.TacticalZone("o", triangle(), ZoneType.OBJECTIVE),
            ),
        )

        val features = collection.features
        assertEquals(RenderProperty.Text("#FF0000"), features[0].properties["fillColor"])
        assertEquals(RenderProperty.Text("#FFA500"), features[1].properties["fillColor"])
    }

    @Test
    fun lineDefaultsMatchDomainDefaults() {
        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(OperationalGraphic.TacticalLine("d", listOf(at(0.0, 0.0), at(1.0, 1.0)))),
        )

        val feature = collection.features[0]
        assertEquals(RenderProperty.Text("#FFFF00"), feature.properties["color"])
        // Float 3.0f widens to Double 3.0 exactly, but width is declared Float in the
        // domain and Double in the property, so the conversion is asserted rather than
        // assumed — a future width type change would move this number silently.
        assertEquals(3.0, (feature.properties["width"] as RenderProperty.Number).value, 0.0001)
    }

    @Test
    fun theWideningFromFloatIsExactForTheseDomainValues() {
        // Named explicitly because the opacity test above needed a tolerance and this
        // one does not. Both are Float in the domain; the difference is that 3.0 and
        // 4.0 are representable in binary floating point and 0.3 is not.
        for (width in listOf(3.0f, 4.0f, 8.0f, 1.0f, 2.0f)) {
            assertEquals(
                "width $width must widen exactly",
                width.toDouble(),
                width.toDouble(),
                0.0,
            )
        }
        assertTrue(
            "0.3f is NOT exactly 0.3 as a Double, which is why the opacity " +
                "assertion needs a tolerance",
            kotlin.math.abs(0.3f.toDouble() - 0.3) > 0.0,
        )
    }

    @Test
    fun firstPointIsPreservedAsRingStart() {
        val collection = GraphicsGeoJsonMapper.toRenderFeatures(
            listOf(OperationalGraphic.TacticalZone("z", triangle(), ZoneType.MEDEVAC)),
        )

        val ring = (collection.features[0].geometry as RenderGeometry.Polygon).coordinates[0]
        assertEquals(-93.0, ring.first().longitude, 0.0)
        assertEquals(44.0, ring.first().latitude, 0.0)
    }
}
