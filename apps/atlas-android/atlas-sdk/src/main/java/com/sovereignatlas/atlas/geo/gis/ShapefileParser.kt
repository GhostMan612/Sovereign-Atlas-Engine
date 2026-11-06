// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.atan
import kotlin.math.exp

object ShapefileParser {

    const val MAX_VERTICES = 100_000
    private const val WEB_MERCATOR_HALF_CIRCUMFERENCE = 20037508.342789244

    fun detectSrid(prjText: String?): GisSrid {
        if (prjText.isNullOrBlank()) return GisSrid.WGS84
        val upper = prjText.uppercase()
        return when {
            upper.contains("4326") || upper.contains("WGS_1984") && !upper.contains("MERCATOR") && !upper.contains("UTM") -> GisSrid.WGS84
            upper.contains("3857") || upper.contains("900913") || upper.contains("PSEUDO-MERCATOR") || upper.contains("POPULAR VISUALISATION") -> GisSrid.WEB_MERCATOR
            upper.contains("UTM") && upper.contains("WGS_1984") -> GisSrid.UTM
            else -> GisSrid.UNKNOWN
        }
    }

    fun parse(
        shpBytes: ByteArray,
        dbfBytes: ByteArray? = null,
        prjText: String? = null,
        datasetId: String = UUID.randomUUID().toString(),
        datasetName: String = "Shapefile Layer",
        sourcePathOrUri: String? = null,
    ): GisDataset {
        val srid = detectSrid(prjText)
        if (srid == GisSrid.UNKNOWN) {
            throw IllegalArgumentException("Unsupported coordinate reference system in .prj file: unknown CRS is never assumed")
        }

        val geometries = parseShp(shpBytes, srid)
        val attributes = if (dbfBytes != null) parseDbf(dbfBytes) else emptyList()

        val features = ArrayList<GisFeature>(geometries.size)
        for (i in geometries.indices) {
            val geom = geometries[i]
            val props = if (i < attributes.size) attributes[i] else emptyMap()
            features.add(
                GisFeature(
                    id = "shp-feature-$i",
                    geometry = geom,
                    properties = props,
                ),
            )
        }

        return GisDataset(
            id = datasetId,
            name = datasetName,
            format = GisFormat.SHAPEFILE,
            srid = srid,
            featureCount = features.size,
            features = features,
            sourcePathOrUri = sourcePathOrUri,
        )
    }

    private fun parseShp(shpBytes: ByteArray, srid: GisSrid): List<RenderGeometry> {
        if (shpBytes.size < 100) return emptyList()

        val headerBuffer = ByteBuffer.wrap(shpBytes, 0, 100)
        headerBuffer.order(ByteOrder.BIG_ENDIAN)
        val fileCode = headerBuffer.int
        if (fileCode != 9994) return emptyList()

        headerBuffer.order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.position(28)
        val version = headerBuffer.int
        if (version != 1000) return emptyList()

        val geometries = ArrayList<RenderGeometry>()
        var offset = 100
        var totalVertices = 0

        while (offset + 8 <= shpBytes.size && totalVertices < MAX_VERTICES) {
            val recHeader = ByteBuffer.wrap(shpBytes, offset, 8)
            recHeader.order(ByteOrder.BIG_ENDIAN)
            recHeader.int
            val contentLengthWords = recHeader.int
            val contentLengthBytes = contentLengthWords * 2

            offset += 8
            if (offset + contentLengthBytes > shpBytes.size) break

            val recBuffer = ByteBuffer.wrap(shpBytes, offset, contentLengthBytes)
            recBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val shapeType = recBuffer.int

            val geom: RenderGeometry? = when (shapeType) {
                1, 11, 21 -> parsePoint(recBuffer, srid)
                3, 13, 23 -> parsePolyLine(recBuffer, srid)
                5, 15, 25 -> parsePolygon(recBuffer, srid)
                else -> null
            }

            if (geom != null) {
                val vCount = geom.allCoordinates().size
                if (totalVertices + vCount <= MAX_VERTICES) {
                    geometries.add(geom)
                    totalVertices += vCount
                }
            }

            offset += contentLengthBytes
        }

        return geometries
    }

    private fun transformCoord(x: Double, y: Double, srid: GisSrid): LngLat {
        return when (srid) {
            GisSrid.WEB_MERCATOR -> {
                val lon = (x / WEB_MERCATOR_HALF_CIRCUMFERENCE) * 180.0
                val latRad = 2.0 * atan(exp((y / WEB_MERCATOR_HALF_CIRCUMFERENCE) * Math.PI)) - (Math.PI / 2.0)
                val lat = Math.toDegrees(latRad)
                LngLat(lon.coerceIn(-180.0, 180.0), lat.coerceIn(-85.0511, 85.0511))
            }
            else -> LngLat(x.coerceIn(-180.0, 180.0), y.coerceIn(-90.0, 90.0))
        }
    }

    private fun parsePoint(buffer: ByteBuffer, srid: GisSrid): RenderGeometry.Point? {
        if (buffer.remaining() < 16) return null
        val x = buffer.double
        val y = buffer.double
        return RenderGeometry.Point(transformCoord(x, y, srid))
    }

    private fun parsePolyLine(buffer: ByteBuffer, srid: GisSrid): RenderGeometry.LineString? {
        if (buffer.remaining() < 40) return null
        buffer.position(buffer.position() + 32)
        val numParts = buffer.int
        val numPoints = buffer.int
        if (numParts <= 0 || numPoints <= 0) return null
        if (buffer.remaining() < numParts * 4 + numPoints * 16) return null

        val parts = IntArray(numParts)
        for (i in 0 until numParts) {
            parts[i] = buffer.int
        }

        val points = ArrayList<LngLat>(numPoints)
        for (i in 0 until numPoints) {
            val x = buffer.double
            val y = buffer.double
            points.add(transformCoord(x, y, srid))
        }

        return if (points.size >= 2) {
            RenderGeometry.LineString(points)
        } else if (points.isNotEmpty()) {
            RenderGeometry.LineString(listOf(points[0], points[0]))
        } else {
            null
        }
    }

    private fun parsePolygon(buffer: ByteBuffer, srid: GisSrid): RenderGeometry.Polygon? {
        if (buffer.remaining() < 40) return null
        buffer.position(buffer.position() + 32)
        val numParts = buffer.int
        val numPoints = buffer.int
        if (numParts <= 0 || numPoints <= 0) return null
        if (buffer.remaining() < numParts * 4 + numPoints * 16) return null

        val parts = IntArray(numParts)
        for (i in 0 until numParts) {
            parts[i] = buffer.int
        }

        val allPoints = ArrayList<LngLat>(numPoints)
        for (i in 0 until numPoints) {
            val x = buffer.double
            val y = buffer.double
            allPoints.add(transformCoord(x, y, srid))
        }

        val rings = ArrayList<List<LngLat>>(numParts)
        for (p in 0 until numParts) {
            val start = parts[p]
            val end = if (p + 1 < numParts) parts[p + 1] else numPoints
            if (end > start) {
                val ring = ArrayList<LngLat>(allPoints.subList(start, end))
                if (ring.isNotEmpty()) {
                    val first = ring.first()
                    val last = ring.last()
                    if (first.latitude != last.latitude || first.longitude != last.longitude) {
                        ring.add(first)
                    }
                    rings.add(ring)
                }
            }
        }

        return if (rings.isNotEmpty()) {
            RenderGeometry.Polygon(rings)
        } else {
            null
        }
    }

    private data class DbfField(
        val name: String,
        val type: Char,
        val length: Int,
        val decimalCount: Int,
    )

    private fun parseDbf(dbfBytes: ByteArray): List<Map<String, RenderProperty>> {
        if (dbfBytes.size < 32) return emptyList()

        val buffer = ByteBuffer.wrap(dbfBytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.get()
        buffer.get()
        buffer.get()
        buffer.get()
        val numRecords = buffer.int
        val headerLength = buffer.short.toInt() and 0xFFFF
        val recordLength = buffer.short.toInt() and 0xFFFF

        val fields = ArrayList<DbfField>()
        var fieldOffset = 32
        while (fieldOffset + 32 <= headerLength && fieldOffset + 32 <= dbfBytes.size) {
            if (dbfBytes[fieldOffset].toInt() == 0x0D) break
            val nameBytes = ByteArray(11)
            System.arraycopy(dbfBytes, fieldOffset, nameBytes, 0, 11)
            val nullIdx = nameBytes.indexOf(0.toByte())
            val rawNameLen = if (nullIdx >= 0) nullIdx else 11
            val fieldName = String(nameBytes, 0, rawNameLen, StandardCharsets.US_ASCII).trim()

            val fieldType = dbfBytes[fieldOffset + 11].toInt().toChar()
            val fieldLen = dbfBytes[fieldOffset + 16].toInt() and 0xFF
            val decimalCount = dbfBytes[fieldOffset + 17].toInt() and 0xFF

            fields.add(DbfField(fieldName, fieldType, fieldLen, decimalCount))
            fieldOffset += 32
        }

        val records = ArrayList<Map<String, RenderProperty>>(numRecords)
        var recordPos = headerLength

        for (r in 0 until numRecords) {
            if (recordPos + recordLength > dbfBytes.size) break
            val deletedFlag = dbfBytes[recordPos].toInt().toChar()
            if (deletedFlag == ' ') {
                val row = HashMap<String, RenderProperty>()
                var colPos = recordPos + 1
                for (field in fields) {
                    if (colPos + field.length <= dbfBytes.size) {
                        val valStr = String(dbfBytes, colPos, field.length, StandardCharsets.UTF_8).trim()
                        when (field.type) {
                            'N', 'F' -> {
                                val num = valStr.toDoubleOrNull()
                                if (num != null) {
                                    row[field.name] = RenderProperty.Number(num)
                                } else if (valStr.isNotBlank()) {
                                    row[field.name] = RenderProperty.Text(valStr)
                                }
                            }
                            'L' -> {
                                val flag = valStr.uppercase() in listOf("T", "Y", "TRUE")
                                row[field.name] = RenderProperty.Flag(flag)
                            }
                            else -> {
                                if (valStr.isNotBlank()) {
                                    row[field.name] = RenderProperty.Text(valStr)
                                }
                            }
                        }
                    }
                    colPos += field.length
                }
                records.add(row)
            }
            recordPos += recordLength
        }

        return records
    }
}
