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
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShapefileParserTest {

    @Test
    fun detectsSridFromPrj() {
        val wgs84Prj = """GEOGCS["GCS_WGS_1984",DATUM["D_WGS_1984",SPHEROID["WGS_1984",6378137.0,298.257223563]],PRIMEM["Greenwich",0.0],UNIT["Degree",0.0174532925199433]]"""
        assertEquals(GisSrid.WGS84, ShapefileParser.detectSrid(wgs84Prj))

        val webMercatorPrj = """PROJCS["WGS_1984_Web_Mercator_Auxiliary_Sphere",GEOGCS["GCS_WGS_1984",DATUM["D_WGS_1984",SPHEROID["WGS_1984",6378137.0,298.257223563]],PRIMEM["Greenwich",0.0],UNIT["Degree",0.0174532925199433]],PROJECTION["Mercator_Auxiliary_Sphere"],PARAMETER["False_Easting",0.0],PARAMETER["False_Northing",0.0],PARAMETER["Central_Meridian",0.0],PARAMETER["Standard_Parallel_1",0.0],PARAMETER["Auxiliary_Sphere_Type",0.0],UNIT["Meter",1.0],AUTHORITY["EPSG","3857"]]"""
        assertEquals(GisSrid.WEB_MERCATOR, ShapefileParser.detectSrid(webMercatorPrj))

        val utmPrj = """PROJCS["WGS 84 / UTM zone 13N",GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]],PROJECTION["Transverse_Mercator"],PARAMETER["latitude_of_origin",0],PARAMETER["central_meridian",-105],PARAMETER["scale_factor",0.9996],PARAMETER["false_easting",500000],PARAMETER["false_northing",0],UNIT["metre",1]]"""
        assertEquals(GisSrid.UTM, ShapefileParser.detectSrid(utmPrj))

        val unknownPrj = """PROJCS["NAD_1983_StatePlane_Colorado_Central_FIPS_0502_Feet",GEOGCS["GCS_North_American_1983",DATUM["D_North_American_1983",SPHEROID["GRS_1980",6378137.0,298.257222101]]]"""
        assertEquals(GisSrid.UNKNOWN, ShapefileParser.detectSrid(unknownPrj))
    }

    @Test
    fun refusesUnknownProjection() {
        val unknownPrj = """PROJCS["Custom_Local_CRS"]"""
        try {
            ShapefileParser.parse(
                shpBytes = ByteArray(100),
                prjText = unknownPrj,
            )
            fail("Expected IllegalArgumentException for unknown CRS")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("unknown CRS is never assumed") == true)
        }
    }

    @Test
    fun decodesSyntheticShpPointRecord() {
        val totalBytes = 100 + 8 + 20
        val buffer = ByteBuffer.allocate(totalBytes)

        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(9994)
        for (i in 0 until 5) buffer.putInt(0)
        buffer.putInt(totalBytes / 2)

        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1000)
        buffer.putInt(1)
        buffer.putDouble(-105.0)
        buffer.putDouble(40.0)
        buffer.putDouble(-105.0)
        buffer.putDouble(40.0)
        for (i in 0 until 4) buffer.putDouble(0.0)

        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(1)
        buffer.putInt(10)

        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1)
        buffer.putDouble(-105.123)
        buffer.putDouble(40.456)

        val dataset = ShapefileParser.parse(
            shpBytes = buffer.array(),
            datasetName = "Points",
        )

        assertEquals(1, dataset.features.size)
        val f = dataset.features.first()
        assertTrue(f.geometry is RenderGeometry.Point)
        val pt = f.geometry as RenderGeometry.Point
        assertEquals(-105.123, pt.coordinates.longitude, 1e-3)
        assertEquals(40.456, pt.coordinates.latitude, 1e-3)
    }

    @Test
    fun decodesSyntheticDbfAttributes() {
        val shpTotalBytes = 100 + 8 + 20
        val shpBuffer = ByteBuffer.allocate(shpTotalBytes)
        shpBuffer.order(ByteOrder.BIG_ENDIAN)
        shpBuffer.putInt(9994)
        for (i in 0 until 5) shpBuffer.putInt(0)
        shpBuffer.putInt(shpTotalBytes / 2)
        shpBuffer.order(ByteOrder.LITTLE_ENDIAN)
        shpBuffer.putInt(1000)
        shpBuffer.putInt(1)
        for (i in 0 until 8) shpBuffer.putDouble(0.0)
        shpBuffer.order(ByteOrder.BIG_ENDIAN)
        shpBuffer.putInt(1)
        shpBuffer.putInt(10)
        shpBuffer.order(ByteOrder.LITTLE_ENDIAN)
        shpBuffer.putInt(1)
        shpBuffer.putDouble(-105.0)
        shpBuffer.putDouble(40.0)

        val headerLen = 32 + 32 + 1
        val recordLen = 1 + 10
        val dbfTotalBytes = headerLen + recordLen
        val dbfBuffer = ByteBuffer.allocate(dbfTotalBytes)
        dbfBuffer.order(ByteOrder.LITTLE_ENDIAN)
        dbfBuffer.put(0x03.toByte())
        dbfBuffer.put(24.toByte()); dbfBuffer.put(10.toByte()); dbfBuffer.put(23.toByte())
        dbfBuffer.putInt(1)
        dbfBuffer.putShort(headerLen.toShort())
        dbfBuffer.putShort(recordLen.toShort())
        for (i in 0 until 20) dbfBuffer.put(0.toByte())

        val fieldName = "NAME\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
        dbfBuffer.put(fieldName.toByteArray(Charsets.US_ASCII), 0, 11)
        dbfBuffer.put('C'.code.toByte())
        dbfBuffer.putInt(0)
        dbfBuffer.put(10.toByte())
        dbfBuffer.put(0.toByte())
        for (i in 0 until 14) dbfBuffer.put(0.toByte())
        dbfBuffer.put(0x0D.toByte())

        dbfBuffer.put(' '.code.toByte())
        val valStr = "Campground"
        dbfBuffer.put(valStr.toByteArray(Charsets.US_ASCII), 0, 10)

        val dataset = ShapefileParser.parse(
            shpBytes = shpBuffer.array(),
            dbfBytes = dbfBuffer.array(),
        )

        assertEquals(1, dataset.features.size)
        val f = dataset.features.first()
        assertEquals(RenderProperty.Text("Campground"), f.properties["NAME"])
    }
}
