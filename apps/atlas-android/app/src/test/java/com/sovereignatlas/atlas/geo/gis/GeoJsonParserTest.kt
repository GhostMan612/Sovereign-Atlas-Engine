// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonParserTest {

    @Test
    fun parsesFeatureCollectionWithPointsAndLines() {
        val geojson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "id": "pt-1",
              "properties": {
                "name": "Lookout Point",
                "elevation": 2450.5,
                "operational": true
              },
              "geometry": {
                "type": "Point",
                "coordinates": [-105.1234, 40.5678]
              }
            },
            {
              "type": "Feature",
              "id": "line-1",
              "properties": {
                "name": "Access Trail"
              },
              "geometry": {
                "type": "LineString",
                "coordinates": [
                  [-105.1234, 40.5678],
                  [-105.1300, 40.5700]
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val dataset = GeoJsonParser.parse(geojson, datasetName = "Test Features")
        assertEquals(2, dataset.featureCount)
        assertEquals(2, dataset.features.size)
        assertEquals("Test Features", dataset.name)
        assertEquals(GisFormat.GEOJSON, dataset.format)
        assertEquals(GisSrid.WGS84, dataset.srid)

        val ptFeature = dataset.features[0]
        assertEquals("pt-1", ptFeature.id)
        assertTrue(ptFeature.geometry is RenderGeometry.Point)
        val pt = ptFeature.geometry as RenderGeometry.Point
        assertEquals(-105.1234, pt.coordinates.longitude, 1e-4)
        assertEquals(40.5678, pt.coordinates.latitude, 1e-4)
        assertEquals(RenderProperty.Text("Lookout Point"), ptFeature.properties["name"])
        assertEquals(RenderProperty.Number(2450.5), ptFeature.properties["elevation"])
        assertEquals(RenderProperty.Flag(true), ptFeature.properties["operational"])

        val lineFeature = dataset.features[1]
        assertEquals("line-1", lineFeature.id)
        assertTrue(lineFeature.geometry is RenderGeometry.LineString)
        val line = lineFeature.geometry as RenderGeometry.LineString
        assertEquals(2, line.coordinates.size)
    }

    @Test
    fun parsesPolygonFeature() {
        val geojson = """
        {
          "type": "Feature",
          "id": "poly-1",
          "properties": { "zone": "Incident Perimeter" },
          "geometry": {
            "type": "Polygon",
            "coordinates": [
              [
                [-105.0, 40.0],
                [-104.0, 40.0],
                [-104.0, 41.0],
                [-105.0, 41.0],
                [-105.0, 40.0]
              ]
            ]
          }
        }
        """.trimIndent()

        val dataset = GeoJsonParser.parse(geojson)
        assertEquals(1, dataset.features.size)
        val f = dataset.features.first()
        assertTrue(f.geometry is RenderGeometry.Polygon)
        val poly = f.geometry as RenderGeometry.Polygon
        assertEquals(1, poly.coordinates.size)
        assertEquals(5, poly.coordinates[0].size)
    }

    @Test
    fun dropsInvalidCoordinates() {
        val invalidJson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "id": "invalid-1",
              "geometry": {
                "type": "Point",
                "coordinates": [-200.0, 40.0]
              }
            },
            {
              "type": "Feature",
              "id": "valid-1",
              "geometry": {
                "type": "Point",
                "coordinates": [-105.0, 40.0]
              }
            }
          ]
        }
        """.trimIndent()

        val dataset = GeoJsonParser.parse(invalidJson)
        assertEquals(1, dataset.features.size)
        assertEquals("valid-1", dataset.features[0].id)
    }
}
