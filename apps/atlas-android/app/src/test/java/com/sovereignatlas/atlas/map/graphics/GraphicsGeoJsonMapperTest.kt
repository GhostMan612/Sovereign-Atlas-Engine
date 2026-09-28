// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map.graphics

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.graphics.OperationalGraphic
import com.sovereignatlas.atlas.geo.graphics.ZoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private fun at(latitude: Double, longitude: Double) = AtlasCoordinate(latitude, longitude)

private fun triangle() = listOf(at(44.0, -93.0), at(45.0, -93.0), at(45.0, -92.0))

final class GraphicsGeoJsonMapperTest {
    @Test
    fun lineBecomesLineStringWithColorAndWidth() {
        val collection = GraphicsGeoJsonMapper.toFeatureCollection(
            listOf(
                OperationalGraphic.TacticalLine(
                    id = "phase-line",
                    points = listOf(at(44.0, -93.0), at(44.5, -92.5)),
                    colorHex = "#FFFF00",
                    width = 4.0f,
                )
            ),
        )
        val features = collection.features()!!
        assertEquals(1, features.size)
        val line = features[0].geometry() as LineString
        assertEquals(2, line.coordinates().size)
        assertEquals("phase-line", features[0].getStringProperty("id"))
        assertEquals("#FFFF00", features[0].getStringProperty("color"))
        assertEquals(4.0, features[0].getNumberProperty("width")!!.toDouble(), 0.0)
    }

    @Test
    fun lineUsesLongitudeAsXAndLatitudeAsY() {
        val collection = GraphicsGeoJsonMapper.toFeatureCollection(
            listOf(
                OperationalGraphic.TacticalLine(
                    id = "l",
                    points = listOf(at(10.0, 20.0), at(11.0, 21.0)),
                )
            ),
        )
        val line = collection.features()!![0].geometry() as LineString
        assertEquals(20.0, line.coordinates()[0].longitude(), 0.0)
        assertEquals(10.0, line.coordinates()[0].latitude(), 0.0)
    }

    @Test
    fun zoneBecomesClosedPolygonWithFillProperties() {
        val collection = GraphicsGeoJsonMapper.toFeatureCollection(
            listOf(
                OperationalGraphic.TacticalZone(
                    id = "medevac",
                    points = triangle(),
                    type = ZoneType.MEDEVAC,
                )
            ),
        )
        val feature = collection.features()!![0]
        val polygon = feature.geometry() as Polygon
        val ring = polygon.coordinates()[0]
        assertEquals(4, ring.size)
        assertEquals(ring.first().longitude(), ring.last().longitude(), 0.0)
        assertEquals(ring.first().latitude(), ring.last().latitude(), 0.0)
        assertEquals("medevac", feature.getStringProperty("id"))
        assertEquals("#00FF00", feature.getStringProperty("fillColor"))
        assertEquals(0.3, feature.getNumberProperty("fillOpacity")!!.toDouble(), 0.0001)
    }

    @Test
    fun alreadyClosedZoneIsNotDuplicated() {
        val closed = triangle() + at(44.0, -93.0)
        val collection = GraphicsGeoJsonMapper.toFeatureCollection(
            listOf(
                OperationalGraphic.TacticalZone(
                    id = "closed",
                    points = closed,
                    type = ZoneType.OBJECTIVE,
                )
            ),
        )
        val ring = (collection.features()!![0].geometry() as Polygon).coordinates()[0]
        assertEquals(4, ring.size)
    }

    @Test
    fun degenerateGeometriesAreDropped() {
        val collection = GraphicsGeoJsonMapper.toFeatureCollection(
            listOf(
                OperationalGraphic.TacticalLine("one-point", listOf(at(1.0, 2.0))),
                OperationalGraphic.TacticalZone("two-points", listOf(at(1.0, 2.0), at(3.0, 4.0)), ZoneType.RESTRICTED),
            ),
        )
        assertTrue(collection.features()!!.isEmpty())
    }

    @Test
    fun zoneCarriesItsOwnPalette() {
        val collection = GraphicsGeoJsonMapper.toFeatureCollection(
            listOf(
                OperationalGraphic.TacticalZone("z", triangle(), ZoneType.RESTRICTED),
                OperationalGraphic.TacticalZone("o", triangle(), ZoneType.OBJECTIVE),
            ),
        )
        val features = collection.features()!!
        assertEquals("#FF0000", features[0].getStringProperty("fillColor"))
        assertEquals("#FFA500", features[1].getStringProperty("fillColor"))
    }

    @Test
    fun lineDefaultsMatchDomainDefaults() {
        val collection = GraphicsGeoJsonMapper.toFeatureCollection(
            listOf(OperationalGraphic.TacticalLine("d", listOf(at(0.0, 0.0), at(1.0, 1.0)))),
        )
        val feature = collection.features()!![0]
        assertEquals("#FFFF00", feature.getStringProperty("color"))
        assertEquals(3.0, feature.getNumberProperty("width")!!.toDouble(), 0.0)
    }

    @Test
    fun firstPointIsPreservedAsRingStart() {
        val collection = GraphicsGeoJsonMapper.toFeatureCollection(
            listOf(OperationalGraphic.TacticalZone("z", triangle(), ZoneType.MEDEVAC)),
        )
        val ring = (collection.features()!![0].geometry() as Polygon).coordinates()[0]
        val first = ring.first() as Point
        assertEquals(-93.0, first.longitude(), 0.0)
        assertEquals(44.0, first.latitude(), 0.0)
    }
}
