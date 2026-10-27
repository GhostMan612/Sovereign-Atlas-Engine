// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KmlStreamingParserTest {

    @Test
    fun parsesPlacemarkPointWithExtendedData() {
        val kml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Placemark>
              <name>SAR Command Post</name>
              <description>Primary Base of Operations</description>
              <ExtendedData>
                <Data name="Agency">
                  <value>USFS</value>
                </Data>
                <Data name="OperationalStatus">
                  <value>Active</value>
                </Data>
              </ExtendedData>
              <Point>
                <coordinates>-105.2500,40.1250,1850</coordinates>
              </Point>
            </Placemark>
          </Document>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml, datasetName = "SAR Incident")
        assertEquals(1, dataset.featureCount)
        assertEquals(1, dataset.features.size)
        assertEquals(GisFormat.KML, dataset.format)
        assertEquals(GisSrid.WGS84, dataset.srid)

        val feature = dataset.features.first()
        assertTrue(feature.geometry is RenderGeometry.Point)
        val pt = feature.geometry as RenderGeometry.Point
        assertEquals(-105.2500, pt.coordinates.longitude, 1e-4)
        assertEquals(40.1250, pt.coordinates.latitude, 1e-4)

        assertEquals(RenderProperty.Text("SAR Command Post"), feature.properties["name"])
        assertEquals(RenderProperty.Text("Primary Base of Operations"), feature.properties["description"])
        assertEquals(RenderProperty.Text("USFS"), feature.properties["Agency"])
        assertEquals(RenderProperty.Text("Active"), feature.properties["OperationalStatus"])
    }

    @Test
    fun parsesLineStringWithWhitespaceSeparatedCoordinates() {
        val kml = """
        <kml>
          <Placemark>
            <name>Ridge Trail</name>
            <LineString>
              <coordinates>
                -105.2500,40.1250,0
                -105.2600,40.1350,100
                -105.2700,40.1450,200
              </coordinates>
            </LineString>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        val feature = dataset.features.first()
        assertTrue(feature.geometry is RenderGeometry.LineString)
        val line = feature.geometry as RenderGeometry.LineString
        assertEquals(3, line.coordinates.size)
        assertEquals(-105.2500, line.coordinates[0].longitude, 1e-4)
        assertEquals(-105.2700, line.coordinates[2].longitude, 1e-4)
    }

    @Test
    fun parsesPolygonAndClosesUnclosedRings() {
        val kml = """
        <kml>
          <Placemark>
            <name>Containment Zone</name>
            <Polygon>
              <outerBoundaryIs>
                <LinearRing>
                  <coordinates>
                    -105.0,40.0
                    -104.0,40.0
                    -104.0,41.0
                    -105.0,41.0
                  </coordinates>
                </LinearRing>
              </outerBoundaryIs>
            </Polygon>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        val feature = dataset.features.first()
        assertTrue(feature.geometry is RenderGeometry.Polygon)
        val poly = feature.geometry as RenderGeometry.Polygon
        assertEquals(1, poly.coordinates.size)
        assertEquals(5, poly.coordinates[0].size)
        assertEquals(poly.coordinates[0].first(), poly.coordinates[0].last())
    }

    @Test
    fun parsesMultiGeometryPointsAndLineStringAsSeparateFeatures() {
        val kml = """
        <kml>
          <Placemark>
            <name>Crew Positions</name>
            <MultiGeometry>
              <Point>
                <coordinates>-105.2500,40.1250</coordinates>
              </Point>
              <Point>
                <coordinates>-105.2600,40.1350</coordinates>
              </Point>
              <LineString>
                <coordinates>-105.2500,40.1250 -105.2600,40.1350</coordinates>
              </LineString>
            </MultiGeometry>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(3, dataset.features.size)
        assertTrue(dataset.features[0].geometry is RenderGeometry.Point)
        assertTrue(dataset.features[1].geometry is RenderGeometry.Point)
        assertTrue(dataset.features[2].geometry is RenderGeometry.LineString)
        assertEquals(
            RenderProperty.Text("Crew Positions"),
            dataset.features[2].properties["name"],
        )
        val firstPoint = dataset.features[0].geometry as RenderGeometry.Point
        assertEquals(-105.2500, firstPoint.coordinates.longitude, 1e-4)
        val secondPoint = dataset.features[1].geometry as RenderGeometry.Point
        assertEquals(-105.2600, secondPoint.coordinates.longitude, 1e-4)
    }

    @Test
    fun parsesMultiGeometryPolygonsAsMultiPolygon() {
        val kml = """
        <kml>
          <Placemark>
            <name>Two Zones</name>
            <MultiGeometry>
              <Polygon>
                <outerBoundaryIs>
                  <LinearRing>
                    <coordinates>-105.0,40.0 -104.0,40.0 -104.0,41.0 -105.0,41.0</coordinates>
                  </LinearRing>
                </outerBoundaryIs>
              </Polygon>
              <Polygon>
                <outerBoundaryIs>
                  <LinearRing>
                    <coordinates>-103.0,40.0 -102.0,40.0 -102.0,41.0 -103.0,41.0</coordinates>
                  </LinearRing>
                </outerBoundaryIs>
                <innerBoundaryIs>
                  <LinearRing>
                    <coordinates>-102.8,40.2 -102.2,40.2 -102.2,40.8 -102.8,40.8</coordinates>
                  </LinearRing>
                </innerBoundaryIs>
              </Polygon>
            </MultiGeometry>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        val feature = dataset.features.first()
        assertTrue(feature.geometry is RenderGeometry.MultiPolygon)
        val multi = feature.geometry as RenderGeometry.MultiPolygon
        assertEquals(2, multi.coordinates.size)
        assertEquals(1, multi.coordinates[0].size)
        assertEquals(5, multi.coordinates[0][0].size)
        assertEquals(2, multi.coordinates[1].size)
        assertEquals(5, multi.coordinates[1][1].size)
    }

    @Test
    fun parsesSinglePolygonMultiGeometryAsPolygon() {
        val kml = """
        <kml>
          <Placemark>
            <name>One Zone</name>
            <MultiGeometry>
              <Polygon>
                <outerBoundaryIs>
                  <LinearRing>
                    <coordinates>-105.0,40.0 -104.0,40.0 -104.0,41.0 -105.0,41.0</coordinates>
                  </LinearRing>
                </outerBoundaryIs>
              </Polygon>
            </MultiGeometry>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        assertTrue(dataset.features.first().geometry is RenderGeometry.Polygon)
    }

    @Test
    fun parsesNamespacedPlacemarkAndCoordinates() {
        val kml = """
        <kml:kml xmlns:kml="http://www.opengis.net/kml/2.2">
          <kml:Document>
            <kml:Placemark>
              <kml:name>Prefixed Post</kml:name>
              <kml:Point>
                <kml:coordinates>-105.2500,40.1250</kml:coordinates>
              </kml:Point>
            </kml:Placemark>
          </kml:Document>
        </kml:kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        assertTrue(dataset.features.first().geometry is RenderGeometry.Point)
        assertEquals(
            RenderProperty.Text("Prefixed Post"),
            dataset.features.first().properties["name"],
        )
    }

    @Test
    fun parsesCdataNameAndEscapedDescription() {
        val kml = """
        <kml>
          <Placemark>
            <name><![CDATA[A & B Post]]></name>
            <description>Fish &amp; Chips</description>
            <Point>
              <coordinates>-105.2500,40.1250</coordinates>
            </Point>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        assertEquals(
            RenderProperty.Text("A & B Post"),
            dataset.features.first().properties["name"],
        )
        assertEquals(
            RenderProperty.Text("Fish & Chips"),
            dataset.features.first().properties["description"],
        )
    }

    @Test
    fun parseFromStreamMatchesParseStringAcrossReadChunks() {
        val coordinates = (0 until 600).joinToString(" ") { index ->
            val lon = -105.0 - index * 0.0001
            "$lon,40.1250,0"
        }
        val kml = "<kml><Placemark><name>Long Trail</name>" +
            "<LineString><coordinates>$coordinates</coordinates></LineString>" +
            "</Placemark></kml>"
        assertTrue(kml.length > 8192)

        val fromString = KmlStreamingParser.parseString(kml)
        val stream = ByteArrayInputStream(kml.toByteArray(StandardCharsets.UTF_8))
        val fromStream = KmlStreamingParser.parse(stream)
        assertEquals(1, fromString.features.size)
        assertEquals(1, fromStream.features.size)
        val line = fromStream.features.first().geometry as RenderGeometry.LineString
        assertEquals(600, line.coordinates.size)
        assertEquals(-105.0, line.coordinates[0].longitude, 1e-9)
        assertEquals(-105.0 - 599 * 0.0001, line.coordinates[599].longitude, 1e-9)
    }

    @Test
    fun parsesNestedMultiGeometryWithoutLosingPoints() {
        val kml = """
        <kml>
          <Placemark>
            <name>Nested</name>
            <MultiGeometry>
              <MultiGeometry>
                <Point>
                  <coordinates>-105.0,40.0</coordinates>
                </Point>
              </MultiGeometry>
              <Point>
                <coordinates>-104.0,41.0</coordinates>
              </Point>
            </MultiGeometry>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(2, dataset.features.size)
        val first = dataset.features[0].geometry as RenderGeometry.Point
        val second = dataset.features[1].geometry as RenderGeometry.Point
        assertEquals(-105.0, first.coordinates.longitude, 1e-9)
        assertEquals(-104.0, second.coordinates.longitude, 1e-9)
    }

    @Test
    fun cdataContainingClosingTagTextDoesNotTerminatePlacemark() {
        val kml = """
        <kml>
          <Placemark>
            <name>Tagged Note</name>
            <description><![CDATA[Note </Placemark> and <Point> here]]></description>
            <Point>
              <coordinates>-105.2500,40.1250</coordinates>
            </Point>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        assertTrue(dataset.features.first().geometry is RenderGeometry.Point)
        assertEquals(
            RenderProperty.Text("Note </Placemark> and <Point> here"),
            dataset.features.first().properties["description"],
        )
    }

    @Test
    fun extendedDataMissingValueDoesNotTakeLaterValue() {
        val kml = """
        <kml>
          <Placemark>
            <name>Data Check</name>
            <ExtendedData>
              <Data name="Empty"></Data>
              <Data name="Filled"><value>Yes</value></Data>
              <Data othername="Nope" name="Real"><value>Ok</value></Data>
            </ExtendedData>
            <Point>
              <coordinates>-105.2500,40.1250</coordinates>
            </Point>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        val properties = dataset.features.first().properties
        assertTrue(!properties.containsKey("Empty"))
        assertEquals(RenderProperty.Text("Yes"), properties["Filled"])
        assertEquals(RenderProperty.Text("Ok"), properties["Real"])
        assertTrue(!properties.containsKey("Nope"))
    }

    @Test
    fun parsesPlacemarkWithOpeningTagSplitAcrossReadChunks() {
        val longId = "x".repeat(9000)
        val kml = "<kml><Placemark id=\"$longId\"><name>Long Tag</name>" +
            "<Point><coordinates>-105.2500,40.1250</coordinates></Point>" +
            "</Placemark></kml>"
        val stream = ByteArrayInputStream(kml.toByteArray(StandardCharsets.UTF_8))
        val dataset = KmlStreamingParser.parse(stream)
        assertEquals(1, dataset.features.size)
        assertTrue(dataset.features.first().geometry is RenderGeometry.Point)
        assertEquals(
            RenderProperty.Text("Long Tag"),
            dataset.features.first().properties["name"],
        )
    }

    @Test
    fun oversizedPlacemarkIsSkippedAndLaterPlacemarkStillParses() {
        val oversizedDescription = "a".repeat(2_100_000)
        val kml = "<kml><Placemark><name>Too Big</name>" +
            "<description>$oversizedDescription</description>" +
            "<Point><coordinates>-105.0,40.0</coordinates></Point></Placemark>" +
            "<Placemark><name>Small</name>" +
            "<Point><coordinates>-104.0,41.0</coordinates></Point></Placemark></kml>"
        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        assertEquals(
            RenderProperty.Text("Small"),
            dataset.features.first().properties["name"],
        )
    }

    @Test
    fun mixedPointAndPolygonInMultiGeometryStaySeparateFeatures() {
        val kml = """
        <kml>
          <Placemark>
            <name>Mixed</name>
            <MultiGeometry>
              <Point>
                <coordinates>-105.0,40.0</coordinates>
              </Point>
              <Polygon>
                <outerBoundaryIs>
                  <LinearRing>
                    <coordinates>-105.0,40.0 -104.0,40.0 -104.0,41.0 -105.0,41.0</coordinates>
                  </LinearRing>
                </outerBoundaryIs>
              </Polygon>
            </MultiGeometry>
          </Placemark>
        </kml>
        """.trimIndent()

        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(2, dataset.features.size)
        assertTrue(dataset.features[0].geometry is RenderGeometry.Point)
        assertTrue(dataset.features[1].geometry is RenderGeometry.Polygon)
    }

    @Test
    fun justBelowCapPlacemarkIsNotDiscardedByTrailingContent() {
        val nearCapDescription = "a".repeat(1_995_000)
        val kml = "<kml><Placemark><name>Near Cap</name>" +
            "<description>$nearCapDescription</description>" +
            "<Point><coordinates>-105.0,40.0</coordinates></Point></Placemark>" +
            "<Placemark><name>Small</name>" +
            "<Point><coordinates>-104.0,41.0</coordinates></Point></Placemark></kml>"
        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(2, dataset.features.size)
        assertEquals(
            RenderProperty.Text("Near Cap"),
            dataset.features[0].properties["name"],
        )
        assertEquals(
            RenderProperty.Text("Small"),
            dataset.features[1].properties["name"],
        )
    }

    @Test
    fun oversizedCdataTerminatorNearChunkEndDoesNotReplayFakeClosingTag() {
        val chunkSize = 8192
        val prefixAndFakeLength = 167
        val paddingLength = chunkSize * 246 - 30 - prefixAndFakeLength
        val padding = "a".repeat(paddingLength)
        val kml = "<kml><Placemark><name>Too Big</name>" +
            "<description><![CDATA[$padding</Placemark>" +
            "<Placemark><name>Fake2</name>" +
            "<Point><coordinates>-103.0,42.0</coordinates></Point></Placemark>]]></description>" +
            "</Placemark>" +
            "<Placemark><name>Small</name>" +
            "<Point><coordinates>-104.0,41.0</coordinates></Point></Placemark></kml>"
        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        assertEquals(
            RenderProperty.Text("Small"),
            dataset.features.first().properties["name"],
        )
    }

    @Test
    fun oversizedCdataTerminatorAtChunkBoundaryDoesNotReplayFakeClosingTag() {
        val chunkSize = 8192
        val prefixAndFakeLength = 167
        val paddingLength = chunkSize * 246 - prefixAndFakeLength
        val padding = "a".repeat(paddingLength)
        val kml = "<kml><Placemark><name>Too Big</name>" +
            "<description><![CDATA[$padding</Placemark>" +
            "<Placemark><name>Fake2</name>" +
            "<Point><coordinates>-103.0,42.0</coordinates></Point></Placemark>]]></description>" +
            "</Placemark>" +
            "<Placemark><name>Small</name>" +
            "<Point><coordinates>-104.0,41.0</coordinates></Point></Placemark></kml>"
        val dataset = KmlStreamingParser.parseString(kml)
        assertEquals(1, dataset.features.size)
        assertEquals(
            RenderProperty.Text("Small"),
            dataset.features.first().properties["name"],
        )
    }
}
